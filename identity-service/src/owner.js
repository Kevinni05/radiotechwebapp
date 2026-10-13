import { AccessError, requireAccess, randomToken, hash, uid, email } from './security.js';
import { generateRegistrationOptions, verifyRegistrationResponse, generateAuthenticationOptions, verifyAuthenticationResponse } from '@simplewebauthn/server';

export const webauthn = { generateRegistrationOptions, verifyRegistrationResponse, generateAuthenticationOptions, verifyAuthenticationResponse };

export function installOwnerMethods(Authority) {
  Authority.prototype.registrationOptions = async function ({ idToken, setupCode }) {
    const token = await this.owner(idToken);
    const owner = await this.store.get('owners/main');
    requireAccess(typeof setupCode === 'string' && owner.setupExpiresAt > this.now()
      && owner.setupHash === hash(setupCode) && (owner.credentials?.length ?? 0) < 2, 'Configurazione passkey non autorizzata.');
    const options = await this.webauthn.generateRegistrationOptions({ rpName: 'RadioTech · Approva accessi', rpID: this.config.rpID,
      userName: this.config.ownerEmail, userID: Buffer.from(token.uid), attestationType: 'none',
      authenticatorSelection: { residentKey: 'required', userVerification: 'required' },
      excludeCredentials: (owner.credentials || []).map(c => ({ id: c.id })) });
    const id = randomToken();
    await this.store.transaction(async tx => tx.set(`challenges/${id}`, { challenge: options.challenge, purpose: 'REGISTER',
      ownerUid: token.uid, setupHash: hash(setupCode), expiresAt: this.now() + 120_000,
      deleteAt: new Date(this.now() + 3_600_000), used: false }));
    return { challengeId: id, options };
  };
  Authority.prototype.takeChallenge = async function (id, ownerUid, purpose) {
    uid(id);
    return this.store.transaction(async tx => {
      const challenge = await tx.get(`challenges/${id}`);
      requireAccess(challenge && !challenge.used && challenge.ownerUid === ownerUid && challenge.purpose === purpose
        && challenge.expiresAt > this.now(), 'Verifica scaduta o già utilizzata.');
      tx.set(`challenges/${id}`, { ...challenge, used: true });
      return challenge;
    });
  };
  Authority.prototype.registerPasskey = async function ({ idToken, challengeId, response }) {
    const token = await this.owner(idToken);
    const challenge = await this.takeChallenge(challengeId, token.uid, 'REGISTER');
    let result;
    try { result = await this.webauthn.verifyRegistrationResponse({ response, expectedChallenge: challenge.challenge,
      expectedOrigin: this.config.origin, expectedRPID: this.config.rpID, requireUserVerification: true }); }
    catch { throw new AccessError(403, 'Verifica della passkey non riuscita. Richiedi una nuova verifica.'); }
    requireAccess(result.verified && result.registrationInfo, 'Passkey non verificata.');
    const credential = result.registrationInfo.credential;
    await this.store.transaction(async tx => {
      const owner = await tx.get('owners/main');
      requireAccess(owner?.uid === token.uid && owner.setupHash === challenge.setupHash && owner.setupExpiresAt > this.now()
        && (owner.credentials?.length ?? 0) < 2 && !(owner.credentials || []).some(c => c.id === credential.id));
      const credentials = [...(owner.credentials || []), { id: credential.id, publicKey: Buffer.from(credential.publicKey).toString('base64'),
        counter: credential.counter, transports: credential.transports || [], registeredAt: this.now() }];
      tx.set('owners/main', { ...owner, credentials, ...(credentials.length === 2 ? { setupHash: null, setupExpiresAt: 0 } : {}) });
      tx.set(`audit/${randomToken()}`, { action: 'OWNER_PASSKEY_REGISTERED', actor: token.uid, at: this.now(), credentialId: credential.id });
    });
    return { registered: true, message: 'Passkey registrata. Conserva un secondo autenticatore separato.' };
  };
  Authority.prototype.actionSnapshot = async function (action, target) {
    uid(target);
    if (action === 'APPROVE') {
      const invite = await this.store.get(`invitations/${target}`);
      requireAccess(invite && ['READY', 'GRANT_FAILED'].includes(invite.status) && invite.expiresAt > this.now() && invite.uid,
        'L’invito non è pronto per l’attivazione.');
      return { action, id: target, uid: invite.uid, email: invite.email, tenantId: invite.tenantId, role: invite.role, kind: invite.kind, totpFactorIds: invite.totpFactorIds };
    }
    requireAccess(['SUSPEND', 'RECOVER'].includes(action), 'Azione non disponibile.', 400);
    const member = await this.store.get(`members/${target}`);
    requireAccess(member, 'Account non trovato.', 404);
    return { action, id: target, uid: target, email: member.email, tenantId: member.tenantId, role: member.role };
  };
  Authority.prototype.approvalOptions = async function ({ idToken, action, target }) {
    const token = await this.owner(idToken);
    const owner = await this.store.get('owners/main');
    requireAccess(owner.credentials?.length >= 2, 'Registra due autenticatori prima di approvare gli accessi.');
    const snapshot = await this.actionSnapshot(action, target);
    const options = await this.webauthn.generateAuthenticationOptions({ rpID: this.config.rpID,
      allowCredentials: owner.credentials.map(c => ({ id: c.id, transports: c.transports })), userVerification: 'required' });
    const id = randomToken();
    await this.store.transaction(async tx => tx.set(`challenges/${id}`, { ownerUid: token.uid, purpose: 'ACTION',
      challenge: options.challenge, snapshot, digest: hash(JSON.stringify(snapshot)), expiresAt: this.now() + 120_000,
      deleteAt: new Date(this.now() + 3_600_000), used: false }));
    return { challengeId: id, options, details: snapshot };
  };
  Authority.prototype.completeAction = async function ({ idToken, challengeId, response }) {
    const token = await this.owner(idToken);
    const challenge = await this.takeChallenge(challengeId, token.uid, 'ACTION');
    const owner = await this.store.get('owners/main');
    const credential = owner.credentials?.find(c => c.id === response?.id);
    requireAccess(credential && owner.credentials.length >= 2, 'Autenticatore non autorizzato.');
    let verification;
    try { verification = await this.webauthn.verifyAuthenticationResponse({ response, expectedChallenge: challenge.challenge,
      expectedOrigin: this.config.origin, expectedRPID: this.config.rpID, requireUserVerification: true,
      credential: { ...credential, publicKey: Buffer.from(credential.publicKey, 'base64') } }); }
    catch { throw new AccessError(403, 'Verifica della passkey non riuscita. Richiedi una nuova verifica.'); }
    requireAccess(verification.verified, 'Verifica passkey non riuscita.');
    const snapshot = await this.actionSnapshot(challenge.snapshot.action, challenge.snapshot.id);
    requireAccess(hash(JSON.stringify(snapshot)) === challenge.digest, 'I dati sono cambiati. Richiedi una nuova approvazione.');
    await this.store.transaction(async tx => {
      const current = await tx.get('owners/main');
      const selected = current.credentials.find(c => c.id === credential.id);
      requireAccess(selected && selected.counter === credential.counter, 'Verifica concorrente. Riprova.');
      tx.set('owners/main', { ...current, credentials: current.credentials.map(c => c.id === credential.id
        ? { ...c, counter: verification.authenticationInfo.newCounter } : c) });
    });
    return this.withUserLock(snapshot.uid, async () => {
      if (snapshot.action === 'APPROVE') return this.grant(snapshot, token.uid);
      await this.store.transaction(async tx => {
        const member = await tx.get(`members/${snapshot.uid}`);
        requireAccess(member?.email === snapshot.email && member.tenantId === snapshot.tenantId);
        tx.set(`members/${snapshot.uid}`, { ...member, status: 'SUSPENDED', updatedAt: this.now() });
        tx.set(`audit/${randomToken()}`, { action: snapshot.action, actor: token.uid, targetUid: snapshot.uid, at: this.now() });
      });
      await this.auth.revokeRefreshTokens(snapshot.uid);
      await this.auth.setCustomUserClaims(snapshot.uid, { tenantId: snapshot.tenantId, mfaRequired: true });
      if (snapshot.action === 'RECOVER') {
        await this.auth.updateUser(snapshot.uid, { multiFactor: { enrolledFactors: [] } });
        return this.issueInvite({ email: snapshot.email, name: 'Recupero account Capo', tenantId: snapshot.tenantId,
          requestedBy: token.uid, kind: 'RECOVERY', targetUid: snapshot.uid });
      }
      return { suspended: true };
    });
  };
  Authority.prototype.grant = async function (snapshot, approver) {
    const user = await this.auth.getUser(snapshot.uid);
    requireAccess(!user.disabled && user.emailVerified && email(user.email) === snapshot.email
      && Array.isArray(snapshot.totpFactorIds) && snapshot.totpFactorIds.length > 0
      && snapshot.totpFactorIds.every(id => user.multiFactor?.enrolledFactors?.some(f => f.factorId === 'totp' && f.uid === id)), 'Email o MFA non più valide.');
    requireAccess(!user.customClaims?.tenantId || user.customClaims.tenantId === snapshot.tenantId);
    await this.store.transaction(async tx => {
      const invite = await tx.get(`invitations/${snapshot.id}`);
      requireAccess(invite && ['READY', 'GRANT_FAILED'].includes(invite.status) && invite.expiresAt > this.now()
        && invite.uid === snapshot.uid && invite.email === snapshot.email && invite.tenantId === snapshot.tenantId
        && invite.role === snapshot.role && invite.kind === snapshot.kind);
      const member = await tx.get(`members/${snapshot.uid}`);
      requireAccess(!member || snapshot.kind !== 'NEW' || invite.status === 'GRANT_FAILED'
        && member.invitationId === snapshot.id && member.status === 'SUSPENDED', 'Account già protetto: usa il percorso di recupero.');
      tx.set(`invitations/${snapshot.id}`, { ...invite, status: 'ACTIVATING', approvedBy: approver, approvedAt: this.now() });
      tx.set(`members/${snapshot.uid}`, { uid: snapshot.uid, email: snapshot.email, tenantId: snapshot.tenantId,
        role: 'CHIEF_EXECUTIVE', status: 'ACTIVATING', invitationId: snapshot.id, approvedBy: approver, totpFactorIds: snapshot.totpFactorIds });
    });
    try {
      await this.auth.setCustomUserClaims(snapshot.uid, { role: 'CHIEF_EXECUTIVE', tenantId: snapshot.tenantId, mfaRequired: true });
      await this.auth.revokeRefreshTokens(snapshot.uid);
      await this.store.transaction(async tx => {
        const invite = await tx.get(`invitations/${snapshot.id}`);
        const member = await tx.get(`members/${snapshot.uid}`);
        requireAccess(invite?.status === 'ACTIVATING' && member?.invitationId === snapshot.id);
        tx.set(`invitations/${snapshot.id}`, { ...invite, status: 'ACTIVE', activatedAt: this.now(), secretHash: null });
        tx.set(`members/${snapshot.uid}`, { ...member, status: 'ACTIVE', activatedAt: this.now() });
        tx.set(`audit/${randomToken()}`, { action: 'CHIEF_ACTIVATED', actor: approver, uid: snapshot.uid,
          email: snapshot.email, tenantId: snapshot.tenantId, invitationId: snapshot.id, at: this.now() });
      });
    } catch {
      await this.store.transaction(async tx => {
        const invite = await tx.get(`invitations/${snapshot.id}`);
        const member = await tx.get(`members/${snapshot.uid}`);
        tx.set(`invitations/${snapshot.id}`, { ...invite, status: 'GRANT_FAILED' });
        tx.set(`members/${snapshot.uid}`, { ...member, status: 'SUSPENDED' });
      });
      throw new Error('Attivazione incompleta: account sospeso, riprova dal canale esterno.');
    }
    try { await this.mail.send({ to: snapshot.email, subject: 'RadioTech: account Capo attivato',
      text: `Il tuo account è stato attivato dal proprietario. Accedi nuovamente con email, password e secondo fattore: ${this.config.origin}/` }); }
    catch { /* Activation is already durable; delivery never grants or rolls back privileges. */ }
    return { activated: true, uid: snapshot.uid };
  };
}
