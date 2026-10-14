import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {readAuthResponse} from './auth-response.mjs';

const source=(await readFile(new URL('./auth-client.entry.js',import.meta.url),'utf8')).replace(/^import .*;\r?$/gm,'');
function harness({initialUser=true,ready=Promise.resolve(),backend}={}) {
 const events=[], persistence=[], listeners=[];
 const user={uid:'operator-a',email:'a@example.test',getIdToken:async()=> 'sdk-id-token',
  getIdTokenResult:async()=>({token:'refreshed-sdk-id-token',claims:{tenantId:'tenant-a',role:'ADMIN'}})};
 const auth={currentUser:initialUser?user:null,authStateReady:()=>ready};
 const context={AbortSignal,DOMException,Event,QRCode:{},readAuthResponse,
  document:{addEventListener:()=>{}},
  window:{dispatchEvent:event=>events.push(event.type)},
  initializeApp:()=>({}),getAuth:()=>auth,
  browserLocalPersistence:'SDK_LOCAL',browserSessionPersistence:'SDK_SESSION',
  setPersistence:async(_,kind)=>persistence.push(kind),
  onAuthStateChanged:(_,listener)=>listeners.push(listener),
  signInWithEmailAndPassword:async()=>{auth.currentUser=user;return{user};},
  signOut:async()=>{auth.currentUser=null;listeners.forEach(listener=>listener(null));},
  fetch:async(path,options)=>path.endsWith('public-config')?Response.json({enabled:true,federatedLoginEnabled:false,apiKey:'public-test-key',projectId:'demo-test'}):
   backend?backend(path,options):Response.json({role:'ADMIN',user:{name:'Operator A'},token:'old-backend-token',refreshToken:'discard-me'})
 };
 vm.runInNewContext(source,context);
 return {api:context.window.RadioTechFederation,auth,user,events,persistence};
}

test('managed login chooses SDK persistence and never returns the backend refresh token',async()=>{
 const h=harness({initialUser:false});
 await h.api.initialize();
 const session=await h.api.login(null,'a@example.test','test password',false);
 assert.equal(session.token,'refreshed-sdk-id-token');
 assert.equal(session.user.uid,'operator-a');
 assert.equal(session.user.tenantId,'tenant-a');
 assert.equal('refreshToken' in session,false);
 assert.deepEqual(h.persistence,['SDK_SESSION']);
 await h.api.login(null,'a@example.test','test password',true);
 assert.deepEqual(h.persistence,['SDK_SESSION','SDK_LOCAL']);
 await assert.rejects(h.api.login('google'),/non configurato/);
});

test('concurrent initialization waits for SDK restoration and logout broadcasts session termination',async()=>{
 let finish;const ready=new Promise(resolve=>finish=resolve);
 const h=harness({ready});
 const initialization=h.api.initialize();
 const restore=h.api.restoreSession();
 let restored=false;restore.then(()=>{restored=true;});
 await Promise.resolve();await Promise.resolve();
 assert.equal(restored,false);
 finish();await initialization;
 assert.equal((await restore).user.uid,'operator-a');
 await h.api.logout();
 assert.deepEqual(h.events,['radiotech:auth-ended']);
 assert.equal(await h.api.restoreSession(),null);
 assert.equal(await h.api.refreshSession(),null);
});

test('an old backend response cannot restore a logged-out or replaced SDK identity',async()=>{
 let finish, started;
 const entered=new Promise(resolve=>started=resolve);
 const reply=new Promise(resolve=>finish=resolve);
 const h=harness({backend:async()=>{started();return reply;}});
 const restore=h.api.restoreSession();
 await entered;
 await h.api.logout();
 const other={...h.user,uid:'operator-b'};
 h.auth.currentUser=other;
 finish(Response.json({role:'ADMIN',user:{name:'Old account'}}));
 await assert.rejects(restore,error=>error.name==='AbortError');
 assert.equal(h.auth.currentUser,other);
});

test('a disabled identity is signed out instead of keeping managed persistence',async()=>{
 const h=harness({backend:async()=>Response.json({message:'Account disabled'},{status:403})});
 await assert.rejects(h.api.restoreSession(),/Account disabled/);
 assert.equal(h.auth.currentUser,null);
 assert.deepEqual(h.events,['radiotech:auth-ended']);
});

test('a plain gateway denial remains a denial and presents a readable error',async()=>{
 const h=harness({backend:async()=>new Response('Invalid CORS request',{status:403})});
 await assert.rejects(h.api.restoreSession(),/Non è stato possibile completare l’accesso/);
 assert.equal(h.auth.currentUser,null);
 assert.deepEqual(h.events,['radiotech:auth-ended']);
});
