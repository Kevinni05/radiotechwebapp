// Administrative, bounded and tenant-scoped. Default mode never deletes data.
'use strict';
const {initializeApp,applicationDefault}=require('firebase-admin/app');
const {getFirestore,FieldPath}=require('firebase-admin/firestore');
const {getStorage}=require('firebase-admin/storage');
function eligible(value,tenant,cutoff){return value?.tenantId===tenant&&value.lifecycleVersion===1&&typeof value.createdAt==='string'&&value.createdAt<cutoff&&!(value.referencedBy?.length)&&value.cleanupState!=='DELETED';}
async function cleanup(db,bucket,tenant,{apply=false,after='',limit=25,now=new Date()}={}){
 if(!tenant||tenant.includes('/')||limit<1||limit>100)throw new Error('Explicit tenant and bounded limit required');
 const cutoff=new Date(now.getTime()-30*86400000).toISOString();
 let query=db.collection('reportFiles').where('tenantId','==',tenant).orderBy(FieldPath.documentId()).limit(limit);
 if(after)query=query.startAfter(after);
 const page=await query.get();let candidates=0,deleted=0;
 for(const doc of page.docs){if(!eligible(doc.data(),tenant,cutoff))continue;candidates++;if(!apply)continue;
  const claimed=await db.runTransaction(async tx=>{const current=await tx.get(doc.ref);if(!eligible(current.data(),tenant,cutoff))return null;tx.update(doc.ref,{cleanupState:'DELETING'});return current.data();});
  if(!claimed)continue;
  // Never clean legacy uploads: only the version written by report-binding-aware servers is eligible.
  // A report transaction rejects DELETING, and its referencedBy commit prevents acquiring this claim.
  if(claimed.backend==='FIREBASE_STORAGE'||claimed.legacyBackend==='FIRESTORE'){
   if(!bucket)throw new Error('Configured bucket required');
   const file=bucket.file('radiotech-report-files/'+doc.id);const [exists]=await file.exists();
   if(exists){const [metadata]=await file.getMetadata();if(metadata.metadata?.tenantId!==tenant||metadata.metadata?.digest!==claimed.digest)throw new Error('Storage ownership mismatch');await file.delete({ifGenerationMatch:metadata.generation});}
  }
  let chunks;
  do{chunks=await doc.ref.collection('chunks').limit(100).get();if(chunks.empty)break;const batch=db.batch();for(const chunk of chunks.docs)batch.delete(chunk.ref);await batch.commit();}while(chunks.size===100);
  await db.runTransaction(async tx=>{const current=await tx.get(doc.ref);if(current.get('cleanupState')!=='DELETING'||current.get('referencedBy')?.length)throw new Error('Cleanup claim changed');tx.update(doc.ref,{cleanupState:'DELETED',deletedAt:now.toISOString()});tx.create(db.collection('auditLogs').doc(),{tenantId:tenant,action:'ORPHAN_ATTACHMENT_DELETE',resource:'reportFiles',resourceId:doc.id,actor:'SYSTEM',result:'SUCCESS',timestamp:now.toISOString()});});deleted++;
 }
 return {scanned:page.size,candidates,deleted,nextAfterId:page.docs.at(-1)?.id??'',hasMore:page.size===limit};
}
if(require.main===module){const [mode='dry-run',tenant,after='']=process.argv.slice(2);if(!['dry-run','apply'].includes(mode)||!process.env.FIREBASE_PROJECT_ID)throw new Error('Usage: dry-run|apply tenant [afterId], explicit FIREBASE_PROJECT_ID');initializeApp({projectId:process.env.FIREBASE_PROJECT_ID,credential:applicationDefault(),storageBucket:process.env.FIREBASE_STORAGE_BUCKET});cleanup(getFirestore(),process.env.FIREBASE_STORAGE_BUCKET?getStorage().bucket():null,tenant,{apply:mode==='apply',after}).then(result=>console.log(JSON.stringify(result))).catch(()=>{console.error('Cleanup deferred; retained metadata permits retry');process.exitCode=1;});}
module.exports={eligible,cleanup};
