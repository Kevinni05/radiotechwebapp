'use strict';
const {initializeApp,applicationDefault}=require('firebase-admin/app');
const {getFirestore,FieldPath}=require('firebase-admin/firestore');
const {collections,reconcile}=require('./projection.cjs');
async function backfill(db,tenant) {
  if(!tenant||tenant.includes('/'))throw new Error('Explicit tenant required');
  const summary=db.collection('dashboardSummaries').doc(tenant);
  await summary.set({tenantId:tenant,ready:false,schemaVersion:1},{merge:true});
  for(const collection of collections) {
    let after=null;
    while(true) {
      let query=db.collection(collection).where('tenantId','==',tenant).orderBy(FieldPath.documentId()).limit(100);
      if(after)query=query.startAfter(after);
      const page=await query.get();
      for(const doc of page.docs)await reconcile(db,collection,doc.id);
      if(page.size<100)break;after=page.docs.at(-1);
    }
  }
  // Also reconcile tombstones/moved documents previously seen by this ledger.
  for(const collection of collections) {
    let after=null;
    while(true){let query=db.collection('dashboardProjectionEntries').where('collection','==',collection).orderBy(FieldPath.documentId()).limit(100);if(after)query=query.startAfter(after);const page=await query.get();for(const doc of page.docs)if(doc.data().value?.tenantId===tenant)await reconcile(db,collection,doc.data().resourceId);if(page.size<100)break;after=page.docs.at(-1);}
  }
  await summary.set({ready:true,backfilledAt:new Date().toISOString()},{merge:true});
}
if(require.main===module){const project=process.env.FIREBASE_PROJECT_ID;if(!project)throw new Error('FIREBASE_PROJECT_ID required');initializeApp({projectId:project,credential:applicationDefault()});backfill(getFirestore(),process.argv[2]).then(()=>console.log('Projection backfill verified')).catch(()=>{console.error('Backfill failed; projection remains unready');process.exitCode=1;});}
module.exports={backfill};
