import {createPagedResource,preserveTableInteraction} from '../paged-resource.js';
// assets: presentation and operations for this feature.
export function createFeature(ctx){
/* ---------- ANTENNAS ---------- */

            const pages=createPagedResource(ctx,{path:'/api/v1/dashboard/antenne/page',legacy:'/api/v1/dashboard/antenne',stateKey:'antennas',tableId:'antennasTableBody',pageSize:200,buttonId:'antennaLoadMore',buttonText:'Carica altri impianti',filters:()=>({q:ctx.$('antennaSearch')?.value||'',status:ctx.$('antennaStatusFilter')?.value||''}),onUpdated:()=>{renderAntennas();ctx.populateTaskSelectors();ctx.updateMap();}});
            async function loadAntennas(more = false) {
                try {
                    await pages(more);
                    renderAntennas();
                    ctx.populateTaskSelectors();
                    ctx.updateMap();
                } catch (error) {
                    console.error(error);
                    ctx.$("antennasTableBody").innerHTML =
                        `<tr><td colspan="6"><div class="empty">Impossibile caricare le antenne: ${ctx.escapeHtml(error.message)}</div></td></tr>`;
                    throw error;
                }
            }

            function renderAntennas(){preserveTableInteraction(ctx.$("antennasTableBody"),renderAntennaRows);}
            function renderAntennaRows() {
                const normalize=value=>String(value||'').normalize('NFKD').replace(/\p{M}/gu,'').toLowerCase();
                const query = normalize(ctx.$("antennaSearch").value.trim());
                const filter = ctx.$("antennaStatusFilter").value.toUpperCase();

                const list = ctx.state.antennas.filter(a => {
                    const haystack = normalize([a.id, a.name, a.status, a.lat, a.lng].join(" "));
                    return (!query || haystack.includes(query)) &&
                        (!filter || String(a.status || "").toUpperCase() === filter);
                });

                ctx.setText("antennaCountLabel", `${list.length} di ${ctx.state.antennas.length} antenne`);

                if (!list.length) {
                    ctx.$("antennasTableBody").innerHTML =
                        `<tr><td colspan="6"><div class="empty">Nessuna antenna trovata.</div></td></tr>`;
                    return;
                }

                ctx.$("antennasTableBody").innerHTML = list.map(a => {
                    const specs = antennaSpecs(a);
                    return `
                <tr>
                    <td>
                        <div class="antenna-name">${ctx.escapeHtml(a.name || a.id)}</div>
                        <div class="coordinates">${ctx.escapeHtml(a.id || "")}</div>
                    </td>
                    <td class="asset-coordinates"><span title="Latitudine">${ctx.number(a.lat, 5)}</span> <span title="Longitudine">${ctx.number(a.lng, 5)}</span></td>
                    <td><span class="badge ${ctx.badgeClass(a.status)}">${ctx.escapeHtml(ctx.formatStatus(a.status))}</span></td>
                    <td><div class="asset-telemetry"><span>Freq.</span><strong>${antennaReading(specs.Frequenza,'MHz')}</strong><span>Potenza</span><strong>${antennaReading(specs.Potenza,'W')}</strong></div></td>
                    <td><div class="asset-telemetry"><span>ROS</span><strong>${antennaReading(specs.ROS,'')}</strong><span>Temp.</span><strong>${antennaReading(specs.Temperatura,'°C')}</strong></div></td>
                    <td>
                        <div class="actions">
                            <button class="mini-btn" data-dossier="${ctx.escapeHtml(a.id)}">Fascicolo</button>
                            <button class="mini-btn" data-qr="${ctx.escapeHtml(a.id)}">QR Code</button>
                            <button class="mini-btn" data-edit="${ctx.escapeHtml(a.id)}">Modifica</button>
                            <button class="mini-btn" data-focus="${ctx.escapeHtml(a.id)}">Mappa</button>
                            <button class="mini-btn" data-delete="${ctx.escapeHtml(a.id)}">Elimina</button>
                        </div>
                    </td>
                </tr>`;
                }).join("");

                ctx.applyPermissions();
                ctx.$("antennasTableBody").querySelectorAll("[data-qr]").forEach(b =>
                    b.addEventListener("click", () => ctx.showAntennaQr(b.dataset.qr))
                );
                ctx.$("antennasTableBody").querySelectorAll("[data-edit]").forEach(b =>
                    b.addEventListener("click", () => openAntennaModal(b.dataset.edit))
                );
                ctx.applyPermissions();
                ctx.$("antennasTableBody").querySelectorAll("[data-focus]").forEach(b =>
                    b.addEventListener("click", () => focusAntenna(b.dataset.focus))
                );
                ctx.applyPermissions();
                ctx.$("antennasTableBody").querySelectorAll("[data-delete]").forEach(b =>
                    b.addEventListener("click", () => deleteAntenna(b.dataset.delete))
                );
            }

            function focusAntenna(id) {
                const a = ctx.state.antennas.find(x => x.id === id);
                if (!a || !ctx.initMap()) return;

                const lat = Number(a.lat);
                const lng = Number(a.lng);
                if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

                ctx.switchView("dashboard");
                setTimeout(() => {
                    ctx.state.map.invalidateSize();
                    ctx.state.map.setView([lat, lng], 14);

                    ctx.state.markerByAntennaId.get(id)?.openPopup();
                }, 100);
            }

            function openAntennaModal(id = null) {
                ctx.state.editingAntennaId = id;
                ctx.$("antennaForm").reset();
                ctx.$("antennaId").value = "";
                ctx.$("antennaModalTitle").textContent = id ? "Modifica antenna" : "Nuova antenna";

                if (id) {
                    const a = ctx.state.antennas.find(x => x.id === id);
                    if (!a) return;
                    const s = antennaSpecs(a);

                    ctx.$("antennaId").value = a.id;
                    ctx.$("antennaName").value = a.name || "";
                    ctx.$("antennaStatus").value = a.status || "ATTIVA";
                    ctx.$("antennaLat").value = a.lat ?? "";
                    ctx.$("antennaLng").value = a.lng ?? "";
                    ctx.$("antennaFreq").value = s.Frequenza ?? "";
                    ctx.$("antennaPower").value = s.Potenza ?? "";
                    ctx.$("antennaRos").value = s.ROS ?? "";
                    ctx.$("antennaTemp").value = s.Temperatura ?? "";
                } else {
                    ctx.$("antennaStatus").value = "ATTIVA";
                }

                ctx.openModal("antennaModal");
            }

            function antennaSpecs(antenna) {
                const s = antenna.specs || {};
                return {
                    Frequenza: s.frequenza ?? s.Frequenza ?? s.frequencyMHz,
                    Potenza: s.potenza ?? s.Potenza ?? s.powerWatts,
                    ROS: s.ros ?? s.ROS,
                    Temperatura: s.temperatura ?? s.Temperatura ?? s.temperature
                };
            }
            function antennaReading(value,unit){
                if(value===null||value===undefined||value==='')return '—';
                return `${ctx.escapeHtml(value)}${unit&&Number.isFinite(Number(value))?` <small class="asset-unit">${unit}</small>`:''}`;
            }

            async function saveAntenna(event) {
                event.preventDefault();

                const id = ctx.state.editingAntennaId;
                const payload = {
                    name: ctx.$("antennaName").value.trim(),
                    lat: Number(ctx.$("antennaLat").value),
                    lng: Number(ctx.$("antennaLng").value),
                    status: ctx.$("antennaStatus").value,
                    specs: id ? {
                        frequenza: ctx.numberOrNull(ctx.$("antennaFreq").value),
                        potenza: ctx.numberOrNull(ctx.$("antennaPower").value),
                        ros: ctx.numberOrNull(ctx.$("antennaRos").value),
                        temperatura: ctx.numberOrNull(ctx.$("antennaTemp").value)
                    } : {
                        frequencyMHz: ctx.numberOrNull(ctx.$("antennaFreq").value),
                        powerWatts: ctx.numberOrNull(ctx.$("antennaPower").value),
                        ros: ctx.numberOrNull(ctx.$("antennaRos").value),
                        temperature: ctx.numberOrNull(ctx.$("antennaTemp").value)
                    }
                };

                if (!payload.name || !Number.isFinite(payload.lat) || !Number.isFinite(payload.lng)) {
                    ctx.toast("Dati non validi", "Controlla nome e coordinate.", "error");
                    return;
                }

                const button = ctx.$("saveAntenna");
                button.disabled = true;
                button.textContent = "Salvataggio...";

                try {
                    if (!id) {
                        await ctx.api("/antenne", {
                            method: "POST",
                            body: JSON.stringify(payload)
                        });
                    } else {
                        await ctx.api(`/antenne/${encodeURIComponent(id)}`, {
                            method: "PUT",
                            body: JSON.stringify(payload)
                        });
                    }

                    ctx.closeModal("antennaModal");
                    await Promise.all([loadAntennas(), ctx.loadStats()]);
                    ctx.toast("Operazione completata", id ? "Antenna aggiornata." : "Antenna creata.");
                } catch (error) {
                    window.RadioTechForms.errors(ctx.$("antennaForm"),error.fieldErrors);
                    ctx.toast("Salvataggio fallito", error.message, "error");
                } finally {
                    button.disabled = false;
                    button.textContent = "Salva antenna";
                }
            }

            async function deleteAntenna(id) {
                const a = ctx.state.antennas.find(x => x.id === id);
                const name = a?.name || id;
                if (!await ctx.confirm(`Eliminare definitivamente "${name}"?`)) return;

                try {
                    await ctx.api(`/antenne/${encodeURIComponent(id)}`, { method: "DELETE" });
                    await Promise.all([loadAntennas(), ctx.loadStats()]);
                    ctx.toast("Antenna eliminata", `${name} è stata rimossa.`);
                } catch (error) {
                    ctx.toast("Eliminazione fallita", error.message, "error");
                }
            }

            let tasksCursor = null, reportsCursor = null;
return {get loadAntennas(){return loadAntennas;},get renderAntennas(){return renderAntennas;},get focusAntenna(){return focusAntenna;},get openAntennaModal(){return openAntennaModal;},get antennaSpecs(){return antennaSpecs;},get saveAntenna(){return saveAntenna;},get deleteAntenna(){return deleteAntenna;},get tasksCursor(){return tasksCursor;},get reportsCursor(){return reportsCursor;},set tasksCursor(value){tasksCursor=value;},set reportsCursor(value){reportsCursor=value;}};
}
