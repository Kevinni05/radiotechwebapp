import test from 'node:test';
import assert from 'node:assert/strict';
import {notificationOperation,completeNotificationOperation} from '../frontend/control-room/notification-operation.js';
test('reload retry retains its operation while another account or changed content gets a new operation',async()=>{
 const values=new Map(),storage={getItem:key=>values.get(key),setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)};
 const fingerprint=JSON.stringify(['uid-a','tenant-a','Titolo privato','Messaggio privato']);
 const first=await notificationOperation(fingerprint,storage);
 assert.equal((await notificationOperation(fingerprint,storage)).id,first.id);
 assert.notEqual((await notificationOperation(fingerprint.replace('tenant-a','tenant-b'),storage)).id,first.id);
 assert.notEqual((await notificationOperation(fingerprint.replace('uid-a','uid-b'),storage)).id,first.id);
 assert.ok([...values.values()].every(value=>!value.includes('privato')));
 completeNotificationOperation(first,storage);
 assert.notEqual((await notificationOperation(fingerprint,storage)).id,first.id);
});
