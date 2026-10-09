import { expect } from '@playwright/test';

export async function session(page, role = 'ADMIN', records = {}) {
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
    const requestedPath = url.pathname.replace('/api/v1', '');
    const paged = ['/tasks/page', '/reports/page'].includes(requestedPath);
    const path = paged ? requestedPath.replace(/\/page$/, '') : requestedPath;
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
    if (path === '/dashboard/stats') data = { antennas: 1, tasks: 1, operators: 1, availability: 100, activeTasks: 1, taskStats:{overdue:1}, pendingReports: (records['/reports'] || []).filter(r=>['SUBMITTED','APPROVAL_PENDING'].includes(r.status)).length };
    if (path === '/reports/count') data = {count: (records['/reports'] || []).filter(r => ['SUBMITTED','APPROVAL_PENDING'].includes(r.status)).length};
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
    if (path === '/operations/health') data = {backend:'FIRESTORE',attachments:{count:9,bytes:1561029},pendingReports:3,backup:{state:'NOT_CONFIGURED'},firestore:{state:'UNAVAILABLE',note:'Metriche Google non disponibili.',quotaDayTimezone:'America/Los_Angeles'},alarms:[{message:'Backup esterno da configurare.'}]};
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
    if (paged && data !== undefined) {
      let items = Array.isArray(data) ? data : data.data;
      if (requestedPath === '/reports/page' && url.searchParams.get('status') && Array.isArray(items)) {
        const status=url.searchParams.get('status');
        items=items.filter(item=>item.status===status || status==='SUBMITTED' && item.status==='APPROVAL_PENDING');
      }
      const start = Number(url.searchParams.get('cursor') || 0), limit = Number(url.searchParams.get('limit') || 50);
      if (Array.isArray(items)) data = {items:items.slice(start,start+limit),nextCursor:items.length>start+limit?String(start+limit):null,hasMore:items.length>start+limit};
    }
    await route.fulfill({ status: data === undefined ? 404 : 200, contentType: 'application/json', body: JSON.stringify(data ?? { message: `Unexpected route: ${method} ${path}` }) });
  });
  await page.route('**/actuator/**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' }));
  await page.goto('/dashboard');
  await expect(page.locator('#app')).toHaveClass(/ready/);
}

