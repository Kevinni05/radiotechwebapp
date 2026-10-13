import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

export class AccessError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
export function requireAccess(condition, message = 'Operazione non autorizzata.', status = 403) {
  if (!condition) throw new AccessError(status, message);
}
export const randomToken = () => randomBytes(32).toString('base64url');
export const hash = value => createHash('sha256').update(value).digest('hex');
export const HIGH_ROLES = new Set(['CAPO', 'CHIEF_EXECUTIVE', 'ADMIN', 'SUPER_ADMIN', 'NETWORK_MANAGER', 'ENGINEER']);
export const INVITED_ROLES = new Set(['CHIEF_EXECUTIVE', 'NETWORK_MANAGER', 'ENGINEER']);
export const isHigh = claims => HIGH_ROLES.has(claims?.role) || claims?.admin === true;
export function email(value) {
  requireAccess(typeof value === 'string' && value.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value), 'Indirizzo email non valido.', 400);
  return value.trim().toLowerCase();
}
export function uid(value) {
  requireAccess(typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value), 'Identità non valida.', 400);
  return value;
}
export function freshMfa(token, now = Date.now()) {
  const elapsed = now / 1000 - token.auth_time;
  requireAccess(token.email_verified === true && token.firebase?.sign_in_second_factor === 'totp'
    && Number.isFinite(elapsed) && elapsed >= -30 && elapsed <= 300,
    'Accedi nuovamente con email verificata e secondo fattore.');
}
export function operatorClaims(input) {
  const role = input.role ?? 'OPERATOR';
  requireAccess(['OPERATOR', 'VIEWER', 'CUSTOMER'].includes(role), 'I privilegi elevati richiedono approvazione esterna.');
  requireAccess(typeof input.tenantId === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(input.tenantId), 'Azienda non valida.', 400);
  const claims = { role, tenantId: input.tenantId };
  if (input.operatorId != null) claims.operatorId = uid(input.operatorId);
  if (role === 'CUSTOMER') claims.customerId = uid(input.customerId);
  if (input.mfaRequired === true) claims.mfaRequired = true;
  if (input.proPermissions != null) {
    requireAccess(Array.isArray(input.proPermissions) && input.proPermissions.length <= 40
      && input.proPermissions.every(p => typeof p === 'string' && /^[a-z][a-z0-9_-]{0,40}:READ$/.test(p)),
      'Le autorizzazioni aggiuntive di scrittura richiedono il servizio amministrativo esterno.', 400);
    claims.proPermissions = [...new Set(input.proPermissions)];
  }
  // Never accept admin, write permissions, device MFA assertions or arbitrary claims.
  return claims;
}
export async function verifyInternal(headers, method, path, body, secret, store, now = Date.now()) {
  requireAccess(secret?.length >= 32, 'Servizio non configurato.', 503);
  const timestamp = headers['x-identity-time'];
  const nonce = headers['x-identity-nonce'];
  const signature = headers['x-identity-signature'];
  requireAccess(/^\d{13}$/.test(timestamp ?? '') && /^[A-Za-z0-9_-]{32,64}$/.test(nonce ?? '')
    && /^[a-f0-9]{64}$/.test(signature ?? '') && Math.abs(now - Number(timestamp)) <= 30_000);
  const expected = createHmac('sha256', secret).update(`${method}\n${path}\n${timestamp}\n${nonce}\n${hash(body)}`).digest();
  requireAccess(timingSafeEqual(Buffer.from(signature, 'hex'), expected));
  await store.transaction(async tx => {
    const key = `nonces/${hash(nonce)}`;
    requireAccess(!await tx.get(key), 'Richiesta già utilizzata.');
    tx.set(key, { expiresAt: now + 60_000, deleteAt: new Date(now + 60_000) });
  });
}

export function configuration(env) {
  const development = env.IDENTITY_DEMO === 'true';
  const origin = new URL(env.IDENTITY_ORIGIN || 'http://localhost:18081');
  const appOrigin = new URL(env.IDENTITY_APP_ORIGIN || 'http://localhost:18080');
  const projectId = env.IDENTITY_FIREBASE_PROJECT || 'demo-radiotech';
  const registryProject = env.IDENTITY_REGISTRY_PROJECT || '';
  const mailProvider = env.IDENTITY_MAIL_PROVIDER || 'firebase';
  requireAccess(['firebase', 'smtp'].includes(mailProvider), 'Provider email non valido.', 503);
  requireAccess(origin.origin !== appOrigin.origin && !origin.username && !origin.password && origin.pathname === '/'
    && !origin.search && !origin.hash && !appOrigin.username && !appOrigin.password && appOrigin.pathname === '/'
    && !appOrigin.search && !appOrigin.hash, 'Origini identità e applicazione non valide.', 503);
  if (development) {
    requireAccess(projectId.startsWith('demo-') && env.FIREBASE_AUTH_EMULATOR_HOST && env.FIRESTORE_EMULATOR_HOST
      && origin.hostname === 'localhost' && appOrigin.hostname === 'localhost', 'Modalità demo ammessa solo con emulatori locali.', 503);
  } else {
    requireAccess(origin.protocol === 'https:' && appOrigin.protocol === 'https:' && registryProject && registryProject !== projectId
      && !env.FIREBASE_AUTH_EMULATOR_HOST && !env.FIRESTORE_EMULATOR_HOST && !env.FIREBASE_STORAGE_EMULATOR_HOST,
      'Produzione richiede HTTPS, registro in un progetto separato e nessun emulatore.', 503);
    requireAccess(env.IDENTITY_OWNER_EMAIL && env.IDENTITY_FIREBASE_API_KEY
      && /^\d{10,30}$/.test(env.IDENTITY_OWNER_GOOGLE_SUB || '') && env.IDENTITY_OWNER_GOOGLE_AUDIENCE
      && (mailProvider === 'firebase' || env.IDENTITY_SMTP_HOST && env.IDENTITY_SMTP_USER && env.IDENTITY_SMTP_PASSWORD && env.IDENTITY_MAIL_FROM),
      'Configurazione identità o recapito email incompleta.', 503);
  }
  requireAccess((env.IDENTITY_APP_SECRET || '').length >= 32, 'Segreto delle richieste applicative non valido.', 503);
  return { development, origin: origin.origin, appOrigin: appOrigin.origin, projectId, registryProject,
    ownerEmail: email(env.IDENTITY_OWNER_EMAIL || 'owner@example.test'), appSecret: env.IDENTITY_APP_SECRET,
    apiKey: env.IDENTITY_FIREBASE_API_KEY || 'demo-key', rpID: origin.hostname,
    ownerProof: development ? 'firebase-demo' : 'google-cloud', ownerGoogleSubject: env.IDENTITY_OWNER_GOOGLE_SUB,
    ownerGoogleAudience: env.IDENTITY_OWNER_GOOGLE_AUDIENCE, mailProvider, authDomain: `${projectId}.firebaseapp.com` };
}
