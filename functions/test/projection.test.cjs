const {test}=require('node:test');
const assert=require('node:assert/strict');
const {initializeApp}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
const {contribution,reconcile}=require('../projection.cjs');
const {backfill}=require('../backfill.cjs');
test('legacy task statuses and review tombstones normalize consistently',()=>{
 assert.equal(contribution('tasks',{tenantId:'a',status:'IN_CORSO'}).counts.inProgress,1);
 assert.equal(contribution('tasks',{tenantId:'a',status:'REPORT_SUBMITTED'}).active,null);
 assert.deepEqual(contribution('maintenanceReports',{tenantId:'a',status:'SUBMITTED',removedAt:'yesterday'}).counts,{});
 assert.equal(contribution('inventory',{tenantId:'a',quantity:0,minimumThreshold:0}).counts.lowStock,1);
 assert.equal(contribution('tasks',{tenantId:'a/b',status:'ASSIGNED'}),null);
});
test('projection converges after duplicate, reordered, moved and deleted source events',{skip:!process.env.FIRESTORE_EMULATOR_HOST},async()=>{
 assert.match(process.env.GCLOUD_PROJECT ?? 'demo-radiotech',/^demo-/);
 const db=getFirestore(initializeApp({projectId:'demo-radiotech'},'projection-test'));
 const tenant='projection-qa-'+Date.now(),other=tenant+'-other',id=tenant+'-task';
 const ref=db.collection('tasks').doc(id),summary=db.collection('dashboardSummaries').doc(tenant);
 try {
  await ref.set({tenantId:tenant,title:'Ponte Alfa',status:'ASSIGNED',dueAt:'2030-01-01T00:00:00Z'});
  await Promise.all([reconcile(db,'tasks',id),reconcile(db,'tasks',id)]);
  assert.equal((await summary.get()).data().counts.tasks,1);
  assert.equal((await summary.collection('activeTasks').get()).size,1);
  await ref.update({status:'COMPLETED',title:'Ponte Beta'});
  await reconcile(db,'tasks',id);await reconcile(db,'tasks',id);
  assert.equal((await summary.get()).data().counts.assigned,0);
  assert.equal((await summary.get()).data().counts.completed,1);
  assert.equal((await db.collection('resourceSearch').where('tenantId','==',tenant).where('prefixes','array-contains','be').get()).size,1);
  assert.equal((await db.collection('resourceSearch').where('tenantId','==',tenant).where('prefixes','array-contains','al').get()).size,0);
  assert.equal((await summary.collection('activeTasks').get()).size,0);
  await ref.update({tenantId:other});await reconcile(db,'tasks',id);
  assert.equal((await summary.get()).data().counts.tasks,0);
  assert.equal((await db.collection('dashboardSummaries').doc(other).get()).data().counts.tasks,1);
  await backfill(db,other);assert.equal((await db.collection('dashboardSummaries').doc(other).get()).data().ready,true);
  await ref.delete();await reconcile(db,'tasks',id);
  assert.equal((await db.collection('dashboardSummaries').doc(other).get()).data().counts.tasks,0);
  assert.equal(await reconcile(db,'tasks',id),false);
 } finally {await ref.delete();await summary.delete();await db.collection('dashboardSummaries').doc(other).delete();await db.terminate();}
});

test('orphan policy preserves legacy, attached and recent uploads',()=>{
 const {eligible}=require('../orphans.cjs');const base={tenantId:'a',lifecycleVersion:1,createdAt:'2020-01-01T00:00:00Z'};
 assert.equal(eligible(base,'a','2021'),true);
 for(const value of [{...base,lifecycleVersion:undefined},{...base,referencedBy:['report']},{...base,createdAt:'2030'},{...base,cleanupState:'DELETED'}])assert.equal(eligible(value,'a','2021'),false);
 assert.equal(eligible(base,'other','2021'),false);
});
