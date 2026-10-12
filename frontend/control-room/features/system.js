// system: presentation and operations for this feature.
export function createFeature(ctx){
/* ---------- SYSTEM ---------- */

            async function loadOperationalHealth() {
                loadPrivateRelease();
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

            async function loadPrivateRelease() {
                const panel=ctx.$('privateDistribution');if(!panel)return;
                try {
                    const release=await ctx.apiFetch('/api/v1/distribution/latest.json');
                    if(!release.available){panel.textContent='Nessuna release pubblicata per questa azienda.';return;}
                    panel.innerHTML=`<p>RadioTech ${ctx.escapeHtml(release.versionName)} (${ctx.escapeHtml(release.versionCode)}) · ${(Number(release.size)/1048576).toFixed(1)} MiB</p><p>APK firmato. Dopo il download Android verifica il pacchetto prima dell’installazione.</p><button type="button" class="btn" data-private-apk>Scarica APK</button><p role="status" data-private-status></p>`;
                    const epoch=ctx.state.sessionEpoch||0;
                    panel.querySelector('[data-private-apk]').onclick=async event=>{
                        const button=event.currentTarget,status=panel.querySelector('[data-private-status]');button.disabled=true;status.textContent='Download e verifica in corso…';
                        try {
                            const file=await ctx.apiFetch(`/api/v1/distribution/${encodeURIComponent(release.filename)}`,{responseType:'bytes',maxResponseBytes:150*1024*1024,timeoutMs:180000});
                            if(epoch!==(ctx.state.sessionEpoch||0))return;
                            const hash=[...new Uint8Array(await crypto.subtle.digest('SHA-256',file.bytes))].map(byte=>byte.toString(16).padStart(2,'0')).join('');
                            if(epoch!==(ctx.state.sessionEpoch||0))return;
                            if(hash!==release.sha256)throw new Error('Integrità APK non valida.');
                            const url=URL.createObjectURL(new Blob([file.bytes],{type:'application/vnd.android.package-archive'})),link=document.createElement('a');link.href=url;link.download=release.filename;link.click();setTimeout(()=>URL.revokeObjectURL(url),30000);status.textContent='APK verificato e scaricato.';
                        }catch(error){status.textContent=`Download non riuscito: ${error.message}`;}finally{button.disabled=false;}
                    };
                }catch(error){panel.textContent=`Canale aggiornamenti non disponibile: ${error.message}`;}
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
