// Run after SDK signing/identity verification. Credentials come from ADC, never CLI arguments.
const fs=require('node:fs'),crypto=require('node:crypto');
const {initializeApp,applicationDefault}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
async function main(){
 const [tenant,apkPath,manifestPath]=process.argv.slice(2);if(!tenant||tenant.includes('/')||tenant.length>128)throw new Error('Explicit tenant required.');
 if(!process.env.FIREBASE_PROJECT_ID)throw new Error('Explicit project required.');
 const manifest=JSON.parse(fs.readFileSync(manifestPath,'utf8').replace(/^\uFEFF/,''));const size=fs.statSync(apkPath).size;
 if(size<1||size>150*1024*1024||!manifest.filename?.match(/^[A-Za-z0-9][A-Za-z0-9._-]{0,150}\.apk$/)||!manifest.signingSha256?.match(/^[a-f0-9]{64}$/)||!Number.isSafeInteger(manifest.versionCode)||manifest.versionCode<1)throw new Error('Invalid verified release metadata.');
 const hash=crypto.createHash('sha256');for await(const bytes of fs.createReadStream(apkPath))hash.update(bytes);const sha=hash.digest('hex');if(sha!==manifest.sha256)throw new Error('APK digest mismatch.');
 const db=getFirestore(initializeApp({projectId:process.env.FIREBASE_PROJECT_ID,credential:applicationDefault()}));
 const ref=db.collection('privateAppPackages').doc(sha);const existing=await ref.get();
 const chunkCount=Math.ceil(size/(512*1024));
 if(existing.get('state')!=='READY'){
  await ref.set({state:'UPLOADING',sha256:sha,size,chunkCount,createdAt:new Date().toISOString()});
  const uploadHash=crypto.createHash('sha256');const handle=fs.openSync(apkPath,'r');try{for(let index=0;index<chunkCount;index++){
   const buffer=Buffer.alloc(Math.min(512*1024,size-index*512*1024));const read=fs.readSync(handle,buffer,0,buffer.length,index*512*1024);if(read!==buffer.length)throw new Error('APK changed while publishing.');
   uploadHash.update(buffer);await ref.collection('chunks').doc(String(index).padStart(6,'0')).set({data:buffer});
  }}finally{fs.closeSync(handle);}
  if(uploadHash.digest('hex')!==sha)throw new Error('APK changed while publishing.');
  await ref.update({state:'READY'});
 }
 const release=db.collection('privateAppReleases').doc(tenant),audit=db.collection('auditLogs').doc();
 await db.runTransaction(async tx=>{const old=await tx.get(release);if(old.exists&&(old.get('versionCode')>manifest.versionCode||old.get('versionCode')===manifest.versionCode&&old.get('sha256')!==sha))throw new Error('Release versionCode must increase for changed bytes.');
  tx.set(release,{...manifest,sha256:sha,size,available:true,tenantId:tenant,channel:'PRIVATE_SITE',publishedAt:new Date().toISOString()});
  tx.create(audit,{tenantId:tenant,action:'PRIVATE_RELEASE_PUBLISHED',actor:'AUTHORIZED_RELEASE_TOOL',resource:'ANDROID_RELEASE',resourceId:sha,result:'SUCCESS',timestamp:new Date().toISOString(),after:{versionCode:manifest.versionCode,sha256:sha}});
 });
 console.log(JSON.stringify({published:true,versionCode:manifest.versionCode,sha256:sha,size,chunkCount}));await db.terminate();
}
main().catch(error=>{console.error('Private release publishing failed: '+(error.code||error.message));process.exitCode=1;});
