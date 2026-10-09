import {chromium} from 'playwright';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {session} from '../web-tests/fixtures/control-room-session.js';

// Static tenant-scoped QA data only; no production API is contacted.
const phase=process.argv[2]||'after';
const root=process.env.VISUAL_OUTPUT||`.dist/ultimate-ui/${phase}`;
const baseURL=process.env.VISUAL_BASE_URL||'http://127.0.0.1:18080';
const widths=[[1440,900],[390,844],[1920,1080],[1280,800],[1024,768],[768,1024],[430,932],[375,812],[320,720]];
const requestedViews=['dashboard','antennas','operations','planning','reports','notifications','tools','inventory','profile','system'];
await mkdir(root,{recursive:true});
const browser=await chromium.launch({headless:true});
const results=[];
const catalog=[{id:'requests',title:'Richieste di assistenza',writable:true,fields:[{key:'title',label:'Titolo',type:'text',required:true},{key:'notes',label:'Descrizione',type:'textarea'}],actions:[]}];
function recordsFor(state){
  const filled=state==='filled';
  const antennas=filled?[{id:'a1',name:'Ponte radio Bari – Monte Caccia',status:'ATTIVA',lat:41.12,lng:16.87,location:'Bari · Puglia',specs:{frequenza:5800,potenza:20,ros:1.2,temperatura:34}},{id:'a2',name:'Ripetitore costiero Brindisi',status:'MANUTENZIONE',lat:40.63,lng:17.94,location:'Brindisi · Puglia',specs:{frequenza:2400,potenza:12,ros:1.6,temperatura:42}}]:[];
  const operators=filled?[{id:'o1',fullName:'Luca De Santis',status:'ATTIVO',role:'OPERATOR',email:'luca@example.test'},{id:'o2',fullName:'Giulia Romano',status:'ATTIVO',role:'OPERATOR',email:'giulia@example.test'}]:[];
  const tasks=filled?[{id:'t1',title:'Verifica trasmettitore Bari',operatorId:'o1',antennaId:'a1',status:'ASSIGNED',dueAt:'2026-10-10T09:00:00Z',description:'Verifica orientamento e misura RF.'},{id:'t2',title:'Manutenzione ripetitore costiero',operatorId:'o2',antennaId:'a2',status:'APPROVED',dueAt:'2026-10-08T09:00:00Z'}]:[];
  const reports=filled?[{id:'r1',taskId:'t1',operatorId:'o1',status:'SUBMITTED',operatorNotes:'Verifica orientamento antenna e test RF completati.',submittedAt:'2026-10-08T14:30:00Z',materialsUsed:[],attachments:[]},{id:'r2',taskId:'t2',operatorId:'o2',status:'APPROVED',operatorNotes:'Manutenzione periodica del ripetitore costiero completata.',submittedAt:'2026-10-08T12:00:00Z',attachments:[]}]:[];
  const inventory=filled?[{id:'s1',name:'Connettore N maschio per cavo RF',sku:'RF-N-001',quantity:24,minimumThreshold:10,supplier:'Telecom Components Italia',location:'Scaffale B-12',active:true},{id:'s2',name:'Cavo coassiale a bassa perdita 50 Ω',sku:'RF-C-002',quantity:3,minimumThreshold:5,supplier:'RadioLink',unit:'m',active:true}]:[];
  return {antennas,'/reports':reports,'/inventory':inventory,respond(path){
    if(path==='/auth/public-config')return{enabled:false};
    if(path==='/tasks')return{success:true,data:tasks};
    if(path==='/operators')return{success:true,data:operators};
    if(path==='/dashboard/stats')return{antennas:antennas.length,tasks:tasks.length,operators:operators.length,availability:filled?50:null,activeTasks:filled?1:0,pendingReports:filled?1:0,inventoryItems:inventory.length,lowStock:filled?1:0,taskStats:{overdue:0}};
    if(path==='/dashboard/notifications')return filled?[{id:'n1',title:'Intervento programmato',message:'Manutenzione Ponte Bari, domani ore 09:00. Confermare disponibilità della squadra e strumenti di misura RF prima della partenza.',createdAt:'2026-10-08T15:30:00Z',target:'ALL'}]:[];
    if(path==='/dashboard/audit')return filled?[{id:'au1',action:'REPORT_SUBMITTED',actorName:'Luca De Santis',createdAt:'2026-10-08T14:30:00Z',details:'Verifica trasmettitore Bari'}]:[];
    if(path==='/incidents')return filled?[{id:'i1',title:'Livello RF da verificare',severity:'HIGH',status:'DETECTED'}]:[];
    if(path==='/alerts')return filled?[{id:'alert1',descrizione:'Livello RF da verificare sul ripetitore costiero',priorita:'ALTA',letto:false}]:[];
    if(path==='/workforce/shifts')return filled?[{name:'Squadra Bari',status:'ACTIVE',readiness:'READY',workMinutes:90,restMinutes:15,version:0}]:[];
    if(path==='/ai/insights')return{scope:'TENANT',assetsObserved:antennas.length,highRiskAssets:filled?1:0,activeTasks:filled?1:0,overdueTasks:0,notice:'Indicatori, non probabilità di guasto.',partial:false,risks:filled?[{assetId:'a2',name:'Ripetitore costiero Brindisi',score:70,level:'HIGH',dataQuality:'LIMITED',reasons:['Asset in manutenzione'],recommendation:'Verificare con il responsabile.',estimatedMaintenanceAt:null}]:[]};
    if(path==='/operations/health')return{backend:'FIRESTORE',attachments:{count:0,bytes:0},pendingReports:filled?1:0,backup:{state:'NOT_CONFIGURED'},firestore:{state:'UNAVAILABLE',note:'Metriche non disponibili nella fixture locale.',quotaDayTimezone:'America/Los_Angeles'},alarms:[{message:'Backup esterno da configurare.'}]};
    if(path==='/pro/catalog')return catalog;
    if(path==='/pro/requests')return{records:filled?[{id:'request1',title:'Verifica copertura radio regionale',notes:'Richiesta di sopralluogo per verifica del collegamento radio e misura del livello di segnale.',status:'OPEN',version:1}]:[],partial:false};
  }};
}
async function settle(page){await page.evaluate(()=>document.fonts.ready);await page.waitForLoadState('networkidle');await page.waitForFunction(()=>![...document.querySelectorAll('[role="status"],.loading,.empty-state')].some(node=>node.getClientRects().length&&/caricamento|caricando/i.test(node.textContent)),null,{timeout:8000});await page.waitForTimeout(120);}
async function metrics(page){return page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,innerWidth,bodyHeight:document.body.scrollHeight,loadingText:[...document.querySelectorAll('[role="status"],.loading,.empty-state')].filter(n=>n.getClientRects().length&&/caricamento|caricando/i.test(n.textContent)).map(n=>n.textContent.trim()),topbarHeight:document.querySelector('.signature-topbar')?.getBoundingClientRect().height}));}
async function freeze(page){return page.evaluate(()=>{const styles=getComputedStyle(document.documentElement),tokens={};for(const property of styles)if(property.startsWith('--'))tokens[property]=styles.getPropertyValue(property).trim();const components={};for(const selector of ['body','.signature-topbar','.sidebar','.session-notes','#sessionNotesClear','#refreshBtn','#notificationBell','.map-shell']){const node=document.querySelector(selector);if(node){const s=getComputedStyle(node);components[selector]=Object.fromEntries(['color','backgroundColor','backgroundImage','borderColor','boxShadow','fontFamily','backdropFilter'].map(key=>[key,s[key]]));}}return{tokens,components};});}
async function capture(page,view,state,width,height){await page.evaluate(()=>window.scrollTo(0,0));const filename=`${view}-${state}-${width}x${height}.png`;await page.screenshot({path:`${root}/${filename}`,fullPage:true,animations:'disabled'});results.push({view,state,width,height,filename,...await metrics(page)});}
try{
for(const state of ['filled','empty']){
  const context=await browser.newContext({baseURL,reducedMotion:'reduce'});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.route('https://**',route=>route.abort());
  await session(page,'ADMIN',recordsFor(state));await settle(page);
  await writeFile(`${root}/visual-identity-${state}.json`,JSON.stringify(await freeze(page),null,2));
  const available=await page.locator('.nav [data-view]').evaluateAll(nodes=>nodes.map(n=>n.dataset.view));
  const views=[...requestedViews,...available.filter(v=>!requestedViews.includes(v))];
  for(const [width,height] of widths){await page.setViewportSize({width,height});for(const view of views){
    try{if(await page.locator('#mobileMenu').isVisible()&&await page.locator('#mobileMenu').getAttribute('aria-expanded')!=='true')await page.locator('#mobileMenu').click();await page.locator(`.nav [data-view="${view}"]`).click();await page.locator(`#view-${view}`).waitFor({state:'visible'});await settle(page);await capture(page,view,state,width,height);if(view==='dashboard'&&[1440,390].includes(width)){await page.locator('.signature-topbar').screenshot({path:`${root}/topbar-${state}-${width}.png`});console.log(`Priority screenshot: ${root}/${view}-${state}-${width}x${height}.png`);}}
    catch(error){results.push({view,state,width,height,error:error.message});}
  }console.log(`${phase} ${state} ${width}x${height}: ${views.length} sections`);await writeFile(`${root}/audit.json`,JSON.stringify(results,null,2));}
  results.push({state,pageErrors:errors});await context.close();
  const portalContext=await browser.newContext({baseURL,reducedMotion:'reduce'});const portalErrors=[];await portalContext.route('https://**',route=>route.abort());await portalContext.route('**/api/**',route=>{const path=new URL(route.request().url()).pathname;if(path==='/api/v1/auth/public-config')return route.fulfill({json:{enabled:false}});if(path==='/api/v1/auth/login')return route.fulfill({json:{role:'CUSTOMER',token:'portal-test',refreshToken:'portal-refresh'}});if(path==='/api/v1/pro/catalog')return route.fulfill({json:catalog});if(path==='/api/v1/pro/requests')return route.fulfill({json:recordsFor(state).respond('/pro/requests')});return route.fulfill({status:404,json:{message:'Unexpected QA route'}});});
  for(const[width,height]of widths){const portal=await portalContext.newPage();portal.on('pageerror',e=>portalErrors.push(e.message));await portal.setViewportSize({width,height});await portal.goto('/portal');await settle(portal);await capture(portal,'login',state,width,height);await portal.getByLabel('Email',{exact:true}).fill('cliente@example.test');await portal.getByLabel('Password',{exact:true}).fill('test-password');await portal.getByRole('button',{name:'Accedi al portale'}).click();await portal.locator('#portalApp').waitFor({state:'visible'});await settle(portal);await capture(portal,'portal',state,width,height);await portal.close();}
  results.push({state,portalErrors});await portalContext.close();
}
}finally{await writeFile(`${root}/audit.json`,JSON.stringify(results,null,2));await browser.close();}
console.log(`Complete: ${root}`);
if(phase==='after'){
  const before=JSON.parse(await readFile(`${root}/../before/visual-identity-filled.json`,'utf8'));
  const after=JSON.parse(await readFile(`${root}/visual-identity-filled.json`,'utf8'));
  const appearance=value=>/#|rgba?\(|hsla?\(|gradient|blur\(|shadow/i.test(value);
  const changedTokens=Object.entries(before.tokens).filter(([key,value])=>appearance(value)||/font/i.test(key)).filter(([key,value])=>after.tokens[key]!==value).map(([key,value])=>({key,before:value,after:after.tokens[key]}));
  const changedComponents=[];
  for(const[selector,properties]of Object.entries(before.components))for(const[key,value]of Object.entries(properties))if(after.components[selector]?.[key]!==value)changedComponents.push({selector,key,before:value,after:after.components[selector]?.[key]});
  await writeFile(`${root}/identity-comparison.json`,JSON.stringify({changedTokens,changedComponents},null,2));
  const gallery=`<!doctype html><html lang="it"><meta charset="utf-8"><title>RadioTech — confronto visivo</title><style>body{margin:0;background:#08090b;color:#f4f4f5;font:14px system-ui}header{position:sticky;top:0;padding:16px;background:#121316;z-index:2;display:flex;gap:16px;align-items:center;flex-wrap:wrap}select{padding:8px;background:#191a1e;color:inherit;border:1px solid #2b2c32;border-radius:8px}main{display:grid;grid-template-columns:1fr 1fr;gap:16px;padding:16px}figure{margin:0;min-width:0}figcaption{padding:12px;font-weight:600}img{width:100%;height:auto}a{color:#8fbaff}@media(max-width:700px){main{grid-template-columns:1fr}}</style><header><strong>RadioTech · prima / dopo</strong><label>Sezione <select id="view">${requestedViews.concat(['login','portal']).map(view=>`<option>${view}</option>`).join('')}</select></label><label>Dati <select id="state"><option value="filled">Presenti</option><option value="empty">Vuoti</option></select></label><label>Viewport <select id="size">${widths.map(([w,h])=>`<option>${w}x${h}</option>`).join('')}</select></label><span>Fixture locali, nessun dato di produzione.</span></header><main><figure><figcaption>Prima</figcaption><a id="beforeLink"><img id="before" alt="Schermata prima delle modifiche"></a></figure><figure><figcaption>Dopo</figcaption><a id="afterLink"><img id="after" alt="Schermata dopo le modifiche"></a></figure></main><script>const select=[...document.querySelectorAll('select')];function update(){const filename=select.map(node=>node.value).join('-')+'.png';for(const phase of['before','after']){document.getElementById(phase).src=phase+'/'+filename;document.getElementById(phase+'Link').href=phase+'/'+filename;}}select.forEach(node=>node.onchange=update);update();</script></html>`;
  await writeFile(`${root}/../comparison.html`,gallery);
  console.log(`Identity differences: ${changedTokens.length} tokens, ${changedComponents.length} component properties`);
}
