// Synthetic isolated tenants only. Tokens/passwords stay in memory and are never logged.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const dependency=require('node:module').createRequire(path.resolve(__dirname,'../functions/package.json'));
const {initializeApp,applicationDefault,deleteApp}=dependency('firebase-admin/app');
const {getFirestore}=dependency('firebase-admin/firestore');const {getAuth}=dependency('firebase-admin/auth');
const origin=process.argv[2]||'https://radiotechwebapp.onrender.com';
if(!['https://radiotechwebapp.onrender.com','http://127.0.0.1:18081'].includes(origin))throw new Error('Approved acceptance origin required.');
async function main(){
 const root=path.join(process.env.USERPROFILE,'.radiotech/cloud');process.env.GOOGLE_APPLICATION_CREDENTIALS=path.join(root,'cli-adc.json');
 const settings=JSON.parse(fs.readFileSync(path.join(root,'acceptance-settings.json'),'utf8'));
 const app=initializeApp({projectId:settings.projectId,credential:applicationDefault()}),db=getFirestore(app),auth=getAuth(app);
 const run=crypto.randomUUID(),tenant='qa-acceptance-'+run,other='qa-negative-'+run,accounts=[],proof={run,origin,startedAt:new Date().toISOString(),checks:[]};
 const add=name=>{proof.checks.push(name);console.log('PASS '+name);};
 const unwrap=value=>value?.data??value;
 async function request(route,token,method='GET',body,expected=200){
  const response=await fetch(origin+'/api/v1'+route,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(60000)});
  if(Array.isArray(expected)){assert(expected.includes(response.status),'Unexpected HTTP '+response.status+' for '+route);return response;}
  assert(method==='POST'&&expected===200?[200,201].includes(response.status):response.status===expected,'Unexpected HTTP '+response.status+' for '+route);return unwrap(await response.json());
 }
 async function account(role,scope){
  const uid='qa-'+crypto.randomUUID(),email=uid+'@radiotech.invalid',password=crypto.randomBytes(32).toString('base64url');
  await auth.createUser({uid,email,password,emailVerified:true,displayName:role==='OPERATOR'?'RadioTech QA technician':'RadioTech QA manager'});accounts.push(uid);
  await auth.setCustomUserClaims(uid,{role,tenantId:scope});
  await db.collection('users').doc(uid).set({uid,tenantId:scope,fullName:'RadioTech QA manager',email,role,status:'ATTIVO',company:'RadioTech QA'});
  const response=await fetch('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key='+settings.webApiKey,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true}),signal:AbortSignal.timeout(30000)});
  if(response.status!==200)throw new Error('QA identity login failed: HTTP '+response.status);return {uid,email,token:(await response.json()).idToken};
 }
 try {
  const manager=await account('ADMIN',tenant),technician=await account('OPERATOR',tenant),foreign=await account('ADMIN',other);
  const deployedRelease=await db.collection('privateAppReleases').doc(settings.tenantId).get();
  if(deployedRelease.exists&&deployedRelease.get('available')===true){
   await db.collection('privateAppReleases').doc(tenant).set({...deployedRelease.data(),tenantId:tenant});
   const latest=await request('/distribution/latest.json',technician.token);
   assert.equal(latest.applicationId,'com.example.gestionale_radio');assert.equal(latest.versionCode,deployedRelease.get('versionCode'));
   assert.equal((await request('/distribution/latest.json',foreign.token)).available,false);
   await request('/distribution/latest.json',null,'GET',null,[401,403]);
   const started=performance.now();
   const response=await fetch(origin+'/api/v1/distribution/'+latest.filename,{headers:{Authorization:'Bearer '+technician.token},signal:AbortSignal.timeout(180000)});
   proof.privateHeadersMs=Math.round(performance.now()-started);
   assert.equal(response.status,200);const digest=crypto.createHash('sha256');let size=0;
   for await(const bytes of response.body){digest.update(bytes);size+=bytes.length;}
   assert.equal(size,latest.size);assert.equal(digest.digest('hex'),latest.sha256);
   proof.privateDownloadMs=Math.round(performance.now()-started);
   assert(proof.privateHeadersMs<60000,'Private staging exceeds native header deadline');
   assert(proof.privateDownloadMs<180000,'Private download exceeds native total deadline');
   add('private signed APK authenticated download, tenant isolation and byte integrity');
  }
  if(process.argv.includes('--distribution-only')){
   assert(deployedRelease.exists&&deployedRelease.get('available')===true,'Published package required');
   proof.success=true;return;
  }
  const operatorId='qa-'+run,badge=crypto.randomBytes(32).toString('base64url');
  await db.collection('operators').doc(operatorId).set({tenantId:tenant,firebaseUid:technician.uid,email:technician.email,fullName:'RadioTech QA technician',role:'OPERATOR',status:'ATTIVO',qrCodeToken:badge,qrExpiresAt:new Date(Date.now()+600000).toISOString()});
  const qrOrigin='https://radiotechwebapp.onrender.com';
  async function qrLogin(){return fetch(qrOrigin+'/api/v1/auth/qr-login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({qrToken:badge}),signal:AbortSignal.timeout(60000)});}
  const qrResponse=await qrLogin();assert.equal(qrResponse.status,200);const qr=await qrResponse.json();assert.equal(typeof qr.customToken,'string');
  assert.equal((await qrLogin()).status,401);add('QR one-time authentication and replay rejection');
  const asset=await request('/dashboard/antenne',manager.token,'POST',{name:'RadioTech QA asset',lat:41.1,lng:16.8,status:'ATTIVA',specs:{frequencyMHz:450,powerWatts:10,ros:1.2,temperature:20}});
  const documentUrl='https://firestore.googleapis.com/v1/projects/'+settings.projectId+'/databases/(default)/documents/antennas/'+asset.id;
  const ownedRead=await fetch(documentUrl,{headers:{Authorization:'Bearer '+manager.token}});assert.equal(ownedRead.status,200);
  const foreignRead=await fetch(documentUrl,{headers:{Authorization:'Bearer '+foreign.token}});assert.equal(foreignRead.status,403);
  const clientWrite=await fetch(documentUrl,{method:'PATCH',headers:{Authorization:'Bearer '+manager.token,'Content-Type':'application/json'},body:JSON.stringify({fields:{name:{stringValue:'Unauthorized direct mutation'}}})});assert.equal(clientWrite.status,403);
  add('deployed Firestore rules allow tenant read and deny cross-tenant read/direct write');
  const stock=await request('/inventory',manager.token,'POST',{name:'QA connector',sku:'qa-'+run,quantity:3,minimumThreshold:1,category:'QA',unit:'pz'});
  const task=await request('/tasks',manager.token,'POST',{title:'RadioTech QA task',operatorId,antennaId:asset.id,description:'Synthetic acceptance',dueAt:new Date(Date.now()+86400000).toISOString(),priority:'MEDIUM'});
  await request('/tasks/'+task.id,foreign.token,'GET',null,[403,404]);await request('/dashboard/stats',technician.token,'GET',null,[403]);add('tenant and role access denied');
  const stats=await request('/dashboard/stats',manager.token);assert(stats);add('real cloud tenant dashboard aggregation');
  for(const step of ['accept','en-route','check-in','start'])await request('/operator/tasks/'+task.id+'/'+step,technician.token,'POST',step==='check-in'?{latitude:41.1,longitude:16.8,accuracy:8}:{});
  add('assigned task acceptance, route, GPS check-in and work start');
  const pdfPath=path.join(root,'report-qa.pdf'),submittedAt=new Date().toISOString();
  fs.writeFileSync(pdfPath+'.input.json',JSON.stringify({antennaId:asset.id,taskId:task.id,inventoryId:stock.id,submittedAt}));
  const generated=require('node:child_process').spawnSync('flutter',['test','scripts/create_qa_report.dart','--reporter','expanded'],{cwd:path.join(process.env.USERPROFILE,'radiotech-consolidation-mobile'),env:{...process.env,RADIOTECH_QA_PDF:pdfPath},shell:process.platform==='win32',encoding:'utf8'});
  fs.writeFileSync(path.join(root,'qa-pdf-generation.log'),generated.stdout+generated.stderr);if(generated.status!==0)throw new Error('Real Flutter PDF generation failed.');
  const technical=JSON.parse(fs.readFileSync(pdfPath+'.json','utf8'));
  const bytes=fs.readFileSync(pdfPath),key=crypto.randomUUID();
  const uploaded=await fetch(origin+'/api/v1/files?name=report.pdf&operationId=qa_'+run,{method:'POST',headers:{Authorization:'Bearer '+technician.token,'Content-Type':'application/octet-stream'},body:bytes,signal:AbortSignal.timeout(60000)});assert.equal(uploaded.status,200,'PDF upload');
  const file=await uploaded.json(),id=file.reference.slice('radiotech-file:'.length);
  await request('/files/'+id,foreign.token,'GET',null,[403,404]);
  const binary=await fetch(origin+'/api/v1/files/'+id+'/content',{headers:{Authorization:'Bearer '+technician.token},signal:AbortSignal.timeout(60000)});assert.equal(binary.status,200,'PDF binary download');assert.deepEqual(Buffer.from(await binary.arrayBuffer()),bytes);add('real Flutter PDF upload, authorized streaming and exact bytes');
  const payload={antennaId:asset.id,operatorNotes:'RadioTech synthetic acceptance report',description:'RadioTech synthetic acceptance report',workPerformed:'QA',findings:'No anomalies',latitude:41.1,longitude:16.8,completedAt:submittedAt,attachments:[file.reference],...technical,materialsUsed:[{inventoryId:stock.id,name:'QA connector',quantity:1}]};
  async function submit(){const response=await fetch(origin+'/api/v1/operator/tasks/'+task.id+'/report',{method:'POST',headers:{Authorization:'Bearer '+technician.token,'Content-Type':'application/json','Idempotency-Key':key},body:JSON.stringify(payload),signal:AbortSignal.timeout(60000)});const result=await response.json();if(response.status!==201)throw new Error('Report submission HTTP '+response.status+': '+String(result.message||result.code||'UNKNOWN').replace(/[A-Za-z0-9_-]{40,}/g,'[REDACTED]'));return result.report;}
  const report=await submit();assert.equal((await submit()).id,report.id);add('report receipt and idempotent replay');
  const listed=await request('/reports',manager.token);assert(listed.some(item=>item.id===report.id));
  await request('/reports/'+report.id+'/approve',manager.token,'POST',{note:'Synthetic acceptance verified'});
  await request('/reports/'+report.id+'/approve',manager.token,'POST',{note:'Idempotent replay'});
  const inventory=await db.collection('inventory').doc(stock.id).get();assert.equal(inventory.get('quantity'),2);add('web approval, task finalization and single stock consumption');
  const before=await request('/reports/'+report.id,manager.token);await request('/dashboard/antenne/'+asset.id,manager.token,'PUT',{name:'Renamed QA asset',lat:41.1,lng:16.8,status:'ATTIVA',specs:{frequencyMHz:450,powerWatts:10,ros:1.2,temperature:20}});
  const after=await request('/reports/'+report.id,manager.token);assert.deepEqual(after.technicalSnapshot,before.technicalSnapshot);add('technical snapshot remains immutable after asset rename');
  await request('/reports/'+report.id,manager.token,'DELETE',{reason:'Synthetic acceptance completed'});assert(!(await request('/reports',manager.token)).some(item=>item.id===report.id));assert.equal((await db.collection('inventory').doc(stock.id).get()).get('quantity'),2);add('reasoned removal preserves audit and stock');
  await auth.updateUser(technician.uid,{disabled:true});await request('/operator/me',technician.token,'GET',null,[401]);add('account suspension invalidates previously issued token');
  proof.success=true;
 } finally {
  // Delete only documents in our unique QA tenants, including their private subcollections.
  for(const collection of await db.listCollections())for(const scope of [tenant,other]){
   const docs=await collection.where('tenantId','==',scope).get();for(const document of docs.docs)await db.recursiveDelete(document.ref);
  }
  for(const uid of accounts)await auth.deleteUser(uid);
  proof.finishedAt=new Date().toISOString();fs.writeFileSync(path.join(root,'live-acceptance-'+run+'.json'),JSON.stringify(proof,null,2));await deleteApp(app);
 }
}
main().catch(error=>{console.error('Acceptance failed: '+error.message);process.exitCode=1;});

