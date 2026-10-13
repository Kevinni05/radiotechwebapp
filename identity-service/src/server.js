import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { mailer } from './mail.js';
import { configuration, verifyInternal, requireAccess } from './security.js';
import { FirestoreStore } from './store.js';
import { IdentityAuthority } from './authority.js';
import { installOwnerMethods, webauthn } from './owner.js';

installOwnerMethods(IdentityAuthority);

export function handler(authority, config) {
  const budgets = new Map();
  let globalWindow = { at: Date.now(), count: 0 };
  const files = new Map([['/', 'index.html'], ['/access.js', 'access.js'], ['/access.css', 'access.css']]);
  return async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
    if (!config.development) res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
    res.setHeader('Content-Security-Policy', `default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://www.googleapis.com${config.development ? ' http://localhost:9099' : ''}; frame-src https://${config.authDomain}; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`);
    const json = (status, value) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(value)); };
    try {
      const now = Date.now();
      if (now - globalWindow.at >= 60_000) globalWindow = { at: now, count: 0 };
      requireAccess(++globalWindow.count <= 1800, 'Troppi tentativi. Riprova più tardi.', 429);
      const address = req.socket.remoteAddress;
      if (budgets.size >= 5000) for (const [key, b] of budgets) if (now - b.at >= 60_000) budgets.delete(key);
      requireAccess(budgets.has(address) || budgets.size < 5000, 'Servizio occupato.', 429);
      let budget = budgets.get(address);
      if (!budget || now - budget.at >= 60_000) { budget = { at: now, count: 0 }; budgets.set(address, budget); }
      requireAccess(++budget.count <= 600, 'Troppi tentativi. Riprova più tardi.', 429);
      const path = new URL(req.url, config.origin).pathname;
      if (req.method === 'GET' && path === '/health') return json(200, { status: 'UP' });
      if (req.method === 'GET' && files.has(path)) {
        const data = await readFile(new URL(`../public/${files.get(path)}`, import.meta.url));
        res.writeHead(200, { 'Content-Type': path.endsWith('.js') ? 'text/javascript; charset=utf-8' : path.endsWith('.css') ? 'text/css; charset=utf-8' : 'text/html; charset=utf-8' });
        return res.end(data);
      }
      if (req.method === 'GET' && path === '/config') return json(200, { apiKey: config.apiKey, projectId: config.projectId,
        authDomain: config.authDomain, appOrigin: config.appOrigin, ownerEmail: config.ownerEmail, ownerProof: config.ownerProof, demo: config.development });
      requireAccess(req.method === 'POST', 'Percorso non disponibile.', 404);
      requireAccess(req.headers['content-type']?.split(';')[0] === 'application/json', 'Formato non valido.', 415);
      const parts = [];
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        requireAccess(size <= 32_768, 'Richiesta troppo grande.', 413);
        parts.push(chunk);
      }
      const raw = Buffer.concat(parts);
      let input;
      try { input = JSON.parse(raw.toString()); } catch { requireAccess(false, 'Richiesta non valida.', 400); }
      requireAccess(input && typeof input === 'object' && !Array.isArray(input), 'Richiesta non valida.', 400);
      if (path.startsWith('/internal/')) {
        await verifyInternal(req.headers, req.method, path, raw, config.appSecret, authority.store);
        if (path === '/internal/status') return json(200, await authority.status(input.idToken));
        if (path === '/internal/device-session') return json(200, await authority.deviceSession(input));
        if (path === '/internal/invite') return json(200, await authority.invite(input));
        if (path === '/internal/invitations') return json(200, { items: await authority.list(input.idToken) });
        if (path === '/internal/cancel') return json(200, await authority.cancel(input.idToken, input.id));
        if (/^\/internal\/operator\/(create|update|claims|clear|delete|revoke|token)$/.test(path)) {
          return json(200, await authority.operator(path.split('/').at(-1), input));
        }
      } else {
        requireAccess(req.headers.origin === config.origin, 'Origine della richiesta non autorizzata.');
        if (path === '/api/invitation') return json(200, await authority.inspect(input.invitation));
        if (path === '/api/accept') return json(200, await authority.accept(input));
        if (path === '/api/session') { await authority.status(input.idToken); return json(200, { authorized: true }); }
        if (path === '/api/owner/list') {
          await authority.owner(input.idToken);
          return json(200, { invitations: await authority.list(input.idToken, true), members: await authority.store.list('members'),
            credentials: (await authority.store.get('owners/main'))?.credentials?.length || 0 });
        }
        if (path === '/api/owner/register/options') return json(200, await authority.registrationOptions(input));
        if (path === '/api/owner/register/complete') return json(200, await authority.registerPasskey(input));
        if (path === '/api/owner/action/options') return json(200, await authority.approvalOptions(input));
        if (path === '/api/owner/action/complete') return json(200, await authority.completeAction(input));
      }
      return json(404, { message: 'Percorso non disponibile.' });
    } catch (error) {
      // Never emit provider exception bodies, credentials, request bodies or invitation secrets.
      const status = Number.isInteger(error.status) ? error.status : 503;
      if (status === 429) res.setHeader('Retry-After', '60');
      return json(status, { message: error.status ? error.message : 'Operazione non completata. Riprova o contatta il proprietario.' });
    }
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { initializeApp, applicationDefault } = await import('firebase-admin/app');
  const { getAuth } = await import('firebase-admin/auth');
  const { getFirestore } = await import('firebase-admin/firestore');
  const { cloudOwner } = await import('./cloud-owner.js');
  const config = configuration(process.env);
  const identityApp = initializeApp({ credential: applicationDefault(), projectId: config.projectId }, 'identity-auth');
  const registryApp = initializeApp({ credential: applicationDefault(), projectId: config.development ? config.projectId : config.registryProject }, 'identity-registry');
  const store = new FirestoreStore(getFirestore(registryApp));
  const mail = mailer(config);
  const authority = new IdentityAuthority({ auth: getAuth(identityApp), store, mail, config, webauthn,
    ownerProofVerifier: config.development ? undefined : cloudOwner(config) });
  const server = createServer(handler(authority, config));
  server.requestTimeout = 30_000;
  server.headersTimeout = 15_000;
  server.listen(Number(process.env.PORT || 18081), '0.0.0.0', () => process.stdout.write('RadioTech identity authority ready\n'));
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => server.close(() => process.exit(0)));
}
