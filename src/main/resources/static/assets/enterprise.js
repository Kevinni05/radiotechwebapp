"use strict";
window.RadioTechEnterprise = function ({ apiFetch, state, escapeHtml: esc, toast, switchView }) {
    const $ = id => document.getElementById(id);
    const unwrap = body => body?.data ?? body;
    const list = body => { const data = unwrap(body); return Array.isArray(data) ? data : []; };
    const managers = new Set(["SUPER_ADMIN", "ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER"]);
    const canManage = () => managers.has(String(state.authRole || state.user?.role || "").toUpperCase());
    let incidents = [], alerts = [], skills = [];
    let refreshPromise = null;
    const next = { DETECTED: "ACKNOWLEDGED", ACKNOWLEDGED: "ASSIGNED", ASSIGNED: "INVESTIGATING", INVESTIGATING: "MITIGATED", MITIGATED: "RESOLVED", RESOLVED: "POST_MORTEM", POST_MORTEM: "CLOSED" };
    const date = value => value ? new Date(value).toLocaleString("it-IT") : "—";

    const nav = document.querySelector(".nav");
    const navButton = document.createElement("button");
    navButton.dataset.view = "enterprise";
    navButton.textContent = "◎ Rischi e competenze";
    navButton.addEventListener("click", () => { switchView("enterprise"); refresh(); });
    nav.append(navButton);
    const section = document.createElement("section");
    section.className = "view";
    section.id = "view-enterprise";
    section.innerHTML = `
      <div class="enterprise-kpis" aria-label="Indicatori operativi">
        <div class="panel"><span>Incidenti aperti</span><strong id="enterpriseOpen">—</strong></div>
        <div class="panel"><span>Severità critica</span><strong id="enterpriseCritical">—</strong></div>
        <div class="panel"><span>Alert da leggere</span><strong id="enterpriseUnread">—</strong></div>
        <div class="panel"><span>Incarichi oltre scadenza</span><strong id="enterpriseOverdue">—</strong></div>
      </div>
      <div class="panel enterprise-section">
        <div class="panel-head"><div><h3>Centro operativo</h3><span>Incidenti, SLA e segnali di rischio dell’azienda</span></div>
        <div class="actions"><button class="btn" id="enterpriseRefresh">Aggiorna</button><button class="btn primary" id="enterpriseEvaluate" data-manager-only>Valuta SLA e rischi</button></div></div>
        <div class="panel-body"><p class="enterprise-status" id="enterpriseStatus" role="status" aria-live="polite"></p>
        <label for="enterpriseSearch">Cerca incidenti e alert</label><input id="enterpriseSearch" type="search" placeholder="Titolo, stato, asset o severità"></div>
      </div>
      <div class="enterprise-grid">
        <div class="panel enterprise-section"><div class="panel-head"><h3>Registro incidenti</h3></div><div class="panel-body" id="enterpriseIncidents"></div></div>
        <div class="panel enterprise-section"><div class="panel-head"><h3>Alert operativi</h3></div><div class="panel-body" id="enterpriseAlerts"></div></div>
      </div>
      <div class="enterprise-grid">
        <div class="panel enterprise-section" data-manager-only><div class="panel-head"><h3>Apri un incidente</h3></div><div class="panel-body">
          <form id="enterpriseIncidentForm" class="enterprise-form">
            <div><label for="enterpriseIncidentTitle">Titolo</label><input id="enterpriseIncidentTitle" required maxlength="160"></div>
            <div><label for="enterpriseIncidentDescription">Descrizione</label><textarea id="enterpriseIncidentDescription" maxlength="2000"></textarea></div>
            <div><label for="enterpriseSeverity">Severità</label><select id="enterpriseSeverity"><option>LOW</option><option selected>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select></div>
            <div><label for="enterpriseAsset">ID asset interessato (facoltativo)</label><input id="enterpriseAsset" maxlength="128"></div>
            <button class="btn primary" type="submit">Registra incidente</button>
          </form></div></div>
        <div class="panel enterprise-section"><div class="panel-head"><div><h3>Competenze e abilitazioni</h3><span>Certificazioni, livelli e scadenze della squadra</span></div></div><div class="panel-body">
          <label for="enterpriseOperator">Operatore</label><select id="enterpriseOperator"><option value="">Seleziona operatore</option></select>
          <div id="enterpriseSkills" aria-live="polite"></div>
          <form id="enterpriseSkillForm" class="enterprise-form" data-manager-only>
            <div><label for="enterpriseSkill">Competenza</label><select id="enterpriseSkill"><option>RF</option><option>LTE</option><option>5G</option><option>FIBER</option><option>IP</option><option>MICROWAVE</option><option>POWER</option><option>HVAC</option><option>SAFETY</option></select></div>
            <div><label for="enterpriseLevel">Livello (1–5)</label><input id="enterpriseLevel" type="number" min="1" max="5" value="1" required></div>
            <div><label for="enterpriseCertification">Certificazione</label><input id="enterpriseCertification" maxlength="200" required></div>
            <div><label for="enterpriseExpiration">Scadenza certificazione</label><input id="enterpriseExpiration" type="date" required></div>
            <div><label for="enterpriseAuthorized">Abilitazione</label><select id="enterpriseAuthorized"><option value="true">Autorizzato</option><option value="false">Non autorizzato</option></select></div>
            <button class="btn primary" type="submit">Salva competenza</button>
          </form></div></div>
      </div>`;
    const container=document.querySelector(".view").parentElement;
    container.insertBefore(section,container.querySelector(":scope > .footer"));

    async function busy(button, action) {
        if (button.disabled) return;
        button.disabled = true;
        try { await action(); }
        catch (error) { toast("Operazione non riuscita", error.message, "error"); }
        finally { button.disabled = false; }
    }

    function render() {
        const query = $("enterpriseSearch").value.trim().toLowerCase();
        const matches = record => !query || Object.values(record).filter(v => typeof v === "string").join(" ").toLowerCase().includes(query);
        const open = incidents.filter(i => i.status !== "CLOSED");
        $("enterpriseOpen").textContent = canManage() ? open.length : "—";
        $("enterpriseCritical").textContent = canManage() ? open.filter(i => i.severity === "CRITICAL").length : "—";
        $("enterpriseUnread").textContent = alerts.filter(a => !a.letto && a.status !== "RESOLVED").length;
        $("enterpriseOverdue").textContent = state.tasks.filter(t => t.dueAt && new Date(t.dueAt) < new Date() && !["CLOSED", "CANCELLED", "COMPLETED", "APPROVED", "REPORT_SUBMITTED"].includes(t.status)).length;
        $("enterpriseIncidents").innerHTML = incidents.filter(matches).map(i => `
          <div class="task-row"><div><div class="task-title">${esc(i.title)}</div>
          <div class="task-meta">${esc(window.RadioTechItalian.label(i.severity))} · ${esc(window.RadioTechItalian.label(i.status))} · ${esc(i.assetId || "Nessun impianto")} · ${esc(date(i.updatedAt || i.createdAt))}</div>
          <p class="enterprise-summary">${esc(i.description || "")}</p>
          ${i.rootCause ? `<p class="enterprise-summary">Causa: ${esc(i.rootCause)}<br>Risoluzione: ${esc(i.resolution || "")}</p>` : ""}
          </div>${canManage() && next[i.status] ? `<button class="mini-btn" data-incident-next="${esc(i.id)}">${esc(window.RadioTechItalian.label(next[i.status]))}</button>` : ""}</div>
        `).join("") || `<div class="enterprise-empty">${canManage() ? "Nessun incidente corrispondente." : "Registro incidenti riservato ai responsabili."}</div>`;
        $("enterpriseAlerts").innerHTML = alerts.filter(matches).map(a => `
          <div class="task-row"><div><div class="task-title">${esc(a.descrizione || a.description || a.title || "Alert operativo")}</div>
          <div class="task-meta">${esc(a.priorita || a.severity || "MEDIA")} · ${esc(a.antennaId || "—")} · ${esc(date(a.timestamp))}</div></div>
          ${!a.letto && canManage() ? `<button class="mini-btn" data-alert-read="${esc(a.id)}">Segna letto</button>` : `<span class="badge">${a.letto ? "Letto" : "Da leggere"}</span>`}</div>
        `).join("") || '<div class="enterprise-empty">Nessun alert corrispondente.</div>';
        section.querySelectorAll("[data-manager-only]").forEach(element => { element.hidden = !canManage(); });
    }

    function refresh() {
        if (refreshPromise) return refreshPromise;
        refreshPromise = (async () => {
            const token = state.token;
            $("enterpriseStatus").textContent = "Aggiornamento in corso…";
            const jobs = [apiFetch("/api/v1/alerts"), apiFetch("/api/v1/operators")];
            const mayReadIncidents = canManage();
            if (mayReadIncidents) jobs.push(apiFetch("/api/v1/incidents"));
            const results = await Promise.allSettled(jobs);
            if (token !== state.token) return;
            if (results[0].status === "fulfilled") alerts = list(results[0].value);
            if (results[1].status === "fulfilled") {
                const selected = $("enterpriseOperator").value;
                $("enterpriseOperator").innerHTML = '<option value="">Seleziona operatore</option>' + list(results[1].value).map(o => `<option value="${esc(o.id)}">${esc(o.fullName || o.email || o.id)}</option>`).join("");
                $("enterpriseOperator").value = selected;
            }
            if (mayReadIncidents && results[2].status === "fulfilled") incidents = list(results[2].value);
            if (!mayReadIncidents) incidents = [];
            render();
            const failed = results.filter(r => r.status === "rejected");
            $("enterpriseStatus").textContent = failed.length ? `Aggiornamento parziale: ${failed.map(r => r.reason.message).join(" · ")}` : `Dati aggiornati alle ${new Date().toLocaleTimeString("it-IT")}`;
        })().finally(() => { refreshPromise = null; });
        return refreshPromise;
    }

    async function loadSkills() {
        const operatorId = $("enterpriseOperator").value;
        if (!operatorId) { $("enterpriseSkills").textContent = "Seleziona un operatore per vedere le abilitazioni."; return; }
        $("enterpriseSkills").textContent = "Caricamento…";
        try {
            const data = await apiFetch(`/api/v1/operators/${encodeURIComponent(operatorId)}/skills`);
            if (operatorId !== $("enterpriseOperator").value) return;
            skills = list(data);
            const now = new Date();
            const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
            $("enterpriseSkills").innerHTML = skills.map(s => `<div class="task-row"><div><strong>${esc(s.skill)}</strong> · Livello ${esc(s.level)}<div class="task-meta">${esc(s.certification)} · Scade ${esc(s.expiration)} · ${s.authorized && s.expiration >= today ? "Autorizzato" : "Non abilitato / scaduto"}</div></div></div>`).join("") || '<div class="enterprise-empty">Nessuna competenza registrata.</div>';
        } catch (error) { $("enterpriseSkills").textContent = error.message; }
    }

    $("enterpriseRefresh").addEventListener("click", event => busy(event.currentTarget, refresh));
    $("enterpriseEvaluate").addEventListener("click", event => busy(event.currentTarget, async () => {
        await apiFetch("/api/v1/alerts/evaluate", { method: "POST" });
        await refresh(); toast("Valutazione completata", "SLA, inventario e incidenti verificati.");
    }));
    $("enterpriseSearch").addEventListener("input", render);
    $("enterpriseOperator").addEventListener("change", loadSkills);
    $("enterpriseIncidentForm").addEventListener("submit", event => {
        event.preventDefault();
        busy(event.submitter, async () => {
            await apiFetch("/api/v1/incidents", { method: "POST", body: JSON.stringify({ title: $("enterpriseIncidentTitle").value.trim(), description: $("enterpriseIncidentDescription").value.trim(), severity: $("enterpriseSeverity").value, assetId: $("enterpriseAsset").value.trim() || null }) });
            event.target.reset(); await refresh(); toast("Incidente registrato");
        });
    });
    $("enterpriseSkillForm").addEventListener("submit", event => {
        event.preventDefault();
        busy(event.submitter, async () => {
            const id = $("enterpriseOperator").value;
            if (!id) throw new Error("Seleziona un operatore.");
            await apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/skills/${encodeURIComponent($("enterpriseSkill").value)}`, { method: "PUT", body: JSON.stringify({ level: Number($("enterpriseLevel").value), certification: $("enterpriseCertification").value.trim(), expiration: $("enterpriseExpiration").value, authorized: $("enterpriseAuthorized").value === "true" }) });
            await loadSkills(); toast("Competenza aggiornata");
        });
    });
    section.addEventListener("click", event => {
        const button = event.target.closest("[data-incident-next], [data-alert-read]");
        if (!button) return;
        busy(button, async () => {
            if (button.dataset.alertRead) {
                await apiFetch(`/api/v1/alerts/${encodeURIComponent(button.dataset.alertRead)}/read`, { method: "POST" });
            } else {
                const incident = incidents.find(i => i.id === button.dataset.incidentNext);
                const payload = { status: next[incident.status] };
                if (payload.status === "RESOLVED") {
                    payload.rootCause = prompt("Causa dell'incidente:");
                    if (!payload.rootCause?.trim()) return;
                    payload.resolution = prompt("Intervento risolutivo:");
                    if (!payload.resolution?.trim()) return;
                }
                await apiFetch(`/api/v1/incidents/${encodeURIComponent(incident.id)}/transitions`, { method: "POST", body: JSON.stringify(payload) });
            }
            await refresh();
        });
    });
    function reset() {
        incidents = []; alerts = []; skills = [];
        $("enterpriseOperator").innerHTML = '<option value="">Seleziona operatore</option>';
        $("enterpriseSkills").textContent = "";
        $("enterpriseIncidentForm").reset(); $("enterpriseSkillForm").reset(); render();
    }
    return { refresh, reset };
};
