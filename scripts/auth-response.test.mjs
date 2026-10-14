import test from 'node:test';
import assert from 'node:assert/strict';
import {readAuthResponse} from './auth-response.mjs';

test('plain CORS rejection produces a usable login error without leaking the response', async () => {
  const result = await readAuthResponse(new Response('Invalid CORS request', {status:403}));
  assert.equal(result.message, 'Non è stato possibile completare l’accesso. Riprova tra poco.');
  assert(!result.message.includes('CORS'));
});
test('HTML gateway error and empty response cannot expose parser exceptions', async () => {
  for (const body of ['<html>upstream unavailable</html>', '']) {
    const result = await readAuthResponse(new Response(body, {status:502}));
    assert.equal(typeof result.message, 'string');
    assert(!result.message.includes('Unexpected token'));
  }
});
test('invalid successful authentication payload cannot become a session', async () => {
  for (const body of ['', 'null', '[]', '"untrusted text"']) {
    await assert.rejects(readAuthResponse(new Response(body, {status:200})), /completare l’accesso/);
  }
});
test('JSON authentication successes and denials retain their contract', async () => {
  const success = {role:'OPERATOR',user:{uid:'qa'},token:'synthetic-placeholder'};
  assert.deepEqual(await readAuthResponse(Response.json(success)), success);
  assert.deepEqual(await readAuthResponse(Response.json({message:'Account sospeso.'},{status:403})), {message:'Account sospeso.'});
});
test('plain throttling error tells the user to wait', async () => {
  assert.match((await readAuthResponse(new Response('too many requests',{status:429}))).message, /Attendi/);
});
