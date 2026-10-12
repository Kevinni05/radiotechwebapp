import {createPagedResource,preserveTableInteraction} from '../paged-resource.js';
// operators: presentation and operations for this feature.
export function createFeature(ctx){
const pages=createPagedResource(ctx,{path:'/api/v1/dashboard/resources/operators/page',legacy:'/api/v1/operators',stateKey:'operators',tableId:'operatorsTableBody',filters:()=>({q:ctx.$('operatorSearch')?.value||'',role:ctx.$('operatorRoleFilter')?.value||''}),onUpdated:()=>{ctx.renderRecipients();renderOperatorTable();ctx.populateTaskSelectors();ctx.refreshReportTitles();}});
/* ---------- OPERATORS ---------- */

            async function loadOperators() {
                try {
                    await pages();
                    ctx.state.operatorLoadError = "";
                    ctx.renderRecipients();
                    renderOperatorTable();
                    ctx.populateTaskSelectors(); ctx.refreshReportTitles();
                } catch (error) {
                    console.warn("Operatori non disponibili:", error.message);
                    ctx.state.operatorLoadError = error.message || "Errore di caricamento";
                    renderOperatorTable();
                }
            }

            function operatorOnline(lastSeen) {
                if (!lastSeen) return false;
                const timestamp = Date.parse(lastSeen);
                return Number.isFinite(timestamp) && Date.now() - timestamp < 5 * 60 * 1000;
            }

            function renderOperatorTable(){preserveTableInteraction(ctx.$("operatorsTableBody"),renderOperatorTableRows);}
            function renderOperatorTableRows() {
                const table = ctx.$("operatorsTableBody");
                if (!table) return;
                const search = (ctx.$("operatorSearch")?.value || "").trim().toLocaleLowerCase("it-IT");
                const roleFilter = ctx.$("operatorRoleFilter")?.value || "";
                const visibleOperators = ctx.state.operators.filter(operator => {
                    const name = `${operator.fullName || ""} ${operator.email || ""}`.toLocaleLowerCase("it-IT");
                    const role = (operator.role || "OPERATOR").toUpperCase();
                    return (!search || name.includes(search)) && (!roleFilter || role === roleFilter);
                });
                const active = ctx.state.operators.filter(operator => (operator.status || "").toUpperCase() === "ATTIVO").length;
                ctx.$("operatorsSummary").textContent = ctx.state.operatorLoadError
                    ? "Elenco non disponibile"
                    : `${ctx.state.operators.length} operatori · ${active} attivi`;
                if (ctx.state.operatorLoadError) {
                    table.innerHTML = `<tr><td colspan="7"><div class="empty">Impossibile caricare gli operatori: ${ctx.escapeHtml(ctx.state.operatorLoadError)}</div></td></tr>`;
                    return;
                }
                if (!visibleOperators.length) {
                    table.innerHTML = `<tr><td colspan="7"><div class="empty">${ctx.state.operators.length ? "Nessun operatore corrisponde ai filtri." : "Nessun operatore registrato."}</div></td></tr>`;
                    return;
                }
                table.innerHTML = visibleOperators.map(operator => {
                    const id = operator.id || "";
                    const name = operator.fullName || operator.email || id;
                    const online = operatorOnline(operator.lastSeen);
                    const role = (operator.role || "OPERATOR").toUpperCase();
                    const linked = Boolean(operator.firebaseUid);
                    const pending = (operator.status || "").toUpperCase() === "IN_ATTESA";
                    return `<tr>
                        <td><strong>${ctx.escapeHtml(name)}</strong><div class="subtle">${ctx.escapeHtml(operator.email || "Email non collegata")}</div></td>
                        <td><div class="actions"><select data-operator-role="${ctx.escapeHtml(id)}" aria-label="Ruolo di ${ctx.escapeHtml(name)}" ${linked && !pending ? "" : "disabled"}>
                            <option value="OPERATOR" ${role === "OPERATOR" ? "selected" : ""}>Operatore</option>
                            <option value="VIEWER" ${role === "VIEWER" ? "selected" : ""}>Sola lettura</option>
                        </select><button class="mini-btn" data-save-role="${ctx.escapeHtml(id)}" ${linked && !pending ? "" : "disabled"}>Salva</button></div></td>
                        <td>${ctx.escapeHtml(operator.level || "TECNICO")}</td>
                        <td>${ctx.escapeHtml(operator.shift || "—")}</td>
                        <td><span class="badge ${online ? "green" : pending ? "red" : "blue"}">${online ? "CONNESSO" : ctx.escapeHtml(ctx.formatStatus(operator.status || "OFFLINE"))}</span></td>
                        <td>${operator.lastSeen ? ctx.escapeHtml(new Date(operator.lastSeen).toLocaleString("it-IT")) : "—"}</td>
                        <td><div class="actions">${pending ? `<button class="mini-btn" data-approve="${ctx.escapeHtml(id)}">Approva</button>` : ""}<button class="mini-btn" data-badge="${ctx.escapeHtml(id)}">Badge</button><button class="mini-btn" data-regenerate="${ctx.escapeHtml(id)}">Rigenera QR</button><button class="mini-btn" data-validity="${ctx.escapeHtml(id)}">Validità QR</button><button class="mini-btn" data-reset-password="${ctx.escapeHtml(id)}">Reimposta password</button><button class="mini-btn danger" data-delete-operator="${ctx.escapeHtml(id)}">Elimina</button></div></td>
                    </tr>`;
                }).join("");
                ctx.applyPermissions(); ctx.refreshReportTitles();
                table.querySelectorAll("[data-save-role]").forEach(button => button.addEventListener("click", () => saveOperatorRole(button.dataset.saveRole)));
                table.querySelectorAll("[data-approve]").forEach(button => button.addEventListener("click", () => approveOperator(button.dataset.approve)));
                table.querySelectorAll("[data-badge]").forEach(button => button.addEventListener("click", () => showBadge(button.dataset.badge)));
                table.querySelectorAll("[data-regenerate]").forEach(button => button.addEventListener("click", () => regenerateQr(button.dataset.regenerate)));
            }

            async function saveOperatorRole(id) {
                const select = document.querySelector(`[data-operator-role="${ctx.CSS.escape(id)}"]`);
                if (!select) return;
                const operator = ctx.state.operators.find(item => item.id === id);
                if (!operator) return;
                const role = select.value;
                if (role === "VIEWER" && (operator.role || "OPERATOR").toUpperCase() !== "VIEWER"
                    && !await ctx.confirm("Il ruolo Sola lettura disabilita l'accesso alle funzioni operative dell'app mobile. Continuare?")) return;
                try {
                    await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/role`, {
                        method: "PATCH", body: JSON.stringify({ role })
                    });
                    await loadOperators();
                    ctx.toast("Ruolo aggiornato", "Il nuovo ruolo sarà attivo dopo il prossimo accesso dell'operatore.");
                } catch (error) {
                    ctx.toast("Aggiornamento ruolo non riuscito", error.message, "error");
                }
            }

            async function approveOperator(id) {
                if (!await ctx.confirm("Approvare l'operatore e abilitarne l'accesso?")) return;
                try {
                    await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/approve`, { method: "POST" });
                    await loadOperators();
                    ctx.toast("Operatore approvato", "L'accesso sarà attivo al prossimo login.");
                } catch (error) {
                    ctx.toast("Approvazione non riuscita", error.message, "error");
                }
            }

            async function createOperator(event) {
                event.preventDefault();
                const button = ctx.$("saveOperator");
                button.disabled = true;
                try {
                    const result = await ctx.apiFetch("/api/v1/operators/generate-credentials", {
                        method: "POST", body: JSON.stringify({
                            fullName: ctx.$("operatorFullName").value.trim(), email: ctx.$("operatorEmail").value.trim() || null,
                            phone: ctx.$("operatorPhone").value.trim() || null, level: ctx.$("operatorLevel").value,
                            shift: ctx.$("operatorShift").value.trim(), password: ctx.$("operatorPassword").value.trim() || null
                        })
                    });
                    const data = result?.data ?? result;
                    ctx.$("operatorCredentialsResult").innerHTML = `Email: <strong>${ctx.escapeHtml(data.email)}</strong> · Password: <strong>${ctx.escapeHtml(data.password)}</strong> · QR: <strong>${ctx.escapeHtml(data.qrCodeToken)}</strong>`;
                    await loadOperators();
                    ctx.toast("Operatore creato", "Credenziali generate. Conserva la password e stampa il badge.");
                    if (data.operator?.id) showBadge(data.operator.id);
                } catch (error) {
                    ctx.toast("Creazione operatore fallita", error.message, "error");
                } finally { button.disabled = false; }
            }

            async function showAntennaQr(id) {
                try {
                    const result = await ctx.apiFetch(`/api/v1/antennas/${encodeURIComponent(id)}/qr`);
                    const data = result?.data ?? result;
                    ctx.$("antennaQrStationName").textContent = data.name || data.antennaId;
                    ctx.$("antennaQrCode").textContent = data.code || "ANT";
                    ctx.$("antennaQrId").textContent = data.antennaId;
                    ctx.$("antennaQrSite").textContent = data.site || "Nessun indirizzo specificato";
                    ctx.$("antennaQrStatus").textContent = data.status || "ATTIVA";
                    ctx.$("antennaQrImage").src = data.imageDataUrl;
                    const downloadLink = ctx.$("antennaQrDownloadLink");
                    if (downloadLink) {
                        downloadLink.href = data.imageDataUrl;
                        downloadLink.download = `QR_${data.code || data.antennaId}.png`;
                    }
                    ctx.openModal("antennaQrModal");
                } catch (error) { ctx.toast("QR antenna non disponibile", error.message, "error"); }
            }

            async function showBadge(id) {
                const operator = ctx.state.operators.find(item => item.id === id);
                if (!operator) return;
                try {
                    const result = await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/badge`);
                    const data = result?.data ?? result;
                    ctx.$("badgeName").textContent = operator.fullName || operator.email || "Operatore RadioTech";
                    ctx.$("badgeDetails").textContent = `${operator.level || "TECNICO"} · ${operator.shift || "GIORNALIERO"}`;
                    ctx.$("badgeToken").textContent = data.qrCodeToken;
                    ctx.$("badgeQr").src = data.imageDataUrl;
                    ctx.$("badgeResetPassword").dataset.resetPassword=id;
                    ctx.$("badgeValidity").dataset.validity=id;
                    ctx.openModal("badgeModal");
                } catch (error) { ctx.toast("Badge non disponibile", error.message, "error"); }
            }

            async function regenerateQr(id) {
                if (!await ctx.confirm("Rigenerare il QR? Il badge precedente verrà invalidato.")) return;
                try { await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/regenerate-qr`, { method: "POST" }); await loadOperators(); ctx.toast("QR rigenerato", "Il badge precedente non è più valido."); await showBadge(id); }
                catch (error) { ctx.toast("Rigenerazione fallita", error.message, "error"); }
            }

            async function syncOperatorFromFirebase() {
                const email = ctx.$('syncOperatorEmail').value.trim();
                const fullName = ctx.$('syncOperatorName').value.trim();
                if (!email) {
                    ctx.toast('Email obbligatoria', 'Inserisci l’email dell’utente Firebase.', 'error');
                    return;
                }
                const button = ctx.$('syncOperatorBtn');
                button.disabled = true;
                try {
                    await ctx.apiFetch('/api/v1/operators/sync-firebase-user', {
                        method: 'POST',
                        body: JSON.stringify({ email, fullName })
                    });
                    ctx.$('syncOperatorEmail').value = '';
                    ctx.$('syncOperatorName').value = '';
                    await loadOperators();
                    ctx.toast('Operatore sincronizzato', 'Ora è disponibile tra i destinatari delle notifiche.');
                } catch (error) {
                    ctx.toast('Sincronizzazione fallita', error.message, 'error');
                } finally {
                    button.disabled = false;
                }
            }
return {get loadOperators(){return loadOperators;},get operatorOnline(){return operatorOnline;},get renderOperatorTable(){return renderOperatorTable;},get saveOperatorRole(){return saveOperatorRole;},get approveOperator(){return approveOperator;},get createOperator(){return createOperator;},get showAntennaQr(){return showAntennaQr;},get showBadge(){return showBadge;},get regenerateQr(){return regenerateQr;},get syncOperatorFromFirebase(){return syncOperatorFromFirebase;}};
}
