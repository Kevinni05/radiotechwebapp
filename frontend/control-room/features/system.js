// system: presentation and operations for this feature.
export function createFeature(ctx){
/* ---------- SYSTEM ---------- */

            async function loadOperationalHealth() {
                ctx.$('operationsHealthRefresh').disabled = true;
                try {
                    const response = await ctx.apiFetch('/api/v1/operations/health');
                    const health = response?.data ?? response, usage = health.firestore || {}, files = health.attachments || {};
                    const number = value => value == null ? 'Non disponibile' : Number(value).toLocaleString('it-IT');
                    const alarms = (health.alarms || []).map(alarm => `<li>${ctx.escapeHtml(alarm.message)}</li>`).join('');
                    const backupLabels = {OK: 'Verificato', NOT_CONFIGURED: 'Da configurare', STALE: 'Backup scaduto', RESTORE_REQUIRED: 'Ripristino da verificare', INVALID: 'Stato da verificare'};
                    ctx.$('operationsHealth').innerHTML = `<p>Allegati: <strong>${number(files.count)}</strong> · ${(Number(files.bytes || 0) / 1048576).toFixed(2)} MiB · ${ctx.escapeHtml(health.backend || '')}</p><p>Report da revisionare: ${number(health.pendingReports)} · Backup: <strong>${ctx.escapeHtml(backupLabels[health.backup?.state] || 'Non disponibile')}</strong></p><p>Letture di oggi: ${number(usage.reads)} · Scritture: ${number(usage.writes)} · Eliminazioni: ${number(usage.deletes)}</p><p>Spazio database e indici: ${usage.storageBytes == null ? 'Non disponibile' : (Number(usage.storageBytes)/1048576).toFixed(2) + ' MiB'}</p><p class="subtle">${ctx.escapeHtml(usage.note || '')} · Giornata quota: ${ctx.escapeHtml(usage.quotaDayTimezone || '')}</p>${alarms ? `<ul role="status">${alarms}</ul>` : '<p>Nessun avviso dalle verifiche disponibili.</p>'}`;
                } catch (error) { ctx.$('operationsHealth').textContent = `Monitoraggio non disponibile: ${error.message}`; }
                finally { ctx.$('operationsHealthRefresh').disabled = false; }
            }

            async function checkSystem() {
                try {
                    await ctx.api("/stats");
                    ctx.setBackendState(true);
                    ctx.toast("Sistema operativo", "Backend raggiungibile.");
                } catch (error) {
                    ctx.setBackendState(false);
                    ctx.setText("firestoreStatus", "UNKNOWN");
                    ctx.$("firestoreBadge").textContent = "CHECK";
                    ctx.$("firestoreBadge").className = "badge yellow";
                    ctx.toast("Problema backend", error.message, "error");
                }
            }

            async function loadAudit() {
                try {
                    const entries = await ctx.api('/audit?limit=30');
                    const list = Array.isArray(entries) ? entries : [];
                    ctx.$('auditList').innerHTML = list.length ? list.map(entry => `<div class="task-row"><div><div class="task-title">${ctx.escapeHtml(ctx.formatStatus(entry.action || 'EVENTO'))}</div><div class="task-meta">${ctx.escapeHtml(ctx.formatStatus(entry.resource || ''))} ${ctx.escapeHtml(entry.resourceId || '')} · ${ctx.escapeHtml(entry.actor || 'Sistema')}</div></div><span class="subtle">${entry.timestamp ? ctx.escapeHtml(new Date(entry.timestamp).toLocaleString('it-IT')) : '—'}</span></div>`).join('') : '<div class="empty">Nessun evento registrato.</div>';
                } catch (error) {
                    ctx.$('auditList').innerHTML = `<div class="empty">Audit non disponibile: ${ctx.escapeHtml(error.message)}</div>`;
                }
            }
return {get loadOperationalHealth(){return loadOperationalHealth;},get checkSystem(){return checkSystem;},get loadAudit(){return loadAudit;}};
}
