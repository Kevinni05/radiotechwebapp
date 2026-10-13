import { readFile } from 'node:fs/promises';
import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { isHigh } from './security.js';

// Optional external administrative credential used only for a read-only inventory, never the web runtime.
const path = process.argv[2];
if (!path) throw new Error('Specifica il percorso della credenziale amministrativa esterna.');
const account = JSON.parse(await readFile(path, 'utf8'));
if (account.project_id !== 'gestionale-radio') throw new Error('Progetto non previsto.');
const credential = cert(account);
const app = initializeApp({ credential, projectId: account.project_id }, 'readonly-inventory');
try {
  const selected = [];
  let pageToken;
  do {
    const result = await getAuth(app).listUsers(1000, pageToken);
    for (const user of result.users) if (isHigh(user.customClaims) || user.email === 'kcagnazzo@gmail.com') {
      selected.push({ uid: user.uid, email: user.email, emailVerified: user.emailVerified, role: user.customClaims?.role,
        tenantId: user.customClaims?.tenantId, disabled: user.disabled, providers: user.providerData.map(p => p.providerId),
        factors: (user.multiFactor?.enrolledFactors || []).map(f => f.factorId) });
    }
    pageToken = result.pageToken;
  } while (pageToken);
  process.stdout.write(JSON.stringify({ projectId: account.project_id, selected }, null, 2) + '\n');
  const access = await credential.getAccessToken();
  const response = await fetch(`https://identitytoolkit.googleapis.com/admin/v2/projects/${account.project_id}/config`, {
    headers: { Authorization: `Bearer ${access.access_token}` }, signal: AbortSignal.timeout(15000) });
  if (response.ok) {
    const config = await response.json();
    process.stdout.write(JSON.stringify({ identityPlatform: { mfa: config.mfa, authorizedDomains: config.authorizedDomains,
      emailPasswordEnabled: config.signIn?.email?.enabled, upgradeTime: config.upgradeTime } }) + '\n');
  } else process.stdout.write(JSON.stringify({ identityPlatform: { httpStatus: response.status } }) + '\n');
  const iam = await fetch(`https://cloudresourcemanager.googleapis.com/v1/projects/${account.project_id}:testIamPermissions`, {
    method: 'POST', headers: { Authorization: `Bearer ${access.access_token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ permissions: ['run.services.create', 'run.services.update', 'resourcemanager.projects.setIamPolicy',
      'iam.serviceAccounts.create', 'serviceusage.services.enable', 'firebaseauth.configs.update', 'firebaseauth.users.update'] }),
    signal: AbortSignal.timeout(15000) });
  process.stdout.write(JSON.stringify({ administrativeCredentialPermissions: iam.ok ? (await iam.json()).permissions || [] : { httpStatus: iam.status } }) + '\n');
} finally { await app.delete(); }
