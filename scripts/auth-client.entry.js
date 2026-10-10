import {initializeApp} from 'firebase/app';
import {getFirestore,collection,query,where,onSnapshot} from 'firebase/firestore';
import {getAuth, signInWithPopup, GoogleAuthProvider, OAuthProvider, EmailAuthProvider, reauthenticateWithCredential, reauthenticateWithPopup, signInWithEmailAndPassword, getMultiFactorResolver, TotpMultiFactorGenerator, multiFactor, setPersistence, browserLocalPersistence, browserSessionPersistence, onAuthStateChanged, signOut} from 'firebase/auth';
import QRCode from 'qrcode';
import '../src/main/resources/static/assets/mfa-dialog.js';
let auth=null, initialization=null, federationEnabled=false;
async function initialize(){
 if(initialization)return initialization;
 if(auth)return true;
 if(!initialization)initialization=(async()=>{
  const r=await fetch('/api/v1/auth/public-config',{signal:AbortSignal.timeout(15000)});
  if(!r.ok)throw new Error('Configurazione accesso non disponibile.');
  const c=await r.json();if(!c.enabled)return false;
  federationEnabled=c.federatedLoginEnabled===true;
  auth=getAuth(initializeApp({apiKey:c.apiKey,authDomain:c.authDomain,projectId:c.projectId}));
  try{
   await auth.authStateReady();
   let uid=auth.currentUser?.uid;
   onAuthStateChanged(auth,user=>{
    const next=user?.uid;
    if(uid&&uid!==next)window.dispatchEvent(new Event('radiotech:auth-ended'));
    uid=next;
   });
  }catch(e){auth=null;throw e;}return true;
 })().finally(()=>{initialization=null;});
 return initialization;
}
function safeAuthError(error){
 const messages={
  'auth/invalid-verification-code':'Codice non valido. Usa il codice più recente e riprova.',
  'auth/invalid-credential':'Credenziali non valide. Riprova.',
  'auth/wrong-password':'Password non valida. Riprova.',
  'auth/too-many-requests':'Troppi tentativi. Attendi prima di riprovare.',
  'auth/requires-recent-login':'La verifica è scaduta. Accedi nuovamente e riprova.',
  'auth/network-request-failed':'Connessione assente. Verifica la rete e riprova.',
  'auth/invalid-multi-factor-session':'La verifica è scaduta. Ripeti l’accesso.',
  'auth/multi-factor-enrollment-not-found':'Secondo fattore non disponibile. Contatta il responsabile.'
 };
 return new Error(messages[error?.code]||'Verifica non riuscita. Riprova o contatta il responsabile.');
}
async function challenge(error){
 if(error.code!=='auth/multi-factor-auth-required')throw safeAuthError(error);
 const resolver=getMultiFactorResolver(auth,error);
 const hint=resolver.hints.find(h=>h.factorId===TotpMultiFactorGenerator.FACTOR_ID);
 if(!hint)throw new Error('Secondo fattore non supportato: contatta il responsabile.');
 return window.RadioTechMfaDialog({title:'Verifica in due passaggi',description:'Apri la tua app di autenticazione e inserisci il codice corrente.',submit:async code=>{
  try{return await resolver.resolveSignIn(TotpMultiFactorGenerator.assertionForSignIn(hint.uid,code));}
  catch(e){throw safeAuthError(e);}
 }});
}
async function exchange(user){
 const token=await user.getIdToken();
 const response=await fetch('/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idToken:token}),signal:AbortSignal.timeout(20000)});
 const data=await response.json();
 if(auth.currentUser!==user)throw new DOMException('Sessione cambiata.','AbortError');
 if(!response.ok){await signOut(auth);throw new Error(data.message||'Account aziendale non autorizzato.');}
 // Backend login can assign claims to a newly linked account. Use the refreshed identity.
 const identity=await user.getIdTokenResult(true);
 if(auth.currentUser!==user)throw new DOMException('Sessione cambiata.','AbortError');
 const {refreshToken:discarded,token:discardedToken,...profile}=data;
 return{...profile,token:identity.token,user:{...data.user,uid:user.uid,tenantId:identity.claims.tenantId,role:data.role}};
}
async function restoreSession(){
 if(!await initialize()||!auth.currentUser)return null;
 return exchange(auth.currentUser);
}
async function refreshSession(){
 if(!auth?.currentUser)return null;
 await auth.currentUser.getIdToken(true);
 return exchange(auth.currentUser);
}
async function login(provider,email,password,remember=false){
 if(!auth&&!await initialize())throw new Error('Accesso federato non configurato dal responsabile.');
 if(provider&&!federationEnabled)throw new Error('Accesso federato non configurato dal responsabile.');
 await setPersistence(auth,remember?browserLocalPersistence:browserSessionPersistence);
 let credential;
 try{credential=provider?await signInWithPopup(auth,provider==='google'?new GoogleAuthProvider():new OAuthProvider('microsoft.com')):await signInWithEmailAndPassword(auth,email,password);}
 catch(e){credential=await challenge(e);}
 return exchange(credential.user);
}
async function reauthenticate(user){
 const providers=user.providerData.map(item=>item.providerId);
 if(providers.includes('password')){
  const result=await window.RadioTechMfaDialog({title:'Conferma la tua identità',description:'Per configurare il secondo fattore conferma la password del tuo account.',label:'Password',password:true,submit:async password=>{
   try{return await reauthenticateWithCredential(user,EmailAuthProvider.credential(user.email,password));}
   catch(e){if(e.code==='auth/multi-factor-auth-required')return{mfaError:e};throw safeAuthError(e);}
  }});
  if(result.mfaError)return challenge(result.mfaError);
  return result;
 }
 const provider=providers.includes('google.com')?new GoogleAuthProvider():providers.includes('microsoft.com')?new OAuthProvider('microsoft.com'):null;
 if(!provider)throw new Error('Metodo di verifica non supportato. Accedi nuovamente o contatta il responsabile.');
 try{return await reauthenticateWithPopup(user,provider);}catch(e){return challenge(e);}
}
async function enrollTotp(){
 const user=auth?.currentUser;
 if(!user)throw new Error('Accedi con email o SSO prima di configurare MFA.');
 await reauthenticate(user);
 if(auth.currentUser?.uid!==user.uid)throw new Error('Account cambiato. Ripeti l’accesso.');
 let secret;
 try{secret=await TotpMultiFactorGenerator.generateSecret(await multiFactor(user).getSession());}
 catch(e){throw safeAuthError(e);}
 const uri=secret.generateQrCodeUrl(user.email||'RadioTech','RadioTech');
 await window.RadioTechMfaDialog({title:'Configura l’app di autenticazione',description:'Scansiona il QR con la tua app di autenticazione, poi inserisci il codice. Per il recupero contatta il responsabile aziendale.',setup:async container=>{
  const canvas=document.createElement('canvas');canvas.setAttribute('role','img');canvas.setAttribute('aria-label','QR per configurare il secondo fattore');
  await QRCode.toCanvas(canvas,uri,{width:256,margin:4,errorCorrectionLevel:'M'});
  if(!container.isConnected)return;
  const label=document.createElement('p');label.textContent='Se non puoi scansionare, inserisci manualmente questa chiave nella tua app:';
  const key=document.createElement('code');key.className='mfa-key';key.textContent=secret.secretKey;
  container.append(canvas,label,key);
 },submit:async code=>{
  if(auth.currentUser?.uid!==user.uid)throw new Error('Account cambiato. Ripeti l’accesso.');
  try{await multiFactor(user).enroll(TotpMultiFactorGenerator.assertionForEnrollment(secret,code),'RadioTech TOTP');}
  catch(e){throw safeAuthError(e);}
  return true;
 }});
 await user.getIdToken(true);
 return true;
}
async function watchInventory(tenant,changed,failed){
 if(!auth?.currentUser||!tenant)return null;
 const identity=await auth.currentUser.getIdTokenResult(true);
 if(identity.claims.tenantId!==tenant)return null;
 return onSnapshot(query(collection(getFirestore(auth.app),'inventory'),where('tenantId','==',tenant)),()=>changed(),()=>failed());
}
window.RadioTechFederation={initialize,login,restoreSession,refreshSession,enrollTotp,watchInventory,logout:async()=>{if(auth)await signOut(auth);}};
document.addEventListener('DOMContentLoaded',async()=>{
 try{
  if(!await initialize()||!federationEnabled)return;const form=document.getElementById('loginForm');if(!form)return;
  for(const[provider,label]of[['google','Accedi con Google'],['microsoft','Accedi con Microsoft']]){
   const button=document.createElement('button');button.type='button';button.className='btn';button.textContent=label;
   button.onclick=async()=>{
    button.disabled=true;
    try{const result=await login(provider,null,null,document.getElementById('rememberLogin')?.checked===true);window.dispatchEvent(new CustomEvent('radiotech:sso',{detail:result}));}
    catch(e){document.getElementById('loginStatus').textContent=e.name==='AbortError'?'Accesso annullato.':e.message;}
    finally{button.disabled=false;}
   };form.append(button);
  }
 }catch{/* Password/QR stay usable when federation is unavailable. */}
});
