        (() => {
            "use strict";

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
                    reportAttachments?.clear();
                    document.querySelectorAll(".report-delete-dialog, .report-material-review").forEach(dialog => dialog.close());
                }
                state.token = token;
                // Legacy server login may renew during this page lifetime; never persist its refresh token.
                state.refreshToken = refreshToken;
                state.user = user || null;
                state.authRole = user?.role || null;
                signatureWorkspace?.syncIdentity();
                purgeLegacyTokens();
                try { localStorage.setItem(CONFIG.STORAGE_REMEMBER, String(remember)); } catch (_) {}
            }

            window.addEventListener("radiotech:auth-ended", () => {
                clearSession();
                showLogin("Sessione terminata o account cambiato. Accedi nuovamente.");
            });

            function clearSession() {
                signatureWorkspace?.reset();
                document.querySelectorAll(".report-delete-dialog, .report-material-review").forEach(dialog => dialog.close());
                reportAttachments?.clear();
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
                const { _retried = false, timeoutMs = CONFIG.REQUEST_TIMEOUT, ...requestOptions } = options;
                const controller = new AbortController();
                const timeout = setTimeout(() => controller.abort(), Math.min(60000, timeoutMs));

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
                        throw new Error(message);
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

            /* ---------- USER / PROFILE ---------- */

            function applyPermissions() {
                const allowed = ["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(state.authRole || state.user?.role);
                for (const id of ["quickAntennaBtn", "quickNotificationBtn", "newAntennaBtn", "newOperatorBtn", "syncOperatorBtn", "createTaskBtn", "newNotificationBtn", "sendNotificationBtn", "newInventoryItemBtn", "editProfileBtn"]) {
                    if ($(id)) $(id).hidden = !allowed;
                }
                document.querySelectorAll("[data-edit], [data-delete], [data-delete-operator], [data-reset-password], [data-validity], [data-save-role], [data-approve], [data-badge], [data-regenerate], [data-inventory-move], [data-inventory-edit], [data-inventory-archive], [data-inventory-restore]").forEach(element => { element.hidden = !allowed; });
                document.querySelectorAll("[data-report-approve], [data-report-reject], [data-report-delete]").forEach(element => { element.hidden = !canManageReports(); });
                document.querySelectorAll("[data-operator-role]").forEach(element => { if (!allowed) element.disabled = true; });
            }

            function hydrateUser() {
                applyPermissions();
                signatureWorkspace?.syncIdentity();
                const u = state.user || {};
                const name = u.name || u.displayName || u.fullName || "Capo RadioTech";
                const email = u.email || "—";
                const role = u.role || u.ruolo || "Chief Executive · RadioTech";

                const avatar = initials(name);

                setText("miniAvatar", avatar);
                setText("miniName", name);
                setText("miniEmail", email);

                setText("profileAvatar", avatar);
                window.RadioTechPersonalization?.syncProfile(state.user);
                setText("profileName", name);
                setText("profileFullName", name);
                setText("profileEmail", email);
                setText("profilePhone", u.phone || u.telefono || "—");
                setText("profileRole", role);
                setText("profileRoleField", role);
                setText("profileUid", u.uid || u.id || "—");
                setText("profileLastLogin", u.lastLogin ? new Date(u.lastLogin).toLocaleString("it-IT") : "Sessione corrente");

                setText("sessionUser", email);
                setText("sessionTokenStatus", state.token ? "Token presente" : "Nessun token");
                setText("sessionPersistence", localStorage.getItem(CONFIG.STORAGE_REMEMBER) === "true" ? "ATTIVA" : "SESSIONE");
            }

            function persistUser() { /* Profile belongs to the current in-memory session. */ }
            async function loadProfile() {
                try {
                    const profile = await api("/profile");
                    if (profile) {
                        const data = profile.data || profile.user || profile;
                        state.user = { ...state.user, ...data, name: data.fullName || data.name || state.user?.name, role: state.authRole || state.user?.role };
                        persistUser();
                        hydrateUser();
                    }
                } catch (error) {
                    console.warn("Profilo non disponibile:", error.message);
                }
            }

            async function saveProfile(event) {
                event.preventDefault();

                const payload = {
                    fullName: $("editProfileName").value.trim(),
                    phone: $("editProfilePhone").value.trim(),
                    company: $("editProfileCompany").value.trim(),
                    photoUrl: window.RadioTechPersonalization?.profilePhoto() ?? state.user?.photoUrl ?? ""
                };

                try {
                    const result = await apiFetch("/api/v1/capo/profile", {
                        method: "PUT",
                        body: JSON.stringify(payload)
                    });

                    const data = result?.data || result?.user || result || payload;
                    state.user = { ...state.user, ...data, name: data.fullName || state.user?.name, role: state.authRole || state.user?.role };
                    persistUser();
                    hydrateUser();
                    closeModal("profileModal");
                    toast("Profilo aggiornato", "Le informazioni del Capo sono state salvate.");
                } catch (error) {
                    toast("Profilo non aggiornato", error.message, "error");
                }
            }

            function openProfileModal() {
                applyPermissions();
                const u = state.user || {};
                $("editProfileName").value = u.name || u.displayName || u.fullName || "";
                $("editProfilePhone").value = u.phone || u.telefono || "";
                $("editProfileCompany").value = u.company || "";
                window.RadioTechPersonalization?.resetProfile(u);
                openModal("profileModal");
            }

            /* ---------- MAP ---------- */

            function initMap() {
                if (state.map) return true;
                if (!window.L) {
                    setText("mapStatus", "Leaflet non disponibile");
                    setText("mapSubstatus", "Ricarica la pagina per inizializzare la mappa.");
                    return false;
                }

                try {
                    state.map = L.map("map", {
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
                    }).addTo(state.map);

                    state.mapReady = true;
                    setText("mapStatus", "Network online");
                    setText("mapSubstatus", "Mappa pronta");
                    return true;
                } catch (error) {
                    console.error("Map init error:", error);
                    setText("mapStatus", "Mappa non disponibile");
                    setText("mapSubstatus", error.message);
                    return false;
                }
            }

            function updateMap() {
                if (!state.map && !initMap()) return;
                state.markerByAntennaId.forEach(marker => marker.remove());
                state.markerByAntennaId.clear();
                state.markers = [];
                const valid = [];
                state.antennas.forEach(a => {
                    const lat = Number(a.lat), lng = Number(a.lng);
                    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
                    const status = String(a.status || "OFFLINE").toUpperCase();
                    const color = status === "ATTIVA" ? "#2bd576" : status === "MANUTENZIONE" ? "#ffb648" : status === "CRITICA" ? "#b66cff" : "#ff5f6d";
                    const icon = L.divIcon({ className: "", html: `<div style="width:18px;height:18px;border-radius:50%;background:${color};border:3px solid rgba(255,255,255,.95);box-shadow:0 0 0 6px ${color}22,0 5px 18px #0009"></div>`, iconSize: [18, 18], iconAnchor: [9, 9] });
                    const marker = L.marker([lat, lng], { icon }).addTo(state.map);
                    const specs = antennaSpecs(a);
                    marker.bindPopup(`<div style="min-width:220px;color:#111827;font-family:system-ui"><strong>${escapeHtml(a.name || a.id)}</strong><br><small>Stato: ${escapeHtml(formatStatus(status))}</small><br><small>Frequenza: ${escapeHtml(specs.Frequenza ?? "—")}</small><br><small>Potenza: ${escapeHtml(specs.Potenza ?? "—")}</small><br><small>ROS: ${escapeHtml(specs.ROS ?? "—")}</small><br><small>Temperatura: ${escapeHtml(specs.Temperatura ?? "—")}</small></div>`);
                    state.markerByAntennaId.set(a.id, marker); state.markers.push(marker); valid.push([lat, lng]);
                });
                setText("mapSubstatus", `${valid.length} antenne geolocalizzate`);
                if (valid.length) state.map.fitBounds(valid, { padding: [35, 35], maxZoom: 12 });
                setTimeout(() => state.map?.invalidateSize(), 50);
            }

            /* ---------- STATS ---------- */

            async function loadStats() {
                const stats = await api("/stats");
                state.stats = stats || {};

                setText("statAntennas", stats?.antennas ?? 0);
                setText("statOperators", stats?.operators ?? 0);
                setText("statInterventions", stats?.interventions ?? 0);
                setText("statTasks", stats?.tasks ?? 0);
                setText("statLowStock", stats?.lowStock ?? 0);
                setText("statOverdueTasks", stats?.taskStats?.overdue ?? 0);

                const availability = Number(stats?.availability || 0);
                const active = Number(stats?.activeAntennas || 0);
                const inactive = Number(stats?.inactiveAntennas || 0);
                const total = Number(stats?.antennas || 0);

                setText("availabilityValue", `${number(availability, 2)}%`);
                setText("availabilityLabel", `${number(availability, 2)}%`);
                setText("activeCount", `${active} / ${total}`);
                setText("inactiveCount", `${inactive} / ${total}`);

                $("activeProgress").style.width = `${Math.min(100, availability)}%`;
                $("inactiveProgress").style.width = `${total ? Math.min(100, (inactive / total) * 100) : 0}%`;

                const availabilityBadge = $("availabilityBadge");
                availabilityBadge.textContent = total ? `${active} / ${total} attive` : "NESSUN ASSET";
                availabilityBadge.className =
                    `badge ${availability >= 95 ? "green" : availability >= 80 ? "yellow" : "red"}`;

                const tasks = stats?.taskStats || {};
                setText("taskAssigned", tasks.assigned ?? 0);
                setText("taskProgress", tasks.inProgress ?? 0);
                setText("taskCompleted", tasks.completed ?? 0);
                setText("taskCancelled", tasks.cancelled ?? 0);

                setText("opsAssigned", tasks.assigned ?? 0);
                setText("opsProgress", tasks.inProgress ?? 0);
                setText("opsCompleted", tasks.completed ?? 0);
                setText("opsCancelled", tasks.cancelled ?? 0);
                setText("opsAvailability", `${number(availability, 2)}%`);
                $("opsAvailabilityProgress").style.width = `${Math.min(100, availability)}%`;
                setText("opsActive", active);
                setText("opsInactive", inactive);

                const calculatedAt = stats?.asOf ? new Date(stats.asOf) : null;
                setText("lastUpdate", calculatedAt && Number.isFinite(calculatedAt.getTime())
                    ? calculatedAt.toLocaleString("it-IT") : "Ora del dato non disponibile");
                setBackendState(true);
                loadSystemHealth().catch(() => { });
            }

            async function loadSystemHealth() {
                try {
                    const r = await apiFetch("/api/v1/health/firebase");
                    const ok = r?.status === "UP";
                    setText("firestoreStatus", ok ? "OK" : "DOWN");
                    $("firestoreBadge").textContent = ok ? "CONNECTED" : "OFFLINE";
                    $("firestoreBadge").className = `badge ${ok ? "green" : "red"}`;
                } catch (e) {
                    setText("firestoreStatus", "DOWN");
                    $("firestoreBadge").textContent = "OFFLINE";
                    $("firestoreBadge").className = "badge red";
                }
            }

            function setBackendState(ok) {
                setText("backendStatus", ok ? "OK" : "ERROR");
                $("backendBadge").textContent = ok ? "ONLINE" : "OFFLINE";
                $("backendBadge").className = `badge ${ok ? "green" : "red"}`;

                if (!ok) {
                    setText("firestoreStatus", "UNKNOWN");
                    $("firestoreBadge").textContent = "UNKNOWN";
                    $("firestoreBadge").className = "badge gray";
                }
            }

            /* ---------- ANTENNAS ---------- */

            async function loadAntennas() {
                try {
                    const data = await api("/antenne");
                    state.antennas = Array.isArray(data) ? data : (data?.antennas || []);
                    renderAntennas();
                    populateTaskSelectors();
                    updateMap();
                } catch (error) {
                    console.error(error);
                    $("antennasTableBody").innerHTML =
                        `<tr><td colspan="8"><div class="empty">Impossibile caricare le antenne: ${escapeHtml(error.message)}</div></td></tr>`;
                    throw error;
                }
            }

            function renderAntennas() {
                const query = $("antennaSearch").value.trim().toLowerCase();
                const filter = $("antennaStatusFilter").value.toUpperCase();

                const list = state.antennas.filter(a => {
                    const haystack = [a.id, a.name, a.status, a.lat, a.lng].join(" ").toLowerCase();
                    return (!query || haystack.includes(query)) &&
                        (!filter || String(a.status || "").toUpperCase() === filter);
                });

                setText("antennaCountLabel", `${list.length} di ${state.antennas.length} antenne`);

                if (!list.length) {
                    $("antennasTableBody").innerHTML =
                        `<tr><td colspan="8"><div class="empty">Nessuna antenna trovata.</div></td></tr>`;
                    return;
                }

                $("antennasTableBody").innerHTML = list.map(a => {
                    const specs = antennaSpecs(a);
                    return `
                <tr>
                    <td>
                        <div class="antenna-name">${escapeHtml(a.name || a.id)}</div>
                        <div class="coordinates">${escapeHtml(a.id || "")}</div>
                    </td>
                    <td>${number(a.lat, 5)}, ${number(a.lng, 5)}</td>
                    <td><span class="badge ${badgeClass(a.status)}">${escapeHtml(formatStatus(a.status))}</span></td>
                    <td>${escapeHtml(specs.Frequenza ?? "—")}</td>
                    <td>${escapeHtml(specs.Potenza ?? "—")}</td>
                    <td>${escapeHtml(specs.ROS ?? "—")}</td>
                    <td>${escapeHtml(specs.Temperatura ?? "—")}</td>
                    <td>
                        <div class="actions">
                            <button class="mini-btn" data-qr="${escapeHtml(a.id)}">QR Code</button>
                            <button class="mini-btn" data-edit="${escapeHtml(a.id)}">Modifica</button>
                            <button class="mini-btn" data-focus="${escapeHtml(a.id)}">Mappa</button>
                            <button class="mini-btn" data-delete="${escapeHtml(a.id)}">Elimina</button>
                        </div>
                    </td>
                </tr>`;
                }).join("");

                applyPermissions();
                $("antennasTableBody").querySelectorAll("[data-qr]").forEach(b =>
                    b.addEventListener("click", () => showAntennaQr(b.dataset.qr))
                );
                $("antennasTableBody").querySelectorAll("[data-edit]").forEach(b =>
                    b.addEventListener("click", () => openAntennaModal(b.dataset.edit))
                );
                applyPermissions();
                $("antennasTableBody").querySelectorAll("[data-focus]").forEach(b =>
                    b.addEventListener("click", () => focusAntenna(b.dataset.focus))
                );
                applyPermissions();
                $("antennasTableBody").querySelectorAll("[data-delete]").forEach(b =>
                    b.addEventListener("click", () => deleteAntenna(b.dataset.delete))
                );
            }

            function focusAntenna(id) {
                const a = state.antennas.find(x => x.id === id);
                if (!a || !initMap()) return;

                const lat = Number(a.lat);
                const lng = Number(a.lng);
                if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

                switchView("dashboard");
                setTimeout(() => {
                    state.map.invalidateSize();
                    state.map.setView([lat, lng], 14);

                    state.markerByAntennaId.get(id)?.openPopup();
                }, 100);
            }

            function openAntennaModal(id = null) {
                state.editingAntennaId = id;
                $("antennaForm").reset();
                $("antennaId").value = "";
                $("antennaModalTitle").textContent = id ? "Modifica antenna" : "Nuova antenna";

                if (id) {
                    const a = state.antennas.find(x => x.id === id);
                    if (!a) return;
                    const s = antennaSpecs(a);

                    $("antennaId").value = a.id;
                    $("antennaName").value = a.name || "";
                    $("antennaStatus").value = a.status || "ATTIVA";
                    $("antennaLat").value = a.lat ?? "";
                    $("antennaLng").value = a.lng ?? "";
                    $("antennaFreq").value = s.Frequenza ?? "";
                    $("antennaPower").value = s.Potenza ?? "";
                    $("antennaRos").value = s.ROS ?? "";
                    $("antennaTemp").value = s.Temperatura ?? "";
                } else {
                    $("antennaStatus").value = "ATTIVA";
                }

                openModal("antennaModal");
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

            async function saveAntenna(event) {
                event.preventDefault();

                const id = state.editingAntennaId;
                const payload = {
                    name: $("antennaName").value.trim(),
                    lat: Number($("antennaLat").value),
                    lng: Number($("antennaLng").value),
                    status: $("antennaStatus").value,
                    specs: id ? {
                        frequenza: numberOrNull($("antennaFreq").value),
                        potenza: numberOrNull($("antennaPower").value),
                        ros: numberOrNull($("antennaRos").value),
                        temperatura: numberOrNull($("antennaTemp").value)
                    } : {
                        frequencyMHz: numberOrNull($("antennaFreq").value),
                        powerWatts: numberOrNull($("antennaPower").value),
                        ros: numberOrNull($("antennaRos").value),
                        temperature: numberOrNull($("antennaTemp").value)
                    }
                };

                if (!payload.name || !Number.isFinite(payload.lat) || !Number.isFinite(payload.lng)) {
                    toast("Dati non validi", "Controlla nome e coordinate.", "error");
                    return;
                }

                const button = $("saveAntenna");
                button.disabled = true;
                button.textContent = "Salvataggio...";

                try {
                    if (!id) {
                        await api("/antenne", {
                            method: "POST",
                            body: JSON.stringify(payload)
                        });
                    } else {
                        await api(`/antenne/${encodeURIComponent(id)}`, {
                            method: "PUT",
                            body: JSON.stringify(payload)
                        });
                    }

                    closeModal("antennaModal");
                    await Promise.all([loadAntennas(), loadStats()]);
                    toast("Operazione completata", id ? "Antenna aggiornata." : "Antenna creata.");
                } catch (error) {
                    toast("Salvataggio fallito", error.message, "error");
                } finally {
                    button.disabled = false;
                    button.textContent = "Salva antenna";
                }
            }

            async function deleteAntenna(id) {
                const a = state.antennas.find(x => x.id === id);
                const name = a?.name || id;
                if (!confirm(`Eliminare definitivamente "${name}"?`)) return;

                try {
                    await api(`/antenne/${encodeURIComponent(id)}`, { method: "DELETE" });
                    await Promise.all([loadAntennas(), loadStats()]);
                    toast("Antenna eliminata", `${name} è stata rimossa.`);
                } catch (error) {
                    toast("Eliminazione fallita", error.message, "error");
                }
            }

            let tasksCursor = null, reportsCursor = null;
            let loadingTasks = false, loadingReports = false, reloadReports = false;
            async function loadTasks(append = false) {
                append = append === true;
                if (loadingTasks) return;
                loadingTasks = true; $('tasksMoreBtn').disabled = true;
                try {
                    const params = new URLSearchParams({limit: '50'});
                    if (append && tasksCursor) params.set('cursor', tasksCursor);
                    const response = await apiFetch(`/api/v1/tasks/page?${params}`);
                    const data = response?.data ?? response;
                    const items = Array.isArray(data) ? data : (data?.items || []);
                    state.tasks = [...new Map([...(append ? state.tasks : []), ...items].map(item => [item.id, item])).values()];
                    tasksCursor = data?.nextCursor || null;
                    $('tasksMoreBtn').hidden = !tasksCursor;
                    $('tasksPageStatus').textContent = `${state.tasks.length} incarichi caricati${tasksCursor ? ' · altri disponibili' : ' · fine elenco'}`;
                    renderTaskList(); populateTaskSelectors(); refreshReportTitles();
                } catch (e) { if (append) toast('Caricamento non riuscito', e.message, 'error'); else $("tasksList").innerHTML = `<div class="empty">Incarichi non disponibili: ${escapeHtml(e.message)}</div>`; }
                finally { loadingTasks = false; $('tasksMoreBtn').disabled = false; }
            }
            function eligibleOperators() {
                return state.operators.filter(operator => {
                    const role = (operator.role || "OPERATOR").toUpperCase();
                    const status = (operator.status || "").toUpperCase();
                    return role === "OPERATOR" && !["IN_ATTESA", "INATTIVO", "DISATTIVATO"].includes(status);
                });
            }
            function populateTaskSelectors() {
                const op = $("taskOperator"); if (op) op.innerHTML = '<option value="">Seleziona operatore</option>' + eligibleOperators().map(o => `<option value="${escapeHtml(o.id)}">${escapeHtml(o.fullName || o.email || o.id)}</option>`).join("");
                const an = $("taskAntenna"); if (an) an.innerHTML = '<option value="">Seleziona antenna</option>' + state.antennas.map(a => `<option value="${escapeHtml(a.id)}">${escapeHtml(a.name || a.id)}</option>`).join("");
            }
            function renderTaskList() {
                if (!$('tasksList')) return;
                const query = ($('taskSearch')?.value || '').trim().toLowerCase();
                const list = state.tasks.filter(t => !query || [t.title, t.description, t.operatorName, t.operatorId, t.status, t.antennaId].join(' ').toLowerCase().includes(query));
                $('tasksList').innerHTML = list.length ? list.map(t => `<div class="task-row"><div><div class="task-title">${escapeHtml(t.title || t.id)}</div><div class="task-meta">${escapeHtml(t.operatorName || t.operatorId || t.operator_uid || "Non assegnato")} · ${escapeHtml(t.antennaId || "—")} · ${t.dueAt ? `scade ${escapeHtml(new Date(t.dueAt).toLocaleString('it-IT'))}` : 'senza scadenza'} · ID ${escapeHtml(t.id)}</div></div><div class="actions"><span class="badge ${badgeClass(t.status)}">${escapeHtml(formatStatus(t.status))}</span>${!["COMPLETED", "REPORT_SUBMITTED", "APPROVED", "CLOSED", "CANCELLED"].includes(t.status) ? `<button class="mini-btn" data-task-cancel="${escapeHtml(t.id)}">Annulla incarico</button>` : ''}</div></div>`).join('') : '<div class="empty">Nessun incarico.</div>';
                $('tasksList').querySelectorAll('[data-task-cancel]').forEach(button => {
                    button.hidden = !["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(state.authRole || state.user?.role);
                    button.addEventListener('click', async () => {
                        const task = state.tasks.find(t => t.id === button.dataset.taskCancel);
                        if (!task || !confirm(`Annullare l’incarico «${task.title || task.id}»? Resterà visibile nello storico.`)) return;
                        button.disabled = true;
                        try {
                            await apiFetch(`/api/v1/tasks/${encodeURIComponent(task.id)}/status`, {method: 'PATCH', body: JSON.stringify({status: 'CANCELLED'})});
                            await Promise.all([loadTasks(), loadStats()]);
                            window.dispatchEvent(new Event('radiotech:tasks-changed'));
                            toast('Incarico annullato', 'L’incarico non blocca più la rimozione dell’operatore.');
                        } catch (error) { toast('Annullamento fallito', error.message, 'error'); }
                        finally { button.disabled = false; }
                    });
                });
            }
            async function createTask() {
                const button = $("createTaskBtn");
                if (button.disabled) return;
                const title = $("taskTitle").value.trim(), operatorId = $("taskOperator").value, antennaId = $("taskAntenna").value;
                if (!title || !operatorId || !antennaId) { toast("Incarico incompleto", "Titolo, operatore e antenna sono obbligatori per il flusso sul campo.", "error"); return; }
                const dueAt = $("taskDueAt").value ? new Date($("taskDueAt").value).toISOString() : null;
                button.disabled = true;
                try { await apiFetch("/api/v1/tasks", { method: "POST", body: JSON.stringify({ title, description: $("taskDescription").value.trim(), operatorId, antennaId: antennaId || null, dueAt, status: "ASSIGNED", priority: $("taskPriority").value }) }); $("taskTitle").value = ""; $("taskDescription").value = ""; $("taskDueAt").value = ""; await Promise.all([loadTasks(), loadStats()]); toast("Incarico assegnato", "L'operatore può ora visualizzarlo nell'app mobile."); } catch (e) { toast("Assegnazione fallita", e.message, "error"); }
                finally { button.disabled = false; }
            }

            let reportAttachments;
            function attachmentViewer() { return reportAttachments ??= window.RadioTechReportAttachments({apiFetch, escapeHtml}); }
            function reportDisplayTitle(report) {
                const task = state.tasks.find(t => t.id === report.taskId);
                return report.taskTitle || task?.title || "Intervento senza compito assegnato";
            }
            function reportDisplaySummary(report) {
                const operator = state.operators.find(o => o.id === report.operatorId || o.firebaseUid === report.operatorFirebaseUid);
                const name = report.operatorName || operator?.fullName || "Operatore non disponibile";
                const instant = new Date(report.submittedAt || report.completedAt || report.createdAt || "");
                const date = Number.isNaN(instant.getTime()) ? "Data non disponibile" : instant.toLocaleDateString("it-IT", {day:"2-digit",month:"2-digit",year:"numeric"});
                return `ID ${report.id} · ${name} · ${date}`;
            }
            function refreshReportTitles() {
                for (const report of state.reports || []) {
                    const row = [...$("reportsList").querySelectorAll("[data-report-id]")].find(n => n.dataset.reportId === report.id);
                    const title = row?.querySelector(".task-title");
                    if (title) { title.textContent = reportDisplayTitle(report); title.title = title.textContent; }
                    const summary = row?.querySelector(".report-summary");
                    if (summary) summary.textContent = reportDisplaySummary(report);
                }
            }
            async function loadReports(append = false) {
                append = append === true;
                if (loadingReports) { if (!append) reloadReports = true; return; }
                const epoch = state.sessionEpoch || 0;
                loadingReports = true; $('reportsMoreBtn').disabled = true;
                try {
                    const params = new URLSearchParams({limit: '50'});
                    const statusFilter = $('reportStatusFilter')?.value || '';
                    if (statusFilter) params.set('status', statusFilter);
                    if (append && reportsCursor) params.set('cursor', reportsCursor);
                    const response = await apiFetch(`/api/v1/reports/page?${params}`, { method: "GET" });
                    if (epoch !== (state.sessionEpoch || 0)) return;
                    if (statusFilter !== ($('reportStatusFilter')?.value || '')) { reloadReports = true; return; }
                    const data = response?.data ?? response;
                    const items = Array.isArray(data) ? data : (data?.items || []);
                    const reports = [...new Map([...(append ? state.reports || [] : []), ...items].map(item => [item.id, item])).values()];
                    state.reports = reports;
                    reportsCursor = data?.nextCursor || null;
                    $('reportsMoreBtn').hidden = !reportsCursor;
                    $('reportsPageStatus').textContent = `${reports.length} report caricati${reportsCursor ? ' · altri disponibili' : ' · fine elenco'}`;
                    attachmentViewer().clear();
                    $("reportsList").innerHTML = reports.length ? reports.map(renderReportRow).join("") : `<div class="empty">${data?.hasMore ? "Nessun report visibile in questa pagina. Carica altri report per continuare." : "Nessun report ricevuto."}</div>`;
                    attachmentViewer().mount($("reportsList"));
                    applyPermissions();
                    $("reportsList").querySelectorAll('[data-report-approve]').forEach(b => b.addEventListener('click', () => reviewReport(b.dataset.reportApprove, true)));
                    $("reportsList").querySelectorAll('[data-report-reject]').forEach(b => b.addEventListener('click', () => reviewReport(b.dataset.reportReject, false)));
                    $("reportsList").querySelectorAll('[data-report-delete]').forEach(b => b.addEventListener('click', () => deleteReport(b.dataset.reportDelete, b)));
                } catch (e) { if (append) toast('Caricamento non riuscito', e.message, 'error'); else $("reportsList").innerHTML = `<div class="empty">Report non disponibili: ${escapeHtml(e.message)}</div>`; }
                finally { loadingReports = false; $('reportsMoreBtn').disabled = false;
                    if (reloadReports) { reloadReports = false; if (state.token) loadReports(); }
                }
            }

            function canManageReports() {
                return ["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(state.authRole || state.user?.role);
            }
            function renderReportRow(report) {
                const id = escapeHtml(report.id || ""), status = report.status || "SUBMITTED";
                const date = report.submittedAt || report.completedAt || report.createdAt;
                return `<article class="task-row report-row" data-report-id="${id}" data-status="${escapeHtml(status)}" data-operator="${escapeHtml(report.operatorName || report.operatorId || "")}" data-date="${escapeHtml((date || "").slice(0,10))}">
                    <div class="report-copy"><div class="task-title">${escapeHtml(reportDisplayTitle(report))}</div>
                    <div class="task-meta report-summary">${escapeHtml(reportDisplaySummary(report))}</div>
                    <div class="task-meta report-note">${escapeHtml(report.operatorNotes || report.description || "Nessuna nota")}</div>
                    ${reportDetails(report)}${attachmentViewer().markup(report.attachments)}</div>
                    <div class="actions"><span class="badge ${badgeClass(status)}">${escapeHtml(formatStatus(status))}</span>
                    ${canManageReports() && ["SUBMITTED", "APPROVAL_PENDING"].includes(status) ? `<button type="button" class="mini-btn" data-report-approve="${id}">${status === "APPROVAL_PENDING" ? "Riprendi approvazione" : "Approva"}</button>${status === "SUBMITTED" ? `<button type="button" class="mini-btn" data-report-reject="${id}">Rifiuta</button>` : ""}` : ""}
                    ${canManageReports() ? `<button type="button" class="btn danger mini-btn" data-report-delete="${id}">Elimina</button>` : ""}
                    <button type="button" class="mini-btn" data-report-toggle aria-expanded="true" aria-label="Comprimi report">⌃</button></div></article>`;
            }
            async function deleteReport(id, trigger) {
                if (!canManageReports() || trigger.disabled) return;
                const report = state.reports.find(item => item.id === id);
                if (!report) return;
                const rowButtons = [...trigger.closest('[data-report-id]').querySelectorAll('[data-report-approve], [data-report-reject], [data-report-delete]')];
                if (rowButtons.some(button => button.disabled)) return;
                const epoch = state.sessionEpoch || 0;
                const dialog = document.createElement('dialog');
                dialog.className = 'report-material-review report-delete-dialog';
                dialog.setAttribute('aria-labelledby', 'reportDeleteTitle');
                dialog.setAttribute('aria-describedby', 'reportDeleteConsequences');
                dialog.innerHTML = `<form><h2 id="reportDeleteTitle">Elimina report</h2>
                    <p class="report-delete-name">${escapeHtml(reportDisplayTitle(report))}</p>
                    <dl><dt>Identificativo</dt><dd>${escapeHtml(id)}</dd><dt>Stato</dt><dd>${escapeHtml(formatStatus(report.status || 'SUBMITTED'))}</dd></dl>
                    <p id="reportDeleteConsequences">Il report sarà rimosso dall’elenco operativo. Documento, allegati, firme e storico delle operazioni rimarranno conservati per la tracciabilità. L’incarico e i movimenti di magazzino già registrati non saranno modificati.</p>
                    <p role="alert" data-delete-error hidden></p>
                    <div class="actions"><button type="button" class="btn" data-delete-cancel>Annulla</button><button type="submit" class="btn danger">Conferma eliminazione</button></div></form>`;
                let busy = false;
                rowButtons.forEach(button => { button.disabled = true; });
                dialog.addEventListener('close', () => { dialog.remove(); rowButtons.forEach(button => { button.disabled = false; }); if (trigger.isConnected) trigger.focus(); else $('reportsRefreshBtn')?.focus(); }, {once:true});
                dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
                dialog.querySelector('[data-delete-cancel]').addEventListener('click', () => dialog.close());
                dialog.querySelector('form').addEventListener('submit', async event => {
                    event.preventDefault();
                    if (busy) return;
                    if (epoch !== (state.sessionEpoch || 0) || !canManageReports()) { dialog.close(); return; }
                    busy = true;
                    dialog.querySelectorAll('button').forEach(button => { button.disabled = true; });
                    const errorBox = dialog.querySelector('[data-delete-error]');
                    errorBox.hidden = true;
                    try {
                        await apiFetch(`/api/v1/reports/${encodeURIComponent(id)}`, {method:'DELETE'});
                        if (epoch !== (state.sessionEpoch || 0)) { dialog.close(); return; }
                        state.reports = state.reports.filter(item => item.id !== id);
                        trigger.closest('[data-report-id]')?.remove();
                        dialog.close();
                        await Promise.all([loadReports(), loadStats()]);
                        toast('Report eliminato', 'Rimosso dall’elenco operativo. Documenti e storico conservati.');
                    } catch (error) {
                        if (epoch !== (state.sessionEpoch || 0)) { dialog.close(); return; }
                        errorBox.textContent = `Eliminazione non riuscita: ${error.message}`;
                        errorBox.hidden = false;
                        toast('Eliminazione fallita', error.message, 'error');
                    } finally {
                        busy = false;
                        dialog.querySelectorAll('button').forEach(button => { button.disabled = false; });
                    }
                });
                document.body.append(dialog); dialog.showModal();
                dialog.querySelector('[data-delete-cancel]').focus();
            }

            function reportDetails(report) {
                const measurements = Object.entries(report.measurements || {}).map(([key, value]) => `<li>${escapeHtml(key)}: ${escapeHtml(value)}</li>`).join("");
                const materials = (report.materialsUsed || []).map(item => `<li>${escapeHtml(item.name || item.itemId || item.inventoryItemId || item.ricambioId || "Materiale")}: ${escapeHtml(item.quantity ?? item.quantita ?? "—")}</li>`).join("");
                const checklist = Object.entries(report.checklist || {}).map(([key, value]) => `<li>${escapeHtml(key)}: ${value === true ? "Verificato" : "Non verificato"}</li>`).join("");
                return `<details class="report-details"><summary>Dettagli intervento</summary><p>Antenna: ${escapeHtml(state.antennas.find(a => a.id === report.antennaId)?.name || report.antennaId || "—")} · Inviato: ${escapeHtml(report.submittedAt ? new Date(report.submittedAt).toLocaleString("it-IT") : "Data non disponibile")}</p><p><strong>Lavoro eseguito</strong><br>${escapeHtml(report.workPerformed || report.description || "Non indicato")}</p><p><strong>Rilievi</strong><br>${escapeHtml(report.findings || "Nessun rilievo indicato")}</p>${measurements ? `<strong>Misure</strong><ul>${measurements}</ul>` : ""}${materials ? `<strong>Materiali</strong><ul>${materials}</ul>` : ""}${checklist ? `<strong>Lista di controllo</strong><ul>${checklist}</ul>` : ""}${report.reviewNote ? `<p><strong>Nota di revisione</strong><br>${escapeHtml(report.reviewNote)}</p>` : ""}</details>`;
            }

            async function reviewReport(id, approve) {
                const currentReport = state.reports.find(item => item.id === id);
                if (!canManageReports() || !currentReport || !(approve ? ["SUBMITTED", "APPROVAL_PENDING"] : ["SUBMITTED"]).includes(currentReport.status)) return;
                const buttons = [...$("reportsList").querySelectorAll('[data-report-approve], [data-report-reject], [data-report-delete]')].filter(b => b.dataset.reportApprove === id || b.dataset.reportReject === id || b.dataset.reportDelete === id);
                if (buttons.some(b => b.disabled)) return;
                const note = approve ? 'Report approvato dalla centrale.' : (prompt('Motivo del rifiuto:') || '');
                if (!approve && !note.trim()) return;
                buttons.forEach(b => { b.disabled = true; });
                try {
                    const epoch=state.sessionEpoch||0, report=state.reports.find(item=>item.id===id);
                    let materialMappings;
                    if(approve && report?.materialsUsed?.some(material=>!['inventoryId','inventoryItemId','itemId','ricambioId','id','sku'].some(field=>material[field]))) {
                        materialMappings=await window.RadioTechReviewMaterials({apiFetch,report,escapeHtml});
                        if(materialMappings===null)return;
                    }
                    if(epoch!==(state.sessionEpoch||0))return;
                    await apiFetch(`/api/v1/reports/${encodeURIComponent(id)}/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: JSON.stringify({ note, materialMappings }) }); await Promise.all([loadReports(), loadTasks(), loadStats()]); window.dispatchEvent(new Event('radiotech:tasks-changed')); toast(approve ? 'Report approvato' : 'Report rifiutato', approve ? 'Materiali aggiornati e incarico completato.' : 'L’operatore può correggere e inviare nuovamente il rapporto dagli incarichi attivi.');
                } catch (e) { toast('Revisione fallita', e.message, 'error'); }
                finally { buttons.forEach(b => { b.disabled = false; }); }
            }

            async function telecomPost(path, payload) { return apiFetch(`/api/v1/telecom-tools${path}`, { method: "POST", body: JSON.stringify(payload) }); }

            async function calculateLinkBudget() {
                try {
                    const r = await telecomPost("/link-budget", { frequencyMHz: Number($("tbFreq").value), distanceKm: Number($("tbDist").value), txPowerDbm: Number($("tbTx").value), txGainDbi: Number($("tbGt").value), rxGainDbi: Number($("tbGr").value), txCableLossDb: Number($("tbLt").value || 0), rxCableLossDb: Number($("tbLr").value || 0), otherLossDb: Number($("tbLo").value || 0), rxSensitivityDbm: $("tbSens").value === "" ? undefined : Number($("tbSens").value) });
                    $("tbResult").innerHTML = `<strong>FSPL:</strong> ${number(r.fsplDb, 2)} dB<br><strong>EIRP:</strong> ${number(r.eirpDbm, 2)} dBm<br><strong>Potenza ricevuta:</strong> ${number(r.receivedPowerDbm, 2)} dBm<br><strong>Margine:</strong> ${r.linkMarginDb == null ? "—" : number(r.linkMarginDb, 2) + " dB"}<br><span class="subtle">${escapeHtml(r.formula)}</span>`;
                } catch (e) { toast("Calcolo non valido", e.message, "error"); }
            }
            async function calculatePower() { try { const payload = $("tbDbm").value !== "" ? { dbm: Number($("tbDbm").value) } : { w: Number($("tbW").value) }; const r = await telecomPost("/power", payload); $("tbPowerResult").textContent = `${number(r.dbm, 2)} dBm · ${number(r.mw, 3)} mW · ${number(r.w, 6)} W`; } catch (e) { toast("Conversione non valida", e.message, "error"); } }
            async function calculateVswr() { try { const r = await telecomPost("/vswr", { vswr: Number($("tbVswr").value) }); $("tbVswrResult").textContent = `Γ ${number(r.reflectionCoefficient, 4)} · Perdita di ritorno ${number(r.returnLossDb, 2)} dB`; } catch (e) { toast("VSWR non valido", e.message, "error"); } }

            /* ---------- NOTIFICATIONS ---------- */

            async function loadOperators() {
                try {
                    const response = await apiFetch("/api/v1/operators");
                    const data = response?.data ?? response;
                    state.operators = Array.isArray(data) ? data : (data?.operators || []);
                    state.operatorLoadError = "";
                    renderRecipients();
                    renderOperatorTable();
                    populateTaskSelectors(); refreshReportTitles();
                } catch (error) {
                    console.warn("Operatori non disponibili:", error.message);
                    state.operatorLoadError = error.message || "Errore di caricamento";
                    renderOperatorTable();
                }
            }

            function operatorOnline(lastSeen) {
                if (!lastSeen) return false;
                const timestamp = Date.parse(lastSeen);
                return Number.isFinite(timestamp) && Date.now() - timestamp < 5 * 60 * 1000;
            }

            function renderOperatorTable() {
                const table = $("operatorsTableBody");
                if (!table) return;
                const search = ($("operatorSearch")?.value || "").trim().toLocaleLowerCase("it-IT");
                const roleFilter = $("operatorRoleFilter")?.value || "";
                const visibleOperators = state.operators.filter(operator => {
                    const name = `${operator.fullName || ""} ${operator.email || ""}`.toLocaleLowerCase("it-IT");
                    const role = (operator.role || "OPERATOR").toUpperCase();
                    return (!search || name.includes(search)) && (!roleFilter || role === roleFilter);
                });
                const active = state.operators.filter(operator => (operator.status || "").toUpperCase() === "ATTIVO").length;
                $("operatorsSummary").textContent = state.operatorLoadError
                    ? "Elenco non disponibile"
                    : `${state.operators.length} operatori · ${active} attivi`;
                if (state.operatorLoadError) {
                    table.innerHTML = `<tr><td colspan="7"><div class="empty">Impossibile caricare gli operatori: ${escapeHtml(state.operatorLoadError)}</div></td></tr>`;
                    return;
                }
                if (!visibleOperators.length) {
                    table.innerHTML = `<tr><td colspan="7"><div class="empty">${state.operators.length ? "Nessun operatore corrisponde ai filtri." : "Nessun operatore registrato."}</div></td></tr>`;
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
                        <td><strong>${escapeHtml(name)}</strong><div class="subtle">${escapeHtml(operator.email || "Email non collegata")}</div></td>
                        <td><div class="actions"><select data-operator-role="${escapeHtml(id)}" aria-label="Ruolo di ${escapeHtml(name)}" ${linked && !pending ? "" : "disabled"}>
                            <option value="OPERATOR" ${role === "OPERATOR" ? "selected" : ""}>Operatore</option>
                            <option value="VIEWER" ${role === "VIEWER" ? "selected" : ""}>Sola lettura</option>
                        </select><button class="mini-btn" data-save-role="${escapeHtml(id)}" ${linked && !pending ? "" : "disabled"}>Salva</button></div></td>
                        <td>${escapeHtml(operator.level || "TECNICO")}</td>
                        <td>${escapeHtml(operator.shift || "—")}</td>
                        <td><span class="badge ${online ? "green" : pending ? "red" : "blue"}">${online ? "CONNESSO" : escapeHtml(formatStatus(operator.status || "OFFLINE"))}</span></td>
                        <td>${operator.lastSeen ? escapeHtml(new Date(operator.lastSeen).toLocaleString("it-IT")) : "—"}</td>
                        <td><div class="actions">${pending ? `<button class="mini-btn" data-approve="${escapeHtml(id)}">Approva</button>` : ""}<button class="mini-btn" data-badge="${escapeHtml(id)}">Badge</button><button class="mini-btn" data-regenerate="${escapeHtml(id)}">Rigenera QR</button><button class="mini-btn" data-validity="${escapeHtml(id)}">Validità QR</button><button class="mini-btn" data-reset-password="${escapeHtml(id)}">Reimposta password</button><button class="mini-btn danger" data-delete-operator="${escapeHtml(id)}">Elimina</button></div></td>
                    </tr>`;
                }).join("");
                applyPermissions(); refreshReportTitles();
                table.querySelectorAll("[data-save-role]").forEach(button => button.addEventListener("click", () => saveOperatorRole(button.dataset.saveRole)));
                table.querySelectorAll("[data-approve]").forEach(button => button.addEventListener("click", () => approveOperator(button.dataset.approve)));
                table.querySelectorAll("[data-badge]").forEach(button => button.addEventListener("click", () => showBadge(button.dataset.badge)));
                table.querySelectorAll("[data-regenerate]").forEach(button => button.addEventListener("click", () => regenerateQr(button.dataset.regenerate)));
            }

            async function saveOperatorRole(id) {
                const select = document.querySelector(`[data-operator-role="${CSS.escape(id)}"]`);
                if (!select) return;
                const operator = state.operators.find(item => item.id === id);
                if (!operator) return;
                const role = select.value;
                if (role === "VIEWER" && (operator.role || "OPERATOR").toUpperCase() !== "VIEWER"
                    && !confirm("Il ruolo Sola lettura disabilita l'accesso alle funzioni operative dell'app mobile. Continuare?")) return;
                try {
                    await apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/role`, {
                        method: "PATCH", body: JSON.stringify({ role })
                    });
                    await loadOperators();
                    toast("Ruolo aggiornato", "Il nuovo ruolo sarà attivo dopo il prossimo accesso dell'operatore.");
                } catch (error) {
                    toast("Aggiornamento ruolo non riuscito", error.message, "error");
                }
            }

            async function approveOperator(id) {
                if (!confirm("Approvare l'operatore e abilitarne l'accesso?")) return;
                try {
                    await apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/approve`, { method: "POST" });
                    await loadOperators();
                    toast("Operatore approvato", "L'accesso sarà attivo al prossimo login.");
                } catch (error) {
                    toast("Approvazione non riuscita", error.message, "error");
                }
            }

            async function createOperator(event) {
                event.preventDefault();
                const button = $("saveOperator");
                button.disabled = true;
                try {
                    const result = await apiFetch("/api/v1/operators/generate-credentials", {
                        method: "POST", body: JSON.stringify({
                            fullName: $("operatorFullName").value.trim(), email: $("operatorEmail").value.trim() || null,
                            phone: $("operatorPhone").value.trim() || null, level: $("operatorLevel").value,
                            shift: $("operatorShift").value.trim(), password: $("operatorPassword").value.trim() || null
                        })
                    });
                    const data = result?.data ?? result;
                    $("operatorCredentialsResult").innerHTML = `Email: <strong>${escapeHtml(data.email)}</strong> · Password: <strong>${escapeHtml(data.password)}</strong> · QR: <strong>${escapeHtml(data.qrCodeToken)}</strong>`;
                    await loadOperators();
                    toast("Operatore creato", "Credenziali generate. Conserva la password e stampa il badge.");
                    if (data.operator?.id) showBadge(data.operator.id);
                } catch (error) {
                    toast("Creazione operatore fallita", error.message, "error");
                } finally { button.disabled = false; }
            }

            async function showAntennaQr(id) {
                try {
                    const result = await apiFetch(`/api/v1/antennas/${encodeURIComponent(id)}/qr`);
                    const data = result?.data ?? result;
                    $("antennaQrStationName").textContent = data.name || data.antennaId;
                    $("antennaQrCode").textContent = data.code || "ANT";
                    $("antennaQrId").textContent = data.antennaId;
                    $("antennaQrSite").textContent = data.site || "Nessun indirizzo specificato";
                    $("antennaQrStatus").textContent = data.status || "ATTIVA";
                    $("antennaQrImage").src = data.imageDataUrl;
                    const downloadLink = $("antennaQrDownloadLink");
                    if (downloadLink) {
                        downloadLink.href = data.imageDataUrl;
                        downloadLink.download = `QR_${data.code || data.antennaId}.png`;
                    }
                    openModal("antennaQrModal");
                } catch (error) { toast("QR antenna non disponibile", error.message, "error"); }
            }

            async function showBadge(id) {
                const operator = state.operators.find(item => item.id === id);
                if (!operator) return;
                try {
                    const result = await apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/badge`);
                    const data = result?.data ?? result;
                    $("badgeName").textContent = operator.fullName || operator.email || "Operatore RadioTech";
                    $("badgeDetails").textContent = `${operator.level || "TECNICO"} · ${operator.shift || "GIORNALIERO"}`;
                    $("badgeToken").textContent = data.qrCodeToken;
                    $("badgeQr").src = data.imageDataUrl;
                    $("badgeResetPassword").dataset.resetPassword=id;
                    $("badgeValidity").dataset.validity=id;
                    openModal("badgeModal");
                } catch (error) { toast("Badge non disponibile", error.message, "error"); }
            }

            async function regenerateQr(id) {
                if (!confirm("Rigenerare il QR? Il badge precedente verrà invalidato.")) return;
                try { await apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/regenerate-qr`, { method: "POST" }); await loadOperators(); toast("QR rigenerato", "Il badge precedente non è più valido."); await showBadge(id); }
                catch (error) { toast("Rigenerazione fallita", error.message, "error"); }
            }

            async function syncOperatorFromFirebase() {
                const email = $('syncOperatorEmail').value.trim();
                const fullName = $('syncOperatorName').value.trim();
                if (!email) {
                    toast('Email obbligatoria', 'Inserisci l’email dell’utente Firebase.', 'error');
                    return;
                }
                const button = $('syncOperatorBtn');
                button.disabled = true;
                try {
                    await apiFetch('/api/v1/operators/sync-firebase-user', {
                        method: 'POST',
                        body: JSON.stringify({ email, fullName })
                    });
                    $('syncOperatorEmail').value = '';
                    $('syncOperatorName').value = '';
                    await loadOperators();
                    toast('Operatore sincronizzato', 'Ora è disponibile tra i destinatari delle notifiche.');
                } catch (error) {
                    toast('Sincronizzazione fallita', error.message, 'error');
                } finally {
                    button.disabled = false;
                }
            }

            function renderRecipients() {
                const recipients = eligibleOperators();
                if (!recipients.length) {
                    $("recipientBox").innerHTML =
                        `<div class="empty">Nessun dipendente disponibile dal backend.</div>`;
                    return;
                }

                $("recipientBox").innerHTML = recipients.map((op, i) => {
                    const id = op.id || op.uid || op.operatorId;
                    const name = op.name || op.fullName || op.displayName || op.email || `Operatore ${i + 1}`;
                    const role = op.role || op.ruolo || "Operatore";

                    return `
                <label class="recipient">
                    <input type="checkbox" value="${escapeHtml(id)}">
                    <span class="avatar">${escapeHtml(initials(name))}</span>
                    <span class="recipient-info">
                        <strong>${escapeHtml(name)}</strong>
                        <span>${escapeHtml(role)}</span>
                    </span>
                </label>`;
                }).join("");
            }

            async function loadNotifications() {
                try {
                    const data = await api("/notifications");
                    state.notifications = Array.isArray(data) ? data : (data?.notifications || []);
                    renderNotifications();
                    updateNotificationCount();
                } catch (error) {
                    console.warn("Notifiche non disponibili:", error.message);
                    $("notificationHistory").innerHTML =
                        `<div class="empty">Endpoint notifiche non disponibile: ${escapeHtml(error.message)}</div>`;
                }
            }

            function renderNotifications() {
                if (!state.notifications.length) {
                    $("notificationHistory").innerHTML =
                        `<div class="empty">Nessuna comunicazione recente.</div>`;
                    return;
                }

                $("notificationHistory").innerHTML = state.notifications.slice(0, 20).map(n => {
                    const title = n.title || n.titolo || "Comunicazione";
                    const message = n.message || n.messaggio || "";
                    const date = n.createdAt || n.timestamp || n.date;
                    const isSignal = n.type === "WORKFORCE_SIGNAL";
                    const metadata = isSignal
                        ? `<span class="notification-meta">Segnalazione urgente${n.severity ? ` · ${escapeHtml(n.severity)}` : ""}${n.assetId ? ` · Asset ${escapeHtml(n.assetId)}` : ""}</span><button class="mini-btn notification-action" type="button" data-open-signals>Apri segnalazioni</button>`
                        : "";

                    return `
                <div class="notification-item${isSignal ? " notification-critical" : ""}">
                    <div class="notification-icon">${isSignal ? "!" : "✦"}</div>
                    <div>
                        <strong>${escapeHtml(title)}</strong>
                        <p>${escapeHtml(message)}</p>
                        ${metadata}
                    </div>
                    <span class="subtle">${date ? escapeHtml(new Date(date).toLocaleDateString("it-IT")) : "—"}</span>
                </div>`;
                }).join("");
                $("notificationHistory").querySelectorAll("[data-open-signals]").forEach(button => {
                    button.addEventListener("click", () => switchView("people"));
                });
            }

            function updateNotificationCount() {
                const unread = state.notifications.filter(n => n.read === false || n.letto === false).length;
                const el = $("notificationCount");

                if (unread > 0) {
                    el.textContent = unread > 99 ? "99+" : unread;
                    el.classList.remove("hidden");
                } else {
                    el.classList.add("hidden");
                }
            }

            async function sendNotification() {
                const target = $("notificationTarget").value;
                const title = $("notificationTitle").value.trim();
                const message = $("notificationMessage").value.trim();
                const priority = $("notificationPriority").value;

                if (!title || !message) {
                    toast("Notifica incompleta", "Inserisci titolo e messaggio.", "error");
                    return;
                }

                let recipients = [];

                if (target === "SELECTED") {
                    recipients = [...$("recipientBox").querySelectorAll("input[type=checkbox]:checked")]
                        .map(x => x.value);

                    if (!recipients.length) {
                        toast("Nessun destinatario", "Seleziona almeno un dipendente.", "error");
                        return;
                    }
                }

                const payload = {
                    target,
                    recipients,
                    title,
                    message,
                    priority,
                    type: "TASK_NOTIFICATION"
                };

                const button = $("sendNotificationBtn");
                button.disabled = true;
                button.textContent = "Invio...";

                try {
                    await apiFetch("/api/v1/notifications", {
                        method: "POST",
                        body: JSON.stringify(payload)
                    });

                    $("notificationTitle").value = "";
                    $("notificationMessage").value = "";
                    toast("Comunicazione salvata", "Disponibile nello storico. L’accettazione della push non conferma la lettura.");
                    await loadNotifications();
                } catch (error) {
                    toast("Invio fallito", error.message, "error");
                } finally {
                    button.disabled = false;
                    button.textContent = "Invia notifica";
                }
            }

            /* ---------- INVENTORY ---------- */

            async function loadInventory() {
                try {
                    const data = await apiFetch("/api/v1/inventory");
                    state.inventory = Array.isArray(data) ? data : (data?.inventory || []);
                    renderInventory();
                } catch (error) {
                    $("inventoryTableBody").innerHTML = `<tr><td colspan="7"><div class="empty">Magazzino non disponibile: ${escapeHtml(error.message)}</div></td></tr>`;
                    $("inventoryStatus").textContent = "Errore di sincronizzazione";
                }
            }

            function renderInventory() {
                const activeItems = state.inventory.filter(item => item.active !== false);
                const totalUnits = activeItems.reduce((sum, item) => sum + inventoryQty(item), 0);
                const lowItems = activeItems.filter(inventoryIsLow);
                const totalValue = activeItems.reduce((sum, item) => sum + inventoryQty(item) * Number(item.unitCost || 0), 0);
                const stats = $("inventoryStats");
                if (stats) stats.innerHTML = `
                    <div class="stat-card"><div class="stat-label">Articoli attivi</div><div class="stat-value">${activeItems.length}</div><div class="stat-foot">catalogo magazzino</div></div>
                    <div class="stat-card"><div class="stat-label">Unità disponibili</div><div class="stat-value">${number(totalUnits, 0)}</div><div class="stat-foot">quantità complessiva</div></div>
                    <div class="stat-card"><div class="stat-label">Sotto scorta</div><div class="stat-value">${lowItems.length}</div><div class="stat-foot">da riordinare</div></div>
                    <div class="stat-card"><div class="stat-label">Valore stimato</div><div class="stat-value">${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR", maximumFractionDigits: 0 }).format(totalValue)}</div><div class="stat-foot">costo unitario × quantità</div></div>`;

                const categories = [...new Set(state.inventory.map(item => item.category).filter(Boolean))].sort((a, b) => a.localeCompare(b, "it"));
                const categoryFilter = $("inventoryCategoryFilter");
                if (categoryFilter) {
                    const selected = categoryFilter.value;
                    categoryFilter.innerHTML = '<option value="">Tutte le categorie</option>' + categories.map(category => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join("");
                    categoryFilter.value = categories.includes(selected) ? selected : "";
                }
                $("inventoryStatus").textContent = `${activeItems.length} articoli attivi · ${lowItems.length} da riordinare`;

                const search = ($("inventorySearch")?.value || "").trim().toLocaleLowerCase("it-IT");
                const category = categoryFilter?.value || "";
                const stockFilter = $("inventoryStockFilter")?.value || "ACTIVE";
                const visible = state.inventory.filter(item => {
                    const active = item.active !== false;
                    const haystack = [item.sku, item.name, item.barcode, item.location, item.warehouse, item.aisle, item.rack, item.bin, item.category, item.supplier].join(" ").toLocaleLowerCase("it-IT");
                    return (stockFilter === "ALL" || (stockFilter === "ARCHIVED" ? !active : active))
                        && (stockFilter !== "LOW" || (active && inventoryIsLow(item)))
                        && (!category || item.category === category)
                        && (!search || haystack.includes(search));
                }).sort((a, b) => String(a.name || "").localeCompare(String(b.name || ""), "it"));
                const table = $("inventoryTableBody");
                if (!visible.length) {
                    table.innerHTML = `<tr><td colspan="7"><div class="empty">${state.inventory.length ? "Nessun articolo corrisponde ai filtri." : "Il magazzino è vuoto. Aggiungi il primo articolo."}</div></td></tr>`;
                    return;
                }
                table.innerHTML = visible.map(item => {
                    const low = inventoryIsLow(item);
                    const active = item.active !== false;
                    const qty = inventoryQty(item);
                    const value = qty * Number(item.unitCost || 0);
                    const stockLabel = low ? "RIORDINO" : "DISPONIBILE";
                    return `<tr>
                        <td><strong>${escapeHtml(item.name || "Articolo")}</strong><div class="subtle">${escapeHtml(item.sku || item.id || "—")}${item.barcode ? ` · ${escapeHtml(item.barcode)}` : ""}</div></td>
                        <td>${escapeHtml(item.category || "Generale")}<div class="subtle">${escapeHtml(item.unit || "pz")}</div></td>
                        <td><strong>${number(qty, 0)}</strong> ${escapeHtml(item.unit || "pz")}<div class="subtle">Soglia ${number(Number(item.minimumThreshold || item.threshold || item.soglia || 0), 0)} · Riordino ${number(Number(item.reorderQuantity || 0), 0)}</div></td>
                        <td>${escapeHtml([item.warehouse,item.aisle,item.rack,item.bin,item.location].filter(Boolean).join(" · ") || "—")}${item.supplier ? `<div class="subtle">${escapeHtml(item.supplier)}</div>` : ""}</td>
                        <td>${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(value)}<div class="subtle">${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(item.unitCost || 0))} / ${escapeHtml(item.unit || "pz")}</div></td>
                        <td><span class="badge ${!active ? "red" : low ? "yellow" : "green"}">${!active ? "ARCHIVIATO" : stockLabel}</span></td>
                        <td><div class="actions">${active ? `<button class="mini-btn" data-inventory-move="${escapeHtml(item.id)}">Movimenta</button><button class="mini-btn" data-inventory-edit="${escapeHtml(item.id)}">Modifica</button><button class="mini-btn" data-inventory-archive="${escapeHtml(item.id)}">Archivia</button>` : `<button class="mini-btn" data-inventory-restore="${escapeHtml(item.id)}">Ripristina</button>`}</div></td>
                    </tr>`;
                }).join("");
                applyPermissions();
                table.querySelectorAll("[data-inventory-move]").forEach(button => button.addEventListener("click", () => openInventoryMovement(button.dataset.inventoryMove)));
                table.querySelectorAll("[data-inventory-edit]").forEach(button => button.addEventListener("click", () => openInventoryItem(button.dataset.inventoryEdit)));
                table.querySelectorAll("[data-inventory-archive]").forEach(button => button.addEventListener("click", () => archiveInventoryItem(button.dataset.inventoryArchive)));
                table.querySelectorAll("[data-inventory-restore]").forEach(button => button.addEventListener("click", () => restoreInventoryItem(button.dataset.inventoryRestore)));
            }

            function inventoryQty(item) { return Math.max(0, Number(item.quantity ?? item.quantitaDisponibile ?? item.stock ?? 0)); }
            function inventoryIsLow(item) {
                const threshold = Number(item.minimumThreshold ?? item.threshold ?? item.soglia ?? item.sogliaMinima ?? 0);
                return item.active !== false && threshold >= 0 && inventoryQty(item) <= threshold;
            }

            function openInventoryItem(id = "") {
                const item = id ? state.inventory.find(row => row.id === id) : null;
                $("inventoryItemForm").reset();
                $("inventoryItemId").value = item?.id || "";
                $("inventorySku").value = item?.sku || "";
                $("inventoryName").value = item?.name || "";
                $("inventoryCategory").value = item?.category || "";
                $("inventoryUnit").value = item?.unit || "pz";
                $("inventoryBarcode").value = item?.barcode || "";
                $("inventoryLocation").value = item?.location || "";
                for (const [id,field] of [ ["Warehouse","warehouse"],["Aisle","aisle"],["Rack","rack"],["Bin","bin"]]) $("inventory"+id).value=item?.[field]||"";
                $("inventorySupplier").value = item?.supplier || "";
                $("inventoryUnitCost").value = Number(item?.unitCost || 0);
                $("inventoryMinimum").value = Number(item?.minimumThreshold || item?.threshold || 0);
                $("inventoryReorder").value = Number(item?.reorderQuantity || item?.minimumThreshold || 1);
                $("inventoryInitialQuantity").value = 0;
                $("inventoryDescription").value = item?.description || "";
                $("inventoryItemModalTitle").textContent = item ? `Modifica ${item.name}` : "Nuovo articolo";
                $("inventoryInitialQuantityField").classList.toggle("hidden", Boolean(item));
                $("inventoryItemFormStatus").textContent = item ? `Disponibilità attuale: ${inventoryQty(item)} ${item.unit || "pz"}. Usa “Movimenta” per modificare le scorte.` : "La quantità iniziale sarà registrata come carico nel registro movimenti.";
                openModal("inventoryItemModal");
            }

            async function saveInventoryItem(event) {
                event.preventDefault();
                const id = $("inventoryItemId").value;
                const payload = {
                    sku: $("inventorySku").value.trim(), name: $("inventoryName").value.trim(),
                    category: $("inventoryCategory").value.trim(), unit: $("inventoryUnit").value.trim() || "pz",
                    barcode: $("inventoryBarcode").value.trim(), location: $("inventoryLocation").value.trim(),
                    supplier: $("inventorySupplier").value.trim(),
                        warehouse: $("inventoryWarehouse").value.trim(), aisle: $("inventoryAisle").value.trim(), rack: $("inventoryRack").value.trim(), bin: $("inventoryBin").value.trim(), unitCost: Number($("inventoryUnitCost").value || 0),
                    minimumThreshold: Number($("inventoryMinimum").value || 0), reorderQuantity: Number($("inventoryReorder").value || 0),
                    description: $("inventoryDescription").value.trim()
                };
                if (!id) payload.quantity = Number($("inventoryInitialQuantity").value || 0);
                const button = $("saveInventoryItem");
                button.disabled = true;
                try {
                    await apiFetch(id ? `/api/v1/inventory/${encodeURIComponent(id)}` : "/api/v1/inventory", {
                        method: id ? "PUT" : "POST", body: JSON.stringify(payload)
                    });
                    closeModal("inventoryItemModal");
                    await loadInventory();
                    toast(id ? "Articolo aggiornato" : "Articolo creato", "Magazzino sincronizzato con il backend.");
                } catch (error) {
                    toast("Salvataggio non riuscito", error.message, "error");
                } finally { button.disabled = false; }
            }

            function openInventoryMovement(id) {
                const item = state.inventory.find(row => row.id === id);
                if (!item) return;
                $("inventoryMovementForm").reset();
                $("inventoryMovementItemId").value = id;
                $("inventoryMovementItemName").textContent = `${item.name} · ${item.sku} · giacenza ${inventoryQty(item)} ${item.unit || "pz"}`;
                $("inventoryMovementQuantity").value = "";
                updateInventoryMovementHint();
                openModal("inventoryMovementModal");
            }

            function updateInventoryMovementHint() {
                const type = $("inventoryMovementType").value;
                $("inventoryMovementQuantityLabel").textContent = type === "ADJUSTMENT" ? "Giacenza reale conteggiata" : "Quantità da movimentare";
                $("inventoryMovementQuantity").min = type === "ADJUSTMENT" ? "0" : "1";
            }

            async function saveInventoryMovement(event) {
                event.preventDefault();
                const button = event.submitter || event.target.querySelector('[type="submit"]');
                if (button.disabled) return;
                button.disabled = true;
                const id = $("inventoryMovementItemId").value;
                try {
                    await apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}/movements`, {
                        method: "POST", body: JSON.stringify({
                            type: $("inventoryMovementType").value,
                            quantity: Number($("inventoryMovementQuantity").value),
                            lotCode: $("inventoryMovementLot").value.trim(), lotExpiresOn: $("inventoryMovementLotExpiry").value,
                            reference: $("inventoryMovementReference").value.trim(),
                            note: $("inventoryMovementNote").value.trim()
                        })
                    });
                    closeModal("inventoryMovementModal");
                    await loadInventory();
                    toast("Movimento registrato", "Giacenza e registro sono stati aggiornati.");
                } catch (error) { toast("Movimento non riuscito", error.message, "error"); }
                finally { button.disabled = false; }
            }

            async function loadInventoryMovements() {
                const body = $("inventoryMovementsBody");
                body.innerHTML = '<tr><td colspan="6"><div class="empty"><span class="spinner"></span> Caricamento movimenti...</div></td></tr>';
                try {
                    const data = await apiFetch("/api/v1/inventory/movements?limit=250");
                    state.inventoryMovements = Array.isArray(data) ? data : (data?.data || []);
                    renderInventoryMovements();
                } catch (error) {
                    body.innerHTML = `<tr><td colspan="6"><div class="empty">Registro non disponibile: ${escapeHtml(error.message)}</div></td></tr>`;
                }
            }

            function renderInventoryMovements() {
                const body = $("inventoryMovementsBody");
                if (!state.inventoryMovements.length) {
                    body.innerHTML = '<tr><td colspan="6"><div class="empty">Nessun movimento registrato.</div></td></tr>';
                    return;
                }
                const labels = { RECEIPT: "CARICO", ISSUE: "SCARICO", ADJUSTMENT: "RETTIFICA", CONSUMPTION: "CONSUMO TASK" };
                body.innerHTML = state.inventoryMovements.map(movement => {
                    const quantity = Number(movement.quantity || 0);
                    const signed = ["ISSUE", "CONSUMPTION"].includes(movement.type) ? -Math.abs(quantity) : quantity;
                    const when = movement.createdAt ? new Date(movement.createdAt).toLocaleString("it-IT") : "—";
                    return `<tr><td>${escapeHtml(when)}</td><td><strong>${escapeHtml(movement.name || "Articolo")}</strong><div class="subtle">${escapeHtml(movement.sku || "")}</div></td><td>${escapeHtml(labels[movement.type] || movement.type || "—")}</td><td class="${signed < 0 ? "text-danger" : ""}">${signed > 0 ? "+" : ""}${number(signed, 0)}</td><td>${movement.previousQuantity == null ? "—" : `${number(Number(movement.previousQuantity), 0)} → ${number(Number(movement.resultingQuantity), 0)}`}</td><td>${escapeHtml([movement.reference, movement.note, movement.lotCode ? "Lotto "+movement.lotCode : "", movement.warehouse, ...(movement.lotAllocations || []).map(l => (l.lotCode || "Senza lotto")+": "+l.quantity)].filter(Boolean).join(" · ") || "—")}</td></tr>`;
                }).join("");
            }

            async function archiveInventoryItem(id) {
                const item = state.inventory.find(row => row.id === id);
                if (!item || !confirm(`Archiviare “${item.name}”? Lo storico resta consultabile e l'articolo sarà ripristinabile.`)) return;
                try {
                    await apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}`, { method: "DELETE" });
                    await loadInventory();
                    toast("Articolo archiviato", "L'articolo è stato mantenuto nello storico.");
                } catch (error) { toast("Archiviazione non riuscita", error.message, "error"); }
            }

            async function restoreInventoryItem(id) {
                try {
                    await apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}/restore`, { method: "POST" });
                    await loadInventory();
                    toast("Articolo ripristinato", "L'articolo è di nuovo disponibile.");
                } catch (error) { toast("Ripristino non riuscito", error.message, "error"); }
            }

            function exportInventoryCsv() {
                const columns = ["sku", "name", "category", "quantity", "unit", "minimumThreshold", "reorderQuantity", "location", "supplier", "barcode", "unitCost", "active"];
                const quote = value => {
                    let text = String(value ?? "");
                    if (/^[\s]*[=+@-]/.test(text)) text = "'" + text;
                    return `"${text.replaceAll('"', '""')}"`;
                };
                const rows = [columns.join(";"), ...state.inventory.map(item => columns.map(column => quote(column === "quantity" ? inventoryQty(item) : column === "minimumThreshold" ? item.minimumThreshold ?? item.threshold ?? 0 : item[column])).join(";"))];
                const blob = new Blob(["\uFEFF" + rows.join("\r\n")], { type: "text/csv;charset=utf-8" });
                const link = document.createElement("a");
                link.href = URL.createObjectURL(blob);
                link.download = `radiotech-magazzino-${new Date().toISOString().slice(0, 10)}.csv`;
                link.click();
                URL.revokeObjectURL(link.href);
            }

            /* ---------- NAVIGATION ---------- */

            function switchView(view) {
                document.querySelectorAll(".view").forEach(v => v.classList.remove("active"));
                document.querySelectorAll(".nav button").forEach(b => b.classList.remove("active"));

                const target = $(`view-${view}`);
                const nav = document.querySelector(`.nav button[data-view="${view}"]`);

                if (target) target.classList.add("active");
                if (nav) nav.classList.add("active");

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

            /* ---------- SYSTEM ---------- */

            async function loadOperationalHealth() {
                $('operationsHealthRefresh').disabled = true;
                try {
                    const response = await apiFetch('/api/v1/operations/health');
                    const health = response?.data ?? response, usage = health.firestore || {}, files = health.attachments || {};
                    const number = value => value == null ? 'Non disponibile' : Number(value).toLocaleString('it-IT');
                    const alarms = (health.alarms || []).map(alarm => `<li>${escapeHtml(alarm.message)}</li>`).join('');
                    const backupLabels = {OK: 'Verificato', NOT_CONFIGURED: 'Da configurare', STALE: 'Backup scaduto', RESTORE_REQUIRED: 'Ripristino da verificare', INVALID: 'Stato da verificare'};
                    $('operationsHealth').innerHTML = `<p>Allegati: <strong>${number(files.count)}</strong> · ${(Number(files.bytes || 0) / 1048576).toFixed(2)} MiB · ${escapeHtml(health.backend || '')}</p><p>Report da revisionare: ${number(health.pendingReports)} · Backup: <strong>${escapeHtml(backupLabels[health.backup?.state] || 'Non disponibile')}</strong></p><p>Letture di oggi: ${number(usage.reads)} · Scritture: ${number(usage.writes)} · Eliminazioni: ${number(usage.deletes)}</p><p>Spazio database e indici: ${usage.storageBytes == null ? 'Non disponibile' : (Number(usage.storageBytes)/1048576).toFixed(2) + ' MiB'}</p><p class="subtle">${escapeHtml(usage.note || '')} · Giornata quota: ${escapeHtml(usage.quotaDayTimezone || '')}</p>${alarms ? `<ul role="status">${alarms}</ul>` : '<p>Nessun avviso dalle verifiche disponibili.</p>'}`;
                } catch (error) { $('operationsHealth').textContent = `Monitoraggio non disponibile: ${error.message}`; }
                finally { $('operationsHealthRefresh').disabled = false; }
            }

            async function checkSystem() {
                try {
                    await api("/stats");
                    setBackendState(true);
                    toast("Sistema operativo", "Backend raggiungibile.");
                } catch (error) {
                    setBackendState(false);
                    setText("firestoreStatus", "UNKNOWN");
                    $("firestoreBadge").textContent = "CHECK";
                    $("firestoreBadge").className = "badge yellow";
                    toast("Problema backend", error.message, "error");
                }
            }

            async function loadAudit() {
                try {
                    const entries = await api('/audit?limit=30');
                    const list = Array.isArray(entries) ? entries : [];
                    $('auditList').innerHTML = list.length ? list.map(entry => `<div class="task-row"><div><div class="task-title">${escapeHtml(formatStatus(entry.action || 'EVENTO'))}</div><div class="task-meta">${escapeHtml(formatStatus(entry.resource || ''))} ${escapeHtml(entry.resourceId || '')} · ${escapeHtml(entry.actor || 'Sistema')}</div></div><span class="subtle">${entry.timestamp ? escapeHtml(new Date(entry.timestamp).toLocaleString('it-IT')) : '—'}</span></div>`).join('') : '<div class="empty">Nessun evento registrato.</div>';
                } catch (error) {
                    $('auditList').innerHTML = `<div class="empty">Audit non disponibile: ${escapeHtml(error.message)}</div>`;
                }
            }

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

            /* ---------- MODALS ---------- */

            function openModal(id) {
                $(id).classList.add("open");
                document.body.classList.add("modal-open");
            }

            function closeModal(id) {
                $(id).classList.remove("open");
                if (!document.querySelector(".modal-backdrop.open")) {
                    document.body.classList.remove("modal-open");
                }
            }

            /* ---------- EVENTS ---------- */

            $("loginForm").addEventListener("submit", login);

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
            $("antennaSearch").addEventListener("input", renderAntennas);
            $("antennaStatusFilter").addEventListener("change", renderAntennas);

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
            $("taskSearch")?.addEventListener("input", renderTaskList);
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
            $("inventorySearch")?.addEventListener("input", renderInventory);
            $("inventoryCategoryFilter")?.addEventListener("change", renderInventory);
            $("inventoryStockFilter")?.addEventListener("change", renderInventory);
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
            $("operatorSearch")?.addEventListener("input", renderOperatorTable);
            $("operatorRoleFilter")?.addEventListener("change", renderOperatorTable);

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
                enterprise = window.RadioTechEnterprise({ apiFetch, state, escapeHtml, toast, switchView });
                companyHub = window.RadioTechCompanyHub({ apiFetch, state, escapeHtml, toast, switchView });
                proSuite = window.RadioTechPro({ apiFetch, state, escapeHtml, toast, switchView });
                proSecurity = window.RadioTechProSecurity({ apiFetch, toast, switchView });
                window.RadioTechTelcoTools();
                window.RadioTechOperations({apiFetch, state, escapeHtml, toast, loadOperators, loadInventory});
                management = window.RadioTechManagement({ apiFetch, state, escapeHtml, switchView, loadOperators, loadReports });
                signatureWorkspace = window.RadioTechSignatureWorkspace({state, switchView, initials});
                setTimeout(() => window.RadioTechPersonalization.init({apiFetch,state,switchView,toast}), 0);
                const skip = document.createElement("a");
                skip.href = "#mainContent"; skip.className = "skip-link"; skip.textContent = "Vai al contenuto";
                document.body.prepend(skip);
                boot();
            });

        })();
