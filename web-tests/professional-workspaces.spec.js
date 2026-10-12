import {test,expect} from '@playwright/test';
import {createHash} from 'node:crypto';
import {session} from './fixtures/control-room-session.js';
test.use({reducedMotion:'reduce'});

test('operational styling preserves the overview over the shared canvas',async({page},testInfo)=>{
 await page.setViewportSize({width:1440,height:3000});
 await page.emulateMedia({reducedMotion:'reduce'});
 await page.clock.setFixedTime(new Date('2026-10-12T08:00:00Z'));
 await page.route('**/assets/professional-workspaces.css',route=>route.fulfill({contentType:'text/css',body:''}));
 await session(page);
 await page.evaluate(()=>document.fonts.ready);
 await expect(page.locator('#statTasks')).toHaveText('1');
 await expect(page.locator('#footerServiceStatus')).toHaveText('Backend disponibile');
 await page.waitForLoadState('networkidle');
 // Transient synchronization feedback is outside the overview and may appear
 // between captures on slower runners. Keep it out of both images.
 await page.locator('#toastStack').evaluate(e=>e.style.visibility='hidden');
 const selectors=['#view-dashboard','#app .main','#app .sidebar','#app .topbar','#app .footer'];
 const snapshot=()=>page.evaluate(selectors=>selectors.flatMap(selector=>[...document.querySelectorAll(selector)].flatMap(root=>selector==='#app .main'?[root]:[root,...root.querySelectorAll('*')]).map(element=>{
  const css=getComputedStyle(element);return ['display','background','color','font','border','padding','margin','gap','width','height','borderRadius','opacity','transform'].map(key=>css[key]);
 })),selectors);
 const imageBefore=await page.locator('#view-dashboard').screenshot({animations:'disabled'});
 const before=await snapshot();
 await page.unroute('**/assets/professional-workspaces.css');
 await page.addStyleTag({url:'/assets/professional-workspaces.css?overview-proof=1'});
 const imageAfter=await page.locator('#view-dashboard').screenshot({animations:'disabled'});
 expect(await snapshot()).toEqual(before);
 const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
 if(digest(imageAfter)!==digest(imageBefore)){
  // A stylesheet invalidation can repaint rounded edges one channel level
  // differently. Compare decoded pixels with only 1/255 channel tolerance;
  // dimensions and all computed styles still have to match exactly.
  const difference=await page.evaluate(async urls=>{
   const images=await Promise.all(urls.map(src=>new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=reject;image.src=src;})));
   if(images[0].width!==images[1].width||images[0].height!==images[1].height)return 255;
   const pixels=images.map(image=>{const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;const ctx=canvas.getContext('2d');ctx.drawImage(image,0,0);return ctx.getImageData(0,0,canvas.width,canvas.height).data;});
   let maximum=0;for(let i=0;i<pixels[0].length;i++)maximum=Math.max(maximum,Math.abs(pixels[0][i]-pixels[1][i]));return maximum;
  },[imageBefore,imageAfter].map(bytes=>'data:image/png;base64,'+bytes.toString('base64')));
  if(difference>1){await testInfo.attach('overview-before',{body:imageBefore,contentType:'image/png'});await testInfo.attach('overview-after',{body:imageAfter,contentType:'image/png'});}
  expect(difference).toBeLessThanOrEqual(1);
 }
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
  expect(await page.locator('#app .main').evaluate(e=>getComputedStyle(e).backgroundColor),'shared page canvas').toBe('rgba(0, 0, 0, 0)');
  if(id==='system'){
   const panels=await view.locator('.system-status-grid > .panel').evaluateAll(items=>items.map(e=>({width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height})));
   expect(Math.abs(panels[0].width-panels[1].width)).toBeLessThanOrEqual(1);
   expect(Math.abs(panels[0].height-panels[1].height)).toBeLessThanOrEqual(1);
  }
  if(id==='reports'&&width===390){
   const widths=await view.locator('.management-filters').evaluate(root=>{
    const available=root.getBoundingClientRect().width;
    return [...root.querySelectorAll('.field')].map(field=>Math.abs(field.getBoundingClientRect().width-available));
   });
   expect(widths.every(delta=>delta<=1),'mobile report filters occupy the full panel width').toBe(true);
  }
 }
});

test('infrastructure filter labels and table columns share a precise alignment',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await session(page,'ADMIN',{antennas:[{id:'a1',name:'Ponte Monte Cavo',status:'ATTIVA',lat:41,lng:12,specs:{frequenza:450}}]});
 await page.locator('.nav [data-view="antennas"]').click();
 await expect(page.locator('#antennasTableBody tr')).toHaveCount(1);
 await expect(page.locator('#view-antennas th')).toHaveCount(6);
 expect(await page.locator('#view-antennas .table-wrap').evaluate(e=>e.scrollWidth<=e.clientWidth)).toBe(true);
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
 await expect(page.locator('[data-report-toggle]')).toHaveAttribute('aria-expanded','false');
 await page.locator('#reportCollapseAll').click();await expect(page.locator('.report-content')).toBeHidden();
 await page.locator('#reportExpandAll').click();await expect(page.locator('.report-content')).toBeVisible();
 const bounds=await page.locator('.report-row').boundingBox(),details=await page.locator('.report-content').boundingBox();
 expect(details.width).toBeGreaterThan(bounds.width*.9);
 await expect(page.locator('[data-report-delete="r1"]')).toBeVisible();
});

test('reports open compressed, expand independently and compress again on reentry',async({page})=>{
 await session(page,'ADMIN',{'/reports':[{id:'compact',taskTitle:'Intervento QA',status:'SUBMITTED',attachments:[],materialsUsed:[]}]});
 await page.locator('.nav [data-view="reports"]').click();
 const toggle=page.locator('[data-report-id="compact"] [data-report-toggle]');
 await expect(toggle).toHaveAttribute('aria-expanded','false');await expect(page.locator('.report-content')).toBeHidden();
 await toggle.click();await expect(toggle).toHaveAttribute('aria-expanded','true');await expect(page.locator('.report-content')).toBeVisible();
 await page.locator('#reportsRefreshBtn').click();await expect(toggle).toHaveAttribute('aria-expanded','true');
 await page.locator('.nav [data-view="dashboard"]').click();await page.locator('.nav [data-view="reports"]').click();
 await expect(toggle).toHaveAttribute('aria-expanded','false');
});

test('operator directory provides account details and aligned inventory actions',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});
 await session(page,'ADMIN',{'/operators':[{id:'op1',fullName:'Tecnico QA',email:'tecnico@example.test',phone:'+39 000 000000',role:'OPERATOR',status:'ATTIVO',level:'SENIOR',shift:'Mattina',firebaseUid:'qa-uid'}],'/inventory':[{id:'stock1',name:'Cavo QA',quantity:4,active:true}]});
 await page.locator('.nav [data-view="operators"]').click();await expect(page.locator('[data-operator-details="op1"]')).toBeVisible();
 await expect(page.locator('#operatorOverview .stat-card')).toHaveCount(4);
 await expect(page.getByRole('textbox',{name:'Email account',exact:true})).toBeVisible();
 await page.locator('[data-operator-details="op1"]').click();
 const dialog=page.getByRole('dialog',{name:'Tecnico QA'});await expect(dialog).toContainText('+39 000 000000');await expect(dialog).toContainText('Mattina');
 await dialog.getByRole('button',{name:'Chiudi scheda'}).click();await expect(dialog).toHaveCount(0);
 await page.locator('#operatorsRefreshBtn').click();
 await expect(page.locator('[data-validity="op1"]')).toBeVisible();
 await page.locator('.nav [data-view="inventory"]').click();
 const edit=await page.locator('[data-inventory-edit="stock1"]').boundingBox(),archive=await page.locator('[data-inventory-archive="stock1"]').boundingBox();
 expect(Math.abs(edit.y-archive.y)).toBeLessThanOrEqual(1);expect(archive.x).toBeGreaterThan(edit.x);
});

test('communication panels and send controls align without stretching an empty inbox',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await session(page);
 await page.locator('.nav [data-view="notifications"]').click();
 const headers=await page.locator('#view-notifications .notification-workspace .panel-head').evaluateAll(items=>items.map(e=>e.getBoundingClientRect().bottom));
 expect(headers).toHaveLength(2);expect(Math.abs(headers[0]-headers[1])).toBeLessThanOrEqual(1);
 const input=await page.locator('#notificationPriority').boundingBox(),button=await page.locator('#sendNotificationBtn').boundingBox();
 expect(Math.abs(input.y+input.height-button.y-button.height)).toBeLessThanOrEqual(1);
});
