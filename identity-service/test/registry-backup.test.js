import test from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {Timestamp,GeoPoint} from 'firebase-admin/firestore';
import {seal,open,encodeValue,decodeValue,requireIsolatedRestore} from '../src/registry-backup.js';

test('protected registry preserves binary credentials and timestamps without plaintext in the archive',()=>{
 const key=randomBytes(32),record={name:'private-owner@example.test',credential:Buffer.from([0,255,17]),time:new Timestamp(123,456),location:new GeoPoint(41,12),roles:['ADMIN'],nested:{$type:'ordinary user data'}};
 const snapshot={registryProject:'identity-project',documents:[{path:'subjects/owner',value:encodeValue(record)}],secrets:[{name:'application-shared-secret',base64:Buffer.from('private-shared-secret').toString('base64')}]};
 const archive=seal(snapshot,key);assert.equal(archive.includes(Buffer.from(record.name)),false);assert.equal(archive.includes(Buffer.from('private-shared-secret')),false);
 const restored=open(archive,key,'identity-project');assert.deepEqual(decodeValue(restored.documents[0].value),record);
 assert.notDeepEqual(seal(snapshot,key),archive);
});
test('tampering, wrong key/project and repeated document paths cannot be restored',()=>{
 const key=randomBytes(32),snapshot={registryProject:'identity-project',documents:[{path:'subjects/owner',value:encodeValue({})}],secrets:[]};
 const archive=seal(snapshot,key),tampered=Buffer.from(archive);tampered[tampered.length-1]^=1;
 assert.throws(()=>open(tampered,key,'identity-project'));assert.throws(()=>open(archive,randomBytes(32),'identity-project'));assert.throws(()=>open(archive,key,'other-project'));
 assert.throws(()=>open(seal({...snapshot,documents:[...snapshot.documents,...snapshot.documents]},key),key,'identity-project'));
 assert.throws(()=>open(Buffer.from('{}'),key,'identity-project'));
});
test('restore cannot write to a cloud host or a production project',()=>{
 requireIsolatedRestore('127.0.0.1:8180','demo-radiotech-identity-restore');
 assert.throws(()=>requireIsolatedRestore('firestore.googleapis.com:443','demo-radiotech-identity-restore'));
 assert.throws(()=>requireIsolatedRestore('127.0.0.1:8180','identity-project'));
});
