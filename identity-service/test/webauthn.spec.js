import { test, expect } from '@playwright/test';
import { createServer } from 'node:http';
import { build } from 'esbuild';
import { MemoryStore } from '../src/store.js';
import { IdentityAuthority } from '../src/authority.js';
import { installOwnerMethods, webauthn } from '../src/owner.js';
import { handler } from '../src/server.js';
import { hash } from '../src/security.js';

installOwnerMethods(IdentityAuthority);
let server, authority, store, origin;

test.beforeAll(async () => {
  const bundle = await build({ stdin: { contents: "export {startRegistration,startAuthentication} from '@simplewebauthn/browser';", resolveDir: process.cwd() },
    bundle: true, write: false, format: 'esm' });
  store = new MemoryStore();
  const ownerToken = { uid: 'owner', email: 'owner@example.test', email_verified: true, auth_time: Date.now() / 1000, firebase: { sign_in_provider: 'google.com' } };
  const newUser = { uid: 'new', email: 'new@example.test', emailVerified: true, customClaims: {}, multiFactor: { enrolledFactors: [{ factorId: 'totp', uid: 'new-totp' }] } };
  const auth = { verifyIdToken: async token => { if (token !== 'owner-token') throw new Error('bad token'); return ownerToken; },
    getUser: async () => structuredClone(newUser), setCustomUserClaims: async (uid, claims) => { newUser.customClaims = claims; }, revokeRefreshTokens: async () => {} };
  const config = { origin: 'http://localhost:18082', appOrigin: 'http://localhost:18080', rpID: 'localhost', ownerEmail: 'owner@example.test', development: true, appSecret: 's'.repeat(40) };
  authority = new IdentityAuthority({ auth, store, config, webauthn, mail: { send: async () => {} } });
  const api = handler(authority, config);
  server = createServer((req, res) => {
    if (req.url === '/test-client.js') { res.setHeader('Content-Type', 'text/javascript'); return res.end(bundle.outputFiles[0].text); }
    if (req.url === '/test-page') { res.setHeader('Content-Type', 'text/html'); return res.end('<!doctype html><html><body>Virtual WebAuthn test</body></html>'); }
    return api(req, res);
  });
  await new Promise(resolve => server.listen(18082, '127.0.0.1', resolve));
  origin = config.origin;
});
test.afterAll(async () => { await new Promise(resolve => server.close(resolve)); });

test('real WebAuthn verification requires the bound owner challenge and rejects replay', async ({ page }) => {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('WebAuthn.enable');
  const setupCode = 'test-owner-setup-code';
  await store.transaction(async tx => tx.set('owners/main', { uid: 'owner', credentials: [], setupHash: hash(setupCode), setupExpiresAt: Date.now() + 60_000 }));
  await page.goto(`${origin}/test-page`);
  let virtualId;
  for (let index = 0; index < 2; index++) {
    if (virtualId) await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId: virtualId });
    const virtual = await cdp.send('WebAuthn.addVirtualAuthenticator', { options: { protocol: 'ctap2', transport: 'internal', hasResidentKey: true,
      hasUserVerification: true, isUserVerified: true, automaticPresenceSimulation: true } });
    virtualId = virtual.authenticatorId;
    const result = await page.evaluate(async setupCode => {
      const { startRegistration } = await import('/test-client.js');
      const options = await (await fetch('/api/owner/register/options', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken: 'owner-token', setupCode }) })).json();
      const response = await startRegistration({ optionsJSON: options.options });
      const verified = await fetch('/api/owner/register/complete', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken: 'owner-token', challengeId: options.challengeId, response }) });
      return { status: verified.status, result: await verified.json() };
    }, setupCode);
    expect(result.status).toBe(200); expect(result.result.registered).toBe(true);
  }
  const inviteId = 'bound-invitation';
  await store.transaction(async tx => tx.set(`invitations/${inviteId}`, { uid: 'new', email: 'new@example.test', name: 'New Chief',
    tenantId: 'tenant-a', role: 'CHIEF_EXECUTIVE', kind: 'NEW', status: 'READY', expiresAt: Date.now() + 60_000, totpFactorIds: ['new-totp'] }));
  const verified = await page.evaluate(async inviteId => {
    const { startAuthentication } = await import('/test-client.js');
    const post = (path, data) => fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
    const options = await (await post('/api/owner/action/options', { idToken: 'owner-token', action: 'APPROVE', target: inviteId })).json();
    const response = await startAuthentication({ optionsJSON: options.options });
    const input = { idToken: 'owner-token', challengeId: options.challengeId, response };
    const first = await post('/api/owner/action/complete', input);
    const replay = await post('/api/owner/action/complete', input);
    return { first: first.status, replay: replay.status };
  }, inviteId);
  expect(verified.first).toBe(200); expect(verified.replay).toBe(403);
  expect((await store.get('members/new')).status).toBe('ACTIVE');
  await cdp.send('WebAuthn.disable');
});

test('API rejects a different origin and GET never activates invitations', async ({ request }) => {
  const denied = await request.post(`${origin}/api/owner/list`, { headers: { Origin: 'https://attacker.example.test' }, data: { idToken: 'owner-token' } });
  expect(denied.status()).toBe(403);
  const scanned = await request.get(`${origin}/api/accept?invitation=stolen`);
  expect(scanned.status()).toBe(404);
});
