import {chromium} from 'playwright';
import {mkdir,writeFile} from 'node:fs/promises';
import {session} from '../web-tests/fixtures/control-room-session.js';
const phase=process.argv[2]||'after'; const root=process.env.VISUAL_OUTPUT||`.dist/enterprise-pro/${phase}`;await mkdir(root,{recursive:true});
const browser=await chromium.launch({headless:true});const results=[];
const widths=[[1920,1080],[1440,900],[1280,800],[1024,768],[768,1024],[430,932],[390,844],[375,812],[320,720]];
for(const state of ['filled','empty']){
const context=await browser.newContext({baseURL:process.env.VISUAL_BASE_URL||'http://127.0.0.1:18080',reducedMotion:'reduce'}); const page=await context.newPage(); const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.route('https://**',route=>route.abort());
const antennas=state==='filled'?[{id:'a1',name:'Ponte radio Bari – Monte Caccia',status:'ATTIVA',lat:41.12,lng:16.87,location:'Bari · Puglia',specs:{frequenza:5800,potenza:20,ros:1.2,temperatura:34}},{id:'a2',name:'Ripetitore costiero Brindisi',status:'MANUTENZIONE',lat:40.63,lng:17.94,specs:{frequenza:2400,potenza:12,ros:1.6,temperatura:42}}]:[];
const records={antennas,'/reports':state==='filled'?[{id:'r1',taskId:'t1',operatorId:'o1',status:'SUBMITTED',operatorNotes:'Verifica orientamento antenna, sostituzione connettore e test RF completati.',submittedAt:'2026-10-08T14:30:00Z',attachments:[]},{id:'r2',taskId:'t2',operatorId:'o1',status:'APPROVED',operatorNotes:'Manutenzione periodica del ripetitore costiero completata.'}]:[],'/inventory':state==='filled'?[{id:'s1',name:'Connettore N maschio per cavo RF',sku:'RF-N-001',quantity:24,minimumThreshold:10,supplier:'Telecom Components Italia',location:'Scaffale B-12',active:true},{id:'s2',name:'Cavo coassiale a bassa perdita 50 Ω',sku:'RF-C-002',quantity:3,minimumThreshold:5,supplier:'RadioLink',unit:'m',active:true}]:[],respond(path){
if(state==='empty'&&['/tasks','/operators'].includes(path))return{success:true,data:[]};
if(state==='empty'&&['/incidents','/alerts','/workforce/shifts'].includes(path))return[];
if(path==='/dashboard/stats')return{antennas:antennas.length,tasks:state==='filled'?8:0,operators:state==='filled'?4:0,availability:99.7,activeTasks:state==='filled'?3:0,pendingReports:state==='filled'?1:0,taskStats:{overdue:state==='filled'?1:0}};
if(path==='/dashboard/notifications')return state==='filled'?[{id:'n1',title:'Intervento programmato',message:'Manutenzione Ponte Bari, domani ore 09:00.',createdAt:'2026-10-08T15:30:00Z',target:'ALL'}]:[];
if(path==='/dashboard/audit')return state==='filled'?[{id:'au1',action:'REPORT_SUBMITTED',actorName:'Luca De Santis',createdAt:'2026-10-08T14:30:00Z',details:'Verifica trasmettitore Bari'}]:[];
if(path==='/alerts')return state==='filled'?[{id:'alert1',descrizione:'Livello RF da verificare sul ripetitore costiero',priorita:'ALTA',letto:false}]:[];
if(path==='/pro/catalog')return[{id:'clients',title:'Clienti e contatti',writable:true,fields:[{key:'name',label:'Ragione sociale',type:'text',required:true},{key:'email',label:'Email',type:'email'},{key:'notes',label:'Note',type:'textarea'}],actions:['ARCHIVE']}];
if(path==='/pro/clients')return{records:state==='filled'?[{id:'c1',name:'Rete Broadcast Puglia S.r.l.',email:'tecnico@example.test',status:'ACTIVE',version:1,notes:'Servizio RF per rete regionale'}]:[],partial:false};
if(path==='/pro/security')return{revocationChecked:true,providerActivation:'Provider aziendale disponibile',mfaActivation:'Configurazione MFA disponibile'};
}};
await session(page,'ADMIN',records);
const views=await page.locator('.nav [data-view]').evaluateAll(nodes=>nodes.map(n=>n.dataset.view));
for(const [width,height] of widths){await page.setViewportSize({width,height});for(const view of views){
try{if(await page.locator('#mobileMenu').isVisible()&&await page.locator('#mobileMenu').getAttribute('aria-expanded')!=='true')await page.locator('#mobileMenu').click();await page.locator(`.nav [data-view="${view}"]`).click();await page.locator(`#view-${view}`).waitFor({state:'visible'});await page.waitForTimeout(100);await page.evaluate(()=>document.fonts.ready);await page.evaluate(()=>window.scrollTo(0,0));const filename=`${view}-${state}-${width}x${height}.png`;await page.screenshot({path:`${root}/${filename}`,fullPage:true});const metrics=await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,innerWidth,bodyHeight:document.body.scrollHeight}));results.push({view,state,width,height,filename,...metrics});}
catch(e){results.push({view,state,width,height,error:e.message});}
}console.log(`${phase} ${state} ${width}: ${views.length} sections`);}
results.push({state,pageErrors:errors});await context.close();}
await writeFile(`${root}/audit.json`,JSON.stringify(results,null,2));await browser.close();console.log(`Complete: ${root}`);
