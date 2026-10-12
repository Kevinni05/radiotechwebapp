// map: presentation and operations for this feature.
export function createFeature(ctx){
/* ---------- MAP ---------- */

            function initMap() {
                if (ctx.state.map) return true;
                if (!window.L) {
                    ctx.setText("mapStatus", "Leaflet non disponibile");
                    ctx.setText("mapSubstatus", "Ricarica la pagina per inizializzare la mappa.");
                    return false;
                }

                try {
                    ctx.state.map = L.map("map", {
                        zoomControl: true,
                        preferCanvas: true,
                        attributionControl: true
                    }).setView([41.9028, 12.4964], 6);

                    /*
                     * Nessun integrity hash: evita che un hash errato blocchi
                     * il caricamento delle tile/script su browser aggiornati.
                     */
                    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
                        maxZoom: 19,
                        crossOrigin: true,
                        attribution: "&copy; OpenStreetMap contributors"
                    }).addTo(ctx.state.map);

                    ctx.state.mapReady = true;
                    ctx.setText("mapStatus", "Network online");
                    ctx.setText("mapSubstatus", "Mappa pronta");
                    return true;
                } catch (error) {
                    console.error("Map init error:", error);
                    ctx.setText("mapStatus", "Mappa non disponibile");
                    ctx.setText("mapSubstatus", error.message);
                    return false;
                }
            }

            function updateMap() {
                if (!ctx.state.map && !initMap()) return;
                ctx.state.markerByAntennaId.forEach(marker => marker.remove());
                ctx.state.markerByAntennaId.clear();
                ctx.state.markers = [];
                const valid = [];
                ctx.state.antennas.forEach(a => {
                    const lat = Number(a.lat), lng = Number(a.lng);
                    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
                    const status = String(a.status || "OFFLINE").toUpperCase();
                    const color = status === "ATTIVA" ? "#2bd576" : status === "MANUTENZIONE" ? "#ffb648" : status === "CRITICA" ? "#b66cff" : "#ff5f6d";
                    const icon = L.divIcon({ className: "", html: `<div role="img" aria-label="${ctx.escapeHtml(a.name||a.id)} · ${ctx.escapeHtml(ctx.formatStatus(status))}" style="width:28px;height:28px;border-radius:50%;display:grid;place-items:center;position:relative;color:#2bd576;background:#14241b;border:3px solid rgba(255,255,255,.95);box-shadow:0 0 0 6px ${color}22,0 5px 18px #0009"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 8v13M7 21l5-10 5 10M4 4a11 11 0 0 0 0 12M20 4a11 11 0 0 1 0 12M8 7a5 5 0 0 0 0 6M16 7a5 5 0 0 1 0 6"/><circle cx="12" cy="7" r="1"/></svg><span aria-hidden="true" style="position:absolute;right:-7px;bottom:-7px;min-width:15px;height:15px;border-radius:50%;display:grid;place-items:center;background:${color};color:#111;font:bold 10px system-ui;border:1px solid white">${status=== "ATTIVA" ? "A" : status=== "MANUTENZIONE" ? "M" : status=== "CRITICA" ? "!" : "O"}</span></div>`, iconSize: [28, 28], iconAnchor: [14, 14] });
                    const marker = L.marker([lat, lng], { icon }).addTo(ctx.state.map);
                    const specs = ctx.antennaSpecs(a);
                    marker.bindPopup(`<div style="min-width:220px;color:#111827;font-family:system-ui"><strong>${ctx.escapeHtml(a.name || a.id)}</strong><br><small>Stato: ${ctx.escapeHtml(ctx.formatStatus(status))}</small><br><small>Frequenza: ${ctx.escapeHtml(specs.Frequenza ?? "—")}</small><br><small>Potenza: ${ctx.escapeHtml(specs.Potenza ?? "—")}</small><br><small>ROS: ${ctx.escapeHtml(specs.ROS ?? "—")}</small><br><small>Temperatura: ${ctx.escapeHtml(specs.Temperatura ?? "—")}</small></div>`);
                    marker.on('click', () => {
                        ctx.switchView('antennas');
                        const row = [...ctx.$('antennasTableBody').querySelectorAll('[data-focus]')].find(button => button.dataset.focus === a.id)?.closest('tr');
                        if (row) { row.scrollIntoView({block:'center'}); row.tabIndex = -1; row.focus(); }
                    });
                    ctx.state.markerByAntennaId.set(a.id, marker); ctx.state.markers.push(marker); valid.push([lat, lng]);
                });
                ctx.setText("mapSubstatus", `${valid.length} antenne geolocalizzate`);
                if(valid.length && ctx.state.mapFittedScope!==(ctx.state.sessionEpoch||0)){ctx.state.map.fitBounds(valid,{padding:[35,35],maxZoom:12});ctx.state.mapFittedScope=ctx.state.sessionEpoch||0;}
                setTimeout(() => ctx.state.map?.invalidateSize(), 50);
            }
return {get initMap(){return initMap;},get updateMap(){return updateMap;}};
}
