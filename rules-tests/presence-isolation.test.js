import {readFile} from 'node:fs/promises';
import {before,after,test} from 'node:test';
import {assertFails,assertSucceeds,initializeTestEnvironment} from '@firebase/rules-unit-testing';
import {ref,set,get,serverTimestamp} from 'firebase/database';
let environment;
before(async()=>{environment=await initializeTestEnvironment({projectId:'demo-radiotech-presence',database:{rules:await readFile(new URL('../database.rules.json',import.meta.url),'utf8')}});});
after(async()=>{await environment?.cleanup();});
const operator=()=>environment.authenticatedContext('user-a',{tenantId:'tenant-a',role:'OPERATOR'}).database();

test('an operator can publish lifecycle evidence only for its own company and identity',async()=>{
 const database=operator();
 await assertSucceeds(set(ref(database,'presence/tenant-a/user-a/connections/phone'),{state:'foreground',updatedAt:serverTimestamp()}));
 await assertSucceeds(set(ref(database,'presence/tenant-a/user-a/connections/phone'),{state:'background',updatedAt:serverTimestamp()}));
 await assertFails(set(ref(database,'presence/tenant-a/user-b/connections/phone'),{state:'foreground',updatedAt:serverTimestamp()}));
 await assertFails(set(ref(database,'presence/tenant-b/user-a/connections/phone'),{state:'foreground',updatedAt:serverTimestamp()}));
});
test('manager reads remain company-scoped and operators cannot enumerate colleagues',async()=>{
 const manager=environment.authenticatedContext('manager',{tenantId:'tenant-a',role:'ADMIN'}).database();
 await assertSucceeds(get(ref(manager,'presence/tenant-a')));
 await assertFails(get(ref(manager,'presence/tenant-b')));
 await assertFails(get(ref(operator(),'presence/tenant-a')));
 await assertFails(get(ref(environment.unauthenticatedContext().database(),'presence/tenant-a')));
});
test('timestamps, lifecycle states and unexpected data are validated while legacy clients remain compatible',async()=>{
 const record=ref(operator(),'presence/tenant-a/user-a/connections/validation');
 await assertFails(set(record,{state:'foreground',updatedAt:Date.now()+3600000}));
 await assertFails(set(record,{state:'tracking',updatedAt:serverTimestamp()}));
 await assertFails(set(record,{state:'foreground',updatedAt:serverTimestamp(),latitude:41}));
 await assertFails(set(record,{state:'foreground'}));
 await assertSucceeds(set(record,true));
 await assertSucceeds(set(record,null));
});
