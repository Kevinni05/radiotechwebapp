const prefix='rt_notification_operation_';
export async function notificationOperation(fingerprint, storage){
 const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(fingerprint));
 const key=prefix+[...new Uint8Array(bytes)].map(value=>value.toString(16).padStart(2,'0')).join('');
 let previous;
 try{previous=JSON.parse(storage.getItem(key)||'null');}catch{}
 if(previous&&typeof previous.id==='string'&&/^[a-f0-9-]{36}$/.test(previous.id)
    &&Number.isFinite(previous.createdAt)&&previous.createdAt<=Date.now()&&Date.now()-previous.createdAt<86400000)return {...previous,key};
 const operation={id:crypto.randomUUID(),createdAt:Date.now(),key};
 try{storage.setItem(key,JSON.stringify({id:operation.id,createdAt:operation.createdAt}));}catch{}
 return operation;
}
export function completeNotificationOperation(operation,storage){
 try{if(operation?.key)storage.removeItem(operation.key);}catch{}
}
