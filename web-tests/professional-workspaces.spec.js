import {test,expect} from '@playwright/test';
import {createHash} from 'node:crypto';
import {session} from './fixtures/control-room-session.js';

test('professional workspace styling leaves the complete overview visually unchanged',async({page})=>{
 await page.clock.setFixedTime(new Date('2026-10-12T08:00:00Z'));
 await page.route('**/assets/professional-workspaces.css',route=>route.fulfill({contentType:'text/css',body:''}));
 await session(page);
 await page.evaluate(()=>document.fonts.ready);
 await expect(page.locator('#statTasks')).toHaveText('1');
 await expect(page.locator('#footerServiceStatus')).toHaveText('Backend disponibile');
 await page.waitForLoadState('networkidle');
 await page.locator('#toastContainer').evaluateAll(items=>items.forEach(e=>e.style.visibility='hidden'));
 const selectors=['#view-dashboard','#app .main','#app .sidebar','#app .topbar','#app .footer'];
 const snapshot=()=>page.evaluate(selectors=>selectors.flatMap(selector=>[...document.querySelectorAll(selector)].flatMap(root=>selector==='#app .main'?[root]:[root,...root.querySelectorAll('*')]).map(element=>{
  const css=getComputedStyle(element);return ['display','background','color','font','border','padding','margin','gap','width','height','borderRadius'].map(key=>css[key]);
 })),selectors);
 const imageBefore=await page.locator('#view-dashboard').screenshot({animations:'disabled'});
 const before=await snapshot();
 await page.unroute('**/assets/professional-workspaces.css');
 await page.addStyleTag({url:'/assets/professional-workspaces.css?overview-proof=1'});
 const imageAfter=await page.locator('#view-dashboard').screenshot({animations:'disabled'});
 expect(await snapshot()).toEqual(before);
 const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
 expect(digest(imageAfter)).toBe(digest(imageBefore));
});

for(const width of [390,768,1440])test(`all operational sections fit and align at ${width}px`,async({page})=>{
 await page.setViewportSize({width,height:1000});await session(page);
 const views=await page.locator('.nav [data-view]').evaluateAll(items=>items.map(e=>e.dataset.view).filter(id=>id!=='dashboard'));
 for(const id of views){
  if(width<1024)await page.locator('#mobileMenu').click();
  await page.locator(`.nav [data-view="${id}"]`).click();
  const view=page.locator(`#view-${id}`);await expect(view).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),id).toBe(true);
  await expect(view.locator('.section-head h2').first()).toBeVisible();
  const overflow=await view.evaluate(root=>[...root.querySelectorAll('input,select,textarea,.panel,.workspace-filter-panel')].filter(e=>e.getBoundingClientRect().width&& !e.closest('.table-wrap')).filter(e=>{const r=e.getBoundingClientRect(),p=root.getBoundingClientRect();return r.left<p.left-1||r.right>p.right+1;}).map(e=>e.id||e.className));
  expect(overflow,id).toEqual([]);
 }
});

test('infrastructure filter labels and table columns share a precise alignment',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await session(page,'ADMIN',{antennas:[{id:'a1',name:'Ponte Monte Cavo',status:'ATTIVA',lat:41,lng:12,specs:{frequenza:450}}]});
 await page.locator('.nav [data-view="antennas"]').click();
 await expect(page.locator('#antennasTableBody tr')).toHaveCount(1);
 const positions=await page.evaluate(()=>{
  const header=document.querySelector('#view-antennas .panel-head h3').getBoundingClientRect().left;
  const filter=document.querySelector('label[for="antennaSearch"]').getBoundingClientRect().left;
  const cell=document.querySelector('#antennasTableBody td').getBoundingClientRect();
  return {header,filter,text:cell.left+parseFloat(getComputedStyle(document.querySelector('#antennasTableBody td')).paddingLeft)};
 });
 expect(Math.abs(positions.header-positions.filter)).toBeLessThanOrEqual(1);
 expect(Math.abs(positions.header-positions.text)).toBeLessThanOrEqual(1);
 await page.getByRole('searchbox',{name:'Cerca antenna',exact:true}).fill('Monte');
 await expect(page.getByRole('searchbox',{name:'Cerca antenna',exact:true})).toHaveValue('Monte');
});

test('report actions remain available while compact rows preserve full-width details',async({page})=>{
 await session(page,'ADMIN',{'/reports':[{id:'r1',taskTitle:'Verifica trasmettitore',status:'SUBMITTED',operatorName:'Tecnico QA',materialsUsed:[],attachments:[]}]});
 await page.locator('.nav [data-view="reports"]').click();
 await expect(page.locator('[data-report-approve="r1"]')).toBeVisible();
 await page.locator('#reportCollapseAll').click();await expect(page.locator('.report-content')).toBeHidden();
 await page.locator('#reportExpandAll').click();await expect(page.locator('.report-content')).toBeVisible();
 const bounds=await page.locator('.report-row').boundingBox(),details=await page.locator('.report-content').boundingBox();
 expect(details.width).toBeGreaterThan(bounds.width*.9);
 await expect(page.locator('[data-report-delete="r1"]')).toBeVisible();
});

test('communication panels and send controls align without stretching an empty inbox',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await session(page);
 await page.locator('.nav [data-view="notifications"]').click();
 const headers=await page.locator('#view-notifications .notification-workspace .panel-head').evaluateAll(items=>items.map(e=>e.getBoundingClientRect().bottom));
 expect(headers).toHaveLength(2);expect(Math.abs(headers[0]-headers[1])).toBeLessThanOrEqual(1);
 const input=await page.locator('#notificationPriority').boundingBox(),button=await page.locator('#sendNotificationBtn').boundingBox();
 expect(Math.abs(input.y+input.height-button.y-button.height)).toBeLessThanOrEqual(1);
});
