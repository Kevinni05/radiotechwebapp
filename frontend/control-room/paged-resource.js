// Bounded reads and persistent list windows across refresh, actions and navigation.
export function createPagedResource(ctx,{path,legacy,stateKey,tableId,onUpdated,pageSize=100,buttonId=`${tableId}-more`,buttonText='Carica altri elementi',filters=()=>({})}) {
 let cursor=null,pages=1,lastScope=null,lastFilters=null;
 async function load(more=false) {
  more=more===true;const scope=ctx.state.sessionEpoch||0,selected=filters(),signature=JSON.stringify(selected),same=scope===lastScope&&signature===lastFilters;
  const depth=!more&&same?pages:1;let next=more&&same?cursor:null,rows=more&&same?[...ctx.state[stateKey]]:[],count=more&&same?pages:0;
  for(let page=0;page<depth;page++) {
   let response;
   const params=new URLSearchParams({limit:String(pageSize),...selected});if(next)params.set('cursor',next);
   try {response=await ctx.apiFetch(`${path}?${params}`);}
   catch(error){if(error.status!==404)throw error;response=await ctx.apiFetch(legacy);}
   if(signature!==JSON.stringify(filters()))return;
   if(scope!==(ctx.state.sessionEpoch||0))throw new Error('Sessione cambiata.');
   const data=response?.data??response,items=Array.isArray(data)?data:(data?.items??data?.[stateKey]??[]);
   rows=[...new Map([...rows,...items].map(item=>[item.id,item])).values()];next=data?.nextCursor??null;count++;
   if(!next)break;
  }
  ctx.state[stateKey]=rows;cursor=next;pages=count;lastScope=scope;lastFilters=signature;
  let button=ctx.$(buttonId);
  if(!button){button=document.createElement('button');button.id=buttonId;button.type='button';button.className='btn';button.textContent=buttonText;ctx.$(tableId)?.closest('.table-wrap')?.after(button);
   button.addEventListener('click',async()=>{button.disabled=true;try{await load(true);onUpdated();}catch(error){ctx.toast('Elementi non caricati',error.message,'error');}finally{button.disabled=false;}});
  }
  button.hidden=!cursor;
 }
 return load;
}
export function preserveTableInteraction(table,render) {
 const wrap=table?.closest('.table-wrap'),active=document.activeElement;
 const attributes=table?.contains(active)?[...active.attributes].filter(attr=>attr.name==='id'||attr.name.startsWith('data-')):[];
 const top=wrap?.scrollTop,left=wrap?.scrollLeft;render();
 if(wrap){wrap.scrollTop=top;wrap.scrollLeft=left;}
 if(attributes.length&&!active.isConnected&&!document.querySelector('dialog[open],.modal-backdrop.open')) {
  const selector=attributes.map(attr=>`[${attr.name}="${CSS.escape(attr.value)}"]`).join('');
  (table.querySelector(selector)??wrap)?.focus({preventScroll:true});
 }
}
