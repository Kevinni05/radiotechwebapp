import {chromium} from 'playwright';
import {mkdir,writeFile} from 'node:fs/promises';
import {session} from '../web-tests/fixtures/control-room-session.js';

// Supplementary fixtures exercise successful weather, actual calculators,
// retained report deletion, and the footer without contacting production.
const phase=process.argv[2]||'after';
const root=process.env.VISUAL_OUTPUT||`.dist/ultimate-ui/${phase}`;
const baseURL=process.env.VISUAL_BASE_URL||'http://127.0.0.1:18080';
const viewports=[[1920,1080],[1440,900],[1280,800],[1024,768],[768,1024],[430,932],[390,844],[375,812],[320,720]];
await mkdir(root,{recursive:true});
const browser=await chromium.launch({headless:true});

async function context(){
  const result=await browser.newContext({baseURL,reducedMotion:'reduce',timezoneId:'Europe/Rome'});
  await result.route('https://**',route=>route.abort());
  return result;
}
async function navigate(page,view){
  const menu=page.locator('#mobileMenu');
  if(await menu.isVisible()&&await menu.getAttribute('aria-expanded')!=='true')await menu.click();
  await page.locator(`.nav [data-view="${view}"]`).click();
  await page.locator(`#view-${view}`).waitFor({state:'visible'});
  await page.waitForLoadState('networkidle');
}
async function screenshot(page,name,fullPage=true){
  await page.evaluate(()=>document.fonts.ready);
  await page.screenshot({path:`${root}/${name}.png`,fullPage,animations:'disabled'});
}
function dashboardRecords(filled){return{
  '/reports':filled?[{id:'r1',taskId:'t1',operatorId:'o1',status:'SUBMITTED',operatorNotes:'Verifica RF completata.',attachments:[]}]:[],
  '/inventory':filled?[{id:'s1',name:'Connettore N',sku:'RF-001',quantity:24,minimumThreshold:10},{id:'s2',name:'Cavo coassiale',sku:'RF-002',quantity:3,minimumThreshold:5}]:[],
  antennas:filled?[{id:'a1',name:'Ponte radio Bari – Monte Caccia',status:'ATTIVA',lat:41.12,lng:16.87,specs:{frequenza:5800,potenza:20,ros:1.2,temperatura:34}},{id:'a2',name:'Ripetitore costiero Brindisi',status:'MANUTENZIONE',lat:40.63,lng:17.94,specs:{frequenza:2400,potenza:12,ros:1.6,temperatura:42}}]:[],
  respond(path){
    if(path==='/operators')return{success:true,data:filled?[{id:'o1',fullName:'Luca De Santis',status:'ATTIVO'},{id:'o2',fullName:'Giulia Romano',status:'ATTIVO'}]:[]};
    if(path==='/tasks')return{success:true,data:filled?[{id:'t1',title:'Verifica trasmettitore Bari',operatorId:'o1',antennaId:'a1',status:'ASSIGNED',dueAt:'2026-10-10T09:00:00Z'},{id:'t2',title:'Manutenzione ripetitore',operatorId:'o2',antennaId:'a2',status:'APPROVED'}]:[]};
    if(path==='/dashboard/stats')return{antennas:filled?2:0,activeAntennas:filled?1:0,inactiveAntennas:filled?1:0,availability:filled?50:0,tasks:filled?2:0,operators:filled?2:0,interventions:0,activeTasks:filled?1:0,pendingReports:filled?1:0,inventoryItems:filled?2:0,lowStock:filled?1:0,taskStats:{assigned:filled?1:0,inProgress:0,completed:filled?1:0,cancelled:0,overdue:0}};
    if(path==='/calendar')return filled?[{day:'2026-10-09',note:'Confermare la squadra e gli strumenti RF per il sopralluogo Bari.',reminderAt:'2026-10-09T15:00:00Z',reminderPending:true}]:[];
    if(path==='/weather')return{temperature_2m:21,weather_code:0,apparent_temperature:20,relative_humidity_2m:60,wind_speed_10m:12,daily:{time:['2026-10-09','2026-10-10','2026-10-11','2026-10-12','2026-10-13','2026-10-14'],weather_code:[0,1,2,3,0,1],temperature_2m_max:[24,23,22,21,24,23],temperature_2m_min:[16,17,16,15,16,17],sunrise:['2026-10-09T07:00'],sunset:['2026-10-09T18:20'],uv_index_max:[4]}};
    if(!filled&&['/incidents','/alerts','/workforce/shifts'].includes(path))return[];
  }
};}
async function dashboards(){
  const results=[];
  for(const filled of[true,false]){
    const ctx=await context(),page=await ctx.newPage();
    await page.addInitScript(()=>localStorage.setItem('rt-weather-position',JSON.stringify({latitude:41.12,longitude:16.87,label:'Bari · Puglia'})));
    await session(page,'ADMIN',dashboardRecords(filled));
    await page.locator('#weatherCondition').filter({hasText:'Soleggiato'}).waitFor();
    for(const[width,height]of viewports){
      await page.setViewportSize({width,height});await page.evaluate(()=>window.scrollTo(0,0));
      const name=`dashboard-success-${filled?'filled':'empty'}-${width}x${height}.png`;
      await screenshot(page,name.slice(0,-4));
      if(width===320)await page.locator('.signature-topbar').screenshot({path:`${root}/topbar-success-${filled?'filled':'empty'}-320.png`,animations:'disabled'});
      results.push({width,height,filled,name,...await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,topbarHeight:document.querySelector('.signature-topbar').getBoundingClientRect().height,bodyHeight:document.body.scrollHeight}))});
    }
    await ctx.close();
  }
  await writeFile(`${root}/dashboard-success-audit.json`,JSON.stringify(results,null,2));
}
async function calculators(){
  const ctx=await context(),page=await ctx.newPage(),results=[];await session(page);
  for(const[width,height]of[[1440,900],[1024,768],[768,1024],[390,844],[320,720]]){
    await page.setViewportSize({width,height});await navigate(page,'tools');
    for(const index of[1,3,4,5,7]){
      await page.locator(`#telco-tab-${index}`).click();await page.evaluate(()=>window.scrollTo(0,0));
      await screenshot(page,`tools-tab${index}-${width}x${height}`);
      results.push({width,height,tab:index,overflow:await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)});
    }
  }
  await writeFile(`${root}/tools-audit.json`,JSON.stringify(results,null,2));await ctx.close();
}
async function footers(){
  const ctx=await context(),page=await ctx.newPage(),results=[];await session(page);
  for(const[width,height]of[[1440,900],[1024,768],[768,1024],[430,932],[390,844],[375,812],[320,720]]){
    await page.setViewportSize({width,height});
    await page.locator('.app-footer').screenshot({path:`${root}/footer-${width}.png`,animations:'disabled'});
    results.push(await page.locator('.app-footer').evaluate((footer,width)=>({width,height:footer.getBoundingClientRect().height,links:[...footer.querySelectorAll('.footer-group button')].map(node=>({text:node.textContent.trim(),height:node.getBoundingClientRect().height,visible:!!node.getClientRects().length})),overflow:document.documentElement.scrollWidth>innerWidth}),width));
  }
  await writeFile(`${root}/footer-audit.json`,JSON.stringify(results,null,2));await ctx.close();
}
async function reports(){
  const ctx=await context(),page=await ctx.newPage();
  await session(page,'ADMIN',{
    '/reports':[{id:'r1',taskId:'t1',operatorId:'o1',status:'SUBMITTED',operatorNotes:'Verifica orientamento antenna e misure RF completate.',submittedAt:'2026-10-08T14:30:00Z',materialsUsed:[],attachments:[]},{id:'r2',taskId:'t2',operatorId:'o1',status:'APPROVED',operatorNotes:'Manutenzione periodica del ripetitore costiero completata.',attachments:[]}],
    respond(path){if(path==='/tasks')return{success:true,data:[{id:'t1',title:'Verifica trasmettitore Bari',operatorId:'o1',status:'ASSIGNED'},{id:'t2',title:'Manutenzione ripetitore costiero',operatorId:'o1',status:'APPROVED'}]};}
  });
  for(const[width,height]of[[1440,900],[375,812]]){
    await page.setViewportSize({width,height});await navigate(page,'reports');
    await page.locator('[data-report-delete="r1"]').waitFor();await page.locator('#reportCollapseAll').click();
    await page.evaluate(()=>window.scrollTo(0,0));await screenshot(page,`reports-collapsed-${width}`);
    await page.locator('[data-report-delete="r2"]').click();await page.locator('.report-delete-dialog').waitFor();
    await screenshot(page,`report-confirm-${width}`,false);
    await page.locator('[data-delete-cancel]').click();
  }
  await ctx.close();
}
try{await dashboards();await calculators();await footers();await reports();}
finally{await browser.close();}
console.log(`Supplementary capture complete: ${root}`);
