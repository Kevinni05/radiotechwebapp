import { test } from 'node:test';
import assert from 'node:assert/strict';
import { IdentityAuthority } from '../src/authority.js';
import { MemoryStore } from '../src/store.js';
import { hash } from '../src/security.js';

function fixture() {
  const now = 1_800_000_000_000, store = new MemoryStore(), calls = [];
  const user = { uid: 'device-user', email: 'device@example.test', emailVerified: false,
    customClaims: { role: 'OPERATOR', tenantId: 'tenant-a' }, multiFactor: { enrolledFactors: [] } };
  const token = { uid: user.uid, email: user.email, role: 'OPERATOR', tenantId: 'tenant-a',
    auth_time: now / 1000, firebase: { sign_in_provider: 'password' } };
  const auth = {
    verifyIdToken: async (value, revoked) => { assert.equal(revoked, true); assert.equal(value, 'proof'); return structuredClone(token); },
    getUser: async value => { assert.equal(value, user.uid); return structuredClone(user); },
    createCustomToken: async (value, claims) => { calls.push({ uid: value, claims }); return 'signed-device-token'; }
  };
  const authority = new IdentityAuthority({ auth, store, config: { ownerProof: 'google-cloud' }, now: () => now });
  const input = { idToken: 'proof', uid: user.uid, tenantId: 'tenant-a', installationId: '0123456789abcdef' };
  return { authority, user, token, input, store, calls, now };
}

test('device sessions bind the fresh verified user and installation without accepting caller claims', async () => {
  for (const role of ['OPERATOR', 'VIEWER', 'CUSTOMER']) {
    const f = fixture(); f.user.customClaims.role = f.token.role = role;
    const result = await f.authority.deviceSession({ ...f.input, radioDeviceId: 'forged', radioMfaVerified: true, admin: true });
    const deviceId = hash('tenant-a:device-user:0123456789abcdef');
    assert.deepEqual(result, { uid: 'device-user', tenantId: 'tenant-a', deviceId, customToken: 'signed-device-token' });
    assert.deepEqual(f.calls, [{ uid: 'device-user', claims: { radioDeviceId: deviceId, radioMfaVerified: false } }]);
    assert.deepEqual(f.user.customClaims, { role, tenantId: 'tenant-a' });
  }
});

test('device issuance rejects UID, tenant, stale or bound proofs before signing', async () => {
  for (const mutate of [
    f => { f.input.uid = 'another'; }, f => { f.input.tenantId = 'other'; },
    f => { f.token.auth_time -= 301; }, f => { f.token.auth_time += 31; },
    f => { delete f.token.auth_time; }, f => { f.token.radioDeviceId = 'old'; },
    f => { f.token.radioMfaVerified = true; }, f => { f.input.installationId = 'short'; },
    f => { f.user.disabled = true; }, f => { f.user.customClaims.tenantId = 'changed'; },
    f => { f.user.customClaims.role = 'VIEWER'; }
  ]) {
    const f = fixture(); mutate(f);
    await assert.rejects(f.authority.deviceSession(f.input)); assert.equal(f.calls.length, 0);
  }
});

test('owners, privileged users and pending grants cannot obtain device tokens', async () => {
  for (const role of ['CHIEF_EXECUTIVE', 'CAPO', 'ADMIN', 'SUPER_ADMIN', 'NETWORK_MANAGER', 'ENGINEER']) {
    const f = fixture(); f.user.customClaims.role = f.token.role = role;
    await assert.rejects(f.authority.deviceSession(f.input)); assert.equal(f.calls.length, 0);
  }
  for (const key of ['owners/main', 'members/device-user', 'protected/device-user']) {
    const f = fixture(); await f.store.transaction(async tx => tx.set(key, { uid: f.user.uid, status: 'ACTIVE' }));
    await assert.rejects(f.authority.deviceSession(f.input)); assert.equal(f.calls.length, 0);
  }
  const f = fixture(); f.user.customClaims.admin = true;
  await assert.rejects(f.authority.deviceSession(f.input)); assert.equal(f.calls.length, 0);
});

test('an enrolled or required MFA factor cannot be replaced by a runtime assertion', async () => {
  for (const mutate of [
    f => { f.user.customClaims.mfaRequired = true; },
    f => { f.user.multiFactor.enrolledFactors = [{ factorId: 'totp', uid: 'factor' }]; },
    f => { f.token.firebase.sign_in_second_factor = 'totp'; f.token.email_verified = true; }
  ]) {
    const f = fixture(); mutate(f);
    await assert.rejects(f.authority.deviceSession({ ...f.input, radioMfaVerified: true })); assert.equal(f.calls.length, 0);
  }
});

test('a current TOTP proof yields MFA-bound claims and rejects removed factors or custom sign-in', async () => {
  const make = () => {
    const f = fixture(); f.user.emailVerified = f.token.email_verified = true;
    f.user.customClaims.mfaRequired = true;
    f.user.multiFactor.enrolledFactors = [{ factorId: 'totp', uid: 'factor' }];
    Object.assign(f.token.firebase, { sign_in_second_factor: 'totp', second_factor_identifier: 'factor' });
    return f;
  };
  const f = make(); await f.authority.deviceSession(f.input);
  assert.equal(f.calls[0].claims.radioMfaVerified, true);
  for (const mutate of [
    item => { item.token.firebase.second_factor_identifier = 'removed'; },
    item => { item.user.multiFactor.enrolledFactors = []; },
    item => { item.token.firebase.sign_in_provider = 'custom'; },
    item => { item.token.email = 'changed@example.test'; }
  ]) {
    const item = make(); mutate(item);
    await assert.rejects(item.authority.deviceSession(item.input)); assert.equal(item.calls.length, 0);
  }
});

test('a legacy low-privilege QR proof may enroll only while unbound and without MFA', async () => {
  const f = fixture(); f.token.firebase.sign_in_provider = 'custom';
  await f.authority.deviceSession(f.input); assert.equal(f.calls[0].claims.radioMfaVerified, false);
});

test('revocation after the initial proof check prevents signing under the user lock', async () => {
  const f = fixture(); let checks = 0;
  f.authority.auth.verifyIdToken = async (_, revoked) => {
    assert.equal(revoked, true);
    if (++checks > 1) throw new Error('revoked');
    return structuredClone(f.token);
  };
  await assert.rejects(f.authority.deviceSession(f.input), error => error.status === 401);
  assert.equal(f.calls.length, 0);
  assert.equal((await f.store.get('userLocks/device-user')).lease, null);
});
