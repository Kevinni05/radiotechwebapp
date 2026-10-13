// Read-only notification/outbox inventory. No payloads, identities or tokens.
const path=require('node:path');
const dependency=require('node:module').createRequire(path.resolve(__dirname,'../functions/package.json'));
const {initializeApp,applicationDefault,deleteApp}=dependency('firebase-admin/app');
const {getFirestore}=dependency('firebase-admin/firestore');
async function main(){
 process.env.GOOGLE_APPLICATION_CREDENTIALS=path.join(process.env.USERPROFILE,'.radiotech/cloud/cli-adc.json');
 const app=initializeApp({projectId:'gestionale-radio',credential:applicationDefault()});
 try{
  const db=getFirestore(app), statuses={},errors={};
  const history=await db.collection('notificationHistory').orderBy('createdAt','desc').limit(20)
   .select('deliveryStatus','deliveryAttempts','createdAt','failedRecipients','delivered').get();
  const notices=[];
  for(const document of history.docs){
   const data=document.data(),state=data.deliveryStatus||'UNKNOWN';statuses[state]=(statuses[state]||0)+1;
   const outcomes=await document.ref.collection('pushDeliveries').select('status','errorCode','attemptedAt').limit(25).get();
   for(const outcome of outcomes.docs){const code=outcome.get('errorCode');if(code)errors[code]=(errors[code]||0)+1;}
   notices.push({id:document.id,status:state,attempts:data.deliveryAttempts,createdAt:data.createdAt,providerAccepted:data.delivered,failed:data.failedRecipients});
  }
  console.log(JSON.stringify({projectId:'gestionale-radio',sample:history.size,statuses,errors,notices}));
 }finally{await deleteApp(app);}
}
main().catch(error=>{console.error(JSON.stringify({error:'Notification inventory failed',code:error.code||error.name}));process.exitCode=1;});
