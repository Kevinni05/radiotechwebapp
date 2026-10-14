import {installWorkforce} from './features/workforce.js';
import {createFeature as create_profile} from './features/profile.js';
import {createFeature as create_map} from './features/map.js';
import {createFeature as create_dashboard} from './features/dashboard.js';
import {createFeature as create_assets} from './features/assets.js';
import {createFeature as create_tasks} from './features/tasks.js';
import {createFeature as create_reports} from './features/reports.js';
import {createFeature as create_operators} from './features/operators.js';
import {createFeature as create_notifications} from './features/notifications.js';
import {createFeature as create_inventory} from './features/inventory.js';
import {createFeature as create_system} from './features/system.js';
        (() => {
            "use strict";
            function debounce(callback,delay){let timer;return ()=>{clearTimeout(timer);timer=setTimeout(callback,delay);};}
const featureContext={get $(){return $;},
get CONFIG(){return CONFIG;},
get CSS(){return CSS;},
get URLSearchParams(){return URLSearchParams;},
get antennaSpecs(){return antennaSpecs;},
get api(){return api;},
get apiFetch(){return apiFetch;},
get applyPermissions(){return applyPermissions;},
get badgeClass(){return badgeClass;},
get canManageReports(){return canManageReports;},
get closeModal(){return closeModal;},
get confirm(){return window.RadioTechDialogs.confirm;},
get eligibleOperators(){return eligibleOperators;},
get escapeHtml(){return escapeHtml;},
get formatStatus(){return formatStatus;},
get initMap(){return initMap;},
get initials(){return initials;},
get loadStats(){return loadStats;},
get loadTasks(){return loadTasks;},
get loadingReports(){return feature_tasks.loadingReports;},set loadingReports(value){feature_tasks.loadingReports=value;},
get loadingTasks(){return feature_tasks.loadingTasks;},set loadingTasks(value){feature_tasks.loadingTasks=value;},
get number(){return number;},
get numberOrNull(){return numberOrNull;},
get openModal(){return openModal;},
get populateTaskSelectors(){return populateTaskSelectors;},
get prompt(){return window.RadioTechDialogs.prompt;},
get refreshReportTitles(){return refreshReportTitles;},
get reloadReports(){return feature_tasks.reloadReports;},set reloadReports(value){feature_tasks.reloadReports=value;},
get renderRecipients(){return renderRecipients;},
get reportAttachments(){return feature_reports.reportAttachments;},set reportAttachments(value){feature_reports.reportAttachments=value;},
get reportsCursor(){return feature_assets.reportsCursor;},set reportsCursor(value){feature_assets.reportsCursor=value;},
get setBackendState(){return setBackendState;},
get setText(){return setText;},
get showAntennaQr(){return showAntennaQr;},
get signatureWorkspace(){return signatureWorkspace;},
get state(){return state;},
get switchView(){return switchView;},
get tasksCursor(){return feature_assets.tasksCursor;},set tasksCursor(value){feature_assets.tasksCursor=value;},
get toast(){return toast;},
get updateMap(){return updateMap;}};

            /*
             * ============================================================
             * RADIO TECH CONTROL CENTER
             *
             * Il frontend è progettato per lavorare con il backend Spring.
             *
             * API principali:
             *   GET    /api/v1/dashboard/stats
             *   GET    /api/v1/dashboard/antenne
             *   PUT    /api/v1/dashboard/antenne/{id}
             *   DELETE /api/v1/dashboard/antenne/{id}
             *   GET    /api/v1/dashboard/operators
             *   GET    /api/v1/dashboard/notifications
             *   POST   /api/v1/dashboard/notifications
             *   GET    /api/v1/dashboard/profile
             *   PUT    /api/v1/dashboard/profile
             *   GET    /api/v1/dashboard/inventory
             *
             * IMPORTANTE:
             * L'autenticazione Firebase deve essere effettuata dal tuo
             * endpoint /api/v1/auth/login, che deve restituire almeno:
             * {
             *   "token": "FIREBASE_ID_TOKEN",
             *   "user": { ... }
             * }
             *
             * Se il tuo login ha un formato diverso, modifica SOLO login().
             * ============================================================
             */

            const CONFIG = Object.freeze({
                API: "/api/v1/dashboard",
                AUTH_LOGIN: "/api/v1/auth/login",
                AUTH_REFRESH: "/api/v1/auth/refresh",
                AUTH_VERIFY: "/api/v1/auth/verify",
                REQUEST_TIMEOUT: 12000,
                REFRESH_INTERVAL: 60000,
                STORAGE_TOKEN: "radiotech_control_token",
                STORAGE_REFRESH: "radiotech_control_refresh",
                STORAGE_USER: "radiotech_control_user",
                STORAGE_REMEMBER: "radiotech_control_remember"
            });

            const state = {
                token: null,
                user: null,
                stats: null,
                antennas: [],
                operators: [],
                operatorLoadError: "",
                notifications: [],
                tasks: [],
                inventory: [],
                inventoryMovements: [],
                map: null,
                markers: [],
                markerByAntennaId: new Map(),
                mapReady: false,
                editingAntennaId: null,
                refreshTimer: null,
                requestController: null
            };
            let enterprise = null;
            let companyHub = null;
            let management = null;
            let proSuite = null;
            let proSecurity = null;
            let signatureWorkspace = null;
            let chiefAccess = null;

            const $ = id => document.getElementById(id);

            function escapeHtml(value) {
                return String(value ?? "")
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;")
                    .replaceAll('"', "&quot;")
                    .replaceAll("'", "&#039;");
            }

            function number(value, digits = 1) {
                const n = Number(value);
                if (!Number.isFinite(n)) return "—";
                return n.toLocaleString("it-IT", {
                    minimumFractionDigits: digits,
                    maximumFractionDigits: digits
                });
            }

            function initials(name) {
                const parts = String(name || "Capo RadioTech").trim().split(/\s+/).filter(Boolean);
                return parts.slice(0, 2).map(x => x[0].toUpperCase()).join("") || "C";
            }

            function toast(title, message = "", type = "success") {
                const el = document.createElement("div");
                el.className = `toast ${type}`;
                el.innerHTML = `<strong>${escapeHtml(title)}</strong><span>${escapeHtml(message)}</span>`;
                $("toastStack").appendChild(el);
                setTimeout(() => el.remove(), 4500);
            }

            function setText(id, value) {
                const el = $(id);
                if (el) el.textContent = value ?? "—";
            }

            function badgeClass(status) {
                const s = String(status || "").toUpperCase();
                if (["ATTIVA", "COMPLETED", "ATTIVO", "ONLINE"].includes(s)) return "green";
                if (["MANUTENZIONE", "IN_PROGRESS", "PENDING"].includes(s)) return "yellow";
                if (["OFFLINE", "CANCELLED", "INATTIVO"].includes(s)) return "red";
                return "gray";
            }

            function formatStatus(status) {
                return window.RadioTechItalian?.label(status) ?? String(status || "N/D").replaceAll("_", " ");
            }

            function numberOrNull(value) {
                if (value === "" || value == null) return null;
                const n = Number(value);
                return Number.isFinite(n) ? n : null;
            }

            function purgeLegacyTokens() {
                for (const storage of [localStorage, sessionStorage]) {
                    for (const key of [CONFIG.STORAGE_TOKEN, CONFIG.STORAGE_REFRESH, CONFIG.STORAGE_USER]) {
                        try { storage.removeItem(key); } catch (_) { /* Memory-only session remains usable. */ }
                    }
                }
            }

            async function getStoredSession() {
                purgeLegacyTokens();
                const epoch = state.sessionEpoch || 0;
                try {
                    const result = await window.RadioTechFederation?.restoreSession();
                    if (!result || epoch !== (state.sessionEpoch || 0)) return false;
                    if (result.role === "CUSTOMER") { window.location.assign("/portal"); return false; }
                    saveSession(result.token, { ...result.user, role: result.role }, localStorage.getItem(CONFIG.STORAGE_REMEMBER) === "true");
                    return true;
                } catch (_) { return false; }
            }

            function saveSession(token, user, remember, refreshToken = null) {
                const identityOf = account => JSON.stringify([account?.tenantId, account?.uid || account?.id || account?.email]);
                if (state.user && identityOf(state.user) !== identityOf(user)) {
                    state.sessionEpoch = (state.sessionEpoch || 0) + 1;
                    window.dispatchEvent(new Event('radiotech:identity-changed'));
                    featureContext.reportAttachments?.clear();
                    document.querySelectorAll(".report-delete-dialog, .report-material-review").forEach(dialog => dialog.close());
                }
                state.token = token;
                // Legacy server login may renew during this page lifetime; never persist its refresh token.
                state.refreshToken = refreshToken;
                state.user = user || null;
                state.authRole = user?.role || null;
                chiefAccess?.syncRole();
                signatureWorkspace?.syncIdentity();
                window.dispatchEvent(new Event('radiotech:session-ready'));
                purgeLegacyTokens();
                try { localStorage.setItem(CONFIG.STORAGE_REMEMBER, String(remember)); } catch (_) {}
            }

            window.addEventListener("radiotech:auth-ended", () => {
                clearSession();
                showLogin("Sessione terminata o account cambiato. Accedi nuovamente.");
            });

            function clearSession() {
                window.RadioTechDialogs?.cancelAll();
                signatureWorkspace?.reset();
                chiefAccess?.reset();
                closeModal("passwordModal");
                window.dispatchEvent(new Event('radiotech:identity-changed'));
                document.querySelectorAll(".report-delete-dialog, .report-material-review").forEach(dialog => dialog.close());
                featureContext.reportAttachments?.clear();
                state.token = null;
                state.user = null;
                state.authRole = null;
                state.refreshToken = null;
                state.sessionEpoch = (state.sessionEpoch || 0) + 1;
                enterprise?.reset();
                companyHub?.reset();
                proSuite?.reset();
                proSecurity?.reset();
                management?.reset();
                purgeLegacyTokens();
                try { localStorage.removeItem(CONFIG.STORAGE_REMEMBER); } catch (_) {}
            }

            function getSessionToken() { return state.token; }

            let sessionRefresh = null;
            function refreshSession() {
                if (!sessionRefresh) sessionRefresh = performSessionRefresh().finally(() => { sessionRefresh = null; });
                return sessionRefresh;
            }
            async function performSessionRefresh() {
                const epoch = state.sessionEpoch || 0;
                const remember = localStorage.getItem(CONFIG.STORAGE_REMEMBER) === "true";
                if (window.RadioTechFederation?.refreshSession) {
                    try {
                        const restored = await window.RadioTechFederation.refreshSession();
                        if (restored) {
                            if (epoch !== (state.sessionEpoch || 0)) return false;
                            saveSession(restored.token, { ...restored.user, role: restored.role }, remember);
                            return true;
                        }
                    } catch (_) { return false; }
                }
                const refreshToken = state.refreshToken;
                if (!refreshToken) return false;
                const response = await fetch(CONFIG.AUTH_REFRESH, {
                    method: "POST",
                    headers: { "Content-Type": "application/json", "Accept": "application/json" },
                    body: JSON.stringify({ refreshToken }),
                    cache: "no-store"
                });
                const body = await response.json().catch(() => null);
                if (!response.ok) return false;
                const data = body?.data || body;
                const token = data?.token || data?.idToken;
                if (!token || epoch !== (state.sessionEpoch || 0)) return false;
                saveSession(token, data.user || state.user, remember, data.refreshToken || refreshToken);
                return true;
            }

            async function apiFetch(url, options = {}) {
                const { _retried = false, responseType, maxResponseBytes, timeoutMs = CONFIG.REQUEST_TIMEOUT, ...requestOptions } = options;
                const privatePackage=url.startsWith('/api/v1/distribution/');
                const responseLimit=privatePackage&&maxResponseBytes===150*1024*1024?maxResponseBytes:10000000;
                const controller = new AbortController();
                const timeout = setTimeout(() => controller.abort(), Math.min(privatePackage?180000:60000, timeoutMs));

                const token = getSessionToken();
                const epoch = state.sessionEpoch || 0;

                const headers = {
                    "Accept": "application/json",
                    ...(options.body ? { "Content-Type": "application/json" } : {}),
                    ...(options.headers || {})
                };

                if (token) headers.Authorization = `Bearer ${token}`;

                try {
                    const response = await fetch(url, {
                        ...requestOptions,
                        headers,
                        signal: controller.signal,
                        cache: "no-store"
                    });

                    if (epoch !== (state.sessionEpoch || 0)) throw new Error("Sessione cambiata: richiesta annullata.");
                    if (response.ok && responseType === 'bytes' && !response.headers.get('Content-Type')?.includes('json')) {
                        if (Number(response.headers.get('Content-Length')) > responseLimit) throw new Error('File troppo grande.');
                        const reader=response.body.getReader(),chunks=[];let length=0;
                        while(true){const part=await reader.read();if(part.done)break;length+=part.value.length;if(length>responseLimit){await reader.cancel();throw new Error('File troppo grande.');}chunks.push(part.value);}
                        if(epoch!==(state.sessionEpoch||0))throw new Error('Sessione cambiata.');
                        const bytes=new Uint8Array(length);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
                        const disposition=response.headers.get('Content-Disposition')||'';
                        const encoded=/filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
                        const name=encoded?decodeURIComponent(encoded):(/filename="([^"]+)"/i.exec(disposition)?.[1]||'allegato');
                        return {name,bytes};
                    }
                    let body = null;
                    const text = await response.text();
                    if (text) {
                        try { body = JSON.parse(text); } catch (_) { body = text; }
                    }

                    if (response.status === 401) {
                        if (!_retried && await refreshSession()) return apiFetch(url, { ...options, _retried: true });
                        clearSession();
                        showLogin("Sessione scaduta. Effettua nuovamente l'accesso.");
                        throw new Error("Sessione non autorizzata o scaduta.");
                    }

                    if (!response.ok) {
                        const message = (body?.message || `HTTP ${response.status}`) + (response.headers.get("X-Request-Id") ? ` · Rif. ${response.headers.get("X-Request-Id")}` : "");
                        const error = new Error(message);
                        error.status=response.status;error.fieldErrors=body?.fieldErrors||{};error.retryable=body?.retryable===true; throw error;
                    }

                    if (epoch !== (state.sessionEpoch || 0)) throw new Error("Sessione cambiata: richiesta annullata.");
                    return body;
                } catch (error) {
                    if (error.name === "AbortError") {
                        throw new Error("Timeout: il backend non ha risposto in tempo.");
                    }
                    throw error;
                } finally {
                    clearTimeout(timeout);
                }
            }

            async function api(path, options = {}) {
                return apiFetch(`${CONFIG.API}${path}`, options);
            }

            /* ---------- LOGIN ---------- */

            async function login(event) {
                event.preventDefault();

                const email = $("loginEmail").value.trim();
                const password = $("loginPassword").value;
                const remember = $("rememberLogin").checked;
                const button = $("loginButton");
                const status = $("loginStatus");

                if (!email || !password) return;

                button.disabled = true;
                button.textContent = "Accesso in corso...";
                status.textContent = "Verifica delle credenziali...";

                try {
                    /*
                     * Se il tuo /api/v1/auth/login usa un payload differente,
                     * modifica questa sola sezione.
                     */
                    const federationReady = window.RadioTechFederation && await window.RadioTechFederation.initialize();
                    const result = federationReady ? new Response(JSON.stringify(await window.RadioTechFederation.login(null,email,password,remember)), {status:200,headers:{"Content-Type":"application/json"}}) : await fetch(CONFIG.AUTH_LOGIN, {
                        method: "POST",
                        headers: { "Content-Type": "application/json", "Accept": "application/json" },
                        body: JSON.stringify({ email, password })
                    });

                    const body = await result.json().catch(() => null);

                    if (!result.ok) {
                        const fallback = result.status === 403
                            ? "Il server blocca questo indirizzo web. Verifica la configurazione del link HTTPS."
                            : result.status >= 500
                                ? "Il server non è disponibile. Verifica che il backend e il tunnel siano attivi."
                                : result.status === 401 ? "Credenziali non valide." : `Accesso non riuscito (HTTP ${result.status}).`;
                        throw new Error(body?.message || fallback);
                    }

                    const token =
                        body?.token ||
                        body?.idToken ||
                        body?.firebaseToken ||
                        body?.data?.token;

                    if (!token) {
                        throw new Error("Il backend non ha restituito un Firebase ID token.");
                    }

                    const user = { ...(body?.user || body?.data?.user || { email }), role: body?.role || body?.data?.role || body?.user?.role };

                    if (user.role === "CUSTOMER") { window.location.assign("/portal"); return; }
                    saveSession(token, user, remember, body?.refreshToken || body?.data?.refreshToken);
                    $("loginPassword").value = "";
                    await startApplication();

                } catch (error) {
                    console.error(error);
                    status.textContent = error.message;
                    toast("Accesso negato", error.message, "error");
                } finally {
                    button.disabled = false;
                    button.textContent = "Accedi al Centro operativo";
                }
            }

            window.addEventListener("radiotech:sso", async event => {
                const body=event.detail;
                if(body.role==="CUSTOMER") { window.location.assign("/portal"); return; }
                saveSession(body.token, {...body.user,role:body.role},false,body.refreshToken);
                await startApplication();
            });

            function showLogin(message = "") {
                $("app").classList.remove("ready");
                $("loginScreen").style.display = "grid";
                if (state.refreshTimer) clearInterval(state.refreshTimer);
                state.refreshTimer = null;
                if (message) $("loginStatus").textContent = message;
            }

            async function startApplication() {
                $("loginScreen").style.display = "none";
                $("app").classList.add("ready");

                hydrateUser();
                await refreshAll();

                if (state.refreshTimer) clearInterval(state.refreshTimer);
                state.refreshTimer = setInterval(() => {
                    if (state.token && !document.hidden) refreshAll(true);
                }, CONFIG.REFRESH_INTERVAL);
            }

            const feature_profile=create_profile(featureContext);
function applyPermissions(...args){return feature_profile.applyPermissions(...args);}
function hydrateUser(...args){return feature_profile.hydrateUser(...args);}
function persistUser(...args){return feature_profile.persistUser(...args);}
function loadProfile(...args){return feature_profile.loadProfile(...args);}
function saveProfile(...args){return feature_profile.saveProfile(...args);}
function openProfileModal(...args){return feature_profile.openProfileModal(...args);}

const feature_map=create_map(featureContext);
function initMap(...args){return feature_map.initMap(...args);}
function updateMap(...args){return feature_map.updateMap(...args);}

const feature_dashboard=create_dashboard(featureContext);
function loadStats(...args){return feature_dashboard.loadStats(...args);}
function loadSystemHealth(...args){return feature_dashboard.loadSystemHealth(...args);}
function setBackendState(...args){return feature_dashboard.setBackendState(...args);}

const feature_assets=create_assets(featureContext);
function loadAntennas(...args){return feature_assets.loadAntennas(...args);}
function renderAntennas(...args){return feature_assets.renderAntennas(...args);}
function focusAntenna(...args){return feature_assets.focusAntenna(...args);}
function openAntennaModal(...args){return feature_assets.openAntennaModal(...args);}
function antennaSpecs(...args){return feature_assets.antennaSpecs(...args);}
function saveAntenna(...args){return feature_assets.saveAntenna(...args);}
function deleteAntenna(...args){return feature_assets.deleteAntenna(...args);}

const feature_tasks=create_tasks(featureContext);
function loadTasks(...args){return feature_tasks.loadTasks(...args);}
function eligibleOperators(...args){return feature_tasks.eligibleOperators(...args);}
function populateTaskSelectors(...args){return feature_tasks.populateTaskSelectors(...args);}
function renderTaskList(...args){return feature_tasks.renderTaskList(...args);}
function createTask(...args){return feature_tasks.createTask(...args);}

const feature_reports=create_reports(featureContext);
function attachmentViewer(...args){return feature_reports.attachmentViewer(...args);}
function reportDisplayTitle(...args){return feature_reports.reportDisplayTitle(...args);}
function reportDisplaySummary(...args){return feature_reports.reportDisplaySummary(...args);}
function refreshReportTitles(...args){return feature_reports.refreshReportTitles(...args);}
function loadReports(...args){return feature_reports.loadReports(...args);}
function canManageReports(...args){return feature_reports.canManageReports(...args);}
function renderReportRow(...args){return feature_reports.renderReportRow(...args);}
function deleteReport(...args){return feature_reports.deleteReport(...args);}
function reportDetails(...args){return feature_reports.reportDetails(...args);}
function reviewReport(...args){return feature_reports.reviewReport(...args);}

async function telecomPost(path, payload) { return apiFetch(`/api/v1/telecom-tools${path}`, { method: "POST", body: JSON.stringify(payload) }); }

            async function calculateLinkBudget() {
                try {
                    const r = await telecomPost("/link-budget", { frequencyMHz: Number($("tbFreq").value), distanceKm: Number($("tbDist").value), txPowerDbm: Number($("tbTx").value), txGainDbi: Number($("tbGt").value), rxGainDbi: Number($("tbGr").value), txCableLossDb: Number($("tbLt").value || 0), rxCableLossDb: Number($("tbLr").value || 0), otherLossDb: Number($("tbLo").value || 0), rxSensitivityDbm: $("tbSens").value === "" ? undefined : Number($("tbSens").value) });
                    $("tbResult").innerHTML = `<strong>FSPL:</strong> ${number(r.fsplDb, 2)} dB<br><strong>EIRP:</strong> ${number(r.eirpDbm, 2)} dBm<br><strong>Potenza ricevuta:</strong> ${number(r.receivedPowerDbm, 2)} dBm<br><strong>Margine:</strong> ${r.linkMarginDb == null ? "—" : number(r.linkMarginDb, 2) + " dB"}<br><span class="subtle">${escapeHtml(r.formula)}</span>`;
                } catch (e) { toast("Calcolo non valido", e.message, "error"); }
            }
            async function calculatePower() { try { const payload = $("tbDbm").value !== "" ? { dbm: Number($("tbDbm").value) } : { w: Number($("tbW").value) }; const r = await telecomPost("/power", payload); $("tbPowerResult").textContent = `${number(r.dbm, 2)} dBm · ${number(r.mw, 3)} mW · ${number(r.w, 6)} W`; } catch (e) { toast("Conversione non valida", e.message, "error"); } }
            async function calculateVswr() { try { const r = await telecomPost("/vswr", { vswr: Number($("tbVswr").value) }); $("tbVswrResult").textContent = `Γ ${number(r.reflectionCoefficient, 4)} · Perdita di ritorno ${number(r.returnLossDb, 2)} dB`; } catch (e) { toast("VSWR non valido", e.message, "error"); } }

            const feature_operators=create_operators(featureContext);
function loadOperators(...args){return feature_operators.loadOperators(...args);}
function operatorOnline(...args){return feature_operators.operatorOnline(...args);}
function renderOperatorTable(...args){return feature_operators.renderOperatorTable(...args);}
function saveOperatorRole(...args){return feature_operators.saveOperatorRole(...args);}
function approveOperator(...args){return feature_operators.approveOperator(...args);}
function createOperator(...args){return feature_operators.createOperator(...args);}
function showAntennaQr(...args){return feature_operators.showAntennaQr(...args);}
function showBadge(...args){return feature_operators.showBadge(...args);}
function regenerateQr(...args){return feature_operators.regenerateQr(...args);}
function syncOperatorFromFirebase(...args){return feature_operators.syncOperatorFromFirebase(...args);}

const feature_notifications=create_notifications(featureContext);
function renderRecipients(...args){return feature_notifications.renderRecipients(...args);}
function loadNotifications(...args){return feature_notifications.loadNotifications(...args);}
function renderNotifications(...args){return feature_notifications.renderNotifications(...args);}
function updateNotificationCount(...args){return feature_notifications.updateNotificationCount(...args);}
function sendNotification(...args){return feature_notifications.sendNotification(...args);}

const feature_inventory=create_inventory(featureContext);
function loadInventory(...args){return feature_inventory.loadInventory(...args);}
function renderInventory(...args){return feature_inventory.renderInventory(...args);}
function inventoryQty(...args){return feature_inventory.inventoryQty(...args);}
function inventoryIsLow(...args){return feature_inventory.inventoryIsLow(...args);}
function openInventoryItem(...args){return feature_inventory.openInventoryItem(...args);}
function saveInventoryItem(...args){return feature_inventory.saveInventoryItem(...args);}
function openInventoryMovement(...args){return feature_inventory.openInventoryMovement(...args);}
function updateInventoryMovementHint(...args){return feature_inventory.updateInventoryMovementHint(...args);}
function saveInventoryMovement(...args){return feature_inventory.saveInventoryMovement(...args);}
function loadInventoryMovements(...args){return feature_inventory.loadInventoryMovements(...args);}
function renderInventoryMovements(...args){return feature_inventory.renderInventoryMovements(...args);}
function archiveInventoryItem(...args){return feature_inventory.archiveInventoryItem(...args);}
function restoreInventoryItem(...args){return feature_inventory.restoreInventoryItem(...args);}
function exportInventoryCsv(...args){return feature_inventory.exportInventoryCsv(...args);}

/* ---------- NAVIGATION ---------- */

            function switchView(view) {
                document.querySelectorAll(".view").forEach(v => v.classList.remove("active"));
                document.querySelectorAll(".nav button").forEach(b => b.classList.remove("active"));

                const target = $(`view-${view}`);
                const nav = document.querySelector(`.nav button[data-view="${view}"]`);

                if (target) target.classList.add("active");
                if (nav) nav.classList.add("active");
                window.dispatchEvent(new CustomEvent('radiotech:view-changed', {detail:{view}}));

                const titles = {
                    dashboard: ["Panoramica", "Monitoraggio infrastruttura radio e operazioni tecniche"],
                    antennas: ["Antenne", "Gestione e controllo dell'infrastruttura"],
                    operations: ["Operatività", "Incarichi e indicatori della squadra"],
                    notifications: ["Centro notifiche", "Comunicazioni operative ai dipendenti"],
                    inventory: ["Inventario", "Materiali e componenti disponibili"],
                    branding: ["Personalizzazione", "Identità aziendale, colori ed etichette del prodotto"],
                    profile: ["Profilo del Capo", "Identità e impostazioni del responsabile"],
                    system: ["Stato sistema", "Backend, Firestore e sessione"],
                    tools: ["TelcoTools", "Calcolo e verifica dei collegamenti radio"],
                    enterprise: ["Rischi e competenze", "Incidenti, SLA e abilitazioni della squadra"],
                    ai: ["Pianificazione preventiva", "Scadenze, priorità e manutenzione programmata"],
                    attendance: ["Presenze", "Timbrature, validazione e incongruenze"],
                    rostering: ["Turnistica", "Pianificazione mensile, vincoli e copertura"],
                    people: ["Squadra e sicurezza", "Turni, pause, segnalazioni e supporto alla squadra"],
                    operators: ["Operatori", "Anagrafica, accessi e abilitazione della squadra"],
                    reports: ["Centro report", "Revisione dei resoconti degli interventi"],
                    planning: ["Agenda interventi", "Scadenze, priorità e pianificazione"],
                    workload: ["Carico della squadra", "Distribuzione degli incarichi attivi"],
                    supplies: ["Approvvigionamenti", "Scorte da reintegrare e costi disponibili"],
                    pro: ["Enterprise Pro", "Clienti, contratti, manutenzione, acquisti e consuntivi"],
                    access: ["Sicurezza e accessi", "Account clienti, secondo fattore e revoca delle sessioni"]
                };

                setText("pageTitle", titles[view]?.[0] || "Panoramica");
                setText("pageSubtitle", titles[view]?.[1] || "");
                signatureWorkspace?.onNavigate(view);

                if (view === "dashboard") {
                    initMap();
                    setTimeout(() => {
                        state.map?.invalidateSize();
                        updateMap();
                    }, 80);
                }

                if (view === "antennas") loadAntennas().catch(() => { });
                if (view === "operations") Promise.all([loadOperators(), loadTasks()]).catch(() => { });
                if (view === "notifications") {
                    loadOperators().catch(() => { });
                    loadNotifications().catch(() => { });
                }
                if (view === "inventory") loadInventory().catch(() => { });
                if (view === "profile") loadProfile().catch(() => { });
                if (view === "system") { loadAudit().catch(() => { }); loadOperationalHealth(); }

                $("sidebar").classList.remove("open");
                window.scrollTo({ top: 0, behavior: "smooth" });
            }

            const feature_system=create_system(featureContext);
function loadOperationalHealth(...args){return feature_system.loadOperationalHealth(...args);}
function checkSystem(...args){return feature_system.checkSystem(...args);}
function loadAudit(...args){return feature_system.loadAudit(...args);}

/* ---------- REFRESH ---------- */

            const refreshCoordinator = window.RadioTechRefreshCoordinator({
                run: runRefresh,
                canRefresh: () => {
                    const editing = document.activeElement?.matches('input, textarea, select, [contenteditable="true"]');
                    return Boolean(state.token) && !document.hidden && !editing &&
                        !document.querySelector('.modal-backdrop.open, dialog[open]');
                }
            });

            function refreshAll(silent = false) {
                return refreshCoordinator.refresh(silent);
            }

            document.addEventListener('visibilitychange', () => {
                if (!document.hidden && state.token) refreshAll(true);
            });

            async function runRefresh(silent = false) {
                if (!state.token) return;

                const view = document.querySelector('.view.active')?.id.replace('view-', '') || 'dashboard';
                const visibleLoaders = {
                    dashboard: [loadStats, loadAntennas, loadTasks],
                    antennas: [loadAntennas],
                    operations: [loadStats, loadTasks, loadOperators],
                    operators: [loadOperators],
                    reports: [loadReports],
                    inventory: [loadInventory],
                    profile: [loadProfile],
                    system: [loadAudit, loadOperationalHealth]
                };
                const loaders = silent
                    ? [...(visibleLoaders[view] || []), loadNotifications]
                    : [loadStats, loadAntennas, loadNotifications, loadProfile, loadReports, loadTasks, loadOperators];
                const tasks = [...new Set(loaders)].map(loader => Promise.resolve().then(() => loader()));
                tasks.push(management?.refreshCurrent());

                const results = await Promise.allSettled(tasks);
                const failed = results.filter(x => x.status === "rejected");

                if (!silent) {
                    if (failed.length) {
                        toast("Sincronizzazione parziale",
                            `${failed.length} servizio/i non hanno risposto.`,
                            "error");
                    } else {
                        toast("Centro operativo aggiornato", "Dati sincronizzati con il backend.");
                    }
                }

                applyPermissions();
                if (state.map) updateMap();
            }

            async function savePassword(event) {
                event.preventDefault();
                const currentPassword = $("currentPassword").value;
                const newPassword = $("newPassword").value;
                const status = $("passwordStatus");
                if (newPassword !== $("confirmPassword").value) {
                    status.textContent = "La nuova password e la conferma non coincidono.";
                    return;
                }
                if (newPassword.length < 8 || !/[A-Z]/.test(newPassword) || !/[a-z]/.test(newPassword)
                    || !/\d/.test(newPassword) || !/[^A-Za-z0-9]/.test(newPassword)) {
                    status.textContent = "La nuova password deve contenere almeno 8 caratteri, maiuscola, minuscola, numero e simbolo.";
                    return;
                }
                if (newPassword === currentPassword) {
                    status.textContent = "La nuova password deve essere diversa da quella attuale.";
                    return;
                }
                const button = $("savePasswordBtn");
                if (button.disabled) return;
                button.disabled = true;
                status.textContent = "Verifica e aggiornamento in corso...";
                try {
                    await apiFetch("/api/v1/auth/change-password", {
                        method: "POST", body: JSON.stringify({ currentPassword, newPassword })
                    });
                    closeModal("passwordModal");
                    signOut();
                    if (window.RadioTechFederation) await window.RadioTechFederation.logout().catch(() => {});
                    showLogin("Password aggiornata. Accedi con la nuova password.");
                    toast("Password aggiornata", "Usa la nuova password per accedere.");
                } catch (error) {
                    status.textContent = error.message;
                } finally {
                    $("passwordForm").reset();
                    button.disabled = false;
                }
            }

            /* ---------- MODALS ---------- */

            function openModal(id) {
                $(id).classList.add("open");
                document.body.classList.add("modal-open");
            }

            function closeModal(id) {
                if (id === "passwordModal") { $("passwordForm").reset(); $("passwordStatus").textContent = ""; }
                $(id).classList.remove("open");
                if (!document.querySelector(".modal-backdrop.open")) {
                    document.body.classList.remove("modal-open");
                }
            }

            /* ---------- EVENTS ---------- */

            $("loginForm").addEventListener("submit", login);
            $("changePasswordBtn").addEventListener("click", () => {
                window.RadioTechChiefAccess.open("password", () => {
                    $("passwordForm").reset(); $("passwordStatus").textContent = "";
                    openModal("passwordModal"); $("currentPassword").focus();
                }).catch(e => toast("Cambio password", e.message, "error"));
            });
            $("passwordForm").addEventListener("submit", savePassword);

            $("protectedChiefLogin").addEventListener("click", async () => {
                const button = $("protectedChiefLogin");
                button.disabled = true;
                $("loginStatus").textContent = "Completa l'accesso nella finestra protetta.";
                try {
                    const idToken = await window.RadioTechChiefAccess.open('login');
                    const response = await fetch(CONFIG.AUTH_LOGIN, { method: 'POST',
                        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ idToken }) });
                    const data = await response.json();
                    if (!response.ok) throw new Error(data.message || 'Accesso non autorizzato.');
                    saveSession(idToken, { ...data.user, role: data.role }, false, null);
                    $("loginPassword").value = '';
                    await startApplication();
                } catch (e) { $("loginStatus").textContent = e.message; }
                finally { button.disabled = false; }
            });

            $("togglePassword").addEventListener("click", () => {
                const input = $("loginPassword");
                input.type = input.type === "password" ? "text" : "password";
            });

            $("mobileMenu").addEventListener("click", () => $("sidebar").classList.toggle("open"));

            document.querySelectorAll(".nav button[data-view]").forEach(button => {
                button.addEventListener("click", () => switchView(button.dataset.view));
            });

            // The header is sticky: scrolling it into view does not move the document.
            window.RadioTechScrollTop = function() {
                const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth';
                document.scrollingElement?.scrollTo({top:0,left:0,behavior});
                window.scrollTo({top:0,left:0,behavior});
            };
            document.querySelectorAll("[data-footer-view]").forEach(button => button.addEventListener("click", () => {
                switchView(button.dataset.footerView);
                window.RadioTechScrollTop();
            }));
            $("footerBackTop").addEventListener("click", () => window.RadioTechScrollTop());
            $("footerYear").textContent = String(new Date().getFullYear());
            function updateFooterStatus() {
                const status = $("backendStatus").textContent.trim().toUpperCase();
                const unavailable = ['ERROR', 'ERRORE'].includes(status);
                $("footerServiceStatus").textContent = status === 'OK' ? 'Backend disponibile' : unavailable ? 'Backend non disponibile' : 'Verifica del servizio in corso';
                document.querySelector('.footer-service').dataset.status = status === 'OK' ? 'up' : unavailable ? 'down' : 'pending';
                $("footerLastUpdate").textContent = $("lastUpdate").textContent;
            }
            const footerObserver = new MutationObserver(updateFooterStatus);
            for (const id of ['backendStatus', 'lastUpdate']) footerObserver.observe($(id), {childList:true,subtree:true,characterData:true});
            updateFooterStatus();

            $("refreshBtn").addEventListener("click", () => refreshAll(false));
            $("systemRefreshBtn").addEventListener("click", checkSystem);
            $('operationsHealthRefresh').addEventListener('click', loadOperationalHealth);
            $('tasksMoreBtn').addEventListener('click', () => loadTasks(true));
            $('reportsMoreBtn').addEventListener('click', () => loadReports(true));
            $("auditRefreshBtn")?.addEventListener("click", () => loadAudit().catch(() => { }));

            $("quickAntennaBtn").addEventListener("click", () => openAntennaModal());
            $("newAntennaBtn").addEventListener("click", () => openAntennaModal());
            $("quickNotificationBtn").addEventListener("click", () => switchView("notifications"));
            $("newNotificationBtn").addEventListener("click", () => switchView("notifications"));

            $("reloadAntennasBtn").addEventListener("click", () => loadAntennas().catch(() => { }));
            $("antennaSearch").addEventListener("input", debounce(()=>loadAntennas().catch(()=>{}),350));
            $("antennaStatusFilter").addEventListener("change",()=>loadAntennas().catch(()=>{}));

            $("antennaForm").addEventListener("submit", saveAntenna);
            $("closeAntennaModal").addEventListener("click", () => closeModal("antennaModal"));
            $("cancelAntenna").addEventListener("click", () => closeModal("antennaModal"));

            $("editProfileBtn").addEventListener("click", openProfileModal);
            $("profileForm").addEventListener("submit", saveProfile);
            $("closeProfileModal").addEventListener("click", () => closeModal("profileModal"));
            $("cancelProfile").addEventListener("click", () => closeModal("profileModal"));

            $("reportsRefreshBtn")?.addEventListener("click", loadReports);
            $("createTaskBtn")?.addEventListener("click", createTask);
            $("operatorsRefreshBtn")?.addEventListener("click", () => loadOperators().catch(() => { }));
            $("newOperatorBtn")?.addEventListener("click", () => {
                $("operatorForm").reset();
                $("operatorCredentialsResult").textContent = "";
                openModal("operatorModal");
            });
            $("operatorForm")?.addEventListener("submit", createOperator);
            $("closeOperatorModal")?.addEventListener("click", () => closeModal("operatorModal"));
            $("cancelOperator")?.addEventListener("click", () => closeModal("operatorModal"));
            $("closeBadgeModal")?.addEventListener("click", () => closeModal("badgeModal"));
            $("closeBadge")?.addEventListener("click", () => closeModal("badgeModal"));
            $("closeAntennaQrModal")?.addEventListener("click", () => closeModal("antennaQrModal"));
            $("closeAntennaQr")?.addEventListener("click", () => closeModal("antennaQrModal"));
            $("printBadge")?.addEventListener("click", () => {
                const printWindow = window.open("", "_blank", "width=500,height=650");
                if (!printWindow) return;
                printWindow.document.write(`<html><head><title>Badge RadioTech</title><style>body{font-family:Arial;text-align:center;padding:30px}img{width:280px}code{display:block;margin-top:14px}</style>    <link rel="stylesheet" href="/assets/operations-upgrade.css">
</head><body>${$("badgePrintArea").innerHTML}</body></html>`);
                printWindow.document.close();
                printWindow.focus();
                printWindow.print();
            });
            $("printAntennaQr")?.addEventListener("click", () => {
                const printWindow = window.open("", "_blank", "width=520,height=680");
                if (!printWindow) return;
                printWindow.document.write(`<html><head><title>QR Stazione RadioTech</title><style>body{font-family:Arial,sans-serif;text-align:center;padding:30px}img{width:260px;margin-bottom:15px}h3{margin:8px 0}p{color:#555;margin:4px 0}code{display:inline-block;margin-top:10px;font-size:14px;background:#eee;padding:4px 8px;border-radius:4px}</style>    <link rel="stylesheet" href="/assets/operations-upgrade.css">
</head><body>${$("antennaQrPrintArea").innerHTML}</body></html>`);
                printWindow.document.close();
                printWindow.focus();
                printWindow.print();
            });
            $("taskSearch")?.addEventListener("input", () => {renderTaskList();clearTimeout(window.radioTechTaskSearchTimer);window.radioTechTaskSearchTimer=setTimeout(()=>loadTasks(),350);});
            $("tbLinkBtn")?.addEventListener("click", calculateLinkBudget);
            $("tbPowerBtn")?.addEventListener("click", calculatePower);
            $("tbVswrBtn")?.addEventListener("click", calculateVswr);

            $("notificationTarget").addEventListener("change", event => {
                $("recipientBox").classList.toggle("hidden", event.target.value !== "SELECTED");
                if (event.target.value === "SELECTED") loadOperators().catch(() => { });
            });

            $("sendNotificationBtn").addEventListener("click", sendNotification);
            $("syncOperatorBtn")?.addEventListener("click", syncOperatorFromFirebase);
            $("reloadNotificationsBtn").addEventListener("click", () => loadNotifications().catch(() => { }));
            $("inventoryRefreshBtn").addEventListener("click", () => loadInventory().catch(() => { }));
            $("newInventoryItemBtn")?.addEventListener("click", () => openInventoryItem());
            $("inventoryItemForm")?.addEventListener("submit", saveInventoryItem);
            $("inventoryMovementForm")?.addEventListener("submit", saveInventoryMovement);
            $("inventoryMovementType")?.addEventListener("change", updateInventoryMovementHint);
            $("inventoryMovementsBtn")?.addEventListener("click", async () => {
                openModal("inventoryMovementsHistoryModal");
                await loadInventoryMovements();
            });
            $("inventoryExportBtn")?.addEventListener("click", exportInventoryCsv);
            $("inventorySearch")?.addEventListener("input", debounce(()=>loadInventory(),350));
            $("inventoryCategoryFilter")?.addEventListener("change",()=>loadInventory());
            $("inventoryStockFilter")?.addEventListener("change",()=>loadInventory());
            document.querySelectorAll("[data-close-modal]").forEach(button => button.addEventListener("click", () => closeModal(button.dataset.closeModal)));

            $("notificationBell").addEventListener("click", () => switchView("notifications"));

            $("centerMapBtn").addEventListener("click", () => {
                if (!state.map) initMap();
                updateMap();
            });

            function signOut() {
                window.RadioTechFederation?.logout().catch(() => {});
                clearSession();
                state.tasks = []; state.antennas = []; state.operators = []; state.notifications = []; state.inventory = [];
                $("loginPassword").value = "";
                if (state.refreshTimer) clearInterval(state.refreshTimer);
                state.refreshTimer = null;
                showLogin("Sei uscito dal Centro operativo.");
                toast("Sessione chiusa", "L'accesso è stato rimosso da questo dispositivo.");
            }
            $("logoutBtn")?.addEventListener("click", signOut);
            $("topbarLogoutBtn")?.addEventListener("click", signOut);
            $("operatorSearch")?.addEventListener("input", debounce(()=>loadOperators(),350));
            $("operatorRoleFilter")?.addEventListener("change",()=>loadOperators());

            document.querySelectorAll(".modal-backdrop").forEach(backdrop => {
                backdrop.addEventListener("click", event => {
                    if (event.target === backdrop) closeModal(backdrop.id);
                });
            });

            document.addEventListener("keydown", event => {
                if (event.key === "Escape") {
                    document.querySelectorAll(".modal-backdrop.open").forEach(x => closeModal(x.id));
                }
            });

            window.addEventListener("resize", () => {
                if (state.map) setTimeout(() => state.map.invalidateSize(), 100);
            });

            /* ---------- BOOT ---------- */

            async function boot() {
                /*
                 * Per sicurezza, il token persistente viene riutilizzato
                 * solamente se "Mantieni l'accesso" era stato scelto.
                 */
                if (await getStoredSession()) {
                    try {
                        await startApplication();
                        return;
                    } catch (error) {
                        console.warn("Persistent session failed:", error);
                        clearSession();
                    }
                }

                showLogin();
            }

            window.addEventListener("DOMContentLoaded", () => {
                chiefAccess = window.RadioTechChiefAccess.install({apiFetch, toast, currentRole: () => state.authRole});
                enterprise = window.RadioTechEnterprise({ apiFetch, state, escapeHtml, toast, switchView });
                companyHub = window.RadioTechCompanyHub({ apiFetch, state, escapeHtml, toast, switchView });
                installWorkforce({ apiFetch, state, escapeHtml, toast, switchView });
                proSuite = window.RadioTechPro({ apiFetch, state, escapeHtml, toast, switchView });
                proSecurity = window.RadioTechProSecurity({ apiFetch, toast, switchView });
                window.RadioTechTelcoTools();
                window.RadioTechOperations({apiFetch, state, escapeHtml, toast, loadOperators, loadInventory});
                management = window.RadioTechManagement({ apiFetch, state, escapeHtml, switchView, loadOperators, loadReports });
                signatureWorkspace = window.RadioTechSignatureWorkspace({state, switchView, initials, apiFetch});
                setTimeout(() => window.RadioTechPersonalization.init({apiFetch,state,switchView,toast}), 0);
                const skip = document.createElement("a");
                skip.href = "#mainContent"; skip.className = "skip-link"; skip.textContent = "Vai al contenuto";
                document.body.prepend(skip);
                boot();
            });

        })();
