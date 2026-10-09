import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { session } from './fixtures/control-room-session.js';

test('login identifies proxy and CORS rejection instead of claiming bad credentials',async({page})=>{
  await page.route('**/api/v1/auth/public-config',route=>route.fulfill({json:{enabled:false}}));
  await page.route('**/api/v1/auth/login',route=>route.fulfill({status:403,contentType:'text/plain',body:'Invalid CORS request'}));
  await page.goto('/');await page.locator('#loginEmail').fill('capo@radiotech.it');await page.locator('#loginPassword').fill('fixture-password');await page.locator('#loginButton').click();
  await expect(page.locator('#loginStatus')).toContainText('configurazione del link HTTPS');await expect(page.locator('#loginStatus')).not.toContainText('Credenziali non valide');
  await page.route('**/api/v1/auth/login',route=>route.fulfill({status:503,contentType:'text/plain',body:'Unavailable'}));
  await page.locator('#loginButton').click();await expect(page.locator('#loginStatus')).toContainText('backend e il tunnel');
});

test('glass surfaces align cards and brand returns home without losing the session', async ({page})=>{
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.setViewportSize({width:1440,height:1000});
  await session(page,'ADMIN',{'/inventory':[{id:'short',name:'Connettore',quantity:4,minimumThreshold:1},{id:'long',name:'Ricambio tecnico con una descrizione più lunga '.repeat(4),sku:'RF-2',quantity:10,minimumThreshold:1}]});
  const boxes=await page.locator('.stats .stat').evaluateAll(nodes=>nodes.map(n=>{const r=n.getBoundingClientRect();return {width:r.width,height:r.height};}));
  expect(boxes).toHaveLength(6);expect(Math.abs(boxes[0].width-boxes[1].width)).toBeLessThan(1);expect(Math.abs(boxes[0].height-boxes[1].height)).toBeLessThan(1);
  await expect(page.locator('.stats .stat').first()).toHaveCSS('backdrop-filter',/blur/);
  const desktopAlignment = await page.locator('.stats .stat').evaluateAll(nodes=>nodes.map(n=>({
    label:n.querySelector('.stat-top')?.getBoundingClientRect().top,
    value:n.querySelector('.stat-value')?.getBoundingClientRect().top,
    footer:n.querySelector('.subtle')?.getBoundingClientRect().bottom
  })));
  expect(new Set(desktopAlignment.map(x=>Math.round(x.label)))).toHaveProperty('size',1);
  expect(new Set(desktopAlignment.map(x=>Math.round(x.value)))).toHaveProperty('size',1);
  expect(new Set(desktopAlignment.map(x=>Math.round(x.footer)))).toHaveProperty('size',1);
  await page.locator('[data-view="inventory"]').click();
  await page.locator('#brandHome').click();await expect(page.locator('#view-dashboard')).toHaveClass(/active/);
  expect(await page.evaluate(()=>sessionStorage.getItem('radiotech_control_token'))).toBe('test-token');
  const panels=await page.locator('#view-dashboard .grid-2 > .panel').evaluateAll(nodes=>nodes.map(n=>{const r=n.getBoundingClientRect();return {width:r.width,height:r.height};}));
  expect(panels[0].width).toBeGreaterThan(panels[1].width);expect(panels.every(panel=>panel.height>0)).toBe(true);
  await expect(page.locator('.footer')).toContainText('RadioTech');
  expect(await page.evaluate(()=>getComputedStyle(document.body,'::before').animationName)).toBe('none');
  await page.emulateMedia({reducedMotion:'no-preference'});
  expect(await page.evaluate(()=>getComputedStyle(document.body,'::before').animationName)).toBe('radiotech-aurora');
  await page.setViewportSize({width:390,height:844});await page.locator('#mobileMenu').click();await page.locator('[data-view="inventory"]').click();
  await page.locator('#mobileMenu').click();await page.locator('#brandHome').focus();await page.keyboard.press('Enter');
  await expect(page.locator('#view-dashboard')).toHaveClass(/active/);await expect(page.locator('#mobileMenu')).toHaveAttribute('aria-expanded','false');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.locator('#workspaceActionsBtn').click();
  await expect(page.locator('#quickAntennaBtn')).toBeVisible();
  await expect(page.locator('#quickNotificationBtn')).toBeVisible();
});

test('Enterprise operational pages keep consistent spacing and notification composer layout',async({page})=>{
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.setViewportSize({width:1440,height:960});
  await session(page,'ADMIN');
  for(const view of ['antennas','operations','notifications','tools','inventory','profile','system']){
    await page.locator('[data-view="'+view+'"]').first().click();
    const active=page.locator('#view-'+view);
    await expect(active).toHaveClass(/active/);
    await expect(active).toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  }
  await page.locator('[data-view="notifications"]').first().click();
  const workspace=page.locator('#view-notifications .notification-workspace');
  await expect(workspace).toBeVisible();
  const panels=await workspace.locator(':scope > .panel').evaluateAll(nodes=>nodes.map(n=>({top:n.getBoundingClientRect().top,width:n.getBoundingClientRect().width})));
  expect(panels).toHaveLength(2);
  expect(Math.abs(panels[0].top-panels[1].top)).toBeLessThan(2);
  expect(panels[0].width).toBeGreaterThan(panels[1].width);
  const fields=await page.locator('#view-notifications .notification-compose-body > .field').evaluateAll(nodes=>nodes.map(n=>({top:n.getBoundingClientRect().top,bottom:n.getBoundingClientRect().bottom})));
  expect(fields).toHaveLength(3);
  for(let i=1;i<fields.length;i++) expect(fields[i].top-fields[i-1].bottom).toBeGreaterThanOrEqual(16);
  for(const width of [768,390,320]){
    await page.setViewportSize({width,height:850});
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await expect(page.locator('#notificationTarget')).toBeVisible();
    await expect(page.locator('#sendNotificationBtn')).toBeVisible();
  }
});

test('Signal customer portal preserves login, requests and logout on small screens', async ({page}) => {
  const errors=[],records=[],calls=[];page.on('pageerror',e=>errors.push(e.message));
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname,method=route.request().method();let data;
    if(path==='/api/v1/auth/public-config')data={enabled:false};
    if(path==='/api/v1/auth/login')data={role:'CUSTOMER',token:'portal-test-token',refreshToken:'portal-test-refresh'};
    if(path==='/api/v1/pro/catalog')data=[{id:'requests',title:'Richieste di assistenza',writable:true,fields:[{key:'name',label:'Oggetto',type:'text',required:true},{key:'notes',label:'Descrizione',type:'textarea'}],actions:[]}];
    if(path==='/api/v1/pro/requests') {
      if(method==='POST'){const body=route.request().postDataJSON();calls.push(body);records.push({id:'request-test',...body.fields,status:'OPEN',version:1});data=records.at(-1);}
      else data={records,partial:false};
    }
    await route.fulfill({status:data===undefined?404:200,contentType:'application/json',body:JSON.stringify(data??{})});
  });
  await page.setViewportSize({width:1440,height:1000});await page.goto('/portal');await page.evaluate(()=>document.fonts.ready);
  await expect(page.locator('body')).toHaveCSS('font-family',/Inter/);
  await page.screenshot({path:'.dist/signal-portal-login-desktop.png',fullPage:true});
  await page.setViewportSize({width:320,height:800});
  await page.locator('[name="email"]').fill('cliente@example.test');await page.locator('[name="password"]').fill('test-password');await page.locator('#portalLogin button').click();
  await expect(page.locator('#proTitle')).toHaveText('Richieste di assistenza');await expect(page.locator('.portal-login')).toBeHidden();
  await page.locator('#proNew').click();await page.locator('#proField-name').fill('Verifica collegamento');await page.locator('#proField-notes').fill('Descrizione della richiesta del cliente.');
  await page.locator('#proForm [type="submit"]').click();await expect(page.locator('#proRecords')).toContainText('Verifica collegamento');
  await page.locator('#portalBrandHome').click();await expect(page.locator('#portalApp')).toBeVisible();await expect(page.locator('.footer')).toContainText('RadioTech');
  expect(calls).toHaveLength(1);expect(calls[0].operationId).toBeTruthy();
  for(const width of [320,768,1440]){
    await page.setViewportSize({width,height:1000});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    const outside=await page.locator('input,select,textarea,button,.pro-record').evaluateAll(nodes=>nodes.filter(n=>n.getClientRects().length).some(n=>{const r=n.getBoundingClientRect();return r.left<0||r.right>innerWidth+1;}));expect(outside).toBe(false);
    if(width!==768)await page.screenshot({path:`.dist/signal-portal-${width}.png`,fullPage:true});
  }
  await page.locator('#portalLogout').click();await expect(page.locator('#portalLogin')).toBeVisible();await expect(page.locator('#portalApp')).toBeHidden();
  expect(errors).toEqual([]);
});

test('Pro and access workspaces contain long content and fields at every breakpoint', async ({page}) => {
  test.setTimeout(60000);
  await page.emulateMedia({reducedMotion:'reduce'});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const fields=[{key:'name',label:'Ragione sociale',type:'text',required:true},{key:'email',label:'Email',type:'email'},{key:'notes',label:'Note',type:'textarea'}];
  await session(page,'ADMIN',{respond(path){
    if(path==='/pro/catalog')return [{id:'clients',title:'Clienti e contatti',writable:true,fields,actions:['ARCHIVE']}];
    if(path==='/pro/clients')return {records:Array.from({length:4},(_,i)=>({id:`c${i}`,name:i?'Cliente aziendale': 'Nome molto lungo '.repeat(10),status:'ACTIVE',version:1,email:'cliente@example.test',notes:'Riferimento'.repeat(35)})),partial:false};
    if(path==='/pro/security')return {revocationChecked:true,providerActivation:'Configurazione del provider aziendale',mfaActivation:'Configurazione MFA richiesta'};
  }});
  expect(await page.locator('.content').evaluate(container=>{const footer=container.querySelector(':scope > .footer');return [...container.querySelectorAll(':scope > .view')].every(view=>Boolean(view.compareDocumentPosition(footer)&Node.DOCUMENT_POSITION_FOLLOWING));})).toBe(true);
  await page.locator('#toastStack').evaluate(node=>node.replaceChildren());
  async function open(view){if(await page.locator('#mobileMenu').isVisible())await page.locator('#mobileMenu').click();await page.locator(`[data-view="${view}"]`).click();}
  async function contained(selector){
    const problems=await page.locator(selector).evaluateAll(nodes=>nodes.filter(n=>n.getClientRects().length).flatMap(n=>{const r=n.getBoundingClientRect();return r.left<0||r.right>innerWidth+1? [n.id||n.className]:[]}));
    expect(problems).toEqual([]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  }
  for(const width of [1440,1024,768,390,320]){
    await page.setViewportSize({width,height:1000});await open('pro');
    await expect(page.locator('.pro-record')).toHaveCount(4);await page.locator('#proNew').click();
    await expect(page.locator('#proField-notes')).toBeVisible();
    await contained('#view-pro input,#view-pro select,#view-pro textarea,#view-pro .btn,#view-pro .pro-record');
    const overlap=await page.locator('#proForm .pro-field').evaluateAll(nodes=>nodes.some((n,i)=>nodes.slice(i+1).some(m=>{const a=n.getBoundingClientRect(),b=m.getBoundingClientRect();return a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top;})));
    expect(overlap).toBe(false);
    if(width===1440||width===390)await page.screenshot({path:`.dist/layout-pro-${width}.png`,fullPage:true});
    await page.locator('#proCancel').click();await page.locator('#proSearch').fill('inesistente');await expect(page.locator('#proRecords')).toContainText('Nessun risultato');await page.locator('#proSearch').clear();
    await open('access');await expect(page.locator('#accessStatus')).toContainText('Controllo delle sessioni attivo');
    await contained('#view-access input,#view-access select,#view-access textarea,#view-access .btn,#view-access .panel');
    const checkbox=await page.locator('#accessMfa').boundingBox();expect(checkbox.width).toBeLessThanOrEqual(22);expect(checkbox.height).toBeLessThanOrEqual(22);
    await page.locator('#accessRole').selectOption('OPERATOR');await expect(page.locator('#accessCustomer')).toBeHidden();
    await page.locator('#accessRole').selectOption('CUSTOMER');await expect(page.locator('#accessCustomer')).toBeVisible();
    if(width===1440||width===390)await page.screenshot({path:`.dist/layout-access-${width}.png`,fullPage:true});
  }
  expect(errors).toEqual([]);
});

test('access form keeps account binding and revocation functional after redesign', async ({page}) => {
  const calls=[];
  await session(page,'ADMIN',{respond(path,method,request){
    if(path==='/pro/security')return {revocationChecked:true};
    if(path.startsWith('/pro/security/accounts/')){calls.push({path,method,body:request.postDataJSON()});return {success:true};}
  }});
  await page.locator('[data-view="access"]').click();await page.locator('#accessUid').fill('account-test');
  await page.locator('#accessRole').selectOption('OPERATOR');await page.locator('#accessMfa').check();
  await page.locator('#accessPermissions').fill('clients:READ, purchases:READ');
  await page.locator('#accessForm [type="submit"]').click();await expect(page.locator('#accessStatus')).toContainText('Accesso assegnato');
  expect(calls[0]).toMatchObject({method:'PUT',body:{role:'OPERATOR',mfaRequired:true,proPermissions:['clients:READ','purchases:READ']}});
  page.once('dialog',dialog=>dialog.accept());await page.locator('#accessRevoke').click();await expect(page.locator('#accessStatus')).toHaveText('Sessioni revocate.');
  expect(calls[1].path).toBe('/pro/security/accounts/account-test/revoke');
});

test('private local report attachments download with authentication and preserve bytes', async ({page}) => {
  const id='a'.repeat(64);let authorization;
  await session(page,'ADMIN',{'/reports':[{id:'r-file',taskId:'t1',operatorId:'o1',status:'SUBMITTED',attachments:[`radiotech-file:${id}`]}],respond(path,method,request){
    if(path===`/files/${id}`){authorization=request.headers().authorization;return{name:'photo-test.txt',base64:Buffer.from('private file').toString('base64')};}
  }});
  await page.locator('[data-view="reports"]').click();await page.locator('#reportsList details summary').click();
  const downloaded=page.waitForEvent('download');await page.locator('[data-file-download]').click();const file=await downloaded;
  expect(file.suggestedFilename()).toBe('photo-test.txt');expect((await readFile(await file.path())).toString()).toBe('private file');expect(authorization).toMatch(/^Bearer /);
});

test('Pro forms retry with the same operation ID, escape content and fit mobile', async ({page}) => {
  const records=[], operations=[];let first=true;
  await session(page,'ADMIN',{respond:(path,method,request)=>{
    if(path==='/pro/catalog')return[{id:'clients',title:'Clienti e contatti',writable:true,fields:[{key:'name',label:'Ragione sociale',type:'text',required:true}],actions:['ARCHIVE']}];
    if(path==='/pro/clients'&&method==='GET')return{records,partial:false};
    if(path==='/pro/clients'&&method==='POST'){const body=request.postDataJSON();operations.push(body.operationId);records.push({id:'c1',...body.fields,status:'ACTIVE',version:1});return records[0];}
  }});
  await page.route('**/api/v1/pro/clients',async route=>{if(route.request().method()==='POST'&&first){first=false;operations.push(route.request().postDataJSON().operationId);await route.fulfill({status:503,contentType:'application/json',body:'{"message":"Riprova"}'});}else await route.fallback();});
  await page.locator('[data-view="pro"]').click();await page.locator('#proNew').click();await page.locator('#proField-name').fill('<img src=x onerror=alert(1)>');
  await page.locator('#proForm [type="submit"]').click();
  await expect(page.locator('#proFormStatus')).toContainText('Riprova');
  await page.locator('#proForm [type="submit"]').click();
  await expect(page.locator('#proForm')).toBeHidden();
  expect(operations).toHaveLength(2);expect(operations[0]).toBe(operations[1]);
  await page.locator('#proRefresh').click();
  await page.setViewportSize({width:320,height:780});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await expect(page.locator('#proRecords img')).toHaveCount(0);
  await expect(page.locator('#proRecords')).toContainText('<img src=x onerror=alert(1)>');
});

test('long report lists remain visible when scroll animations are enabled', async ({ page }) => {
  await page.emulateMedia({reducedMotion:'no-preference'});
  await session(page,'ADMIN',{'/reports':Array.from({length:300},(_,index) => ({id:`r-${index}`,taskId:`t-${index}`,operatorId:'o1',status:'SUBMITTED',operatorNotes:'Documentazione del lavoro sul campo'}))});
  await page.locator('[data-view="reports"]').click();
  await expect(page.locator('#reportsList .task-row')).toHaveCount(50);
  for (let count = 100; count <= 300; count += 50) {
    await page.locator('#reportsMoreBtn').click();
    await expect(page.locator('#reportsList .task-row')).toHaveCount(count);
  }
  await expect(page.locator('#reportsList').locator('..')).toHaveCSS('opacity','1');
  await expect(page.locator('#reportsList').locator('..')).not.toHaveClass(/motion-pending/);
});

test('task assignment validates input and prevents duplicate clicks while saving', async ({ page }) => {
  const tasks = []; let creates = 0;
  await session(page,'ADMIN',{antennas:[{id:'a1',name:'Sede Bari',status:'ATTIVA',lat:41.1,lng:16.8}], '/tasks':{data:tasks}, async respond(path,method,request) {
    if(path === '/tasks' && method === 'POST') { ++creates; await new Promise(resolve => setTimeout(resolve,300)); const task = {...request.postDataJSON(),id:'t-new'}; tasks.push(task); return {data:task}; }
  }});
  await page.locator('[data-view="operations"]').click();
  await page.locator('#createTaskBtn').click(); await expect(page.locator('#toastStack')).toContainText('Incarico incompleto');
  expect(creates).toBe(0);
  await page.locator('#taskTitle').fill('Verifica impianto'); await page.locator('#taskOperator').selectOption('o1'); await page.locator('#taskAntenna').selectOption('a1');
  await page.locator('#createTaskBtn').dblclick();
  await expect(page.locator('#tasksList')).toContainText('Verifica impianto'); expect(creates).toBe(1);
  await expect(page.locator('#createTaskBtn')).toBeEnabled();
  await page.locator('[data-view="planning"]').click(); await expect(page.locator('#planningList')).toContainText('Verifica impianto');
});

test('inventory creates, edits, moves, archives and restores an item on a narrow viewport', async ({ page }) => {
  await page.setViewportSize({width:390,height:844});
  const items = [], movements = [];
  await session(page,'ADMIN',{'/inventory':items,'/inventory/movements':movements, respond(path,method,request) {
    if (path === '/inventory' && method === 'POST') { const item = {...request.postDataJSON(),id:'s1',active:true}; items.push(item); return item; }
    if (path === '/inventory/s1' && method === 'PUT') { Object.assign(items[0],request.postDataJSON()); return items[0]; }
    if (path === '/inventory/s1' && method === 'DELETE') { items[0].active = false; return items[0]; }
    if (path === '/inventory/s1/restore') { items[0].active = true; return items[0]; }
    if (path === '/inventory/s1/movements' && method === 'POST') {
      const input = request.postDataJSON(), previousQuantity = items[0].quantity;
      items[0].quantity += input.type === 'RECEIPT' ? input.quantity : -input.quantity;
      const movement = {...input,id:'m1',name:items[0].name,previousQuantity,resultingQuantity:items[0].quantity}; movements.push(movement); return movement;
    }
  }});
  await page.locator('#mobileMenu').click(); await page.locator('[data-view="inventory"]').click();
  await page.locator('#newInventoryItemBtn').click();
  await page.locator('#inventorySku').fill('RF-001'); await page.locator('#inventoryName').fill('Connettore RF');
  await page.locator('#inventoryInitialQuantity').fill('5'); await page.locator('#inventoryMinimum').fill('10');
  await page.locator('#saveInventoryItem').click(); await expect(page.locator('#inventoryTableBody')).toContainText('Connettore RF');
  await page.locator('[data-inventory-edit="s1"]').click(); await page.locator('#inventorySupplier').fill('Fornitore Bari'); await page.locator('#saveInventoryItem').click();
  await expect(page.locator('#inventoryTableBody')).toContainText('Fornitore Bari');
  await page.locator('[data-inventory-move="s1"]').click(); await page.locator('#inventoryMovementQuantity').fill('3');
  await page.locator('#inventoryMovementNote').fill('Carico da fornitore'); await page.locator('#inventoryMovementForm [type="submit"]').click();
  await expect(page.locator('#inventoryMovementModal')).toBeHidden(); expect(items[0].quantity).toBe(8);
  await page.locator('#inventoryMovementsBtn').click(); await expect(page.locator('#inventoryMovementsBody')).toContainText('5 → 8');
  await page.locator('#inventoryMovementsHistoryModal .modal-foot button').click();
  page.once('dialog',dialog => dialog.accept()); await page.locator('[data-inventory-archive="s1"]').click();
  await expect(page.locator('[data-inventory-move="s1"]')).toHaveCount(0);
  await page.locator('#inventoryStockFilter').selectOption('ARCHIVED'); await page.locator('[data-inventory-restore="s1"]').click();
  await page.locator('#inventoryStockFilter').selectOption('ACTIVE'); await expect(page.locator('[data-inventory-move="s1"]')).toBeVisible();
  expect(items[0].active).toBe(true);
});

test('agenda filters real deadlines, exports the selection and opens operator workload', async ({ page }) => {
  const now = new Date(); now.setHours(23,59,0,0);
  await session(page, 'ADMIN', { '/tasks': {data:[
    {id:'t1',title:'Verifica oggi',operatorId:'o1',status:'ASSIGNED',priority:'CRITICAL',dueAt:now.toISOString()},
    {id:'t2',title:'Intervento arretrato',operatorId:'o1',status:'IN_PROGRESS',dueAt:'2020-01-01T00:00:00Z'},
    {id:'t3',title:'Da pianificare',operatorId:'o1',status:'ASSIGNED'},
    {id:'t4',title:'Già approvato',operatorId:'o1',status:'APPROVED',dueAt:'2020-01-01T00:00:00Z'},
    {id:'t5',title:'Report inviato',operatorId:'o1',status:'REPORT_SUBMITTED',dueAt:'2020-01-01T00:00:00Z'},
    {id:'t6',title:'Ancora da assegnare',status:'CREATED'},
  ]} });
  await page.locator('[data-view="planning"]').click();
  await expect(page.locator('#planningStatus')).toContainText('aggiornati');
  await expect(page.locator('#planningList .task-row')).toHaveCount(4);
  await page.locator('#planningPeriod').selectOption('TODAY');
  await expect(page.locator('#planningList')).toContainText('Verifica oggi');
  await expect(page.locator('#planningList .task-row')).toHaveCount(1);
  await page.locator('#planningPeriod').selectOption('OVERDUE');
  await expect(page.locator('#planningList')).toContainText('Intervento arretrato');
  const download = page.waitForEvent('download'); await page.locator('#planningExport').click();
  const file = await download; const content = await readFile(await file.path(),'utf8');
  expect(content).toContain('Intervento arretrato'); expect(content).not.toContain('Già approvato');
  await page.locator('[data-view="workload"]').click();
  await expect(page.locator('#workloadList')).toContainText('3 attivi');
  await expect(page.locator('#workloadList')).toContainText('1 scaduti');
  await page.locator('[data-workload-operator=""]').click();
  await expect(page.locator('#planningOperator')).toHaveValue('@unassigned');
  await expect(page.locator('#planningList .task-row')).toHaveCount(1);
  await expect(page.locator('#planningList')).toContainText('Ancora da assegnare');
  await page.locator('[data-view="workload"]').click();
  await expect(page.locator('#workloadStatus')).toContainText('aggiornati');
  await page.locator('[data-workload-operator="o1"]').click();
  await expect(page.locator('#planningOperator')).toHaveValue('o1');
  await expect(page.locator('#planningList .task-row')).toHaveCount(3);
  await page.locator('#planningAssign').click();
  await expect(page.locator('#taskTitle')).toBeFocused();
});

test('procurement excludes archived stock, distinguishes missing costs and escapes CSV formulas', async ({ page }) => {
  await session(page, 'ADMIN', {'/inventory':[
    {id:'s1',name:'=SUM(1;2)',sku:'RF',supplier:'Fornitore RF',quantity:2,minimumThreshold:10,reorderQuantity:20,unitCost:3,active:true},
    {id:'s2',name:'Batteria',quantity:0,minimumThreshold:5},
    {id:'s3',name:'Scorta sufficiente',quantity:100,minimumThreshold:5},
    {id:'s4',name:'Articolo archiviato',quantity:0,minimumThreshold:5,active:false},
  ]});
  await page.locator('[data-view="supplies"]').click();
  await expect(page.locator('#suppliesList .task-row')).toHaveCount(2);
  await expect(page.locator('#suppliesMetrics')).toContainText('60,00');
  await expect(page.locator('#suppliesList')).toContainText('Costo non disponibile');
  await page.locator('#suppliesSearch').fill('Fornitore RF');
  await expect(page.locator('#suppliesList .task-row')).toHaveCount(1);
  const download = page.waitForEvent('download'); await page.locator('#suppliesExport').click();
  const file = await download; const content = await readFile(await file.path(),'utf8');
  expect(content).toContain("'=SUM(1;2)"); expect(content).not.toContain('Batteria');
  await page.locator('#suppliesInventory').click(); await expect(page.locator('#view-inventory')).toBeVisible();
});

test('report review filters and approval update the dedicated review center', async ({ page }) => {
  await page.route('https://firebasestorage.googleapis.com/example.pdf', async route => route.fulfill({contentType:'application/pdf',body:await readFile(new URL('./fixtures/professional-report.pdf',import.meta.url))}));
  const reports = [{id:'r1',taskId:'t1',operatorId:'o1',operatorNotes:'Verifica RF',status:'SUBMITTED',workPerformed:'Allineamento ponte radio',measurements:{ROS:1.2},attachments:['javascript:alert(1)','https://firebasestorage.googleapis.com/example.pdf']},{id:'r2',operatorNotes:'Sostituzione batteria',status:'REJECTED'}];
  await session(page,'ADMIN',{ '/reports':reports, respond(path, method, request) {
    if (path === '/reports/r1/approve' && method === 'POST') { reports[0].status = 'APPROVED'; return reports[0]; }
  }});
  await page.locator('[data-view="reports"]').click();
  await expect(page.locator('#reportsList .task-row')).toHaveCount(2);
  await page.locator('#reportStatusFilter').selectOption('SUBMITTED');
  await expect(page.locator('#reportsList .task-row:visible')).toHaveCount(1);
  await page.locator('#reportsList .task-row:visible summary').click();
  await expect(page.locator('#reportsList .task-row:visible')).toContainText('Allineamento ponte radio');
  await expect(page.locator('#reportsList .task-row:visible')).toContainText('ROS: 1.2');
  await expect(page.locator('#reportsList [data-report-file]')).toHaveCount(1);
  await expect(page.locator('#reportsList [data-report-file]')).toHaveAttribute('data-report-file','https://firebasestorage.googleapis.com/example.pdf');
  const approved = page.waitForRequest(r => r.url().endsWith('/reports/r1/approve'));
  await page.locator('[data-report-approve="r1"]').click();
  expect((await approved).postDataJSON().note).toContain('approvato');
  await expect(page.locator('#reportFilterEmpty')).toBeVisible();
  await page.locator('#reportStatusFilter').selectOption('APPROVED');
  await expect(page.locator('#reportsList .task-row:visible')).toHaveCount(1);
  await expect(page.locator('[data-report-approve]')).toHaveCount(0);
  await page.locator('#reportSearch').fill('batteria');
  await expect(page.locator('#reportFilterEmpty')).toBeVisible();
});

test('legacy report review maps actual stock, requires unresolved choices and cancels without approving', async ({page}) => {
  const errors=[], approvals=[];
  page.on('pageerror',error=>errors.push(error.message));
  const materials=[{name:'Connettore N',quantity:1},{name:'Cavo RG-213',quantity:1},{name:'Fusibile 5A',quantity:2}];
  const inventory=[{id:'connector',name:'Connettore N',sku:'RF-001',quantity:37},{id:'cable',name:'Cavo coassiale',sku:'RF-002',quantity:3}];
  const report={id:'legacy',status:'APPROVAL_PENDING',materialsUsed:materials};
  await session(page,'ADMIN',{'/reports':[report],respond(path,method,request){
    if(path==='/reports/legacy/review-context')return {materials,inventory,mappings:{},locked:false};
    if(path==='/reports/legacy/approve'){approvals.push(request.postDataJSON());report.status='APPROVED';return report;}
  }});
  await page.locator('[data-view="reports"]').click();
  await page.locator('#reportsList summary').click();
  const approve=page.locator('[data-report-approve="legacy"]');
  await approve.click();
  const dialog=page.locator('dialog.report-material-review');
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('#reviewMaterial-0')).toHaveValue('connector');
  await expect(dialog.locator('#reviewMaterial-1')).toHaveValue('');
  await dialog.locator('[type="submit"]').click();
  expect(approvals).toHaveLength(0);
  await dialog.locator('[data-cancel]').click();
  await expect(dialog).toHaveCount(0);
  await expect(approve).toBeEnabled();
  await approve.click();
  await page.setViewportSize({width:320,height:800});
  await expect(dialog).toBeVisible();
  expect(await dialog.evaluate(node=>node.getBoundingClientRect().width<=innerWidth)).toBe(true);
  await dialog.locator('#reviewMaterial-1').selectOption('cable');
  await dialog.locator('#reviewMaterial-2').selectOption('@external');
  await dialog.locator('[type="submit"]').click();
  await expect(dialog).toHaveCount(0);
  await expect.poll(()=>approvals.length).toBe(1);
  expect(approvals[0].materialMappings).toEqual({'0':'connector','1':'cable','2':'@external'});
  expect(materials[1].name).toBe('Cavo RG-213');
  expect(errors).toEqual([]);
});

test('interrupted legacy approval preserves consumed mappings and escapes material names', async ({page}) => {
  const materials=[{name:'<img src=x onerror=alert(1)>',quantity:1}], approvals=[];
  const report={id:'locked',status:'APPROVAL_PENDING',materialsUsed:materials};
  await session(page,'ADMIN',{'/reports':[report],respond(path,method,request){
    if(path==='/reports/locked/review-context')return {materials,inventory:[{id:'real',name:'Connettore N',sku:'RF-001',quantity:36}],mappings:{'0':'real'},locked:true};
    if(path==='/reports/locked/approve'){approvals.push(request.postDataJSON());report.status='APPROVED';return report;}
  }});
  await page.locator('[data-view="reports"]').click();await page.locator('#reportsList summary').click();
  await page.locator('[data-report-approve="locked"]').click();
  const dialog=page.locator('dialog.report-material-review');
  await expect(dialog.locator('select')).toBeDisabled();await expect(dialog.locator('select')).toHaveValue('real');
  await expect(dialog.locator('label')).toContainText('<img');await expect(dialog.locator('img')).toHaveCount(0);
  await dialog.locator('[type="submit"]').click();await expect.poll(()=>approvals.length).toBe(1);
  expect(approvals[0]).not.toHaveProperty('materialMappings');
});

test('planning denies mutation for viewers and clears failed or expired data', async ({ page }) => {
  await session(page,'VIEWER');
  await page.locator('[data-view="planning"]').click();
  await expect(page.locator('#planningList')).toContainText('Verifica trasmettitore');
  await expect(page.locator('#planningAssign')).toBeHidden();
  await page.route('**/api/v1/tasks',route => route.fulfill({status:503,contentType:'application/json',body:'{"message":"Rete temporaneamente non disponibile"}'}));
  await page.locator('#planningRefresh').click();
  await expect(page.locator('#planningStatus')).toContainText('non disponibili');
  await expect(page.locator('#planningList')).toBeEmpty();
  await expect(page.locator('#planningExport')).toBeDisabled();
  await page.locator('#workspaceAccountBtn').click();await page.locator('#topbarLogoutBtn').click();
  await expect(page.locator('#planningMetrics')).toBeEmpty();
  await expect(page.locator('#workloadList')).toBeEmpty();
});

test('all workspaces contain their content at narrow, tablet and desktop widths', async ({ page }) => {
  test.setTimeout(180000);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const failures = [];
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await session(page, 'ADMIN', {
    '/tasks': { data: [
      { id:'t1', title:'Intervento urgente sul collegamento radio della sede operativa', description:'Verificare il trasmettitore e documentare tutte le misure', operatorId:'o1', antennaId:'a1', status:'ASSIGNED', priority:'HIGH', dueAt:'2020-01-01T08:00:00Z' },
      { id:'t2', title:'Ispezione senza scadenza', operatorId:'o1', status:'IN_PROGRESS' },
    ] },
    '/reports': [{ id:'r1', taskId:'t1', operatorId:'o1', status:'SUBMITTED', operatorNotes:'Misure e documentazione tecnica del collegamento radio' }],
    '/inventory': [{id:'s1', name:'Connettore coassiale per manutenzione', sku:'SKU'.repeat(30), quantity:1, minimumThreshold:10, reorderQuantity:20, supplier:'Fornitore tecnico della rete', location:'Deposito principale', unitCost:12.5}],
    '/dashboard/notifications': [{ title:'Comunicazione operativa alla squadra', message:'Documento'.repeat(25), createdAt:'2026-10-05T10:00:00Z' }],
  });
  const views = await page.locator('.nav [data-view]').evaluateAll(nodes => nodes.map(n => n.dataset.view));
  for (const width of [320, 390, 768, 1024, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    for (const view of views) {
      if (width <= 980) await page.locator('#mobileMenu').click();
      await page.locator(`.nav [data-view="${view}"]`).click();
      await expect(page.locator(`#view-${view}`)).toBeVisible();
      const problems = await page.evaluate(() => {
        const result = [];
        if (document.documentElement.scrollWidth > innerWidth + 1) result.push('page overflow');
        const groups = document.querySelectorAll('.view.active, .view.active .grid-2, .view.active .grid-equal, .view.active .enterprise-grid, .view.active .form-grid, .view.active .stats-grid, .view.active .management-filters, .topbar, .top-actions');
        for (const group of groups) {
          const boxes = [...group.children].filter(n => n.checkVisibility()).map(n => ({ name: n.id || n.className, r: n.getBoundingClientRect() }));
          for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
            const a = boxes[i], b = boxes[j];
            if (Math.min(a.r.right,b.r.right) - Math.max(a.r.left,b.r.left) > 1 && Math.min(a.r.bottom,b.r.bottom) - Math.max(a.r.top,b.r.top) > 1) result.push(`${a.name} overlaps ${b.name}`);
          }
        }
        for (const n of document.querySelectorAll('.view.active input, .view.active select, .view.active textarea, .view.active .panel, .topbar button')) {
          if (!n.checkVisibility() || n.closest('.table-wrap')) continue;
          const r = n.getBoundingClientRect();
          if (r.left < -1 || r.right > innerWidth + 1) result.push(`${n.id || n.className} outside viewport`);
        }
        return result;
      });
      failures.push(...problems.map(problem => `${width}px ${view}: ${problem}`));
      if ([320, 1280].includes(width) && ['inventory','planning','operators','reports'].includes(view)) await page.screenshot({path:`build/reports/web/final-${view}-${width}.png`, fullPage:true});
    }
  }
  expect(errors).toEqual([]);
  expect(failures).toEqual([]);
});

test('agenda counts actual pending reports and opens interrupted approvals beyond the first archive page',async({page})=>{
 const reports=[...Array.from({length:55},(_,i)=>({id:`done-${i}`,status:'APPROVED'})),{id:'waiting',status:'SUBMITTED'},{id:'interrupted',status:'APPROVAL_PENDING'}];
 await session(page,'ADMIN',{'/reports':reports,'/tasks':{data:[{id:'stale-task',status:'REPORT_SUBMITTED'}]}});
 await page.locator('.nav [data-view="planning"]').click();
 await expect(page.locator('#planningMetrics .stat-card').last().locator('.stat-value')).toHaveText('2');
 await expect(page.locator('#widgetPendingReports')).toHaveText('2');
 await page.locator('#planningReview').click();
 await expect(page.locator('#view-reports')).toBeVisible();
 await expect(page.locator('#reportStatusFilter')).toHaveValue('SUBMITTED');
 await expect(page.locator('#reportsList .report-row')).toHaveCount(2);
 await expect(page.getByRole('button',{name:'Riprendi approvazione',exact:true})).toBeVisible();
 await expect(page.locator('[data-report-reject="interrupted"]')).toHaveCount(0);
 await page.locator('#reportClearFilters').click();
 await expect(page.locator('#reportsList .report-row')).toHaveCount(50);
});

test('weather normalizes saved legacy coordinates and refresh recovers after an outage',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('rt-weather-position',JSON.stringify({lat:'41.9',lng:'12.5',label:'Roma salvata'})));
 let fail=true; const requests=[];
 await session(page,'ADMIN',{respond(path,method,request){if(path==='/weather'){requests.push(new URL(request.url()).searchParams.get('latitude'));return{temperature_2m:22,weather_code:0,daily:{}};}}});
 await page.route('**/api/v1/weather?*',route=>fail?route.fulfill({status:503,json:{message:'Temporaneamente non disponibile'}}):route.fallback());
 await page.locator('#refreshWeather').click();
 await expect(page.locator('#weatherCondition')).toHaveText('Meteo non disponibile');
 fail=false;await page.locator('#refreshWeather').click();
 await expect(page.locator('#liveWeather')).toContainText('Roma salvata · 22 °C');
 expect(requests).toContain('41.9');
});

test('home counters include records beyond the first loaded archive page',async({page})=>{
 await session(page,'ADMIN',{
   '/tasks':{data:Array.from({length:120},(_,i)=>({id:`task-${i}`,status:'ASSIGNED'}))},
   '/reports':Array.from({length:90},(_,i)=>({id:`report-${i}`,status:'SUBMITTED'})),
   '/dashboard/stats':{operators:9,antennas:2,tasks:120,activeTasks:120,pendingReports:90,taskStats:{overdue:35},lowStock:3},
 });
 await expect(page.locator('#statOperators')).toHaveText('9');
 await expect(page.locator('#widgetActiveTasks')).toHaveText('120');
 await expect(page.locator('#widgetPendingReports')).toHaveText('90');
 await expect(page.locator('#widgetOverdueTasks')).toHaveText('35');
});


test('all assignments remain visible beyond fifty and an active task can be cancelled', async ({page}) => {
  const tasks = Array.from({length: 55}, (_, index) => ({id: `task-${index}`, title: `Intervento ${index}`, operatorId: 'o1', status: 'IN_PROGRESS'}));
  const changes = [];
  await session(page, 'ADMIN', {respond(path, method, request) {
    if (path === '/tasks' && method === 'GET') return {data: tasks};
    if (path === '/tasks/task-54/status' && method === 'PATCH') {
      changes.push(request.postDataJSON()); tasks[54].status = 'CANCELLED'; return tasks[54];
    }
  }});
  await page.locator('[data-view="operations"]').click();
  await expect(page.locator('#tasksList .task-row')).toHaveCount(50);
  await page.locator('#tasksMoreBtn').click();
  await expect(page.locator('#tasksList .task-row')).toHaveCount(55);
  page.once('dialog', dialog => dialog.accept());
  await page.locator('[data-task-cancel="task-54"]').click();
  await expect(page.locator('[data-task-cancel="task-54"]')).toHaveCount(0);
  await page.locator('#tasksMoreBtn').click();
  expect(changes).toEqual([{status: 'CANCELLED'}]);
  await expect(page.locator('#tasksList .task-row').last()).toContainText('Annullato');
});

test('a viewer cannot cancel an active assignment', async ({page}) => {
  await session(page, 'VIEWER');
  await page.locator('[data-view="operations"]').click();
  await expect(page.locator('[data-task-cancel="t1"]')).toBeHidden();
});

test('system monitoring exposes missing backups and unknown cloud values', async ({page}) => {
  await session(page,'ADMIN');
  await page.locator('[data-view="system"]').click();
  await expect(page.locator('#operationsHealth')).toContainText('Da configurare');
  await expect(page.locator('#operationsHealth')).toContainText('Letture di oggi: Non disponibile');
  await expect(page.locator('#operationsHealth')).toContainText('Backup esterno da configurare');
  await page.locator('#operationsHealthRefresh').click();
  await expect(page.locator('#operationsHealthRefresh')).toBeEnabled();
});

test('a failed report page preserves already loaded reports', async ({page}) => {
  await session(page,'ADMIN',{'/reports':Array.from({length:60},(_,i)=>({id:`report-${i}`,status:'APPROVED',taskTitle:`Rapporto ${i}`}))});
  await page.locator('[data-view="reports"]').click();
  await expect(page.locator('#reportsList .task-row')).toHaveCount(50);
  await page.route('**/api/v1/reports/page?*', route => new URL(route.request().url()).searchParams.has('cursor') ? route.fulfill({status:503,json:{message:'Temporaneamente non disponibile'}}) : route.fallback());
  await page.locator('#reportsMoreBtn').click();
  await expect(page.locator('#toastStack')).toContainText('Caricamento non riuscito');
  await expect(page.locator('#reportsList .task-row')).toHaveCount(50);
  await expect(page.locator('#reportsMoreBtn')).toBeEnabled();
});

test('root serves the login and protects unauthenticated API requests', async ({ page, request }) => {
  const response = await page.goto('/');
  expect(response.status()).toBe(200);
  expect(response.headers()['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(response.headers()['cache-control']).toContain('no-store');
  await expect(page.locator('#loginEmail')).toBeVisible();
  expect((await request.get('/api/v1/tasks')).status()).toBe(401);
});

test('wrapped task responses render and enterprise workflows call real route contracts', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await session(page);
  await page.locator('[data-view="operations"]').click();
  await expect(page.locator('#tasksList')).toContainText('Verifica trasmettitore');
  await page.locator('[data-view="enterprise"]').click();
  await expect(page.locator('#enterpriseOpen')).toHaveText('1');
  await expect(page.locator('#enterpriseOverdue')).toHaveText('1');
  await expect(page.locator('#enterpriseAlerts img')).toHaveCount(0);
  await expect(page.locator('#enterpriseAlerts')).toContainText('<img src=x onerror=alert(1)>');
  await page.locator('[data-incident-next="i1"]').click();
  await expect(page.locator('#enterpriseIncidents')).toContainText('Preso in carico');
  await page.locator('#enterpriseOperator').selectOption('o1');
  await expect(page.locator('#enterpriseSkills')).toContainText('RF Test');
  expect(errors).toEqual([]);
  await page.screenshot({ path: 'build/reports/web/enterprise-desktop.png', fullPage: true });
  await page.locator('#workspaceAccountBtn').click();await page.locator('#topbarLogoutBtn').click();
  await expect(page.locator('#loginEmail')).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('radiotech_control_token'))).toBeNull();
});

test('mobile navigation and risk workspace fit the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await session(page);
  await page.locator('#mobileMenu').click();
  await page.locator('[data-view="enterprise"]').click();
  await expect(page.locator('#enterpriseOpen')).toHaveText('1');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'build/reports/web/enterprise-mobile.png', fullPage: true });
});

test('viewer sees risks without manager mutation controls', async ({ page }) => {
  await session(page, 'VIEWER');
  await page.locator('[data-view="enterprise"]').click();
  await expect(page.locator('#enterpriseStatus')).toContainText('aggiornati');
  await expect(page.locator('#enterpriseEvaluate')).toBeHidden();
  await expect(page.locator('#enterpriseIncidentForm')).toBeHidden();
  await expect(page.locator('#enterpriseSkillForm')).toBeHidden();
  await expect(page.locator('#enterpriseOpen')).toHaveText('—');
  await expect(page.locator('#enterpriseIncidents')).toContainText('riservato ai responsabili');
  await expect(page.locator('#enterpriseAlerts')).toContainText('Da leggere');
  await page.locator('[data-view="operators"]').click();
  await expect(page.locator('[data-badge="o1"]')).toBeHidden();
  await expect(page.locator('#newOperatorBtn')).toBeHidden();
});

test('manager profile updates and badge retrieval use dedicated endpoints', async ({ page }) => {
  await session(page);
  await page.locator('[data-view="profile"]').click();
  await page.locator('#editProfileBtn').click();
  await page.locator('#editProfileName').fill('Direzione RadioTech');
  await page.locator('#editProfileCompany').fill('RadioTech Enterprise');
  const saved = page.waitForRequest(request => request.url().endsWith('/api/v1/capo/profile') && request.method() === 'PUT');
  await page.locator('#profileForm button[type="submit"]').click();
  expect((await saved).postDataJSON()).toEqual({ fullName: 'Direzione RadioTech', phone: '', company: 'RadioTech Enterprise' });
  await expect(page.locator('#profileName')).toHaveText('Direzione RadioTech');
  await page.locator('[data-view="operators"]').click();
  await page.locator('[data-badge="o1"]').click();
  await expect(page.locator('#badgeToken')).toHaveText('badge-only-token');
  await expect(page.locator('#badgeModal')).toBeVisible();
  await page.locator('#closeBadgeModal').click();
  page.once('dialog', dialog => dialog.accept());
  await page.locator('[data-regenerate="o1"]').click();
  await expect(page.locator('#badgeModal')).toBeVisible();
  await expect(page.locator('#badgeToken')).toHaveText('fresh-badge-token');
});

test('antenna RF measurements survive creation and subsequent editing', async ({ page }) => {
  await session(page);
  await page.locator('[data-view="antennas"]').click();
  await page.locator('#newAntennaBtn').click();
  await page.locator('#antennaName').fill('Ponte radio Bari');
  await page.locator('#antennaLat').fill('41.12');
  await page.locator('#antennaLng').fill('16.87');
  await page.locator('#antennaFreq').fill('5800');
  await page.locator('#antennaPower').fill('20');
  await page.locator('#antennaRos').fill('1.2');
  await page.locator('#antennaTemp').fill('35');
  const created = page.waitForRequest(request => request.url().endsWith('/api/v1/dashboard/antenne') && request.method() === 'POST');
  await page.locator('#saveAntenna').click();
  expect((await created).postDataJSON().specs).toEqual({ frequencyMHz: 5800, powerWatts: 20, ros: 1.2, temperature: 35 });
  await expect(page.locator('#antennasTableBody')).toContainText('5800');
  await page.locator('[data-edit="a-new"]').click();
  await expect(page.locator('#antennaFreq')).toHaveValue('5800');
  await page.locator('#antennaPower').fill('25');
  const edited = page.waitForRequest(request => request.url().endsWith('/api/v1/dashboard/antenne/a-new') && request.method() === 'PUT');
  await page.locator('#saveAntenna').click();
  expect((await edited).postDataJSON().specs).toEqual({ frequenza: 5800, potenza: 25, ros: 1.2, temperatura: 35 });
  await expect(page.locator('#antennasTableBody')).toContainText('25');
});

test('AI offers evidence, private chat context and safe output rendering', async ({ page }) => {
  await session(page);
  await page.locator('[data-view="ai"]').click();
  await expect(page.locator('#aiRiskCount')).toHaveText('1');
  await expect(page.locator('#aiRisks')).toContainText('Storico insufficiente');
  await page.locator('#aiMessage').fill('Prepara un briefing');
  const sent = page.waitForRequest(request => request.url().endsWith('/api/v1/ai/chat'));
  await page.locator('#aiSend').click();
  expect((await sent).postDataJSON().includeOperationalContext).toBe(false);
  await expect(page.locator('#aiConversation')).toContainText('Risposta dal modello');
  await expect(page.locator('#aiConversation img')).toHaveCount(0);
  await page.locator('.sidebar [data-view="system"]').scrollIntoViewIfNeeded();
  const sidebarFits = await page.evaluate(() => {
    const lastNav = document.querySelector('.sidebar [data-view="system"]').getBoundingClientRect();
    const profile = document.querySelector('.sidebar-bottom').getBoundingClientRect();
    return lastNav.bottom <= profile.top;
  });
  expect(sidebarFits).toBe(true);
  await page.locator('#toastStack').evaluate(node => node.replaceChildren());
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: 'build/reports/web/ai-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'build/reports/web/ai-mobile.png', fullPage: true });
  await page.locator('#workspaceAccountBtn').click();await page.locator('#topbarLogoutBtn').click();
  await expect(page.locator('#aiConversation')).toBeEmpty();
  await expect(page.locator('#aiContext')).not.toBeChecked();
  await expect(page.locator('#aiAssets')).toHaveText('—');
});

test('chat survives token refresh and clears private state on session expiry', async ({ page }) => {
  await session(page);
  await page.evaluate(() => sessionStorage.setItem('radiotech_control_refresh', 'test-refresh'));
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'renewed-token', refreshToken: 'test-refresh' }) }));
  let requests = 0;
  await page.route('**/api/v1/ai/chat', route => {
    requests++;
    return route.fulfill({ status: requests === 1 ? 401 : 200, contentType: 'application/json', body: JSON.stringify(requests === 1 ? { message: 'expired' } : { answer: 'Risposta dopo rinnovo', notice: 'Verificare.' }) });
  });
  await page.locator('[data-view="ai"]').click();
  await expect(page.locator('#aiSend')).toBeEnabled();
  await page.locator('#aiMessage').fill('Contesto riservato della sessione');
  await page.locator('#aiSend').click();
  await expect(page.locator('#aiConversation')).toContainText('Risposta dopo rinnovo');
  expect(requests).toBe(2);
  expect(await page.evaluate(() => sessionStorage.getItem('radiotech_control_token'))).toBe('renewed-token');
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 401, contentType: 'application/json', body: '{"message":"expired"}' }));
  await page.route('**/api/v1/ai/chat', route => route.fulfill({ status: 401, contentType: 'application/json', body: '{"message":"expired"}' }));
  await page.locator('#aiMessage').fill('Altra richiesta');
  await page.locator('#aiSend').click();
  await expect(page.locator('#loginEmail')).toBeVisible();
  await expect(page.locator('#aiConversation')).toBeEmpty();
  await expect(page.locator('#aiMessage')).toBeEmpty();
});

test('shift changes and safety signals are reviewable on a small viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await session(page);
  await page.locator('#mobileMenu').click();
  await page.locator('[data-view="people"]').click();
  await expect(page.locator('#shiftSummary')).toContainText('Fuori turno');
  await page.locator('[data-shift-action="START"]').click();
  await expect(page.locator('#shiftSummary')).toContainText('Al lavoro');
  await page.locator('#signalDescription').fill('Cavo esposto vicino alla postazione');
  await page.locator('#peopleSignalForm button[type="submit"]').click();
  await expect(page.locator('#peopleSignals')).toContainText('Cavo esposto');
  await page.locator('[data-review-signal="s1"]').click();
  await expect(page.locator('#peopleSignals')).toContainText('In carico');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.locator('#toastStack').evaluate(node => node.replaceChildren());
  await page.evaluate(() => { document.activeElement?.blur(); window.scrollTo(0, 0); });
  await page.screenshot({ path: 'build/reports/web/safety-mobile.png', fullPage: true });
});

test('new sign-in design loads local typography and fits a small screen', async ({ page }) => {
  const errors = [];
  page.on('pageerror',error => errors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await page.evaluate(() => document.fonts.ready);
  await expect(page.locator('.login-shell')).toHaveCSS('opacity','1');
  await expect(page.locator('.login-copy h1')).toContainText('Il controllo è tuo.');
  expect((await page.request.get('/assets/brand/radiotech-symbol-v1.png')).status()).toBe(200);
  expect(await page.locator('.login-visual .brand-mark img').evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
  expect(await page.evaluate(() => document.fonts.check('500 13px Inter'))).toBe(true);
  await page.screenshot({ path: 'build/reports/web/redesign-login-desktop.png', fullPage: true });
  await page.locator('#togglePassword').click();
  await expect(page.locator('#loginPassword')).toHaveAttribute('type','text');
  await page.setViewportSize({ width: 320, height: 568 });
  await page.locator('#loginScreen').evaluate(node => { node.scrollTop = 0; });
  await expect(page.locator('.login-mobile-brand')).toBeVisible();
  await expect(page.locator('#loginButton')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'build/reports/web/redesign-login-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
});

test('overview shortcuts, scroll reveals and reduced motion remain usable', async ({ page }) => {
  const errors = [];
  page.on('pageerror',error => errors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await session(page);
  await expect(page.locator('.overview-head')).toHaveClass(/motion-visible/);
  await page.locator('#toastStack').evaluate(node => node.replaceChildren());
  await page.screenshot({ path: 'build/reports/web/redesign-overview-desktop.png', fullPage: true });
  await page.locator('[data-workspace-link="ai"]').click();
  await expect(page.locator('#view-ai')).toHaveClass(/active/);
  await expect(page.locator('#aiRiskCount')).toHaveText('1');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior)).toBe('auto');
  await expect(page.locator('#view-ai .panel').first()).toHaveCSS('opacity','1');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('#mobileMenu').click();
  await page.locator('[data-view="dashboard"]').click();
  await page.screenshot({ path: 'build/reports/web/redesign-overview-mobile.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(errors).toEqual([]);
});

test('mobile menu and dialogs support keyboard navigation and focus return', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await session(page);
  await page.locator('#mobileMenu').click();
  await expect(page.locator('#mobileMenu')).toHaveAttribute('aria-expanded','true');
  await expect(page.locator('#mainContent')).toHaveAttribute('inert','');
  await page.locator('#sidebarClose').focus();
  await page.keyboard.press('Shift+Tab');
  await expect(page.locator('[data-view="system"]')).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.locator('#mobileMenu')).toHaveAttribute('aria-expanded','false');
  await expect(page.locator('#mobileMenu')).toBeFocused();
  await page.locator('#mobileMenu').click();
  await page.locator('[data-view="antennas"]').click();
  await page.locator('#newAntennaBtn').click();
  await expect(page.locator('#antennaModal .modal')).toHaveAttribute('role','dialog');
  await expect(page.locator('#antennaName')).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.locator('#antennaModal')).toBeHidden();
  await expect(page.locator('#newAntennaBtn')).toBeFocused();
});

test('Aurora feedback respects reduced motion and the Pro editor fits every viewport', async ({page}) => {
  await page.emulateMedia({reducedMotion:'no-preference'});
  await session(page,'ADMIN',{respond(path) {
    if(path==='/pro/catalog')return [{id:'clients',title:'Clienti e contatti',writable:true,fields:[{key:'name',label:'Ragione sociale',type:'text',required:true}],actions:['ARCHIVE']}];
    if(path==='/pro/clients')return {records:[{id:'c1',name:'Cliente con ragione sociale molto lunga '.repeat(5),status:'ACTIVE',version:1}],partial:false};
  }});
  await page.locator('[data-view="pro"]').click();
  await page.locator('#proNew').click();
  await expect(page.locator('#proNew')).toHaveClass(/press-glow/);
  expect(await page.evaluate(()=>getComputedStyle(document.body,'::before').animationName)).toBe('radiotech-aurora');
  for(const width of [320,768,1440]) {
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    const bounds=await page.locator('#proForm').boundingBox();
    expect(bounds.x).toBeGreaterThanOrEqual(0);expect(bounds.x+bounds.width).toBeLessThanOrEqual(width);
  }
  await page.screenshot({path:'.dist/aurora-web-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await expect(page.locator('#sidebar')).toHaveCSS('visibility','hidden');
  await page.screenshot({path:'.dist/aurora-web-phone.png',fullPage:true});
  await page.emulateMedia({reducedMotion:'reduce'});
  expect(await page.evaluate(()=>getComputedStyle(document.body,'::before').animationName)).toBe('none');
});


test('report attachments show image and PDF previews, enlargement and explicit downloads', async ({page}) => {
  const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  const pdf=await readFile(new URL('./fixtures/professional-report.pdf',import.meta.url));
  const png=await readFile('src/main/resources/static/assets/brand/radiotech-symbol-v1.png');
  const ids=['a','b','c','d'].map(c=>c.repeat(64)); let retry=false;
  await session(page,'ADMIN',{'/reports':[{id:'report-preview',antennaId:'a1',operatorName:'Operatore di collaudo',submittedAt:'2026-10-06T16:30:25Z',status:'SUBMITTED',attachments:ids.map(id=>'radiotech-file:'+id)}],antennas:[{id:'a1',name:'Ponte Sanarica'}],respond(path){
    if(path===`/files/${ids[0]}`)return {name:'Fotografia impianto.png',base64:png.toString('base64')};
    if(path===`/files/${ids[1]}`)return {name:'Rapporto tecnico.pdf',base64:pdf.toString('base64')};
    if(path===`/files/${ids[2]}`)return {name:'test.html',base64:Buffer.from('<script>window.untrustedAttachment=true</script>').toString('base64')};
    if(path===`/files/${ids[3]}` && retry)return {name:'Foto recuperata.png',base64:png.toString('base64')};
  }});
  await page.locator('[data-view="reports"]').click();
  const list=page.locator('#reportsList');
  await expect(list.locator('img').first()).toBeVisible();
  expect(await list.locator('img').first().evaluate(img=>img.complete && img.naturalWidth>0)).toBe(true);
  await list.locator('.report-file').nth(1).scrollIntoViewIfNeeded();
  await expect(list.locator('canvas[data-pdf-rendered="true"]')).toBeVisible();
  await expect(list.locator('.report-pdf-toolbar')).toContainText('Pagina 1 di 2');
  await expect(list.locator('canvas')).toHaveAttribute('aria-label',/Ponte radio Sanarica/);
  await list.locator('[data-file-open]').nth(1).click();
  const pdfDialog=page.locator('.report-file-dialog');
  await expect(pdfDialog.locator('canvas[data-pdf-rendered="true"]')).toBeVisible();
  await pdfDialog.getByRole('button',{name:'Successiva'}).click();
  await expect(pdfDialog).toContainText('Pagina 2 di 2');
  await pdfDialog.getByLabel('Zoom PDF').selectOption('1.5');
  await pdfDialog.getByRole('button',{name:'Chiudi'}).click();
  await list.locator('.report-file').nth(2).scrollIntoViewIfNeeded();
  await expect(list.locator('.report-file').nth(2)).toContainText('Questo formato è disponibile per il download');
  expect(await page.evaluate(()=>window.untrustedAttachment)).toBeUndefined();
  await list.locator('.report-file').nth(3).scrollIntoViewIfNeeded();
  await expect(list.locator('.report-file').nth(3).getByRole('button',{name:'Riprova'})).toBeVisible();
  retry=true; await list.locator('.report-file').nth(3).getByRole('button',{name:'Riprova'}).click();
  await expect(list.locator('.report-file').nth(3).locator('img')).toBeVisible();
  await list.locator('[data-file-open]').first().click(); await expect(page.locator('.report-file-dialog')).toBeVisible();
  await expect(page.locator('.report-file-dialog img')).toBeVisible(); await page.keyboard.press('Escape'); await expect(page.locator('.report-file-dialog')).toHaveCount(0);
  const downloadEvent=page.waitForEvent('download'); await list.locator('[data-file-download]').nth(1).click();
  const download=await downloadEvent; expect(download.suggestedFilename()).toBe('Rapporto tecnico.pdf');
  for (const width of [320,768,1440]) { await page.setViewportSize({width,height:1000}); expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true); }
  await page.setViewportSize({width:1440,height:1000});
  await list.scrollIntoViewIfNeeded();
  await page.screenshot({path:'.dist/report-attachments-desktop.png',fullPage:true});
  await page.locator('#reportsRefreshBtn').click(); await expect(list.locator('img').first()).toBeVisible();
  expect(errors).toEqual([]);
});


test('advanced report filters and collapse retain Italian status and original attachments',async({page})=>{
 await session(page,'ADMIN',{'/reports':[
 {id:'r-new',taskId:'t-new',operatorName:'Operatore Uno',status:'SUBMITTED',submittedAt:'2026-10-07T09:00:00Z',operatorNotes:'Allineamento RF'},
 {id:'r-old',operatorName:'Operatore Due',status:'APPROVED',submittedAt:'2026-09-01T09:00:00Z',operatorNotes:'Collaudo ottico'}]});
 await page.locator('.nav [data-view="reports"]').click();await expect(page.locator('#reportsList')).toContainText('Da revisionare');
 await page.locator('#reportDateFrom').fill('2026-10-01');await page.locator('#reportDateFrom').dispatchEvent('change');await expect(page.locator('.report-row:visible')).toHaveCount(1);
 await page.locator('#reportOperatorFilter').selectOption('Operatore Uno');await page.locator('#reportStatusFilter').selectOption('SUBMITTED');await expect(page.locator('.report-row:visible')).toHaveCount(1);
 await page.locator('#reportCollapseAll').click();await expect(page.locator('.report-row:visible [data-report-toggle]')).toHaveAttribute('aria-expanded','false');await expect(page.locator('.report-row:visible .report-details')).toBeHidden();
 await page.locator('.report-row:visible [data-report-toggle]').click();await expect(page.locator('.report-row:visible .report-details')).toBeVisible();
 await page.locator('#reportClearFilters').click();await expect(page.locator('.report-row:visible')).toHaveCount(2);
});

test('personal calendar saves notes and reminders using local time without overwriting edits',async({page})=>{
 await session(page);let note={day:'2026-10-07',note:'Verifica ponte radio',reminderAt:'2099-10-07T08:00:00Z'};const writes=[];
 await page.route('**/api/v1/calendar**',route=>{if(route.request().method()==='PUT'){const input=route.request().postDataJSON();writes.push(input);note={...input,day:new URL(route.request().url()).pathname.split('/').pop()};return route.fulfill({json:note});}return route.fulfill({json:[note]});});
 await page.locator('#calendarDay').fill('2026-10-07');await page.locator('#calendarDay').dispatchEvent('change');
 await expect(page.locator('#liveDateTime')).not.toBeEmpty();await page.locator('#calendarNote').fill('Nota aggiornata dal responsabile');await page.locator('#calendarReminder').fill('2099-10-07T12:30');
 await page.locator('#calendarNoteForm [type=submit]').click();await expect.poll(()=>writes.length).toBe(1);expect(writes[0].note).toBe('Nota aggiornata dal responsabile');expect(new Date(writes[0].reminderAt).getTime()).toBeGreaterThan(Date.now());await expect(page.locator('#calendarNote')).toHaveValue('Nota aggiornata dal responsabile');
});

test('TelcoTools calculates CIDR, VLSM and RF at narrow widths and exports the session',async({page})=>{
 await session(page);await page.locator('.nav [data-view="tools"]').click();await page.getByRole('tab',{name:'IP / CIDR',exact:true}).click();const ip=page.locator('.telco-card').filter({hasText:'IPv4 / CIDR'});await ip.locator('button[type=submit]').click();await expect(ip.locator('output')).toContainText('192.168.1.0/24');
 const vlsm=page.locator('.telco-card').filter({hasText:'Piano VLSM'});await vlsm.locator('button[type=submit]').click();await expect(vlsm.locator('output')).toContainText('100 host');
 await page.getByRole('tab',{name:'Radio',exact:true}).click();const eirp=page.locator('.telco-card').filter({hasText:'EIRP'});await eirp.locator('button[type=submit]').click();await expect(eirp.locator('output')).toContainText('47 dBm');
 await page.getByRole('tab',{name:'Report',exact:true}).click();await expect(page.locator('#telcoSummary')).toContainText('EIRP: 47 dBm');
 for(const width of [320,390,768,1440]){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();}
});

test('QR validity config and operator deletion call their protected APIs',async({page})=>{
 await session(page);const requests=[];await page.route('**/api/v1/operators/**',route=>{requests.push({path:new URL(route.request().url()).pathname,method:route.request().method(),data:route.request().postDataJSON()});return route.fulfill({json:{message:'Operazione completata'}});});
 await page.locator('.nav [data-view="operators"]').click();const button=page.locator('[data-validity]').first();await button.click();await page.locator('#validityMode').selectOption('UNLIMITED');await page.locator('#validityForm [type=submit]').click();await expect.poll(()=>requests.some(r=>r.path.endsWith('/qr-validity'))).toBeTruthy();expect(requests.find(r=>r.path.endsWith('/qr-validity')).data).toEqual({mode:'UNLIMITED',expiresAt:null});
 page.on('dialog',d=>d.accept());await page.locator('[data-delete-operator]').first().click();await expect.poll(()=>requests.some(r=>r.method==='DELETE')).toBeTruthy();
});


test('report headings use assigned task title operator name and date even with delayed metadata', async ({ page }) => {
  await page.setViewportSize({width:320,height:850});
  await session(page,'ADMIN',{
    '/reports':[{id:'r-heading',taskId:'t-heading',operatorId:'o1',submittedAt:'2026-10-07T10:00:00Z',status:'APPROVED',operatorNotes:'Verifica completata',attachments:[]}],
    '/operators':[{id:'o1',fullName:'Mario Rossi',status:'ATTIVO',role:'OPERATOR'}],
    async respond(path,method) {if(path==='/tasks'&&method==='GET'){await new Promise(resolve=>setTimeout(resolve,450));return {data:[{id:'t-heading',title:'Verifica collegamento <sede>',operatorId:'o1',status:'COMPLETED'}]};}}
  });
  await page.locator('#mobileMenu').click();
  await page.locator('.nav [data-view="reports"]').click();
  const title=page.locator('#reportsList [data-report-id="r-heading"] .task-title');
  await expect(title).toHaveText('Verifica collegamento <sede>');
  await expect(page.locator('#reportsList [data-report-id="r-heading"] .report-summary')).toHaveText('ID r-heading · Mario Rossi · 07/10/2026');
  await expect(title).not.toContainText('r-heading');
  expect(await title.evaluate(node=>node.getBoundingClientRect().right<=innerWidth)).toBeTruthy();
});


test('late calendar refresh preserves an edited note and its reminder',async({page})=>{
 let releaseRead,reads=0;const waiting=new Promise(resolve=>{releaseRead=resolve;});
 await session(page,'ADMIN',{async respond(path,method){if(path==='/calendar'&&method==='GET'){reads++;await waiting;return [{day:'2026-10-07',note:'Nota precedente'}];}}});
 await expect.poll(()=>reads).toBe(1);
 await page.locator('#calendarDay').fill('2026-10-07');await page.locator('#calendarDay').dispatchEvent('change');
 await page.locator('#calendarNote').fill('Bozza da conservare');await page.locator('#calendarReminder').fill('2099-10-07T12:30');
 releaseRead();await page.waitForTimeout(1200);
 await expect(page.locator('#calendarNote')).toHaveValue('Bozza da conservare');await expect(page.locator('#calendarReminder')).toHaveValue('2099-10-07T12:30');
});


test('environment widget searches cities and antennas with five day forecast and contained clock',async({page})=>{
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const forecast={temperature_2m:24,weather_code:0,apparent_temperature:25,relative_humidity_2m:60,wind_speed_10m:12,observedAt:'2026-10-07T10:00:00Z',timezone:'Europe/Rome',daily:{time:['2026-10-07','2026-10-08','2026-10-09','2026-10-10','2026-10-11','2026-10-12'],weather_code:[0,0,3,61,2,95],temperature_2m_min:[15,15,16,14,13,12],temperature_2m_max:[24,25,26,22,21,20],precipitation_probability_max:[0,0,20,80,10,90],sunrise:['2026-10-07T07:00'],sunset:['2026-10-07T18:30'],uv_index_max:[4]}};
 await session(page,'ADMIN',{antennas:[{id:'a1',name:'Ponte Bari',latitude:41.1,longitude:16.8}],'/weather':forecast,'/weather/locations':{results:[{name:'Lecce',admin1:'Puglia',country:'Italia',latitude:40.35,longitude:18.17}]}});
 await expect(page.locator('#liveWeather')).toContainText('Ponte Bari');await expect(page.locator('#liveWeather')).toContainText('24 °C');await expect(page.locator('.weather-day')).toHaveCount(5);await expect(page.locator('#weatherOutlook')).not.toHaveAttribute('open','');await expect(page.locator('#weatherForecast')).toBeHidden();await page.locator('#weatherOutlook summary').click();await expect(page.locator('#weatherForecast')).toBeVisible();
 await expect(page.locator('#liveDateTime')).toHaveText(/\d{1,2} [a-z]+ \d{4}/);await expect(page.locator('#liveTime')).toHaveText(/\d{2}:\d{2}:\d{2}/);
 await expect(page.locator('#weatherExtras')).toContainText('Umidità');await expect(page.locator('#weatherExtras')).toContainText('60 %');expect(await page.locator('#weatherExtras strong').evaluateAll(nodes=>nodes.every(n=>n.clientHeight<20))).toBe(true);
 await page.locator('#weatherPicker summary').click();await page.locator('#weatherSearch').fill('Lecce');await page.locator('#weatherSearchForm button').click();await page.getByRole('button',{name:'Lecce · Puglia · Italia',exact:true}).click();await expect(page.locator('#liveWeather')).toContainText('Lecce');
 expect(await page.evaluate(()=>JSON.parse(localStorage.getItem('rt-weather-position')).latitude)).toBe(40.35);
 await page.locator('#weatherPicker summary').click();await page.locator('#weatherAntennas').click();await page.getByRole('button',{name:'Ponte Bari',exact:true}).click();await expect(page.locator('#liveWeather')).toContainText('Ponte Bari');
 for(const width of [1440,768,390,320]){await page.setViewportSize({width,height:1000});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(await page.locator('.environment-widget input,.weather-day,.environment-details>div,.analog-clock').evaluateAll(nodes=>nodes.filter(n=>n.getClientRects().length).some(n=>{const r=n.getBoundingClientRect();return r.left<0||r.right>innerWidth+1;}))).toBe(false);if(width===1440||width===390){await page.locator('#weatherOutlook').evaluate(n=>n.open=false);await page.locator('.environment-widget').screenshot({path:`build/reports/web/environment-console-${width}.png`});}}
 expect(errors).toEqual([]);
});


test('Raycast footer navigates workspaces preserves session and fits all screen widths',async({page})=>{
 await page.emulateMedia({reducedMotion:'reduce'});const errors=[];page.on('pageerror',e=>errors.push(e.message));await session(page);
 const footer=page.locator('footer.app-footer');await expect(footer).toContainText('Kevin Cagnazzo e Anthony Piccinonno');await expect(page.locator('#footerServiceStatus')).toHaveText('Backend disponibile');
 for(const width of [1440,1024,768,390,320]){await page.setViewportSize({width,height:1000});await footer.scrollIntoViewIfNeeded();expect(await footer.locator('button,.footer-group,.footer-brand,.footer-bottom').evaluateAll(nodes=>nodes.filter(n=>n.getClientRects().length).some(n=>{const r=n.getBoundingClientRect();return r.left<0||r.right>innerWidth+1;}))).toBe(false);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);}
 for(const view of ['reports','planning','inventory','pro','access','system','dashboard']){await footer.locator(`[data-footer-view="${view}"]`).first().click();await expect(page.locator(`#view-${view}`)).toBeVisible();}
 expect(await page.evaluate(()=>sessionStorage.getItem('radiotech_control_token'))).toBe('test-token');await footer.scrollIntoViewIfNeeded();await page.locator('#footerBackTop').click();await expect(page.locator('.topbar')).toBeInViewport();await expect.poll(()=>page.evaluate(()=>document.scrollingElement.scrollTop)).toBe(0);
 await page.locator('#backendStatus').evaluate(n=>n.textContent='ERROR');await expect(page.locator('#footerServiceStatus')).toHaveText('Backend non disponibile');expect(errors).toEqual([]);
 await footer.scrollIntoViewIfNeeded();await page.screenshot({path:'build/reports/web/footer-320.png'});
});


test('compact environment starts closed and operational widgets use actual records',async({page})=>{
 await page.emulateMedia({reducedMotion:'reduce'});
 await session(page,'ADMIN',{'/dashboard/stats':{antennas:0,tasks:2,operators:1,availability:100,lowStock:1,activeTasks:1,pendingReports:1,taskStats:{overdue:1}},'/tasks':{data:[{id:'w1',title:'Verifica',status:'ASSIGNED',dueAt:'2020-01-01T00:00:00Z'},{id:'w2',title:'Concluso',status:'COMPLETED',dueAt:'2020-01-01T00:00:00Z'}]},'/reports':[{id:'wr1',status:'SUBMITTED'},{id:'wr2',status:'APPROVED'}],'/inventory':[{id:'wi1',name:'Ricambio',quantity:2,minimumThreshold:3},{id:'wi2',name:'Riserva',quantity:20,minimumThreshold:3}]});
 await expect(page.locator('#weatherForecast')).toBeHidden();await expect(page.locator('#widgetActiveTasks')).toHaveText('1');await expect(page.locator('#widgetOverdueTasks')).toHaveText('1');await expect(page.locator('#widgetPendingReports')).toHaveText('1');await expect(page.locator('#widgetLowStock')).toHaveText('1');
 for(const width of [1440,768,390,320]){await page.setViewportSize({width,height:900});const widget=page.locator('.environment-widget');await widget.scrollIntoViewIfNeeded();expect(await widget.evaluate(n=>n.scrollWidth<=n.clientWidth+1)).toBe(true);expect(await widget.locator('button,input,.environment-details>div,.environment-operations>button').evaluateAll(nodes=>nodes.filter(n=>n.getClientRects().length).some(n=>{const r=n.getBoundingClientRect();return r.left<0||r.right>innerWidth+1;}))).toBe(false);}
 await page.locator('#weatherOutlook summary').click();await expect(page.locator('#weatherForecast')).toBeVisible();await page.locator('#weatherOutlook summary').click();await expect(page.locator('#weatherForecast')).toBeHidden();
 await page.locator('[data-widget-view="reports"]').click();await expect(page.locator('#view-reports')).toBeVisible();await page.locator('footer [data-footer-view="dashboard"]').click();await expect(page.locator('#weatherForecast')).toBeHidden();
 for(const motion of ['reduce','no-preference']){await page.emulateMedia({reducedMotion:motion});await page.locator('footer').scrollIntoViewIfNeeded();expect(await page.evaluate(()=>document.scrollingElement.scrollTop)).toBeGreaterThan(200);await page.locator('#footerBackTop').click();await expect.poll(()=>page.evaluate(()=>document.scrollingElement.scrollTop),{timeout:8000}).toBe(0);}
 await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:'build/reports/web/weather-compact-desktop.png'});
 await page.setViewportSize({width:390,height:1000});await page.screenshot({path:'build/reports/web/weather-compact-mobile.png'});
});
