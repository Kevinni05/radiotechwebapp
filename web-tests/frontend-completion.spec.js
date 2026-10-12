import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

test('business confirmation supports cancel and keyboard submit without native prompts',async({page})=>{
 let status='ASSIGNED',writes=0;page.on('dialog',()=>{throw new Error('Unexpected native prompt');});
 await session(page,'ADMIN',{respond(path,method,request){if(path==='/tasks')return {data:[{id:'qa',title:'Verifica ponte',status}]};if(path==='/tasks/qa/status'&&method==='PATCH'){status=request.postDataJSON().status;writes++;return {success:true};}}});
 await page.locator('[data-view="operations"]').first().click();await page.locator('[data-task-cancel="qa"]').click();
 await expect(page.getByRole('dialog',{name:'Conferma operazione'})).toBeVisible();await page.keyboard.press('Escape');expect(writes).toBe(0);
 await page.locator('[data-task-cancel="qa"]').click();await page.keyboard.press('Tab');await page.keyboard.press('Enter');
 await expect.poll(()=>writes).toBe(1);await expect(page.locator('[data-task-cancel="qa"]')).toHaveCount(0);
});

test('dashboard shortcuts apply report and stock filters',async({page})=>{
 await session(page,'ADMIN',{'/reports':[{id:'pending',status:'SUBMITTED'},{id:'approved',status:'APPROVED'}],'/inventory':[{id:'low',name:'Ricambio basso',quantity:0,minimumThreshold:1},{id:'full',name:'Ricambio disponibile',quantity:10,minimumThreshold:1}]});
 await page.locator('[data-widget-view="reports"]').click();await expect(page.locator('#reportStatusFilter')).toHaveValue('SUBMITTED');await expect(page.locator('[data-report-id="pending"]')).toBeVisible();await expect(page.locator('[data-report-id="approved"]')).toHaveCount(0);
 await page.locator('#brandHome').click();await page.locator('[data-widget-view="inventory"]').click();await expect(page.locator('#inventoryStockFilter')).toHaveValue('LOW');await expect(page.locator('#inventoryTableBody')).toContainText('Ricambio basso');await expect(page.locator('#inventoryTableBody')).not.toContainText('Ricambio disponibile');
});

test('refresh preserves loaded asset pages and the server receives search filters',async({page})=>{
 const requests=[];
 await session(page,'ADMIN',{respond(path,method,request){if(path==='/dashboard/antenne/page'){const query=new URL(request.url()).searchParams;requests.push({cursor:query.get('cursor'),q:query.get('q')});return {items:[{id:query.get('cursor')?'b':'a',name:query.get('cursor')?'Ponte Beta':'Ponte Alfa',status:'ATTIVA'}],nextCursor:query.get('cursor')?null:'a'};}}});
 await page.locator('[data-view="antennas"]').first().click();await page.locator('#antennaLoadMore').click();await expect(page.locator('#antennasTableBody tr')).toHaveCount(2);
 await page.locator('#reloadAntennasBtn').click();await expect(page.locator('#antennasTableBody tr')).toHaveCount(2);
 await page.locator('#antennaSearch').fill('Alfa');await expect.poll(()=>requests.some(query=>query.q==='Alfa')).toBe(true);
});

test('calendar keyboard navigation protects an unsaved note',async({page})=>{
 await session(page);await page.locator('#calendarNote').fill('Nota non ancora salvata');const selected=await page.locator('#calendarDay').inputValue();
 await page.locator('.calendar-day').first().focus();await page.keyboard.press('ArrowRight');
 const dialog=page.getByRole('dialog',{name:'Conferma operazione'});await expect(dialog).toContainText('nota non salvata');await page.keyboard.press('Escape');
 await expect(page.locator('#calendarNote')).toHaveValue('Nota non ancora salvata');await expect(page.locator('#calendarDay')).toHaveValue(selected);
});

test('asset validation announces field errors and keeps the form available for retry',async({page})=>{
 await session(page);await page.route('**/api/v1/dashboard/antenne',route=>route.request().method()==='POST'?route.fulfill({status:400,json:{message:'Dati non validi',fieldErrors:{name:'Inserisci un nome completo'},requestId:'qa-validation',retryable:false}}):route.fallback());
 await page.locator('[data-view="antennas"]').first().click();await page.locator('#newAntennaBtn').click();await page.locator('#antennaName').fill('A');await page.locator('#antennaLat').fill('41');await page.locator('#antennaLng').fill('12');await page.locator('#saveAntenna').click();
 await expect(page.locator('#antennaName')).toHaveAttribute('aria-invalid','true');await expect(page.locator('#antennaForm [role="alert"]')).toHaveText('Inserisci un nome completo');await expect(page.locator('#antennaModal')).toBeVisible();
});

test('report refresh preserves collapsed rows and open intervention details',async({page})=>{
 await session(page,'ADMIN',{'/reports':[{id:'keep',status:'SUBMITTED'},{id:'details',status:'APPROVED'}]});await page.locator('[data-view="reports"]').first().click();
 await page.locator('[data-report-id="keep"] [data-report-toggle]').click();await page.locator('[data-report-id="details"] .report-details summary').click();
 await page.locator('#reportsRefreshBtn').click();await expect(page.locator('[data-report-id="keep"]')).toHaveClass(/report-collapsed/);await expect(page.locator('[data-report-id="details"] .report-details')).toHaveAttribute('open','');
});

test('report removal requires a reason and a keyboard user can cancel at 200 percent zoom',async({page})=>{
 await session(page,'ADMIN',{'/reports':[{id:'reason',status:'SUBMITTED'}]});await page.locator('[data-view="reports"]').first().click();await page.locator('[data-report-delete="reason"]').click();
 const dialog=page.getByRole('dialog',{name:'Elimina report'});await dialog.getByRole('button',{name:'Conferma eliminazione'}).click();await expect(dialog.getByRole('alert')).toContainText('motivazione');
 await page.setViewportSize({width:640,height:900});await page.evaluate(()=>document.documentElement.style.zoom='2');await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.keyboard.press('Escape');await expect(dialog).toHaveCount(0);await expect(page.locator('[data-report-delete="reason"]')).toBeFocused();
});
