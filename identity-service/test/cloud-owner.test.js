import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cloudOwner } from '../src/cloud-owner.js';

test('cloud owner proof pins verified Google subject and audience independently of Firebase', async () => {
  const config = { ownerEmail: 'owner@example.test', ownerGoogleSubject: '1234567890123', ownerGoogleAudience: 'trusted-client' };
  let input;
  const claims = { email: config.ownerEmail, email_verified: true, sub: config.ownerGoogleSubject, iat: Date.now() / 1000 };
  const client = { verifyIdToken: async request => { input = request; return { getPayload: () => claims }; } };
  const verify = cloudOwner(config, client);
  assert.equal((await verify('signed-cloud-identity')).uid, 'google-1234567890123');
  assert.deepEqual(input, { idToken: 'signed-cloud-identity', audience: 'trusted-client' });
  claims.sub = 'attacker'; await assert.rejects(verify('signed-cloud-identity'));
  claims.sub = config.ownerGoogleSubject; claims.email_verified = false; await assert.rejects(verify('signed-cloud-identity'));
});

test('Firebase tokens and invalid Google signatures cannot prove ownership', async () => {
  const verify = cloudOwner({ ownerEmail: 'owner@example.test', ownerGoogleSubject: '1234567890123', ownerGoogleAudience: 'trusted-client' },
    { verifyIdToken: async () => { throw new Error('invalid issuer/audience/signature'); } });
  await assert.rejects(verify('firebase-token'));
  await assert.rejects(verify(null));
});
