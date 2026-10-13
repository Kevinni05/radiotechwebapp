import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { IdentityAuthority } from '../src/authority.js';
import { installOwnerMethods } from '../src/owner.js';
import { MemoryStore } from '../src/store.js';
import { randomToken, hash, verifyInternal, configuration, operatorClaims } from '../src/security.js';

installOwnerMethods(IdentityAuthority);

export async function fixture() {
  const now = 1_800_000_000_000;
  const store = new MemoryStore();
  const users = new Map([
    ['chief', { uid: 'chief', email: 'chief@example.test', emailVerified: true, customClaims: { role: 'CHIEF_EXECUTIVE', tenantId: 'tenant-a' }, multiFactor: { enrolledFactors: [{ factorId: 'totp', uid: 'chief-totp' }] } }],
    ['new', { uid: 'new', email: 'new@example.test', emailVerified: true, customClaims: {}, multiFactor: { enrolledFactors: [{ factorId: 'totp', uid: 'new-totp' }] } }],
    ['low', { uid: 'low', email: 'low@example.test', emailVerified: false, customClaims: { role: 'OPERATOR', tenantId: 'tenant-a' }, multiFactor: { enrolledFactors: [] } }],
    ['owner', { uid: 'owner', email: 'owner@example.test', emailVerified: true, customClaims: {}, multiFactor: { enrolledFactors: [] } }]
  ]);
  const tokens = new Map([
    ['chief-token', { uid: 'chief', email: 'chief@example.test', email_verified: true, role: 'CHIEF_EXECUTIVE', tenantId: 'tenant-a', auth_time: now / 1000, firebase: { sign_in_provider: 'password', sign_in_second_factor: 'totp', second_factor_identifier: 'chief-totp' } }],
    ['new-token', { uid: 'new', email: 'new@example.test', email_verified: true, auth_time: now / 1000, firebase: { sign_in_provider: 'password', sign_in_second_factor: 'totp', second_factor_identifier: 'new-totp' } }],
    ['owner-token', { uid: 'owner', email: 'owner@example.test', email_verified: true, auth_time: now / 1000, firebase: { sign_in_provider: 'google.com' } }]
  ]);
  const calls = [], messages = [];
  const auth = {
    getUser: async id => { if (!users.has(id)) throw new Error('missing'); return structuredClone(users.get(id)); },
    createUser: async input => { calls.push(['create', input]); const user = { ...input, uid: 'created', customClaims: {}, multiFactor: { enrolledFactors: [] } }; users.set('created', user); return user; },
    updateUser: async (id, input) => { calls.push(['update', id, input]); Object.assign(users.get(id), input); },
    setCustomUserClaims: async (id, claims) => { calls.push(['claims', id, claims]); users.get(id).customClaims = claims; },
    revokeRefreshTokens: async id => { calls.push(['revoke', id]); },
    deleteUser: async id => { calls.push(['delete', id]); users.delete(id); },
    createCustomToken: async id => { calls.push(['token', id]); return `custom-${id}`; }
  };
  // verifyIdToken must be called with revocation checking enabled.
  auth.verifyIdToken = async (value, revoked) => { assert.equal(revoked, true); if (!tokens.has(value)) throw new Error('invalid'); return structuredClone(tokens.get(value)); };
  const webauthn = {
    generateRegistrationOptions: async () => ({ challenge: randomToken() }),
    verifyRegistrationResponse: async input => { assert.equal(input.requireUserVerification, true); assert.equal(input.expectedOrigin, 'https://identity.example.test'); return { verified: true, registrationInfo: { credential: { id: randomToken(), publicKey: Buffer.from('fixture-public-key'), counter: 0 } } }; },
    generateAuthenticationOptions: async () => ({ challenge: randomToken() }),
    verifyAuthenticationResponse: async input => { assert.equal(input.requireUserVerification, true); assert.equal(input.expectedOrigin, 'https://identity.example.test'); return { verified: true, authenticationInfo: { newCounter: input.credential.counter + 1 } }; }
  };
  await store.transaction(async tx => {
    tx.set('members/chief', { uid: 'chief', email: 'chief@example.test', role: 'CHIEF_EXECUTIVE', tenantId: 'tenant-a', status: 'ACTIVE', activatedAt: now - 60_000, totpFactorIds: ['chief-totp'] });
    tx.set('owners/main', { uid: 'owner', credentials: [ { id: 'key-1', publicKey: Buffer.from('key-1').toString('base64'), counter: 0 }, { id: 'key-2', publicKey: Buffer.from('key-2').toString('base64'), counter: 0 } ] });
  });
  const authority = new IdentityAuthority({ auth, store, webauthn, now: () => now,
    config: { origin: 'https://identity.example.test', ownerEmail: 'owner@example.test', rpID: 'identity.example.test' },
    mail: { send: async message => { messages.push(message); } } });
  return { authority, store, users, tokens, calls, messages, now };
}

async function ready(f) {
  const invite = await f.authority.invite({ idToken: 'chief-token', name: 'Nuovo Capo', email: 'new@example.test' });
  const invitation = f.messages[0].text.match(/#invite=([^\s]+)/)[1];
  await f.authority.accept({ invitation, idToken: 'new-token' });
  return invite;
}

test('email and TOTP acceptance leave the user without privileged claims', async () => {
  const f = await fixture(); const invite = await ready(f);
  assert.equal((await f.store.get(`invitations/${invite.id}`)).status, 'READY');
  assert.equal((await f.store.get(`members/new`)), undefined);
  assert.equal(f.calls.length, 0);
  assert.equal((await f.authority.list('chief-token'))[0].secretHash, undefined);
});

test('only owner Google login plus a bound WebAuthn challenge grants chief', async () => {
  const f = await fixture(); const invite = await ready(f);
  await assert.rejects(f.authority.approvalOptions({ idToken: 'chief-token', action: 'APPROVE', target: invite.id }));
  const options = await f.authority.approvalOptions({ idToken: 'owner-token', action: 'APPROVE', target: invite.id });
  assert.equal(options.details.email, 'new@example.test');
  await f.authority.completeAction({ idToken: 'owner-token', challengeId: options.challengeId, response: { id: 'key-1' } });
  assert.equal((await f.store.get('members/new')).status, 'ACTIVE');
  assert.deepEqual(f.users.get('new').customClaims, { role: 'CHIEF_EXECUTIVE', tenantId: 'tenant-a', mfaRequired: true });
  assert.equal((await f.store.get(`invitations/${invite.id}`)).secretHash, null);
  await assert.rejects(f.authority.completeAction({ idToken: 'owner-token', challengeId: options.challengeId, response: { id: 'key-1' } }));
});

test('changed recipient or tenant invalidates an existing approval challenge', async () => {
  const f = await fixture(); const invite = await ready(f);
  const options = await f.authority.approvalOptions({ idToken: 'owner-token', action: 'APPROVE', target: invite.id });
  await f.store.transaction(async tx => { const item = await tx.get(`invitations/${invite.id}`); tx.set(`invitations/${invite.id}`, { ...item, tenantId: 'other-tenant' }); });
  await assert.rejects(f.authority.completeAction({ idToken: 'owner-token', challengeId: options.challengeId, response: { id: 'key-1' } }));
  assert.equal(f.calls.length, 0);
});

test('every low privilege operation refuses active and pending chiefs and the owner', async () => {
  const f = await fixture(); await ready(f);
  for (const id of ['chief', 'new', 'owner']) for (const operation of ['update', 'claims', 'clear', 'delete', 'revoke', 'token']) {
    await assert.rejects(f.authority.operator(operation, { uid: id, password: 'AttackPassword1!', claims: { role: 'OPERATOR', tenantId: 'tenant-a' } }));
  }
  assert.equal(f.calls.length, 0);
});

test('runtime cannot issue high roles or forge MFA and extra permissions', async () => {
  const f = await fixture();
  for (const role of ['ADMIN', 'SUPER_ADMIN', 'CAPO', 'CHIEF_EXECUTIVE', 'NETWORK_MANAGER', 'ENGINEER']) {
    await assert.rejects(f.authority.operator('claims', { uid: 'low', claims: { role, tenantId: 'tenant-a' } }));
  }
  assert.throws(() => operatorClaims({ role: 'OPERATOR', tenantId: 'tenant-a', proPermissions: ['accounts:WRITE'] }));
  assert.deepEqual(operatorClaims({ role: 'OPERATOR', tenantId: 'tenant-a', admin: true, radioMfaVerified: true }),
    { role: 'OPERATOR', tenantId: 'tenant-a' });
  assert.equal(f.calls.length, 0);
});

test('current factors, verified email, recent MFA and active registry are required', async () => {
  for (const change of [ t => { delete t.firebase.sign_in_second_factor; }, t => { t.email_verified = false; },
    t => { t.radioMfaVerified = true; t.radioDeviceId = 'remembered'; }, t => { t.firebase.sign_in_provider = 'custom'; },
    t => { t.auth_time -= 29_000; }, t => { t.role = 'ADMIN'; }, t => { t.tenantId = 'other'; } ]) {
    const f = await fixture(); change(f.tokens.get('chief-token')); await assert.rejects(f.authority.status('chief-token'));
  }
  const f = await fixture(); f.users.get('chief').multiFactor.enrolledFactors = [];
  await assert.rejects(f.authority.status('chief-token'));
});

test('unapproved replacement TOTP factors cannot grant or use a chief session', async () => {
  const f = await fixture();
  f.users.get('chief').multiFactor.enrolledFactors = [{ factorId: 'totp', uid: 'replacement' }];
  f.tokens.get('chief-token').firebase.second_factor_identifier = 'replacement';
  await assert.rejects(f.authority.status('chief-token'));
  const g = await fixture();
  const request = await ready(g);
  const options = await g.authority.approvalOptions({ idToken: 'owner-token', action: 'APPROVE', target: request.id });
  g.users.get('new').multiFactor.enrolledFactors = [{ factorId: 'totp', uid: 'replacement' }];
  await assert.rejects(g.authority.completeAction({ idToken: 'owner-token', challengeId: options.challengeId, response: { id: 'key-1' } }));
  assert.equal(g.calls.length, 0);
});

test('Firebase invitation budgets run before creating any account or sending a second email', async () => {
  const f = await fixture();
  f.authority.config.mailProvider = 'firebase';
  f.auth = f.authority.auth;
  f.auth.getUserByEmail = async () => { throw Object.assign(new Error('missing'), { code: 'auth/user-not-found' }); };
  await f.authority.invite({ idToken: 'chief-token', name: 'Nuova persona', email: 'unused@example.test' });
  assert.equal(f.calls.filter(c => c[0] === 'create').length, 1);
  assert.equal(f.messages.length, 1);
  await assert.rejects(f.authority.invite({ idToken: 'chief-token', name: 'Nuova persona', email: 'unused@example.test' }), e => e.status === 429);
  assert.equal(f.calls.filter(c => c[0] === 'create').length, 1);
  assert.equal(f.messages.length, 1);
});

test('Google Cloud owner proof does not promote or reserve a separate operator with the same email', async () => {
  const f = await fixture();
  f.authority.config.ownerProof = 'google-cloud';
  f.users.get('low').email = f.authority.config.ownerEmail;
  await f.store.transaction(async tx => tx.set('owners/main', { uid: 'google-12345678901234567890', credentials: [] }));
  await f.authority.operator('claims', { uid: 'low', claims: { role: 'OPERATOR', tenantId: 'tenant-a' } });
  assert.deepEqual(f.users.get('low').customClaims, { role: 'OPERATOR', tenantId: 'tenant-a' });
  assert.equal(await f.store.get('members/low'), undefined);
});

test('email verification and real enrolled MFA cannot be supplied as browser flags', async () => {
  const f = await fixture(); const invite = await f.authority.invite({ idToken: 'chief-token', name: 'Nuovo Capo', email: 'new@example.test' });
  const invitation = f.messages[0].text.match(/#invite=([^\s]+)/)[1];
  f.users.get('new').multiFactor.enrolledFactors = [];
  await assert.rejects(f.authority.accept({ invitation, idToken: 'new-token', mfaVerified: true, emailVerified: true }));
  assert.equal((await f.store.get(`invitations/${invite.id}`)).status, 'PENDING');
});

test('wrong email or foreign tenant cannot accept a stolen invitation', async () => {
  const f = await fixture(); await f.authority.invite({ idToken: 'chief-token', name: 'Nuovo Capo', email: 'new@example.test' });
  const invitation = f.messages[0].text.match(/#invite=([^\s]+)/)[1];
  f.tokens.get('new-token').tenantId = 'other';
  await assert.rejects(f.authority.accept({ invitation, idToken: 'new-token' }));
});

test('expired invitation, cancelled invitation and old MFA cannot grant privileges', async () => {
  const f = await fixture(); const invite = await ready(f);
  await f.authority.cancel('chief-token', invite.id);
  await assert.rejects(f.authority.approvalOptions({ idToken: 'owner-token', action: 'APPROVE', target: invite.id }));
  f.tokens.get('chief-token').auth_time -= 301;
  await assert.rejects(f.authority.invite({ idToken: 'chief-token', name: 'Altro Capo', email: 'other@example.test' }));
});

test('password-only token for the owner cannot register passkeys or approve', async () => {
  const f = await fixture(); f.tokens.get('owner-token').firebase.sign_in_provider = 'password';
  await assert.rejects(f.authority.owner('owner-token'));
});

test('WebAuthn failure consumes the challenge and cannot grant', async () => {
  const f = await fixture(); const invite = await ready(f);
  const options = await f.authority.approvalOptions({ idToken: 'owner-token', action: 'APPROVE', target: invite.id });
  f.authority.webauthn.verifyAuthenticationResponse = async () => ({ verified: false });
  await assert.rejects(f.authority.completeAction({ idToken: 'owner-token', challengeId: options.challengeId, response: { id: 'key-1' } }));
  assert.equal(f.calls.length, 0);
  assert.equal((await f.store.get(`challenges/${options.challengeId}`)).used, true);
});

test('partial grant fails closed and can be retried only by a new owner approval', async () => {
  const f = await fixture(); const invite = await ready(f);
  let fails = true;
  f.authority.auth.setCustomUserClaims = async (id, claims) => { if (fails) throw new Error('provider down'); f.users.get(id).customClaims = claims; };
  const options = await f.authority.approvalOptions({ idToken: 'owner-token', action: 'APPROVE', target: invite.id });
  await assert.rejects(f.authority.completeAction({ idToken: 'owner-token', challengeId: options.challengeId, response: { id: 'key-1' } }));
  assert.equal((await f.store.get('members/new')).status, 'SUSPENDED');
  assert.equal((await f.store.get(`invitations/${invite.id}`)).status, 'GRANT_FAILED');
  await assert.rejects(f.authority.operator('delete', { uid: 'new' }));
  fails = false;
  const retry = await f.authority.approvalOptions({ idToken: 'owner-token', action: 'APPROVE', target: invite.id });
  await f.authority.completeAction({ idToken: 'owner-token', challengeId: retry.challengeId, response: { id: 'key-1' } });
  assert.equal((await f.store.get('members/new')).status, 'ACTIVE');
});

test('recovery suspends, revokes and requires a new bound invitation and approval', async () => {
  const f = await fixture();
  const options = await f.authority.approvalOptions({ idToken: 'owner-token', action: 'RECOVER', target: 'chief' });
  const result = await f.authority.completeAction({ idToken: 'owner-token', challengeId: options.challengeId, response: { id: 'key-1' } });
  assert.equal((await f.store.get('members/chief')).status, 'SUSPENDED');
  assert.equal((await f.store.get(`invitations/${result.id}`)).targetUid, 'chief');
  assert.equal(f.users.get('chief').customClaims.role, undefined);
  assert.deepEqual(f.users.get('chief').multiFactor.enrolledFactors, []);
  await assert.rejects(f.authority.status('chief-token'));
});

test('email delivery failure leaves invitation unusable and grants nothing', async () => {
  const f = await fixture(); f.authority.mail.send = async () => { throw new Error('smtp'); };
  await assert.rejects(f.authority.invite({ idToken: 'chief-token', name: 'Nuovo Capo', email: 'new@example.test' }));
  assert.equal((await f.store.list('invitations'))[0].status, 'DELIVERY_FAILED');
  assert.equal(f.calls.length, 0);
});

test('two concurrent operations on the same user do not race', async () => {
  const f = await fixture(); let release; const pending = new Promise(r => { release = r; });
  const first = f.authority.withUserLock('low', () => pending);
  await new Promise(r => setTimeout(r, 1));
  await assert.rejects(f.authority.operator('delete', { uid: 'low' }));
  release(); await first;
  assert.equal(f.calls.length, 0);
});

test('HMAC requests bind body/path/time and reject replay', async () => {
  const store = new MemoryStore(); const now = Date.now(), body = Buffer.from('{"uid":"low"}');
  const secret = 's'.repeat(40), nonce = randomToken(), path = '/internal/operator/token';
  const signature = createHmac('sha256', secret).update(`POST\n${path}\n${now}\n${nonce}\n${hash(body)}`).digest('hex');
  const headers = { 'x-identity-time': String(now), 'x-identity-nonce': nonce, 'x-identity-signature': signature };
  await verifyInternal(headers, 'POST', path, body, secret, store, now);
  await assert.rejects(verifyInternal(headers, 'POST', path, body, secret, store, now));
  await assert.rejects(verifyInternal(headers, 'POST', '/internal/owner/approve', body, secret, new MemoryStore(), now));
  await assert.rejects(verifyInternal(headers, 'POST', path, Buffer.from('{}'), secret, new MemoryStore(), now));
  await assert.rejects(verifyInternal(headers, 'POST', path, body, secret, new MemoryStore(), now + 31_000));
});

test('production rejects common origins, emulator settings and a shared registry project', () => {
  const base = { IDENTITY_ORIGIN: 'https://identity.example.test', IDENTITY_APP_ORIGIN: 'https://app.example.test',
    IDENTITY_FIREBASE_PROJECT: 'radio-production', IDENTITY_REGISTRY_PROJECT: 'radio-isolated-identity',
    IDENTITY_APP_SECRET: 's'.repeat(40), IDENTITY_OWNER_EMAIL: 'owner@example.test', IDENTITY_FIREBASE_API_KEY: 'public-key',
    IDENTITY_OWNER_GOOGLE_SUB: '101939638982626665189', IDENTITY_OWNER_GOOGLE_AUDIENCE: 'trusted-client.apps.googleusercontent.com',
    IDENTITY_SMTP_HOST: 'smtp.example.test', IDENTITY_SMTP_USER: 'sender', IDENTITY_SMTP_PASSWORD: 'password', IDENTITY_MAIL_FROM: 'sender@example.test' };
  assert.equal(configuration(base).development, false);
  for (const patch of [{ IDENTITY_REGISTRY_PROJECT: 'radio-production' }, { FIREBASE_AUTH_EMULATOR_HOST: 'localhost:9099' },
    { IDENTITY_APP_ORIGIN: base.IDENTITY_ORIGIN }, { IDENTITY_ORIGIN: 'http://identity.example.test' }, { IDENTITY_APP_SECRET: 'weak' }]) {
    assert.throws(() => configuration({ ...base, ...patch }));
  }
});
