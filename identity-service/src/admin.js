import { readFile, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { randomToken, hash, requireAccess, email, uid, configuration, isHigh } from './security.js';
import { IdentityAuthority } from './authority.js';
import { FirestoreStore } from './store.js';
import { mailer } from './mail.js';

// Run only from the owner's trusted administrative environment. Never package its credentials in the web runtime.
const config = configuration(process.env);
const identityApp = initializeApp({ credential: applicationDefault(), projectId: config.projectId }, 'identity-admin-auth');
const registryApp = initializeApp({ credential: applicationDefault(), projectId: config.development ? config.projectId : config.registryProject }, 'identity-admin-registry');
const auth = getAuth(identityApp);
const store = new FirestoreStore(getFirestore(registryApp));
const args = process.argv.slice(2);
const [command, targetUid, value] = args;
try {
  if (command === 'owner-setup') {
    uid(targetUid);
    if (config.ownerProof === 'google-cloud') {
      requireAccess(targetUid === `google-${config.ownerGoogleSubject}`, 'Identità Google del proprietario non corrispondente.');
    } else {
      const user = await auth.getUser(targetUid);
      requireAccess(user.emailVerified && email(user.email) === config.ownerEmail && user.providerData.some(p => p.providerId === 'google.com'),
        'Accedi prima come proprietario tramite Google nel servizio protetto; usa l’UID mostrato dalla pagina.');
    }
    const owner = await store.get('owners/main');
    requireAccess(!owner || owner.uid === targetUid && !owner.credentials?.length, 'Il proprietario è già configurato. Usa la procedura di recupero esterna.');
    requireAccess(typeof value === 'string' && value.length > 0, 'Specifica un percorso locale esterno al repository per il codice di configurazione.', 400);
    const secret = randomToken();
    await writeFile(value, secret, { flag: 'wx', mode: 0o600 });
    await store.transaction(async tx => {
      const previous = await tx.get('owners/main');
      requireAccess(!previous || previous.uid === targetUid && !previous.credentials?.length);
      tx.set('owners/main', { uid: targetUid, email: config.ownerEmail, credentials: [], setupHash: hash(secret), setupExpiresAt: Date.now() + 1_800_000 });
    });
    process.stdout.write('Configurazione predisposta. Il codice è nel file protetto; non viene mostrato nei log. Registra due autenticatori entro 30 minuti.\n');
  } else if (command === 'migration' || command === 'migration-dry-run') {
    uid(targetUid);
    const address = email(value);
    const user = await auth.getUser(targetUid);
    requireAccess(isHigh(user.customClaims) && user.customClaims.tenantId && !user.disabled, 'Il destinatario deve essere il capo esistente.');
    const target = await auth.getUserByEmail(address).catch(error => {
      if (error.code !== 'auth/user-not-found') throw error;
      return null;
    });
    requireAccess(!target || target.uid === targetUid, 'La nuova email appartiene già a un altro account. Nessun account viene unito o sostituito.');
    if (command === 'migration-dry-run') {
      process.stdout.write(JSON.stringify({ uid: targetUid, previousEmail: user.email, newEmail: address, tenantId: user.customClaims.tenantId, preservesUid: true, writes: false }) + '\n');
    } else {
      const owner = await store.get('owners/main');
      requireAccess(owner?.uid === `google-${config.ownerGoogleSubject}` && owner.credentials?.length >= 2,
        'Configura prima il proprietario e due passkey nel servizio protetto. Nessun account è stato modificato.');
      const provider = await auth.projectConfigManager().getProjectConfig();
      requireAccess(provider.multiFactorConfig?.state === 'ENABLED'
        && provider.multiFactorConfig.providerConfigs?.some(p => p.state === 'ENABLED' && p.totpProviderConfig),
        'Configura e verifica prima il provider TOTP. Nessun account è stato modificato.');
      await writeFile(join(homedir(), '.radiotech', 'identity', `migration-${targetUid}-${Date.now()}.json`),
        JSON.stringify({ uid: user.uid, email: user.email, emailVerified: user.emailVerified,
          customClaims: user.customClaims, displayName: user.displayName, disabled: user.disabled,
          factors: (user.multiFactor?.enrolledFactors || []).map(f => ({ uid: f.uid, factorId: f.factorId })) }),
        { flag: 'wx', mode: 0o600 });
      // Freeze the authorization first. A partial migration must never leave a password-only privileged account.
      await store.transaction(async tx => {
        tx.set(`protected/${targetUid}`, { kind: 'MIGRATION', since: Date.now() });
        tx.set(`members/${targetUid}`, { uid: targetUid, email: address, tenantId: user.customClaims.tenantId,
          role: 'CHIEF_EXECUTIVE', status: 'SUSPENDED', previousEmail: user.email });
        tx.set(`audit/${randomToken()}`, { action: 'CHIEF_MIGRATION_STARTED', targetUid, at: Date.now() });
      });
      await auth.setCustomUserClaims(targetUid, { tenantId: user.customClaims.tenantId, mfaRequired: true });
      await auth.revokeRefreshTokens(targetUid);
      await auth.updateUser(targetUid, { email: address, emailVerified: false });
      const authority = new IdentityAuthority({ auth, store, config, mail: mailer(config) });
      await authority.issueInvite({ email: address, name: user.displayName || 'Capo RadioTech', tenantId: user.customClaims.tenantId,
        requestedBy: 'EXTERNAL_OWNER', kind: 'MIGRATION', targetUid });
      process.stdout.write('Migrazione preparata mantenendo l’UID. Completa conferma email, MFA e approvazione protetta.\n');
    }
  } else if (command === 'inventory') {
    const users = [];
    let pageToken;
    do {
      const result = await auth.listUsers(1000, pageToken);
      for (const user of result.users) if (isHigh(user.customClaims)) users.push({ uid: user.uid, email: user.email,
        role: user.customClaims.role, tenantId: user.customClaims.tenantId, disabled: user.disabled, emailVerified: user.emailVerified,
        factors: (user.multiFactor?.enrolledFactors || []).map(f => f.factorId) });
      pageToken = result.pageToken;
    } while (pageToken);
    process.stdout.write(JSON.stringify({ projectId: config.projectId, privileged: users }, null, 2) + '\n');
  } else {
    throw new Error('Comandi disponibili: inventory; owner-setup UID FILE_PROTETTO; migration-dry-run UID EMAIL; migration UID EMAIL.');
  }
} finally { await Promise.all([identityApp.delete(), registryApp.delete()]); }
