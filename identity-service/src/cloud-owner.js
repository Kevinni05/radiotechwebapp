import { OAuth2Client } from 'google-auth-library';
import { requireAccess, email } from './security.js';

export function cloudOwner(config, client = new OAuth2Client()) {
  return async idToken => {
    requireAccess(typeof idToken === 'string' && idToken.length <= 16000, 'Identità Google del proprietario richiesta.', 401);
    let claims;
    try { claims = (await client.verifyIdToken({ idToken, audience: config.ownerGoogleAudience })).getPayload(); }
    catch { requireAccess(false, 'Identità Google del proprietario non valida o scaduta.', 401); }
    const age = Date.now() / 1000 - claims.iat;
    requireAccess(claims.email_verified === true && email(claims.email) === config.ownerEmail
      && claims.sub === config.ownerGoogleSubject && age >= -30 && age <= 3600,
      'Account Google del proprietario richiesto.');
    return { uid: `google-${claims.sub}`, email: claims.email, email_verified: true };
  };
}
