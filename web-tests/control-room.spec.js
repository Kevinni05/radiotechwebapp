import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test('private local report attachments download with authentication and preserve bytes', async ({page}) => {
  const id='a'.repeat(64);let authorization;
  await session(page,'ADMIN',{'/reports':[{id:'r-file',taskId:'t1',operatorId:'o1',status:'SUBMITTED',attachments:[`radiotech-file:${id}`]}],respond(path,method,request){
    if(path===`/files/${id}`){authorization=request.headers().authorization;return{name:'photo-test.txt',base64:Buffer.from('private file').toString('base64')};}
  }});
  await page.locator('[data-view="reports"]').click();await page.locator('#reportsList details summary').click();
  const downloaded=page.waitForEvent('download');await page.locator('[data-local-file]').click();const file=await downloaded;
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
  await expect(page.locator('#reportsList .task-row')).toHaveCount(300);
  await expect(page.locator('#reportsList').locator('..')).toHaveCSS('opacity','1');
  await expect(page.locator('#reportsList').locator('..')).not.toHaveClass(/motion-pending/);
});

test('task assignment validates input and prevents duplicate clicks while saving', async ({ page }) => {
  const tasks = []; let creates = 0;
  await session(page,'ADMIN',{antennas:[{id:'a1',name:'Sede Bari',status:'ATTIVA',lat:41.1,lng:16.8}], '/tasks':{data:tasks}, async respond(path,method,request) {
    if(path === '/tasks' && method === 'POST') { ++creates; await new Promise(resolve => setTimeout(resolve,300)); const task = {...request.postDataJSON(),id:'t-new'}; tasks.push(task); return {data:task}; }
  }});
  await page.locator('[data-view="operations"]').click();
  await page.locator('#createTaskBtn').click(); await expect(page.locator('#toastStack')).toContainText('Task incompleto');
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
  await expect(page.locator('#reportsList a')).toHaveCount(1);
  await expect(page.locator('#reportsList a')).toHaveAttribute('rel','noopener noreferrer');
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
  await page.locator('#topbarLogoutBtn').click();
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

async function session(page, role = 'ADMIN', records = {}) {
  await page.addInitScript(role => {
    sessionStorage.setItem('radiotech_control_token', 'test-token');
    sessionStorage.setItem('radiotech_control_user', JSON.stringify({ role, name: 'Responsabile', email: 'admin@example.test', tenantId: 'test-tenant' }));
  }, role);
  const incidents = [{ id: 'i1', title: 'Backhaul indisponibile', severity: 'HIGH', status: 'DETECTED' }];
  const antennas = records.antennas || [];
  let shift = { status: 'OFF_DUTY', readiness: 'READY', version: 0, workMinutes: 0, restMinutes: 0 };
  const signals = [];
  let badgeToken = 'badge-only-token';
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    let data;
    const method = route.request().method();
    if (method === 'GET' && ['/dashboard/antenne', '/dashboard/notifications', '/dashboard/audit', '/reports', '/inventory', '/inventory/movements'].includes(path)) data = [];
    if (path === '/dashboard/antenne' && method === 'GET') data = antennas;
    if (path === '/dashboard/antenne' && method === 'POST') {
      const input = JSON.parse(route.request().postData());
      const item = { ...input, id: 'a-new', specs: { frequenza: input.specs.frequencyMHz, potenza: input.specs.powerWatts, ros: input.specs.ros, temperatura: input.specs.temperature } };
      antennas.push(item); data = item;
    }
    if (path === '/dashboard/antenne/a-new' && method === 'PUT') {
      Object.assign(antennas[0], JSON.parse(route.request().postData())); data = antennas[0];
    }
    if (path === '/dashboard/stats') data = { antennas: 1, tasks: 1, operators: 1, availability: 100 };
    if (path === '/auth/verify') data = { success: true, user: { role } };
    if (path === '/dashboard/profile' && method === 'GET') data = { fullName: 'Responsabile', company: 'RadioTech', role };
    if (path === '/capo/profile' && method === 'PUT') data = { success: true, data: JSON.parse(route.request().postData()) };
    if (path === '/tasks') data = { success: true, data: [{ id: 't1', title: 'Verifica trasmettitore', operatorId: 'o1', antennaId: 'a1', status: 'ASSIGNED', dueAt: '2020-01-01T00:00:00Z' }] };
    if (path === '/operators') data = { success: true, data: [{ id: 'o1', fullName: 'Tecnico Bari', status: 'ATTIVO', role: 'OPERATOR' }] };
    if (path === '/operators/o1/badge' && method === 'GET') data = { success: true, data: { qrCodeToken: badgeToken, imageDataUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jMZkAAAAASUVORK5CYII=' } };
    if (path === '/operators/o1/regenerate-qr' && method === 'POST') {
      badgeToken = 'fresh-badge-token';
      data = {success: true, data: {qrCodeToken: badgeToken}};
    }
    if (path === '/alerts') data = [{ id: 'alert1', descrizione: '<img src=x onerror=alert(1)>', priorita: 'CRITICA', letto: false }];
    if (path === '/incidents') data = incidents;
    if (path === '/incidents/i1/transitions') { incidents[0].status = JSON.parse(route.request().postData()).status; data = incidents[0]; }
    if (path === '/health' || path === '/health/firebase') data = { status: 'UP' };
    if (path === '/operators/o1/skills') data = [{ skill: 'RF', level: 4, certification: 'RF Test', expiration: '2030-01-01', authorized: true }];
    if (path === '/ai/status') data = { enabled: true, model: 'test-local' };
    if (path === '/ai/insights') data = { scope: 'TENANT', assetsObserved: 1, highRiskAssets: 1, activeTasks: 1, overdueTasks: 1, notice: 'Indicatori, non probabilità di guasto.', partial: false, risks: [{ assetId: 'a1', name: 'Ponte Bari', score: 70, level: 'HIGH', dataQuality: 'LIMITED', reasons: ['Asset offline'], recommendation: 'Verificare con il responsabile.', estimatedMaintenanceAt: null }] };
    if (path === '/ai/chat' && method === 'POST') data = { answer: '<img src=x onerror=alert(1)> Risposta dal modello', notice: 'Verificare le decisioni.' };
    if (path === '/workforce/me/shift') {
      if (method === 'PUT') { const input = JSON.parse(route.request().postData()); shift = { ...shift, status: input.action === 'START' ? 'ACTIVE' : input.action === 'BREAK' ? 'BREAK' : 'OFF_DUTY', readiness: input.readiness, version: shift.version + 1 }; }
      data = shift;
    }
    if (path === '/workforce/shifts') data = [{ ...shift, name: 'Squadra Bari' }];
    if (path === '/workforce/signals') {
      if (method === 'POST') signals.push({ ...JSON.parse(route.request().postData()), id: 's1', name: 'Tecnico', status: 'OPEN', version: 0, createdAt: '2026-10-03T10:00:00Z' });
      data = method === 'POST' ? signals.at(-1) : signals;
    }
    if (path === '/workforce/signals/s1' && method === 'PATCH') { Object.assign(signals[0], JSON.parse(route.request().postData()), { version: 1 }); data = signals[0]; }
    if (method === 'GET' && records[path]) data = records[path];
    if (records.respond) { const custom = await records.respond(path, method, route.request()); if (custom !== undefined) data = custom; }
    await route.fulfill({ status: data === undefined ? 404 : 200, contentType: 'application/json', body: JSON.stringify(data ?? { message: `Unexpected route: ${method} ${path}` }) });
  });
  await page.route('**/actuator/**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' }));
  await page.goto('/dashboard');
  await expect(page.locator('#app')).toHaveClass(/ready/);
}

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
  await expect(page.locator('#enterpriseIncidents')).toContainText('ACKNOWLEDGED');
  await page.locator('#enterpriseOperator').selectOption('o1');
  await expect(page.locator('#enterpriseSkills')).toContainText('RF Test');
  expect(errors).toEqual([]);
  await page.screenshot({ path: 'build/reports/web/enterprise-desktop.png', fullPage: true });
  await page.locator('#topbarLogoutBtn').click();
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
  await page.locator('#topbarLogoutBtn').click();
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
  await expect(page.locator('.login-copy h1')).toContainText('Una visione superiore.');
  expect((await page.request.get('/assets/brand/radiotech-symbol-v1.png')).status()).toBe(200);
  expect(await page.locator('.login-visual .brand-mark img').evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
  expect(await page.evaluate(() => document.fonts.check('500 13px Manrope'))).toBe(true);
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
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior)).toBe('auto');
  expect(await page.locator('#view-ai .panel').first().evaluate(node => getComputedStyle(node).opacity)).toBe('1');
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
