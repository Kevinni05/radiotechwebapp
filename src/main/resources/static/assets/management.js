"use strict";
window.RadioTechManagement = function ({ apiFetch, state, escapeHtml: esc, switchView, loadOperators, loadReports }) {
  const $ = id => document.getElementById(id);
  const closed = new Set(['CLOSED', 'CANCELLED', 'COMPLETED', 'APPROVED']);
  const active = task => !closed.has(task.status) && task.status !== 'REPORT_SUBMITTED';
  const due = task => {
    const date = new Date(task.dueAt || '');
    return Number.isFinite(date.getTime()) ? date : null;
  };
  const overdue = task => active(task) && due(task) && due(task) < new Date();
  const dayKey = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
  const money = value => new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' }).format(value);
  const numeric = value => Number.isFinite(Number(value)) ? Math.max(0, Number(value)) : 0;
  function records(body) {
    const value = body?.data ?? body;
    if (!Array.isArray(value)) throw new Error('Risposta del servizio non valida. Riprova.');
    return value;
  }
  let tasks = [], operators = [], stock = [], requestVersion = 0, pendingReports = 0;
  let planningReady = false, supplyReady = false;
  function createView(id, title, description, body, onOpen) {
    const section = document.createElement('section'); section.id = `view-${id}`; section.className = 'view';
    section.innerHTML = `<div class="section-head"><div><span class="section-kicker">Gestione aziendale</span><h2>${title}</h2><p>${description}</p></div></div>${body}`;
    document.querySelector('.content').insertBefore(section, document.querySelector('.footer'));
    const nav = document.createElement('button'); nav.dataset.view = id; nav.textContent = title;
    nav.addEventListener('click', () => { switchView(id); onOpen?.(); });
    document.querySelector('.nav').append(nav);
    return section;
  }
  const team = createView('operators', 'Operatori', 'Anagrafica della squadra, approvazioni, ruoli e badge di accesso.', '', loadOperators);
  team.append($('operatorsTableBody').closest('.panel'), $('syncOperatorBtn').closest('.panel'));
  const reports = createView('reports', 'Centro report', 'Consulta i resoconti dal campo e approva o rifiuta gli interventi da revisionare.', `
    <div class="management-filters"><div class="field"><label for="reportSearch">Cerca report</label><input id="reportSearch" type="search" placeholder="ID report, incarico, operatore o note"></div><div class="field"><label for="reportStatusFilter">Stato revisione</label><select id="reportStatusFilter"><option value="">Tutti gli stati</option><option value="SUBMITTED">Da revisionare</option><option value="APPROVED">Approvati</option><option value="REJECTED">Rifiutati</option></select></div><div class="field"><label for="reportDateFrom">Dal giorno</label><input type="date" id="reportDateFrom"></div><div class="field"><label for="reportDateTo">Al giorno</label><input type="date" id="reportDateTo"></div><div class="field"><label for="reportOperatorFilter">Operatore</label><select id="reportOperatorFilter"><option value="">Tutti gli operatori</option></select></div></div><div class="toolbar"><button class="btn" id="reportCollapseAll">Comprimi tutti</button><button class="btn" id="reportExpandAll">Espandi tutti</button><button class="btn" id="reportClearFilters">Azzera filtri</button></div>`, loadReports);
  reports.append($('reportsList').closest('.panel'));
  function filterReports() {
    const query = $('reportSearch').value.trim().toLowerCase(), status = $('reportStatusFilter').value;
    $('reportsList').querySelectorAll('.task-row').forEach(row => {
      row.hidden = !row.textContent.toLowerCase().includes(query) || !!status && row.dataset.status !== status && !(status === 'SUBMITTED' && row.dataset.status === 'APPROVAL_PENDING') || !!$('reportOperatorFilter').value && row.dataset.operator !== $('reportOperatorFilter').value || !!$('reportDateFrom').value && (!row.dataset.date || row.dataset.date < $('reportDateFrom').value) || !!$('reportDateTo').value && (!row.dataset.date || row.dataset.date > $('reportDateTo').value);
    });
    let empty = $('reportFilterEmpty');
    if (!empty) { empty = document.createElement('p'); empty.id = 'reportFilterEmpty'; empty.className = 'empty'; empty.textContent = 'Nessun report corrisponde ai filtri.'; reports.append(empty); }
    const hasFilters = ['reportSearch','reportStatusFilter','reportDateFrom','reportDateTo','reportOperatorFilter'].some(id => $(id).value);
    empty.hidden = !hasFilters || !!$('reportsList').querySelector('.task-row:not([hidden])') || $('reportsList').textContent.includes('Report non disponibili:');
  }
  $('reportSearch').addEventListener('input', filterReports); $('reportStatusFilter').addEventListener('change', () => loadReports());
  for (const id of ['reportDateFrom','reportDateTo','reportOperatorFilter']) $(id).addEventListener('change', filterReports);
  $('reportClearFilters').addEventListener('click', () => { for (const id of ['reportSearch','reportStatusFilter','reportDateFrom','reportDateTo','reportOperatorFilter']) $(id).value = ''; loadReports(); });
  function collapseReports(value) { $('reportsList').querySelectorAll('.report-row').forEach(row => { row.classList.toggle('report-collapsed', value); const button = row.querySelector('[data-report-toggle]'); if(button){button.setAttribute('aria-expanded', String(!value));button.textContent=value?'⌄':'⌃';button.setAttribute('aria-label',value?'Espandi report':'Comprimi report');} }); }
  $('reportCollapseAll').addEventListener('click', () => collapseReports(true)); $('reportExpandAll').addEventListener('click', () => collapseReports(false));
  $('reportsList').addEventListener('click', event => { const button = event.target.closest('[data-report-toggle]'); if(!button)return; const row=button.closest('.report-row'); const value=!row.classList.contains('report-collapsed');row.classList.toggle('report-collapsed',value);button.setAttribute('aria-expanded',String(!value));button.textContent=value?'⌄':'⌃';button.setAttribute('aria-label',value?'Espandi report':'Comprimi report'); });
  new MutationObserver(() => { const selected=$('reportOperatorFilter').value; const names=[...new Set([...$('reportsList').querySelectorAll('.report-row')].map(r=>r.dataset.operator).filter(Boolean))].sort(); $('reportOperatorFilter').innerHTML='<option value="">Tutti gli operatori</option>'+names.map(n=>`<option value="${esc(n)}">${esc(n)}</option>`).join(''); $('reportOperatorFilter').value=selected; filterReports(); }).observe($('reportsList'), { childList: true });
  createView('planning', 'Agenda interventi', 'Scadenze degli incarichi, priorità e avanzamento. Le date sono mostrate nel fuso orario del dispositivo.', `
    <div class="toolbar"><button class="btn" id="planningRefresh">Aggiorna agenda</button><button class="btn" id="planningReview">Apri report da revisionare</button><button class="btn" id="planningExport" disabled>Esporta CSV</button><button class="btn primary" id="planningAssign">Assegna incarico</button></div>
    <p id="planningStatus" class="management-status" role="status"></p>
    <div id="planningMetrics" class="stats-grid"></div>
    <div class="panel"><div class="panel-body"><div class="management-filters">
      <div class="field"><label for="planningSearch">Ricerca</label><input id="planningSearch" type="search" placeholder="Titolo, impianto o operatore"></div>
      <div class="field"><label for="planningPeriod">Periodo</label><select id="planningPeriod"><option value="ACTIVE">Tutti gli attivi</option><option value="TODAY">Oggi</option><option value="WEEK">Prossimi 7 giorni</option><option value="OVERDUE">Scaduti</option><option value="UNDATED">Senza scadenza</option><option value="ALL">Tutti, incluso storico</option></select></div>
      <div class="field"><label for="planningOperator">Operatore</label><select id="planningOperator"><option value="">Tutta la squadra</option></select></div>
    </div><div id="planningList" class="management-list"></div></div></div>`, loadPlanning);
  createView('workload', 'Carico della squadra', 'Incarichi attivi, urgenze e scadenze per operatore. Il numero di incarichi non misura ore lavorate o disponibilità.', `
    <div class="toolbar"><button class="btn" id="workloadRefresh">Aggiorna carichi</button></div><p id="workloadStatus" class="management-status" role="status"></p><div id="workloadList" class="management-load"></div>`, loadPlanning);
  createView('supplies', 'Approvvigionamenti', 'Lista di reintegro proposta dal magazzino, con fornitori e costi disponibili. Gli ordini richiedono la normale procedura aziendale.', `
    <div class="toolbar"><button class="btn" id="suppliesRefresh">Aggiorna scorte</button><button class="btn" id="suppliesExport" disabled>Esporta lista CSV</button><button class="btn" id="suppliesInventory">Apri magazzino</button></div>
    <p id="suppliesStatus" class="management-status" role="status"></p><div id="suppliesMetrics" class="stats-grid"></div>
    <div class="panel"><div class="panel-body"><div class="management-filters"><div class="field"><label for="suppliesSearch">Cerca articolo o fornitore</label><input id="suppliesSearch" type="search" placeholder="Nome, SKU, fornitore o posizione"></div></div><div id="suppliesList" class="management-list"></div></div></div>`, loadSupplies);
  function metric(label, value, note) { return `<div class="stat-card"><div class="stat-label">${label}</div><div class="stat-value">${esc(value)}</div><div class="stat-foot">${note}</div></div>`; }
  const operatorName = task => operators.find(o => o.id === task.operatorId)?.fullName || task.operatorName || task.operatorId || 'Non assegnato';
  $('planningReview').addEventListener('click', () => {
    $('reportStatusFilter').value = 'SUBMITTED';
    switchView('reports'); loadReports();
  });
  function selectedTasks() {
    const query = $('planningSearch').value.trim().toLowerCase(), period = $('planningPeriod').value, operator = $('planningOperator').value;
    const today = new Date(); today.setHours(0,0,0,0); const end = new Date(today); end.setDate(end.getDate() + 7);
    return tasks.filter(task => {
      const date = due(task);
      return (!operator || (operator === '@unassigned' ? !task.operatorId : task.operatorId === operator)) && [task.title, task.id, task.antennaId, operatorName(task), task.description].join(' ').toLowerCase().includes(query)
        && (period === 'ALL' || active(task) && (period === 'ACTIVE' || period === 'OVERDUE' && overdue(task) || period === 'UNDATED' && !date || period === 'TODAY' && date && dayKey(date) === dayKey(today) || period === 'WEEK' && date && date >= today && date < end));
    }).sort((a,b) => (due(a)?.getTime() ?? Infinity) - (due(b)?.getTime() ?? Infinity) || String(a.id).localeCompare(String(b.id)));
  }
  function renderPlanning() {
    if (!planningReady) return;
    const current = tasks.filter(active);
    $('planningMetrics').innerHTML = metric('Incarichi attivi', current.length, 'esclusi chiusi e in revisione') + metric('Scaduti', current.filter(overdue).length, 'scadenza superata') + metric('Senza scadenza', current.filter(t => !due(t)).length, 'da pianificare') + metric('In revisione', pendingReports, 'report da verificare');
    let previous = '';
    $('planningList').innerHTML = selectedTasks().map(task => {
      const date = due(task), key = date ? dayKey(date) : 'UNDATED';
      const heading = key !== previous ? `<h3 class="management-day">${date ? esc(date.toLocaleDateString('it-IT', { weekday:'long', day:'numeric', month:'long', year:'numeric' })) : 'Senza scadenza'}</h3>` : '';
      previous = key;
      return `${heading}<article class="task-row"><div><strong>${esc(task.title || task.id)}</strong><div class="task-meta">${esc(operatorName(task))} · ${esc(task.antennaId || 'Nessun impianto')} · ${date ? esc(date.toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'})) : 'Data da definire'}</div><p class="enterprise-summary">${esc(task.description || '')}</p></div><div class="actions"><span class="badge ${overdue(task) ? 'red' : 'blue'}">${overdue(task) ? 'SCADUTO' : esc(window.RadioTechItalian.label(task.status))}</span><span class="badge">${esc(window.RadioTechItalian.label(task.priority || 'MEDIUM'))}</span></div></article>`;
    }).join('') || '<p class="empty">Nessun incarico corrisponde ai filtri.</p>';
    const assignedIds = new Set(operators.map(o => o.id));
    const groups = [...operators.map(o => ({ id:o.id, name:o.fullName || o.email || o.id, status:o.status })), ...[...new Set(current.map(t => t.operatorId || '').filter(id => !assignedIds.has(id)))].map(id => ({id, name:id || 'Non assegnato', status:'Anagrafica da verificare'}))];
    $('workloadList').innerHTML = groups.map(o => {
      const work = current.filter(t => (t.operatorId || '') === o.id);
      return `<article class="panel"><h3>${esc(o.name)}</h3><p class="subtle">${esc(o.status || 'Stato non disponibile')}</p><div class="actions"><span class="badge blue">${work.length} attivi</span><span class="badge red">${work.filter(overdue).length} scaduti</span><span class="badge yellow">${work.filter(t => ['HIGH','CRITICAL'].includes(t.priority)).length} urgenti</span></div><div class="actions"><button class="btn" data-workload-operator="${esc(o.id)}">Vedi incarichi</button></div></article>`;
    }).join('') || '<p class="empty">Nessun operatore o incarico disponibile.</p>';
  }
  async function loadPlanning() {
    const version = ++requestVersion, epoch = state.sessionEpoch || 0;
    planningReady = false; $('planningExport').disabled = true;
    for (const id of ['planningStatus','workloadStatus']) $(id).textContent = 'Caricamento in corso…';
    for (const id of ['planningMetrics','planningList','workloadList']) $(id).replaceChildren();
    $('planningAssign').hidden = !['ADMIN','SUPER_ADMIN','CHIEF_EXECUTIVE','CAPO','NETWORK_MANAGER','ENGINEER'].includes(state.authRole || state.user?.role);
    try {
      const [taskData, operatorData, reportData] = await Promise.all([apiFetch('/api/v1/tasks'), apiFetch('/api/v1/operators'), apiFetch('/api/v1/reports/count?status=SUBMITTED')]);
      if (version !== requestVersion || epoch !== (state.sessionEpoch || 0)) return;
      tasks = records(taskData); operators = records(operatorData);
      pendingReports = (reportData?.data ?? reportData)?.count;
      if (!Number.isInteger(pendingReports) || pendingReports < 0) throw new Error('Conteggio report non disponibile. Riprova.');
      planningReady = true;
      const selected = $('planningOperator').value;
      const names = new Map(operators.map(o => [o.id, o.fullName || o.email || o.id]));
      tasks.forEach(t => { if (t.operatorId && !names.has(t.operatorId)) names.set(t.operatorId,t.operatorName || t.operatorId); });
      if (tasks.some(t => !t.operatorId)) names.set('@unassigned','Non assegnato');
      $('planningOperator').innerHTML = '<option value="">Tutta la squadra</option>' + [...names].map(([id,name]) => `<option value="${esc(id)}">${esc(name)}</option>`).join('');
      $('planningOperator').value = names.has(selected) ? selected : '';
      renderPlanning(); $('planningExport').disabled = false;
      for (const id of ['planningStatus','workloadStatus']) $(id).textContent = `Dati aggiornati alle ${new Date().toLocaleTimeString('it-IT')}.`;
    } catch(e) { if (version === requestVersion && epoch === (state.sessionEpoch || 0)) for (const id of ['planningStatus','workloadStatus']) $(id).textContent = `Dati non disponibili: ${e.message}`; }
  }
  function proposals() {
    return stock.filter(i => i.active !== false).map(i => {
      const quantity = numeric(i.quantity ?? i.quantitaDisponibile ?? i.stock), threshold = numeric(i.minimumThreshold ?? i.threshold ?? i.soglia ?? i.sogliaMinima);
      const count = Math.max(numeric(i.reorderQuantity), threshold - quantity);
      const cost = i.unitCost != null && i.unitCost !== '' && Number.isFinite(Number(i.unitCost)) && Number(i.unitCost) >= 0 ? count * Number(i.unitCost) : null;
      return { ...i, quantity, threshold, count, cost };
    }).filter(i => i.quantity <= i.threshold).sort((a,b) => String(a.supplier || '').localeCompare(String(b.supplier || ''), 'it') || String(a.name || '').localeCompare(String(b.name || ''), 'it'));
  }
  function visibleProposals() { const query = $('suppliesSearch').value.trim().toLowerCase(); return proposals().filter(i => [i.name,i.sku,i.supplier,i.location].join(' ').toLowerCase().includes(query)); }
  function renderSupplies() {
    if (!supplyReady) return;
    const items = proposals();
    $('suppliesMetrics').innerHTML = metric('Articoli sotto scorta', items.length, 'solo articoli attivi') + metric('Costo noto proposto', money(items.reduce((sum,i) => sum + (i.cost ?? 0),0)), 'esclusi i costi mancanti') + metric('Senza costo', items.filter(i => i.cost == null).length, 'preventivo da richiedere') + metric('Quantità da definire', items.filter(i => !i.count).length, 'imposta quantità di riordino');
    $('suppliesList').innerHTML = visibleProposals().map(i => `<article class="task-row"><div><strong>${esc(i.name || i.id)}</strong><div class="task-meta">SKU ${esc(i.sku || '—')} · ${esc(i.supplier || 'Fornitore da definire')} · ${esc(i.location || 'Posizione non indicata')}</div><p class="enterprise-summary">Disponibili ${esc(i.quantity)} ${esc(i.unit || 'pz')} · Soglia ${esc(i.threshold)} · Reintegro proposto ${i.count ? `${esc(i.count)} ${esc(i.unit || 'pz')}` : 'da definire'}</p></div><span class="badge yellow">${i.cost == null ? 'Costo non disponibile' : esc(money(i.cost))}</span></article>`).join('') || '<p class="empty">Nessun articolo da reintegrare per questo filtro.</p>';
  }
  let supplyVersion = 0;
  async function loadSupplies() {
    const version = ++supplyVersion, epoch = state.sessionEpoch || 0;
    supplyReady = false; $('suppliesExport').disabled = true; $('suppliesStatus').textContent = 'Caricamento in corso…';
    $('suppliesMetrics').replaceChildren(); $('suppliesList').replaceChildren();
    try {
      const data = await apiFetch('/api/v1/inventory');
      if (version !== supplyVersion || epoch !== (state.sessionEpoch || 0)) return;
      stock = records(data); supplyReady = true; renderSupplies(); $('suppliesExport').disabled = false;
      $('suppliesStatus').textContent = 'Lista proposta aggiornata. Nessun ordine emesso; costi al netto di IVA e trasporto.';
    } catch(e) { if (version === supplyVersion && epoch === (state.sessionEpoch || 0)) $('suppliesStatus').textContent = `Scorte non disponibili: ${e.message}`; }
  }
  function csv(name, rows) {
    const quote = value => { let text = String(value ?? ''); if (/^\s*[=+@-]/.test(text)) text = "'" + text; return `"${text.replaceAll('"','""')}"`; };
    const url = URL.createObjectURL(new Blob(['\uFEFF' + rows.map(row => row.map(quote).join(';')).join('\r\n')], {type:'text/csv;charset=utf-8'}));
    const link = document.createElement('a'); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000);
  }
  $('planningExport').addEventListener('click', () => { if (planningReady) csv('radiotech-agenda.csv', [['ID','Titolo','Operatore','Asset','Scadenza ISO','Stato','Priorità'], ...selectedTasks().map(t => [t.id,t.title,operatorName(t),t.antennaId,due(t)?.toISOString(),t.status,t.priority])]); });
  $('suppliesExport').addEventListener('click', () => { if (supplyReady) csv('radiotech-reintegro.csv', [['SKU','Articolo','Fornitore','Disponibilità','Soglia','Quantità proposta','Unità','Costo noto EUR'],...visibleProposals().map(i => [i.sku,i.name,i.supplier,i.quantity,i.threshold,i.count,i.unit,i.cost])]); });
  for (const id of ['planningSearch','planningPeriod','planningOperator']) $(id).addEventListener(id === 'planningSearch' ? 'input' : 'change',renderPlanning);
  $('suppliesSearch').addEventListener('input',renderSupplies);
  $('planningRefresh').addEventListener('click',loadPlanning); $('workloadRefresh').addEventListener('click',loadPlanning); $('suppliesRefresh').addEventListener('click',loadSupplies);
  $('suppliesInventory').addEventListener('click',() => switchView('inventory'));
  $('planningAssign').addEventListener('click',() => { switchView('operations'); $('taskTitle').focus(); });
  $('workloadList').addEventListener('click',event => { const button = event.target.closest('[data-workload-operator]'); if (!button) return; switchView('planning'); $('planningOperator').value = button.dataset.workloadOperator || '@unassigned'; $('planningPeriod').value = 'ACTIVE'; $('planningSearch').value = ''; renderPlanning(); });
  return { refreshCurrent() {
    const id = document.querySelector('.view.active')?.id;
    if (['view-planning','view-workload'].includes(id)) return loadPlanning();
    if (id === 'view-supplies') return loadSupplies();
  }, reset() {
    ++requestVersion; ++supplyVersion; tasks = []; operators = []; stock = []; planningReady = false; supplyReady = false;
    for (const id of ['planningMetrics','planningList','workloadList','suppliesMetrics','suppliesList','planningStatus','workloadStatus','suppliesStatus']) $(id).replaceChildren();
    for (const id of ['planningSearch','suppliesSearch','reportSearch']) $(id).value = '';
    $('planningOperator').innerHTML = '<option value="">Tutta la squadra</option>'; $('planningPeriod').value = 'ACTIVE'; $('reportStatusFilter').value = '';
    $('planningExport').disabled = true; $('suppliesExport').disabled = true;
  } };
};
