// Administrative recovery only. Never delegate registry access to web CI/runtime.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,dirname,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes,createCipheriv,createDecipheriv,createHash} from 'node:crypto';
import {initializeApp,applicationDefault,deleteApp} from 'firebase-admin/app';
import {getFirestore,Firestore,Timestamp,GeoPoint,DocumentReference} from 'firebase-admin/firestore';

const format='radiotech-identity-registry/v1';
const magic=Buffer.from('RTIDENTITY1');
const repository=resolve(fileURLToPath(new URL('../../',import.meta.url)));
const maximumBytes=50*1024*1024;
const compare=(a,b)=>a<b?-1:a>b?1:0;

export function encodeValue(value){
 if(value===null)return ['null'];
 if(typeof value==='string'||typeof value==='boolean')return [typeof value,value];
 if(typeof value==='number'){if(!Number.isFinite(value))throw new Error('Non-finite registry value.');return ['number',value];}
 if(value instanceof Timestamp)return ['timestamp',value.seconds,value.nanoseconds];
 if(value instanceof GeoPoint)return ['geo',value.latitude,value.longitude];
 if(value instanceof DocumentReference)return ['reference',value.path];
 if(Buffer.isBuffer(value)||value instanceof Uint8Array)return ['bytes',Buffer.from(value).toString('base64')];
 if(value instanceof Date)return ['date',value.toISOString()];
 if(Array.isArray(value))return ['array',value.map(encodeValue)];
 if(value&&Object.getPrototypeOf(value)===Object.prototype)return ['object',Object.entries(value).sort(([a],[b])=>compare(a,b)).map(([key,item])=>[key,encodeValue(item)])];
 throw new Error('Unsupported registry value.');
}
export function decodeValue(value,db){
 if(!Array.isArray(value))throw new Error('Invalid typed registry value.');
 switch(value[0]){
  case 'null':return null;
  case 'string':case 'boolean':case 'number':return value[1];
  case 'timestamp':return new Timestamp(value[1],value[2]);
  case 'geo':return new GeoPoint(value[1],value[2]);
  case 'reference':return db.doc(value[1]);
  case 'bytes':return Buffer.from(value[1],'base64');
  case 'date':return new Date(value[1]);
  case 'array':return value[1].map(item=>decodeValue(item,db));
  case 'object':return Object.fromEntries(value[1].map(([key,item])=>[key,decodeValue(item,db)]));
  default:throw new Error('Unsupported registry type.');
 }
}
export function seal(snapshot,key){
 if(key.length!==32)throw new Error('A separate 32-byte administrative backup key is required.');
 const nonce=randomBytes(12),cipher=createCipheriv('aes-256-gcm',key,nonce);
 cipher.setAAD(Buffer.from(format));
 const plaintext=Buffer.from(JSON.stringify({format,...snapshot}));
 if(plaintext.length>maximumBytes)throw new Error('Registry backup exceeds its limit.');
 const encrypted=Buffer.concat([cipher.update(plaintext),cipher.final()]);
 return Buffer.concat([magic,nonce,cipher.getAuthTag(),encrypted]);
}
export function open(archive,key,expectedProject){
 if(key.length!==32||archive.length>maximumBytes+100||archive.length<magic.length+29||!archive.subarray(0,magic.length).equals(magic))throw new Error('Invalid encrypted registry archive.');
 const offset=magic.length,decipher=createDecipheriv('aes-256-gcm',key,archive.subarray(offset,offset+12));
 decipher.setAAD(Buffer.from(format));decipher.setAuthTag(archive.subarray(offset+12,offset+28));
 const data=JSON.parse(Buffer.concat([decipher.update(archive.subarray(offset+28)),decipher.final()]).toString('utf8'));
 if(data.format!==format||data.registryProject!==expectedProject||!Array.isArray(data.documents)||data.documents.length>50000||!Array.isArray(data.secrets))throw new Error('Registry/project identity mismatch.');
 const paths=new Set();
 for(const doc of data.documents){
  if(typeof doc.path!=='string'||doc.path.split('/').length%2!==0||doc.path.split('/').some(part=>!part||part==='.'||part==='..')||paths.has(doc.path))throw new Error('Invalid or duplicated document path.');
  paths.add(doc.path);
 }
 if(data.secrets.some(secret=>secret.name!=='application-shared-secret'||typeof secret.base64!=='string')||data.secrets.length>1)throw new Error('Unexpected protected secret.');
 return data;
}
function outsideRepository(path){
 const absolute=resolve(path);
 if(absolute===repository||absolute.startsWith(repository+sep))throw new Error('Archives, keys and recovered secrets must stay outside the repository.');
 return absolute;
}
export function requireIsolatedRestore(host,project){
 if(!/^(localhost|127\.0\.0\.1):\d{2,5}$/.test(host||'')||project!=='demo-radiotech-identity-restore')throw new Error('Restore is allowed only in the local isolated identity emulator.');
}
async function collect(db,transaction){
 const documents=[];
 async function visit(collection){
  for(const ref of await collection.listDocuments()){
   const snapshot=transaction?await transaction.get(ref):await ref.get();
   if(snapshot.exists){documents.push({path:ref.path,value:encodeValue(snapshot.data())});if(documents.length>50000)throw new Error('Registry document limit exceeded.');}
   for(const child of await ref.listCollections())await visit(child);
  }
 }
 for(const collection of await db.listCollections())await visit(collection);
 return documents.sort((a,b)=>compare(a.path,b.path));
}
async function main(){
 const [mode,...args]=process.argv.slice(2),options={};
 for(let index=0;index<args.length;index+=2){if(!args[index]?.startsWith('--')||!args[index+1])throw new Error('Use named option/value pairs.');options[args[index].slice(2)]=args[index+1];}
 if(!['export','verify','restore-emulator'].includes(mode)||!options['registry-project']||!options.archive||!options['key-file'])throw new Error('Specify mode, --registry-project, --archive and --key-file.');
 const archivePath=outsideRepository(options.archive),key=await readFile(outsideRepository(options['key-file']));
 let app,emulatorDb;
 try {
  if(mode==='export'){
   if(process.env.FIRESTORE_EMULATOR_HOST)throw new Error('Administrative export cannot target an emulator.');
   app=initializeApp({projectId:options['registry-project'],credential:applicationDefault()},'registry-recovery-export');
   const db=getFirestore(app);
   const documents=await db.runTransaction(transaction=>collect(db,transaction),{readOnly:true});
   const secrets=options['secret-file']?[{name:'application-shared-secret',base64:(await readFile(outsideRepository(options['secret-file']))).toString('base64')}]:[];
   const snapshot={registryProject:options['registry-project'],createdAt:new Date().toISOString(),documents,secrets};
   const archive=seal(snapshot,key);await mkdir(dirname(archivePath),{recursive:true});await writeFile(archivePath,archive,{flag:'wx',mode:0o600});
   console.log(JSON.stringify({exported:true,documents:documents.length,protectedSecrets:secrets.length,sha256:createHash('sha256').update(archive).digest('hex')}));return;
  }
  const snapshot=open(await readFile(archivePath),key,options['registry-project']);
  if(mode==='verify'){console.log(JSON.stringify({verified:true,documents:snapshot.documents.length,protectedSecrets:snapshot.secrets.length,createdAt:snapshot.createdAt}));return;}
  const project='demo-radiotech-identity-restore';requireIsolatedRestore(process.env.FIRESTORE_EMULATOR_HOST,project);
  emulatorDb=new Firestore({projectId:project,host:process.env.FIRESTORE_EMULATOR_HOST,ssl:false});const db=emulatorDb;
  if((await db.listCollections()).length)throw new Error('The isolated restore database must be empty.');
  for(const document of snapshot.documents)await db.doc(document.path).create(decodeValue(document.value,db));
  const restored=await collect(db);
  if(JSON.stringify(restored)!==JSON.stringify(snapshot.documents))throw new Error('Restored registry differs from its authenticated snapshot.');
  if(snapshot.secrets.length){
   if(!options['recovery-directory'])throw new Error('Specify a private --recovery-directory to prove protected secret recovery.');
   const recovery=outsideRepository(options['recovery-directory']);await mkdir(recovery,{recursive:true});
   for(const secret of snapshot.secrets){const target=resolve(recovery,secret.name+'.recovered');await writeFile(target,Buffer.from(secret.base64,'base64'),{flag:'wx',mode:0o600});if(!(await readFile(target)).equals(Buffer.from(secret.base64,'base64')))throw new Error('Protected secret recovery failed.');}
  }
  console.log(JSON.stringify({restored:true,isolated:true,documents:restored.length,protectedSecrets:snapshot.secrets.length,targetProject:project}));
 }finally{if(app)await deleteApp(app);if(emulatorDb)await emulatorDb.terminate();}
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(error=>{console.error(JSON.stringify({error:'Identity registry recovery failed',type:error.name}));process.exitCode=1;});
