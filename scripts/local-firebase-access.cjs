// Reuse the owner's existing Firebase CLI login. Never print credentials or tokens.
const fs=require('node:fs'),path=require('node:path');
const dependency=require('node:module').createRequire(path.resolve(__dirname,'../functions/package.json'));
const {initializeApp,applicationDefault,deleteApp}=dependency('firebase-admin/app');
const {getFirestore}=dependency('firebase-admin/firestore');
const {getAuth}=dependency('firebase-admin/auth');
async function main(){
 const root=path.join(process.env.USERPROFILE,'.radiotech','cloud');fs.mkdirSync(root,{recursive:true});
 if(process.platform==='win32'){
  const {execFileSync}=require('node:child_process');
  const sid=execFileSync('whoami',['/user','/fo','csv','/nh'],{encoding:'utf8'}).match(/"(S-1-[0-9-]+)"/)?.[1];
  if(!sid)throw new Error('Unable to determine the credential owner.');
  execFileSync('icacls',[root,'/inheritance:r','/grant:r',`*${sid}:(OI)(CI)F`],{stdio:'pipe'});
 }
 const cli=JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE,'.config/configstore/firebase-tools.json'),'utf8'));
 if(!cli.tokens?.refresh_token)throw new Error('Existing Firebase CLI login required.');
 const api=require(path.join(process.env.APPDATA,'npm/node_modules/firebase-tools/lib/api.js'));
 const adc={type:'authorized_user',client_id:api.clientId(),client_secret:api.clientSecret(),refresh_token:cli.tokens.refresh_token};
 const credentialPath=path.join(root,'cli-adc.json');fs.writeFileSync(credentialPath,JSON.stringify(adc),{mode:0o600});
 process.env.GOOGLE_APPLICATION_CREDENTIALS=credentialPath;
 const app=initializeApp({projectId:'gestionale-radio',credential:applicationDefault()});
 const db=getFirestore(app);
 const operators=await db.collection('operators').limit(1).get();
 const tenant=operators.docs[0]?.get('tenantId');
 const keyConfig=JSON.parse(fs.readFileSync(path.join(process.env.USERPROFILE,'radiotech-consolidation-mobile/android/app/google-services.json'),'utf8'));
 if(keyConfig.project_info.project_id!=='gestionale-radio')throw new Error('Project mismatch.');
 fs.writeFileSync(path.join(root,'acceptance-settings.json'),JSON.stringify({projectId:'gestionale-radio',tenantId:tenant,webApiKey:keyConfig.client[0].api_key[0].current_key}),{mode:0o600});
 let authRead=false;try{await getAuth(app).listUsers(1);authRead=true;}catch(error){console.log('Auth administrative access unavailable: '+(error.code||'UNKNOWN'));}
 console.log(JSON.stringify({firestoreRead:true,tenantAvailable:!!tenant,authRead,credentialFilePrepared:true}));
 await deleteApp(app);
}
main().catch(error=>{console.error('Local cloud access verification failed: '+(error.code||error.name));process.exitCode=1;});
