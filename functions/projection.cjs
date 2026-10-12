'use strict';
const crypto = require('node:crypto');
const collections = ['antennas','operators','tasks','inventory','interventions','maintenanceReports'];
const aliases = {NEW:'CREATED',PENDENTE:'ASSIGNED',ACCETTATO:'ACCEPTED',IN_TRANSITO:'EN_ROUTE',ROUTE:'EN_ROUTE',CHECK_IN:'CHECKED_IN',CHECKEDIN:'CHECKED_IN',IN_CORSO:'IN_PROGRESS',IN_LAVORAZIONE:'IN_PROGRESS',IN_ATTESA:'WAITING',COMPLETATO:'COMPLETED',REPORT:'REPORT_SUBMITTED',SUBMITTED:'REPORT_SUBMITTED',APPROVATO:'APPROVED',CHIUSO:'CLOSED',ANNULLATO:'CANCELLED',CANCELED:'CANCELLED'};
const statuses = ['CREATED','ASSIGNED','ACCEPTED','EN_ROUTE','CHECKED_IN','IN_PROGRESS','WAITING','COMPLETED','REPORT_SUBMITTED','APPROVED','CLOSED','CANCELLED'];
function contribution(collection, value) {
  if (!value || typeof value.tenantId !== 'string' || !value.tenantId.trim() || value.tenantId.includes('/')) return null;
  const counts = {}, raw = String(value.status ?? '').trim().toUpperCase().replaceAll(' ','_');
  let active = null;
  if (collection === 'antennas') { counts.antennas=1; if (raw==='ATTIVA') counts.activeAntennas=1; if(raw==='MANUTENZIONE')counts.maintenanceAntennas=1;if(raw==='OFFLINE')counts.offlineAntennas=1; }
  if (collection === 'operators') { counts.operators=1; if(['ATTIVO','ACTIVE'].includes(raw))counts.activeOperators=1; }
  if (collection === 'interventions') counts.interventions=1;
  if (collection === 'inventory') { counts.inventoryItems=1;if(Number.isFinite(value.quantity)&&Number.isFinite(value.minimumThreshold)&&value.quantity<=value.minimumThreshold)counts.lowStock=1; }
  if (collection === 'maintenanceReports' && ['SUBMITTED','APPROVAL_PENDING'].includes(value.status) && (value.removedAt==null || String(value.removedAt).trim()==='')) counts.pendingReports=1;
  if (collection === 'tasks') {
    counts.tasks=1; const status=aliases[raw]??raw;
    if(statuses.includes(status)) {
      if(status==='ASSIGNED')counts.assigned=1;if(status==='IN_PROGRESS')counts.inProgress=1;
      if(['COMPLETED','APPROVED','CLOSED'].includes(status))counts.completed=1;if(status==='CANCELLED')counts.cancelled=1;
      if(!['COMPLETED','REPORT_SUBMITTED','APPROVED','CLOSED','CANCELLED'].includes(status)){counts.activeTasks=1;active={status,dueAt:typeof value.dueAt==='string'?value.dueAt:''};}
    }
  }
  const label=String(value.name??value.fullName??value.full_name??value.title??value.taskTitle??'').trim().slice(0,180);
  const normalized=label.normalize('NFKD').replace(/\p{M}/gu,'').toLowerCase();
  const prefixes=[...new Set([normalized,...normalized.split(/\s+/).slice(0,4)].flatMap(word=>Array.from({length:Math.max(0,Math.min(32,word.length)-1)},(_,i)=>word.slice(0,i+2))))].slice(0,120);
  const search=label&&prefixes.length?{label,prefixes}:null;
  return {tenantId:value.tenantId,counts,active,search};
}
async function reconcile(db, collection, id) {
  if(!collections.includes(collection))throw new Error('Unsupported projection source');
  const key=crypto.createHash('sha256').update(`${collection}/${id}`).digest('hex');
  const source=db.collection(collection).doc(id), ledger=db.collection('dashboardProjectionEntries').doc(key);
  return db.runTransaction(async tx=>{
    // Read the CURRENT authoritative source rather than trusting event order.
    // Duplicate and out-of-order trigger invocations therefore converge.
    const [current,previous]=await Promise.all([tx.get(source),tx.get(ledger)]);
    const before=previous.exists?previous.data().value:null,after=contribution(collection,current.exists?current.data():null);
    if(JSON.stringify(before)===JSON.stringify(after))return false;
    const tenants=[...new Set([before?.tenantId,after?.tenantId].filter(Boolean))];
    const refs=tenants.map(tenant=>db.collection('dashboardSummaries').doc(tenant));
    const snapshots=await Promise.all(refs.map(ref=>tx.get(ref)));
    for(let i=0;i<tenants.length;i++) {
      const tenant=tenants[i],counts={...(snapshots[i].data()?.counts??{})};
      for(const [value,sign] of [[before,-1],[after,1]])if(value?.tenantId===tenant)for(const [field,number]of Object.entries(value.counts))counts[field]=(counts[field]??0)+sign*number;
      if(Object.values(counts).some(value=>value<0))throw new Error('Projection ledger inconsistency');
      tx.set(refs[i],{tenantId:tenant,schemaVersion:1,counts,updatedAt:new Date().toISOString()},{merge:true});
      const active=refs[i].collection('activeTasks').doc(key);
      if(collection==='tasks') {
        if(after?.tenantId===tenant&&after.active)tx.set(active,{...after.active,taskId:id});else tx.delete(active);
      }
    }
    const searchRef=db.collection('resourceSearch').doc(key);
    if(after?.search && collection!=='interventions')tx.set(searchRef,{tenantId:after.tenantId,collection,resourceId:id,...after.search,updatedAt:new Date().toISOString()});else tx.delete(searchRef);
    tx.set(ledger,{collection,resourceId:id,value:after,updatedAt:new Date().toISOString()});
    return true;
  });
}
module.exports={collections,contribution,reconcile};
