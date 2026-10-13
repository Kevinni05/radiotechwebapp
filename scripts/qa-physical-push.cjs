// Synthetic receiver on the owner's isolated Samsung QA installation only.
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const dependency=require('node:module').createRequire(path.resolve(__dirname,'../functions/package.json'));
const {initializeApp,applicationDefault,deleteApp}=dependency('firebase-admin/app');
const {getAuth}=dependency('firebase-admin/auth'),{getFirestore}=dependency('firebase-admin/firestore');
const {getMessaging}=dependency('firebase-admin/messaging');
const privateRoot=path.join(process.env.USERPROFILE,'.radiotech/cloud');
const statePath=path.join(privateRoot,'physical-push-state.json'),definesPath=path.join(privateRoot,'physical-push-defines.json');
async function main(){
 const action=process.argv[2],serial=process.argv[3];
 if(!['prepare','send','cleanup'].includes(action))throw new Error('Use prepare, send SERIAL, or cleanup.');
 process.env.GOOGLE_APPLICATION_CREDENTIALS=path.join(privateRoot,'cli-adc.json');
 const app=initializeApp({projectId:'gestionale-radio',credential:applicationDefault()});
 const auth=getAuth(app),db=getFirestore(app);
 try {
  if(action==='prepare'){
   try{await fs.access(statePath);throw new Error('Clean up the existing QA run before preparing another.');}catch(error){if(error.code!=='ENOENT')throw error;}
   const run=crypto.randomUUID().replaceAll('-',''),uid='qa-push-'+run,tenant='qa-push-'+run,operator='qa-push-'+run,notice='qa_push_'+run;
   const email=uid+'@example.test',password=crypto.randomBytes(32).toString('base64url');
   await fs.writeFile(statePath,JSON.stringify({uid,tenant,operator,notice,run}));
   await auth.createUser({uid,email,password,emailVerified:true,displayName:'Operatore QA sintetico'});
   await auth.setCustomUserClaims(uid,{tenantId:tenant,role:'OPERATOR',operatorId:operator});
   await db.collection('operators').doc(operator).create({tenantId:tenant,firebaseUid:uid,name:'Operatore QA sintetico',role:'OPERATOR',status:'ATTIVO',fcmTokens:[],qaRun:run,createdAt:new Date().toISOString()});
   await fs.writeFile(definesPath,JSON.stringify({QA_EMAIL:email,QA_PASSWORD:password,QA_NOTIFICATION_ID:notice,QA_TENANT_ID:tenant,API_BASE_URL:'https://radiotechwebapp.onrender.com'}));
   console.log(JSON.stringify({prepared:true,synthetic:true,definesPath}));return;
  }
  const state=JSON.parse(await fs.readFile(statePath,'utf8'));
  if(!state.uid.startsWith('qa-push-')||state.uid!=='qa-push-'+state.run||state.operator!==state.uid||state.tenant!==state.uid||state.notice!=='qa_push_'+state.run)throw new Error('Unexpected QA ownership.');
  if(action==='cleanup'){
   const profile=await db.collection('operators').doc(state.operator).get();
   if(profile.exists&&profile.get('qaRun')!==state.run)throw new Error('Unexpected QA fixture.');
   await db.collection('notificationHistory').doc(state.notice).delete();
   await db.collection('operators').doc(state.operator).delete();
   try{await auth.deleteUser(state.uid);}catch(error){if(error.code!=='auth/user-not-found')throw error;}
   await fs.unlink(definesPath).catch(error=>{if(error.code!=='ENOENT')throw error;});await fs.unlink(statePath);
   console.log(JSON.stringify({cleaned:true,synthetic:true}));return;
  }
  if(!serial||!/^[A-Za-z0-9_-]+$/.test(serial))throw new Error('An explicit device serial is required.');
  const adb=path.join(process.env.LOCALAPPDATA,'Android/sdk/platform-tools/adb.exe');
  let receiver;
  for(let attempt=0;attempt<600;attempt++){
   try{receiver=JSON.parse(execFileSync(adb,['-s',serial,'shell','run-as','com.example.gestionale_radio.qa','cat','app_flutter/qa-push.json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}));}catch{}
   if(receiver?.phase==='ready'&&receiver.notificationId===state.notice)break;
   await new Promise(resolve=>setTimeout(resolve,1000));
  }
  if(receiver?.phase!=='ready'||receiver.uid!==state.uid||receiver.tenantId!==state.tenant||receiver.notificationId!==state.notice||!receiver.token)throw new Error('QA receiver not ready.');
  const profile=await db.collection('operators').doc(state.operator).get();
  if(!profile.get('fcmTokens')?.includes(receiver.token))throw new Error('The real API did not register this QA token.');
  await db.collection('notificationHistory').doc(state.notice).create({qaRun:state.run,tenantId:state.tenant,target:state.operator,recipientUid:state.uid,title:'Prova QA sintetica',message:'Collaudo provider verso Samsung',type:'GENERAL_NOTIFICATION',createdAt:new Date().toISOString(),deliveryStatus:'SENDING',lease:'qa-physical-'+state.run,leaseUntil:new Date(Date.now()+300000).toISOString(),deliveryAttempts:1,readBy:[]});
  await getMessaging(app).send({token:receiver.token,data:{notificationId:state.notice,tenantId:state.tenant,recipientUid:state.uid,type:'GENERAL_NOTIFICATION'},notification:{title:'RADIO TECH QA',body:'Prova sintetica sul dispositivo del proprietario.'},android:{priority:'high',notification:{channelId:'radiotech_alerts',tag:state.notice}}});
  await db.collection('notificationHistory').doc(state.notice).update({deliveryStatus:'SENT',delivered:1,providerAcceptedAt:new Date().toISOString()});
  console.log(JSON.stringify({providerAccepted:true,apiRegistrationVerified:true,notificationId:state.notice,device:serial,scope:'provider-to-foreground-QA-only'}));
 }finally{await deleteApp(app);}
}
main().catch(error=>{console.error(JSON.stringify({error:'Synthetic physical push check failed',code:error.code||error.name,message:error.message}));process.exitCode=1;});
