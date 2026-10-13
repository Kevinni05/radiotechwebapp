import { initializeApp } from 'firebase/app';
import { getAuth, setPersistence, inMemoryPersistence, connectAuthEmulator, signInWithEmailAndPassword,
  createUserWithEmailAndPassword, signInWithPopup, GoogleAuthProvider, getMultiFactorResolver,
  TotpMultiFactorGenerator, multiFactor, sendEmailVerification, sendPasswordResetEmail, reload,
  updatePassword, signOut } from 'firebase/auth';
import { startRegistration, startAuthentication } from '@simplewebauthn/browser';

const $ = id => document.getElementById(id);
let auth, config, invitation, invitationValue, totpSecret, resolver, ownerView = false, ownerCloudToken = null;
const intent = new URLSearchParams(location.search).get('intent');
const requestId = new URLSearchParams(location.search).get('request');
const fragment = new URLSearchParams(location.hash.slice(1));
// Remove invitation and owner proofs before any network requests or asynchronous initialization.
history.replaceState(null, '', location.pathname + location.search);
const status = message => { $('status').textContent = message; };
const errors = {
  'auth/invalid-credential': 'Email o password non corretta.',
  'auth/email-already-in-use': 'Questo indirizzo possiede già un account. Accedi con la sua password.',
  'auth/invalid-verification-code': 'Codice non corretto. Inserisci quello attuale dell’app Authenticator.',
  'auth/too-many-requests': 'Troppi tentativi. Riprova più tardi.',
  'auth/requires-recent-login': 'Accedi nuovamente con password e secondo fattore, poi ripeti l’operazione.',
  'auth/user-token-expired': 'La sessione è stata revocata. Accedi nuovamente con password e secondo fattore.',
  'auth/operation-not-allowed': 'Il servizio di autenticazione deve essere configurato dal proprietario.',
  'auth/unverified-email': 'Verifica l’indirizzo email prima di configurare il secondo fattore.',
  'auth/weak-password': 'La password non soddisfa i requisiti del servizio.',
  'auth/popup-closed-by-user': 'Accesso annullato.',
  'auth/unauthorized-domain': 'Il dominio del servizio accessi deve essere autorizzato in Firebase.'
};
function explain(error) { return errors[error.code] || (error.code ? 'Operazione non completata. Verifica i dati e riprova.' : error.message); }
async function busy(button, work) {
  if (button.disabled) return;
  button.disabled = true;
  try { await work(); } catch (error) { status(explain(error)); }
  finally { button.disabled = false; }
}
async function post(path, data = {}) {
  const response = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data), cache: 'no-store', signal: AbortSignal.timeout(30_000) });
  const result = await response.json();
  if (!response.ok) throw new Error(result.message || 'Operazione non completata.');
  return result;
}
const token = () => ownerView && ownerCloudToken ? Promise.resolve(ownerCloudToken) : auth.currentUser.getIdToken(true);

async function signedIn() {
  $('password').value = '';
  $('challengeCode').value = '';
  $('loginPanel').hidden = true;
  $('challengePanel').hidden = true;
  await reload(auth.currentUser);
  if (ownerView) return showOwner();
  if (!auth.currentUser.emailVerified) {
    $('verificationPanel').hidden = false;
    status('Verifica la tua casella email per proseguire.');
    return;
  }
  $('verificationPanel').hidden = true;
  if (!multiFactor(auth.currentUser).enrolledFactors.some(f => f.factorId === 'totp')) {
    if (!invitation) { status('Per configurare o recuperare la MFA serve un invito del proprietario.'); return; }
    const session = await multiFactor(auth.currentUser).getSession();
    totpSecret = await TotpMultiFactorGenerator.generateSecret(session);
    $('totpSecret').textContent = totpSecret.secretKey;
    $('totpPanel').hidden = false;
    status('Configura il secondo fattore prima di completare l’invito.');
    return;
  }
  $('totpPanel').hidden = true;
  $('totpSecret').textContent = '';
  totpSecret = null;
  $('accountPanel').hidden = false;
  $('acceptInvitation').hidden = !invitation;
  if (intent === 'password') {
    $('passwordForm').hidden = false;
    $('openApplication').hidden = true;
  }
  status(invitation ? 'Conferma l’invito per richiedere l’approvazione del proprietario.' : 'Identità verificata. Puoi proseguire.');
  if (intent === 'login' && !invitation) await openApplication();
}
async function handleLogin(work) {
  try { await work(); await signedIn(); }
  catch (error) {
    if (error.code !== 'auth/multi-factor-auth-required') throw error;
    resolver = getMultiFactorResolver(auth, error);
    if (!resolver.hints.some(h => h.factorId === 'totp')) throw new Error('Secondo fattore non supportato. Contatta il proprietario.');
    $('loginPanel').hidden = true;
    $('challengePanel').hidden = false;
    $('challengeCode').focus();
    status('Inserisci il codice della tua app Authenticator.');
  }
}
async function openApplication() {
  const idToken = await token();
  await post('/api/session', { idToken });
  if (window.opener && /^[A-Za-z0-9_-]{32,64}$/.test(requestId || '')) {
    window.opener.postMessage({ type: 'radiotech:identity-session', idToken, requestId }, config.appOrigin);
    await signOut(auth);
    status('Accesso completato. Torna alla finestra del Centro operativo.');
    window.close();
  } else {
    status('Apri il Centro operativo e seleziona “Accesso protetto del Capo”.');
  }
}
async function showOwner() {
  $('ownerUid').textContent = `Proprietario: ${config.ownerEmail}`;
  const data = await post('/api/owner/list', { idToken: await token() });
  $('ownerLogin').hidden = true;
  $('refreshOwner').hidden = false;
  $('setupForm').hidden = data.credentials >= 2;
  if (data.credentials >= 2) $('setupCode').value = '';
  $('ownerRequests').replaceChildren();
  $('ownerMembers').replaceChildren();
  for (const item of data.invitations) {
    const article = document.createElement('article');
    const heading = document.createElement('strong'); heading.textContent = `${item.name} · ${item.email}`;
    const details = document.createElement('p'); details.textContent = `Stato: ${item.status} · Azienda: ${item.tenantId} · Ruolo: ${item.role} · UID: ${item.uid || 'in attesa'}`;
    article.append(heading, details);
    if (['READY', 'GRANT_FAILED'].includes(item.status)) {
      const button = document.createElement('button'); button.textContent = 'Approva con passkey';
      button.onclick = () => busy(button, () => performAction('APPROVE', item.id));
      article.append(button);
    }
    $('ownerRequests').append(article);
  }
  for (const member of data.members) {
    const article = document.createElement('article');
    const label = document.createElement('p'); label.textContent = `${member.email} · ${member.status} · ${member.tenantId}`;
    article.append(label);
    for (const [action, text] of [['SUSPEND', 'Sospendi accesso'], ['RECOVER', 'Avvia recupero MFA']]) {
      const button = document.createElement('button'); button.className = 'secondary'; button.textContent = text;
      button.onclick = () => busy(button, () => performAction(action, member.uid)); article.append(button);
    }
    $('ownerMembers').append(article);
  }
  status(data.credentials >= 2 ? 'Approva solo le identità che riconosci. Verifica email, UID, azienda e ruolo.' : 'Registra due autenticatori per proteggere le approvazioni e il recupero.');
}
async function performAction(action, target) {
  const options = await post('/api/owner/action/options', { idToken: await token(), action, target });
  const details = options.details;
  if (!confirm(`Confermi ${action === 'APPROVE' ? 'l’attivazione' : action === 'RECOVER' ? 'il recupero della MFA' : 'la sospensione'}?\nEmail: ${details.email}\nUID: ${details.uid}\nAzienda: ${details.tenantId}\nRuolo: ${details.role}`)) return;
  const response = await startAuthentication({ optionsJSON: options.options });
  await post('/api/owner/action/complete', { idToken: await token(), challengeId: options.challengeId, response });
  await showOwner();
}

$('loginForm').onsubmit = event => { event.preventDefault(); busy(event.submitter, () => {
  ownerView = false;
  return handleLogin(() => signInWithEmailAndPassword(auth, $('email').value.trim(), $('password').value));
}); };
$('createAccount').onclick = event => busy(event.target, async () => {
  if (!invitation || $('email').value.trim().toLowerCase() !== invitation.email) throw new Error('Usa l’indirizzo indicato nell’invito.');
  if ($('password').value.length < 12) throw new Error('Scegli una password unica di almeno 12 caratteri.');
  await createUserWithEmailAndPassword(auth, invitation.email, $('password').value);
  await sendEmailVerification(auth.currentUser);
  await signedIn();
});
$('challengeForm').onsubmit = event => { event.preventDefault(); busy(event.submitter, async () => {
  const hint = resolver.hints.find(h => h.factorId === 'totp');
  await resolver.resolveSignIn(TotpMultiFactorGenerator.assertionForSignIn(hint.uid, $('challengeCode').value));
  resolver = null; await signedIn();
}); };
$('sendVerification').onclick = event => busy(event.target, async () => {
  await sendEmailVerification(auth.currentUser); status('Email inviata. Controlla anche la cartella spam.');
});
$('checkVerification').onclick = event => busy(event.target, signedIn);
$('enrollForm').onsubmit = event => { event.preventDefault(); busy(event.submitter, async () => {
  await multiFactor(auth.currentUser).enroll(TotpMultiFactorGenerator.assertionForEnrollment(totpSecret, $('enrollCode').value), 'RadioTech Authenticator');
  $('totpSecret').textContent = ''; $('enrollCode').value = ''; totpSecret = null;
  await signOut(auth); $('totpPanel').hidden = true; $('loginPanel').hidden = false;
  status('Secondo fattore configurato. Accedi nuovamente con password e codice per completare la verifica.');
}); };
$('acceptInvitation').onclick = event => busy(event.target, async () => {
  const result = await post('/api/accept', { invitation: invitationValue, idToken: await token() });
  status(result.message); $('acceptInvitation').hidden = true;
});
$('openApplication').onclick = event => busy(event.target, openApplication);
$('changePassword').onclick = () => { $('passwordForm').hidden = false; };
$('passwordForm').onsubmit = event => { event.preventDefault(); busy(event.submitter, async () => {
  if ($('newPassword').value !== $('confirmPassword').value) throw new Error('Le password non coincidono.');
  if ($('newPassword').value.length < 12) throw new Error('Scegli una password di almeno 12 caratteri.');
  const identity = await auth.currentUser.getIdTokenResult();
  if (!identity.claims.firebase?.sign_in_second_factor || Date.now() / 1000 - Number(identity.claims.auth_time) > 300) throw new Error('Accedi nuovamente con password e secondo fattore.');
  await updatePassword(auth.currentUser, $('newPassword').value);
  $('passwordForm').reset(); await signOut(auth);
  $('accountPanel').hidden = true; $('loginPanel').hidden = false;
  status('Password aggiornata. Accedi nuovamente con password e secondo fattore.');
}); };
$('resetPassword').onclick = event => busy(event.target, async () => {
  await sendPasswordResetEmail(auth, $('email').value.trim());
  status('Se l’indirizzo possiede un account, riceverà le istruzioni per recuperare la password. La MFA resta richiesta.');
});
$('ownerLogin').onclick = event => busy(event.target, async () => {
  if (config.ownerProof === 'google-cloud') {
    status('Avvia “open-chief-approvals.ps1” sul PC del proprietario: l’accesso Google viene verificato separatamente e ogni approvazione richiede la passkey.');
    return;
  }
  ownerView = true;
  const provider = new GoogleAuthProvider(); provider.setCustomParameters({ prompt: 'select_account' });
  await handleLogin(() => signInWithPopup(auth, provider));
});
$('refreshOwner').onclick = event => busy(event.target, showOwner);
$('setupForm').onsubmit = event => { event.preventDefault(); busy(event.submitter, async () => {
  const options = await post('/api/owner/register/options', { idToken: await token(), setupCode: $('setupCode').value });
  const response = await startRegistration({ optionsJSON: options.options });
  await post('/api/owner/register/complete', { idToken: await token(), challengeId: options.challengeId, response });
  await showOwner();
}); };
$('logout').onclick = async () => { await signOut(auth); location.reload(); };

try {
  config = await (await fetch('/config', { cache: 'no-store' })).json();
  auth = getAuth(initializeApp({ apiKey: config.apiKey, projectId: config.projectId, authDomain: config.authDomain }));
  if (config.demo) connectAuthEmulator(auth, 'http://localhost:9099');
  await setPersistence(auth, inMemoryPersistence);
  auth.languageCode = 'it';
  invitationValue = fragment.get('invite');
  ownerCloudToken = config.ownerProof === 'google-cloud' ? fragment.get('owner') : null;
  if (ownerCloudToken && fragment.get('setup')) $('setupCode').value = fragment.get('setup');
  if (invitationValue) {
    invitation = await post('/api/invitation', { invitation: invitationValue });
    $('invitationPanel').hidden = false; $('createAccount').hidden = invitation.accountPrepared;
    $('invitationDetails').textContent = `${invitation.name} · ${invitation.email}`;
    $('email').value = invitation.email; $('email').readOnly = true;
  }
  if (ownerCloudToken) { ownerView = true; $('loginPanel').hidden = true; await showOwner(); }
  else status(invitation ? 'Accedi o crea il tuo account personale per accettare l’invito.' : 'Accedi con email, password e secondo fattore.');
} catch (error) { status(explain(error)); }
