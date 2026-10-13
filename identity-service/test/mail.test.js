import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mailer } from '../src/mail.js';

test('managed invitation email sets a password but does not assign claims or mark email verified', async () => {
  const config = { mailProvider: 'firebase', apiKey: 'public-key', origin: 'https://identity.example.test' };
  let request;
  const mail = mailer(config, {}, async (url, options) => { request = { url, input: JSON.parse(options.body) }; return { ok: true }; });
  await mail.send({ to: 'new@example.test', invitationUrl: config.origin + '/#invite=bound-link' });
  assert.deepEqual(request.input, { requestType: 'PASSWORD_RESET', email: 'new@example.test',
    continueUrl: 'https://identity.example.test/#invite=bound-link', canHandleCodeInApp: false });
  await assert.rejects(mail.send({ to: 'new@example.test', invitationUrl: 'https://attacker.example.test/#invite=stolen' }));
});

test('delivery failure is reported and activation notices cannot send arbitrary reset links', async () => {
  let calls = 0;
  const mail = mailer({ mailProvider: 'firebase', apiKey: 'public-key', origin: 'https://identity.example.test' }, {},
    async () => { calls++; return { ok: false }; });
  await assert.rejects(mail.send({ to: 'new@example.test', invitationUrl: 'https://identity.example.test/#invite=valid' }));
  await mail.send({ to: 'new@example.test', subject: 'already activated' });
  assert.equal(calls, 1);
});
