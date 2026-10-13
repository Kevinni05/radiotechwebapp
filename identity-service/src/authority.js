import { AccessError, requireAccess, randomToken, hash, email, uid, freshMfa, isHigh, operatorClaims } from './security.js';

export class IdentityAuthority {
  constructor({ auth, store, mail, config, webauthn, ownerProofVerifier, now = () => Date.now() }) {
    Object.assign(this, { auth, store, mail, config, webauthn, ownerProofVerifier, now });
  }
  async verify(idToken) {
    requireAccess(typeof idToken === 'string' && idToken.length <= 16_000, 'Accesso richiesto.', 401);
    try { return await this.auth.verifyIdToken(idToken, true); }
    catch { throw new AccessError(401, 'Sessione scaduta. Accedi nuovamente.'); }
  }
  async owner(idToken) {
    if (this.config.ownerProof === 'google-cloud') {
      requireAccess(this.ownerProofVerifier, 'Accesso proprietario non configurato.', 503);
      const token = await this.ownerProofVerifier(idToken);
      const owner = await this.store.get('owners/main');
      requireAccess(owner?.uid === token.uid, 'Proprietario non configurato.');
      return token;
    }
    const token = await this.verify(idToken);
    const age = this.now() / 1000 - token.auth_time;
    requireAccess(token.email_verified === true && email(token.email) === this.config.ownerEmail
      && token.firebase?.sign_in_provider === 'google.com' && age >= -30 && age <= 300,
      'Accedi con l’account Google del proprietario.');
    const owner = await this.store.get('owners/main');
    requireAccess(owner?.uid === token.uid, 'Proprietario non configurato.');
    return token;
  }
  async membership(token, { fresh = false } = {}) {
    const member = await this.store.get(`members/${uid(token.uid)}`);
    const current = await this.auth.getUser(token.uid);
    requireAccess(member?.status === 'ACTIVE' && member.tenantId === token.tenantId && isHigh(token)
      && token.role === member.role && token.email_verified === true && email(token.email) === member.email
      && current.emailVerified && !current.disabled && email(current.email) === member.email
      && current.multiFactor?.enrolledFactors?.some(f => f.factorId === 'totp')
      && member.totpFactorIds?.includes(token.firebase?.second_factor_identifier)
      && current.multiFactor.enrolledFactors.some(f => f.factorId === 'totp' && f.uid === token.firebase?.second_factor_identifier)
      && token.firebase?.sign_in_second_factor === 'totp'
      && token.firebase?.sign_in_provider !== 'custom'
      && Number.isFinite(token.auth_time) && token.auth_time >= Math.floor(member.activatedAt / 1000)
      && token.auth_time >= this.now() / 1000 - 28_800
      && !token.radioDeviceId && !token.radioMfaVerified, 'Account privilegiato non autorizzato.');
    if (fresh) freshMfa(token, this.now());
    return member;
  }
  async status(idToken) {
    const token = await this.verify(idToken);
    const member = await this.membership(token);
    return { uid: token.uid, role: member.role, tenantId: member.tenantId, status: member.status, mfaRequired: true };
  }
  async invite({ idToken, email: address, name }) {
    const token = await this.verify(idToken);
    const member = await this.membership(token, { fresh: true });
    return this.issueInvite({ email: address, name, tenantId: member.tenantId, requestedBy: token.uid, kind: 'NEW' });
  }
  async issueInvite({ email: address, name, tenantId, requestedBy, kind, targetUid = null }) {
    address = email(address);
    requireAccess(typeof name === 'string' && name.trim().length >= 2 && name.length <= 100, 'Inserisci il nome del destinatario.', 400);
    const id = randomToken();
    const secret = randomToken();
    const now = this.now();
    await this.store.transaction(async tx => {
      const limiterKey = `inviteLimits/${hash(`${tenantId}:${address}`)}`;
      const limiter = await tx.get(limiterKey);
      requireAccess(!limiter || now - limiter.at >= 60_000, 'Invito già richiesto. Attendi un minuto.', 429);
      const countKey = `inviteBudgets/${hash(`${tenantId}:${Math.floor(now / 3_600_000)}`)}`;
      const budget = await tx.get(countKey);
      requireAccess((budget?.count ?? 0) < 10, 'Troppi inviti. Riprova più tardi.', 429);
      tx.set(limiterKey, { at: now });
      tx.set(countKey, { count: (budget?.count ?? 0) + 1 });
      tx.set(`invitations/${id}`, { email: address, name: name.trim(), tenantId, requestedBy, kind, targetUid,
        secretHash: hash(secret), status: 'PENDING', createdAt: now, expiresAt: now + 86_400_000, role: 'CHIEF_EXECUTIVE' });
      if (targetUid) tx.set(`protected/${targetUid}`, { invitationId: id, since: now });
      tx.set(`audit/${randomToken()}`, { action: 'INVITE_REQUESTED', actor: requestedBy, invitationId: id, at: now, tenantId, email: address });
    });
    try {
      if (this.config.mailProvider === 'firebase') {
        let account;
        try { account = await this.auth.getUserByEmail(address); }
        catch (error) {
          if (error.code !== 'auth/user-not-found') throw error;
          account = await this.auth.createUser({ email: address, displayName: name.trim(), password: randomToken() + 'Aa1!', emailVerified: false });
        }
        requireAccess(!targetUid || targetUid === account.uid, 'L’indirizzo appartiene a un altro account.');
        requireAccess(kind !== 'NEW' || !isHigh(account.customClaims), 'Account già privilegiato: usa il percorso di recupero.');
        requireAccess(!account.disabled, 'Account sospeso: usa il recupero esterno.');
        targetUid = account.uid;
        await this.withUserLock(targetUid, () => this.store.transaction(async tx => {
          const invitation = await tx.get(`invitations/${id}`);
          requireAccess(invitation?.status === 'PENDING');
          tx.set(`invitations/${id}`, { ...invitation, targetUid });
          tx.set(`protected/${targetUid}`, { invitationId: id, since: now });
        }));
      }
      await this.mail.send({ to: address, subject: 'RadioTech: invito a un account Capo',
        invitationUrl: `${this.config.origin}/#invite=${id}.${secret}`,
        text: `È stato richiesto un account personale Capo per ${name.trim()}.\nVerifica l’indirizzo, configura il secondo fattore e attendi l’approvazione del proprietario.\nIl link scade tra 24 ore:\n${this.config.origin}/#invite=${id}.${secret}\nSe non attendevi questo invito, ignoralo.` });
    } catch {
      await this.store.transaction(async tx => { const invite = await tx.get(`invitations/${id}`); tx.set(`invitations/${id}`, { ...invite, status: 'DELIVERY_FAILED' }); });
      throw new AccessError(503, 'Invito non recapitato. Nessun account è stato attivato.');
    }
    return { id, status: 'PENDING', email: address, expiresAt: now + 86_400_000 };
  }
  invitationSecret(value) {
    requireAccess(typeof value === 'string' && /^[A-Za-z0-9_-]{43}\.[A-Za-z0-9_-]{43}$/.test(value), 'Invito non valido.', 400);
    return value.split('.');
  }
  validInvite(invite, secret) {
    requireAccess(invite && invite.expiresAt > this.now() && invite.secretHash === hash(secret)
      && ['PENDING', 'READY'].includes(invite.status), 'Invito scaduto, revocato o già utilizzato.');
  }
  async inspect(value) {
    const [id, secret] = this.invitationSecret(value);
    const invite = await this.store.get(`invitations/${id}`);
    this.validInvite(invite, secret);
    return { id, email: invite.email, name: invite.name, status: invite.status, kind: invite.kind, expiresAt: invite.expiresAt,
      accountPrepared: this.config.mailProvider === 'firebase' };
  }
  async accept({ invitation, idToken }) {
    const token = await this.verify(idToken);
    freshMfa(token, this.now());
    return this.withUserLock(token.uid, () => this.acceptLocked(invitation, token));
  }
  async acceptLocked(invitation, token) {
    const [id, secret] = this.invitationSecret(invitation);
    const user = await this.auth.getUser(token.uid);
    requireAccess(!user.disabled && user.emailVerified && user.multiFactor?.enrolledFactors?.some(f => f.factorId === 'totp'),
      'Verifica l’email e configura il secondo fattore.');
    await this.store.transaction(async tx => {
      const invite = await tx.get(`invitations/${id}`);
      this.validInvite(invite, secret);
      const member = await tx.get(`members/${uid(token.uid)}`);
      requireAccess(email(user.email) === invite.email && email(token.email) === invite.email
        && (!invite.targetUid || invite.targetUid === token.uid) && (!invite.uid || invite.uid === token.uid)
        && (!token.tenantId || token.tenantId === invite.tenantId)
        && (!member || member.tenantId === invite.tenantId)
        && (!isHigh(token) || invite.kind !== 'NEW'), 'L’identità non corrisponde al destinatario dell’invito.');
      const factors = user.multiFactor.enrolledFactors.filter(f => f.factorId === 'totp').map(f => f.uid);
      requireAccess(factors.includes(token.firebase?.second_factor_identifier), 'Il fattore usato non corrisponde a quello registrato.');
      tx.set(`invitations/${id}`, { ...invite, uid: token.uid, status: 'READY', acceptedAt: this.now(), totpFactorIds: factors });
      tx.set(`protected/${token.uid}`, { invitationId: id, since: this.now() });
      tx.set(`audit/${randomToken()}`, { action: 'INVITE_ACCEPTED', actor: token.uid, invitationId: id, at: this.now(), tenantId: invite.tenantId });
    });
    return { status: 'READY', message: 'Email e secondo fattore verificati. Attendi l’approvazione del proprietario.' };
  }
  async list(idToken, ownerView = false) {
    const token = ownerView ? await this.owner(idToken) : await this.verify(idToken);
    const member = ownerView ? null : await this.membership(token);
    return (await this.store.list('invitations')).filter(i => !member || i.tenantId === member.tenantId)
      .sort((a, b) => b.createdAt - a.createdAt).slice(0, 100)
      .map(({ secretHash, ...item }) => ({ ...item, status: ['PENDING', 'READY'].includes(item.status) && item.expiresAt <= this.now() ? 'EXPIRED' : item.status }));
  }
  async cancel(idToken, id) {
    const token = await this.verify(idToken);
    const member = await this.membership(token, { fresh: true });
    uid(id);
    await this.store.transaction(async tx => {
      const invite = await tx.get(`invitations/${id}`);
      requireAccess(invite?.tenantId === member.tenantId && ['PENDING', 'READY'].includes(invite.status));
      tx.set(`invitations/${id}`, { ...invite, status: 'REVOKED', revokedAt: this.now() });
    });
    return { revoked: true };
  }
  async protectedUid(targetUid) {
    uid(targetUid);
    const member = await this.store.get(`members/${targetUid}`);
    const user = await this.auth.getUser(targetUid);
    const owner = await this.store.get('owners/main');
    const pending = await this.store.get(`protected/${targetUid}`);
    requireAccess(!member && !pending && !user.multiFactor?.enrolledFactors?.length && owner?.uid !== targetUid && !isHigh(user.customClaims)
      && (this.config.ownerProof === 'google-cloud' || email(user.email) !== this.config.ownerEmail), 'Account protetto: operazione amministrativa esterna richiesta.');
    return user;
  }
  async operator(operation, input) {
    if (operation === 'create') {
      const address = email(input.email);
      requireAccess(this.config.ownerProof === 'google-cloud' || address !== this.config.ownerEmail, 'Indirizzo del proprietario riservato.');
      const user = await this.auth.createUser({ email: address, ...(input.password ? { password: input.password } : {}),
        displayName: typeof input.displayName === 'string' ? input.displayName.slice(0, 100) : '', emailVerified: false });
      return { uid: user.uid };
    }
    return this.withUserLock(input.uid, async () => this.operatorLocked(operation, input));
  }
  async withUserLock(targetUid, work) {
    uid(targetUid);
    const lease = randomToken();
    const key = `userLocks/${targetUid}`;
    await this.store.transaction(async tx => {
      const existing = await tx.get(key);
      requireAccess(!existing?.lease, 'Operazione già in corso su questa identità.', 409);
      tx.set(key, { lease, startedAt: this.now() });
    });
    try { return await work(); }
    finally {
      await this.store.transaction(async tx => {
        const current = await tx.get(key);
        if (current?.lease === lease) tx.set(key, { lease: null, finishedAt: this.now() });
      });
    }
  }
  async operatorLocked(operation, input) {
    const user = await this.protectedUid(input.uid);
    if (operation === 'update') {
      requireAccess(typeof input.password === 'string' && input.password.length >= 8, 'Password non valida.', 400);
      await this.auth.updateUser(user.uid, { password: input.password,
        ...(typeof input.displayName === 'string' ? { displayName: input.displayName.slice(0, 100) } : {}) });
    } else if (operation === 'claims') {
      const claims = operatorClaims(input.claims);
      requireAccess(!user.customClaims?.tenantId || claims.tenantId === user.customClaims.tenantId);
      await this.auth.setCustomUserClaims(user.uid, claims);
      await this.auth.revokeRefreshTokens(user.uid);
    } else if (operation === 'clear') {
      requireAccess(['OPERATOR', 'VIEWER'].includes(user.customClaims?.role));
      await this.auth.setCustomUserClaims(user.uid, {});
      await this.auth.revokeRefreshTokens(user.uid);
    } else if (operation === 'delete') {
      await this.auth.revokeRefreshTokens(user.uid);
      await this.auth.deleteUser(user.uid);
    } else if (operation === 'revoke') {
      await this.auth.revokeRefreshTokens(user.uid);
    } else if (operation === 'token') {
      requireAccess(['OPERATOR', 'VIEWER'].includes(user.customClaims?.role)
        && !user.customClaims?.mfaRequired && !user.multiFactor?.enrolledFactors?.length && !user.disabled,
        'Questo account richiede un accesso interattivo.');
      return { customToken: await this.auth.createCustomToken(user.uid) };
    } else throw new AccessError(404, 'Operazione non disponibile.');
    return { uid: user.uid, success: true };
  }
}
