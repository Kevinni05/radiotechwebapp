import {chromium} from 'playwright';
import {mkdir,writeFile} from 'node:fs/promises';
import {session} from '../web-tests/fixtures/control-room-session.js';
import {measureBoxLayout} from '../web-tests/fixtures/box-layout.js';
const phase=process.argv[2]||'after';
const root=process.env.VISUAL_OUTPUT||`.dist/spacing/${phase}`;
const browser=await chromium.launch();
const context=await browser.newContext({baseURL:process.env.VISUAL_BASE_URL||'http://127.0.0.1:18080',reducedMotion:'reduce'});
const page=await context.newPage();await page.route('https://**',route=>route.abort());

await session(page,'ADMIN',{'/inventory':[{id:'s1',name:'Connettore N',sku:'N001',quantity:12,active:true}],'/reports':[{id:'r1',status:'SUBMITTED',operatorNotes:'Verifica del ponte radio',attachments:[]}]});
const views=await page.locator('.nav [data-view]').evaluateAll(ns=>ns.map(n=>n.dataset.view));
const audit=[];await mkdir(root,{recursive:true});
for(const width of [1440,768,390]){await page.setViewportSize({width,height:900});for(const view of views){
 if(await page.locator('#mobileMenu').isVisible())await page.locator('#mobileMenu').click();
 await page.locator(`.nav [data-view="${view}"]`).click();await page.waitForTimeout(180);
 const result=await page.locator('.view.active').evaluate(measureBoxLayout);
 audit.push({view,width,...result});if(result.issues.length||['dashboard','inventory','operations'].includes(view))await page.screenshot({path:`${root}/${view}-${width}.png`,fullPage:true});

}}
await writeFile(`${root}/audit.json`,JSON.stringify(audit,null,2));
console.log(JSON.stringify(audit.filter(r=>r.issues.length)));await browser.close();
