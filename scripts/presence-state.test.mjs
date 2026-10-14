import assert from 'node:assert/strict';
import test from 'node:test';
import {resolvePresence} from '../frontend/control-room/presence-state.js';
const now = 1_000_000;
test('foreground, background and expired evidence have distinct meanings', () => {
  for(const [state,age,label] of [['foreground',100,'App aperta'],['background',100,'In background'],['foreground',90_001,'Stato non aggiornato']]) {
    const result=resolvePresence({connections:{phone:{state,updatedAt:now-age}}},{now});
    assert.equal(result.label,label);
    assert.equal(result.online,label==='App aperta');
  }
});
test('multiple devices aggregate without a stale phone hiding active evidence', () => {
  const record={connections:{old:{state:'foreground',updatedAt:1},tablet:{state:'background',updatedAt:now},phone:{state:'foreground',updatedAt:now}}};
  assert.equal(resolvePresence(record,{now}).state,'foreground');
  delete record.connections.phone;
  assert.equal(resolvePresence(record,{now}).state,'background');
  delete record.connections.tablet;
  assert.equal(resolvePresence(record,{now}).state,'stale');
});
test('absence, legacy booleans, disconnected observer and future timestamps do not assert foreground', () => {
  assert.equal(resolvePresence(undefined,{now}).known,false);
  assert.equal(resolvePresence({connections:{phone:true}},{now}).state,'stale');
  assert.equal(resolvePresence({connections:{phone:{state:'foreground',updatedAt:now}}},{now,connected:false}).known,false);
  assert.equal(resolvePresence({connections:{phone:{state:'foreground',updatedAt:now+60_000}}},{now}).state,'stale');
  assert.equal(resolvePresence({lastSeen:now-10,connections:{}},{now}).state,'offline');
});
