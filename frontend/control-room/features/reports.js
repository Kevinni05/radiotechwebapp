// reports: presentation and operations for this feature.
export function createFeature(ctx){
let reportAttachments,lastStatusFilter=null;
const reportFilters=()=>({status:ctx.$('reportStatusFilter')?.value||'',q:ctx.$('reportSearch')?.value.trim()||'',operator:ctx.$('reportOperatorFilter')?.value||'',from:ctx.$('reportDateFrom')?.value||'',to:ctx.$('reportDateTo')?.value||''});
            function attachmentViewer() { return reportAttachments ??= window.RadioTechReportAttachments({apiFetch: ctx.apiFetch, escapeHtml: ctx.escapeHtml}); }
            function reportDisplayTitle(report) {
                const task = ctx.state.tasks.find(t => t.id === report.taskId);
                return report.taskTitle || task?.title || "Intervento senza compito assegnato";
            }
            function reportDisplaySummary(report) {
                const operator = ctx.state.operators.find(o => o.id === report.operatorId || o.firebaseUid === report.operatorFirebaseUid);
                const name = report.operatorName || operator?.fullName || "Operatore non disponibile";
                const instant = new Date(report.submittedAt || report.completedAt || report.createdAt || "");
                const date = Number.isNaN(instant.getTime()) ? "Data non disponibile" : instant.toLocaleDateString("it-IT", {day:"2-digit",month:"2-digit",year:"numeric"});
                return `ID ${report.id} · ${name} · ${date}`;
            }
            function refreshReportTitles() {
                for (const report of ctx.state.reports || []) {
                    const row = [...ctx.$("reportsList").querySelectorAll("[data-report-id]")].find(n => n.dataset.reportId === report.id);
                    const title = row?.querySelector(".task-title");
                    if (title) { title.textContent = reportDisplayTitle(report); title.title = title.textContent; }
                    const summary = row?.querySelector(".report-summary");
                    if (summary) summary.textContent = reportDisplaySummary(report);
                }
            }
            async function loadReports(append = false) {
                append = append === true;
                if (ctx.loadingReports) { if (!append) ctx.reloadReports = true; return; }
                const epoch = ctx.state.sessionEpoch || 0;
                ctx.loadingReports = true; ctx.$('reportsMoreBtn').disabled = true;
                try {
                    const filters=reportFilters(),statusFilter=JSON.stringify(filters);
                    const depth=append?1:statusFilter===lastStatusFilter?Math.max(1,Math.ceil((ctx.state.reports||[]).length/50)):1;
                    let cursor=append?ctx.reportsCursor:null,items=[],data;
                    for(let page=0;page<depth;page++) {
                        const params=new ctx.URLSearchParams({limit:'50'});for(const [key,value] of Object.entries(filters))if(value)params.set(key,value);if(cursor)params.set('cursor',cursor);
                        const response=await ctx.apiFetch(`/api/v1/reports/page?${params}`);data=response?.data??response;
                        if(epoch!==(ctx.state.sessionEpoch||0))return;
                        if(statusFilter!==JSON.stringify(reportFilters())){ctx.reloadReports=true;return;}
                        items.push(...(Array.isArray(data)?data:(data?.items||[])));cursor=data?.nextCursor||null;if(!cursor)break;
                    }
                    const reports=[...new Map([...(append?ctx.state.reports||[]:[]),...items].map(item=>[item.id,item])).values()];
                    ctx.state.reports=reports;ctx.reportsCursor=cursor;lastStatusFilter=statusFilter;
                    ctx.$('reportsMoreBtn').hidden = !ctx.reportsCursor;
                    ctx.$('reportsPageStatus').textContent = `${reports.length} report caricati${ctx.reportsCursor ? ' · altri disponibili' : ' · fine elenco'}`;
                    const list=ctx.$('reportsList');
                    const previous=new Map([...list.querySelectorAll('[data-report-id]')].map(row=>[row.dataset.reportId,{collapsed:row.classList.contains('report-collapsed'),details:row.querySelector('.report-details')?.open}]));
                    const focused=document.activeElement,focusedRow=focused?.closest('[data-report-id]');
                    const focusedAction=focusedRow?[...focused.attributes].find(attribute=>attribute.name.startsWith('data-report-'))?.name:null;
                    const scrollY=window.scrollY;
                    attachmentViewer().clear();
                    ctx.$("reportsList").innerHTML = reports.length ? reports.map(renderReportRow).join("") : `<div class="empty">${data?.hasMore ? "Nessun report visibile in questa pagina. Carica altri report per continuare." : "Nessun report ricevuto."}</div>`;
                    attachmentViewer().mount(ctx.$("reportsList"));
                    for(const row of list.querySelectorAll('[data-report-id]')) {
                        const saved=previous.get(row.dataset.reportId);if(!saved)continue;
                        row.classList.toggle('report-collapsed',saved.collapsed);
                        const toggle=row.querySelector('[data-report-toggle]');toggle?.setAttribute('aria-expanded',String(!saved.collapsed));toggle?.setAttribute('aria-label',saved.collapsed?'Espandi report':'Comprimi report');if(toggle)toggle.textContent=saved.collapsed?'⌄':'⌃';
                        const details=row.querySelector('.report-details');if(details)details.open=!!saved.details;
                        if(focusedRow?.dataset.reportId===row.dataset.reportId&&focusedAction&&!document.querySelector('dialog[open]'))row.querySelector(`[${focusedAction}]`)?.focus({preventScroll:true});
                    }
                    window.scrollTo({top:scrollY,behavior:'instant'});
                    ctx.applyPermissions();
                    ctx.$("reportsList").querySelectorAll('[data-report-approve]').forEach(b => b.addEventListener('click', () => reviewReport(b.dataset.reportApprove, true)));
                    ctx.$("reportsList").querySelectorAll('[data-report-reject]').forEach(b => b.addEventListener('click', () => reviewReport(b.dataset.reportReject, false)));
                    ctx.$("reportsList").querySelectorAll('[data-report-delete]').forEach(b => b.addEventListener('click', () => deleteReport(b.dataset.reportDelete, b)));
                } catch (e) { if (append || ctx.state.reports.length) ctx.toast('Caricamento non riuscito', e.message, 'error'); else ctx.$("reportsList").innerHTML = `<div class="empty">Report non disponibili: ${ctx.escapeHtml(e.message)}</div>`; }
                finally { ctx.loadingReports = false; ctx.$('reportsMoreBtn').disabled = false;
                    if (ctx.reloadReports) { ctx.reloadReports = false; if (ctx.state.token) loadReports(); }
                }
            }

            function canManageReports() {
                return ["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(ctx.state.authRole || ctx.state.user?.role);
            }
            function renderReportRow(report) {
                const id = ctx.escapeHtml(report.id || ""), status = report.status || "SUBMITTED";
                const date = report.submittedAt || report.completedAt || report.createdAt;
                return `<article class="task-row report-row" data-report-id="${id}" data-status="${ctx.escapeHtml(status)}" data-operator="${ctx.escapeHtml(report.operatorName || report.operatorId || "")}" data-date="${ctx.escapeHtml((date || "").slice(0,10))}">
                    <div class="report-main"><div class="report-copy"><h3 class="task-title">${ctx.escapeHtml(reportDisplayTitle(report))}</h3>
                    <div class="task-meta report-summary">${ctx.escapeHtml(reportDisplaySummary(report))}</div>
                    <div class="task-meta report-note">${ctx.escapeHtml(report.operatorNotes || report.description || "Nessuna nota")}</div>
                    </div>
                    <div class="actions"><span class="badge ${ctx.badgeClass(status)}">${ctx.escapeHtml(ctx.formatStatus(status))}</span>
                    ${canManageReports() && ["SUBMITTED", "APPROVAL_PENDING"].includes(status) ? `<button type="button" class="mini-btn" data-report-approve="${id}">${status === "APPROVAL_PENDING" ? "Riprendi approvazione" : "Approva"}</button>${status === "SUBMITTED" ? `<button type="button" class="mini-btn" data-report-reject="${id}">Rifiuta</button>` : ""}` : ""}
                    ${canManageReports() ? `<button type="button" class="btn danger mini-btn" data-report-delete="${id}">Elimina</button>` : ""}
                    <button type="button" class="mini-btn" data-report-toggle aria-expanded="true" aria-label="Comprimi report">⌃</button></div></div>
                    <div class="report-content">${reportDetails(report)}${attachmentViewer().markup(report.attachments)}</div></article>`;
            }
            async function deleteReport(id, trigger) {
                if (!canManageReports() || trigger.disabled) return;
                const report = ctx.state.reports.find(item => item.id === id);
                if (!report) return;
                const rowButtons = [...trigger.closest('[data-report-id]').querySelectorAll('[data-report-approve], [data-report-reject], [data-report-delete]')];
                if (rowButtons.some(button => button.disabled)) return;
                const epoch = ctx.state.sessionEpoch || 0;
                const dialog = document.createElement('dialog');
                dialog.className = 'report-material-review report-delete-dialog';
                dialog.setAttribute('aria-labelledby', 'reportDeleteTitle');
                dialog.setAttribute('aria-describedby', 'reportDeleteConsequences');
                dialog.innerHTML = `<form novalidate><h2 id="reportDeleteTitle">Elimina report</h2>
                    <p class="report-delete-name">${ctx.escapeHtml(reportDisplayTitle(report))}</p>
                    <dl><dt>Identificativo</dt><dd>${ctx.escapeHtml(id)}</dd><dt>Stato</dt><dd>${ctx.escapeHtml(ctx.formatStatus(report.status || 'SUBMITTED'))}</dd></dl>
                    <p id="reportDeleteConsequences">Il report sarà rimosso dall’elenco operativo. Documento, allegati, firme e storico delle operazioni rimarranno conservati per la tracciabilità. L’incarico e i movimenti di magazzino già registrati non saranno modificati.</p>
                    <label class="field" for="reportDeleteReason">Motivo della rimozione<textarea id="reportDeleteReason" name="reason" required minlength="3" maxlength="500" rows="3"></textarea></label>
                    <p role="alert" data-delete-error hidden></p>
                    <div class="actions"><button type="button" class="btn" data-delete-cancel>Annulla</button><button type="submit" class="btn danger">Conferma eliminazione</button></div></form>`;
                let busy = false;
                rowButtons.forEach(button => { button.disabled = true; });
                dialog.addEventListener('close', () => { dialog.remove(); rowButtons.forEach(button => { button.disabled = false; }); if (trigger.isConnected) trigger.focus(); else ctx.$('reportsRefreshBtn')?.focus(); }, {once:true});
                dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
                dialog.querySelector('[data-delete-cancel]').addEventListener('click', () => dialog.close());
                dialog.querySelector('form').addEventListener('submit', async event => {
                    event.preventDefault();
                    if (busy) return;
                    if (epoch !== (ctx.state.sessionEpoch || 0) || !canManageReports()) { dialog.close(); return; }
                    const reasonField=dialog.querySelector('[name="reason"]');
                    if(reasonField.value.trim().length<3){const message=dialog.querySelector('[data-delete-error]');message.textContent='Specifica una motivazione di almeno 3 caratteri.';message.hidden=false;reasonField.setAttribute('aria-invalid','true');reasonField.focus();return;}
                    reasonField.removeAttribute('aria-invalid');
                    busy = true;
                    dialog.querySelectorAll('button').forEach(button => { button.disabled = true; });
                    const errorBox = dialog.querySelector('[data-delete-error]');
                    errorBox.hidden = true;
                    try {
                        await ctx.apiFetch(`/api/v1/reports/${encodeURIComponent(id)}`, {method:'DELETE',body:JSON.stringify({reason:dialog.querySelector('[name="reason"]').value.trim()})});
                        if (epoch !== (ctx.state.sessionEpoch || 0)) { dialog.close(); return; }
                        ctx.state.reports = ctx.state.reports.filter(item => item.id !== id);
                        trigger.closest('[data-report-id]')?.remove();
                        dialog.close();
                        await Promise.all([loadReports(), ctx.loadStats()]);
                        ctx.toast('Report eliminato', 'Rimosso dall’elenco operativo. Documenti e storico conservati.');
                    } catch (error) {
                        if (epoch !== (ctx.state.sessionEpoch || 0)) { dialog.close(); return; }
                        errorBox.textContent = `Eliminazione non riuscita: ${error.message}`;
                        errorBox.hidden = false;
                        ctx.toast('Eliminazione fallita', error.message, 'error');
                    } finally {
                        busy = false;
                        dialog.querySelectorAll('button').forEach(button => { button.disabled = false; });
                    }
                });
                document.body.append(dialog); dialog.showModal();
                dialog.querySelector('[data-delete-cancel]').focus();
            }

            function reportDetails(report) {
                const measurements = Object.entries(report.measurements || {}).map(([key, value]) => `<li>${ctx.escapeHtml(key)}: ${ctx.escapeHtml(value)}</li>`).join("");
                const materials = (report.materialsUsed || []).map(item => `<li>${ctx.escapeHtml(item.name || item.itemId || item.inventoryItemId || item.ricambioId || "Materiale")}: ${ctx.escapeHtml(item.quantity ?? item.quantita ?? "—")}</li>`).join("");
                const checklist = Object.entries(report.checklist || {}).map(([key, value]) => `<li>${ctx.escapeHtml(key)}: ${value === true ? "Verificato" : "Non verificato"}</li>`).join("");
                return `<details class="report-details"><summary>Dettagli intervento</summary><p>Antenna: ${ctx.escapeHtml(ctx.state.antennas.find(a => a.id === report.antennaId)?.name || report.antennaId || "—")} · Inviato: ${ctx.escapeHtml(report.submittedAt ? new Date(report.submittedAt).toLocaleString("it-IT") : "Data non disponibile")}</p><p><strong>Lavoro eseguito</strong><br>${ctx.escapeHtml(report.workPerformed || report.description || "Non indicato")}</p><p><strong>Rilievi</strong><br>${ctx.escapeHtml(report.findings || "Nessun rilievo indicato")}</p>${measurements ? `<strong>Misure</strong><ul>${measurements}</ul>` : ""}${materials ? `<strong>Materiali</strong><ul>${materials}</ul>` : ""}${checklist ? `<strong>Lista di controllo</strong><ul>${checklist}</ul>` : ""}${report.reviewNote ? `<p><strong>Nota di revisione</strong><br>${ctx.escapeHtml(report.reviewNote)}</p>` : ""}</details>`;
            }

            async function reviewReport(id, approve) {
                const currentReport = ctx.state.reports.find(item => item.id === id);
                if (!canManageReports() || !currentReport || !(approve ? ["SUBMITTED", "APPROVAL_PENDING"] : ["SUBMITTED"]).includes(currentReport.status)) return;
                const buttons = [...ctx.$("reportsList").querySelectorAll('[data-report-approve], [data-report-reject], [data-report-delete]')].filter(b => b.dataset.reportApprove === id || b.dataset.reportReject === id || b.dataset.reportDelete === id);
                if (buttons.some(b => b.disabled)) return;
                const note = approve ? 'Report approvato dalla centrale.' : (await ctx.prompt('Motivo del rifiuto:') || '');
                if (!approve && !note.trim()) return;
                buttons.forEach(b => { b.disabled = true; });
                try {
                    const epoch=ctx.state.sessionEpoch||0, report=ctx.state.reports.find(item=>item.id===id);
                    let materialMappings;
                    if(approve && report?.materialsUsed?.some(material=>!['inventoryId','inventoryItemId','itemId','ricambioId','id','sku'].some(field=>material[field]))) {
                        materialMappings=await window.RadioTechReviewMaterials({apiFetch: ctx.apiFetch,report,escapeHtml: ctx.escapeHtml});
                        if(materialMappings===null)return;
                    }
                    if(epoch!==(ctx.state.sessionEpoch||0))return;
                    await ctx.apiFetch(`/api/v1/reports/${encodeURIComponent(id)}/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: JSON.stringify({ note, materialMappings }) }); await Promise.all([loadReports(), ctx.loadTasks(), ctx.loadStats()]); window.dispatchEvent(new Event('radiotech:tasks-changed')); ctx.toast(approve ? 'Report approvato' : 'Report rifiutato', approve ? 'Materiali aggiornati e incarico completato.' : 'L’operatore può correggere e inviare nuovamente il rapporto dagli incarichi attivi.');
                } catch (e) { ctx.toast('Revisione fallita', e.message, 'error'); }
                finally { buttons.forEach(b => { b.disabled = false; }); }
            }
return {get attachmentViewer(){return attachmentViewer;},get reportDisplayTitle(){return reportDisplayTitle;},get reportDisplaySummary(){return reportDisplaySummary;},get refreshReportTitles(){return refreshReportTitles;},get loadReports(){return loadReports;},get canManageReports(){return canManageReports;},get renderReportRow(){return renderReportRow;},get deleteReport(){return deleteReport;},get reportDetails(){return reportDetails;},get reviewReport(){return reviewReport;},get reportAttachments(){return reportAttachments;},set reportAttachments(value){reportAttachments=value;}};
}
