"use strict";
window.RadioTechCompanyHub = function ({ apiFetch, state, escapeHtml: esc, toast, switchView }) {
  const $ = id => document.getElementById(id);
  const manager = () => ["SUPER_ADMIN", "ADMIN", "CAPO", "CHIEF_EXECUTIVE", "NETWORK_MANAGER"].includes(state.authRole || state.user?.role);
  const label = value => ({ ACTIVE: "Al lavoro", BREAK: "In pausa", OFF_DUTY: "Fuori turno", READY: "Disponibile", NEEDS_BREAK: "Richiede pausa", REQUEST_SUPPORT: "Richiede supporto", OPEN: "Da prendere in carico", ACKNOWLEDGED: "In carico", RESOLVED: "Risolto", HAZARD: "Pericolo", NEAR_MISS: "Quasi incidente", SUPPORT_REQUEST: "Supporto", CRITICAL: "Critico", HIGH: "Alto", MEDIUM: "Medio", LOW: "Basso" }[value] || value);
  let history = [], shift = null, signals = [], risks = [], busy = false;
  function addView(name, title, markup) {
    const button = document.createElement("button"); button.dataset.view = name; button.textContent = title;
    button.addEventListener("click", () => { switchView(name); if (name === "ai") loadAI(); else loadPeople(); });
    document.querySelector(".nav").append(button);
    const section = document.createElement("section"); section.className = "view"; section.id = `view-${name}`; section.innerHTML = markup;
    document.querySelector(".view").parentElement.append(section);
  }
  addView("ai", "✧ AI e previsioni", `
    <div class="enterprise-kpis"><div class="panel"><span>Asset osservati</span><strong id="aiAssets">—</strong></div><div class="panel"><span>Priorità elevate</span><strong id="aiRiskCount">—</strong></div><div class="panel"><span>Incarichi attivi</span><strong id="aiTasks">—</strong></div><div class="panel"><span>Oltre scadenza</span><strong id="aiOverdue">—</strong></div></div>
    <div class="enterprise-grid"><div class="panel enterprise-section"><div class="panel-head"><div><h3>Pianificazione preventiva</h3><span>Indicatori spiegabili e intervalli storici</span></div><button id="aiRefresh" class="btn">Aggiorna</button></div><div class="panel-body"><p id="aiInsightStatus" class="enterprise-status" role="status"></p><label for="aiRiskSearch">Cerca asset</label><input id="aiRiskSearch" type="search" placeholder="Nome o identificativo"><div id="aiRisks"></div><button class="btn" id="aiExport">Esporta priorità CSV</button></div></div>
    <div class="panel enterprise-section"><div class="panel-head"><div><h3>Assistente aziendale</h3><span>Chat libera con modello locale</span></div><button id="aiClear" class="btn">Nuova chat</button></div><div class="panel-body"><p id="aiProvider" class="enterprise-summary"></p><div id="aiConversation" class="ai-conversation" role="log" aria-live="polite"></div><form id="aiChatForm" class="enterprise-form"><label for="aiMessage">Messaggio</label><textarea id="aiMessage" rows="4" required maxlength="3000" placeholder="Chiedi un'analisi, prepara una comunicazione o approfondisci un problema tecnico"></textarea><label class="ai-context"><input type="checkbox" id="aiContext"> Includi solo indicatori operativi autorizzati</label><button class="btn primary" id="aiSend" type="submit">Invia all'assistente</button><p id="aiChatStatus" class="enterprise-status" role="status"></p></form><p class="enterprise-summary">La chat resta in questa sessione e non esegue azioni. Verifica le risposte prima di prendere decisioni. Nessun costo per richiesta del modello locale; l'infrastruttura resta a carico dell'azienda.</p></div></div></div>`);
  addView("people", "♡ Squadra e sicurezza", `
    <div class="enterprise-grid"><div class="panel enterprise-section"><div class="panel-head"><h3>Il mio turno e le pause</h3><button class="btn" id="peopleRefresh">Aggiorna</button></div><div class="panel-body"><p id="shiftSummary" class="enterprise-status" role="status"></p><p id="shiftReminder" class="enterprise-summary"></p><label for="shiftReadiness">Disponibilità dichiarata</label><select id="shiftReadiness"><option value="READY">Disponibile</option><option value="NEEDS_BREAK">Ho bisogno di una pausa</option><option value="REQUEST_SUPPORT">Chiedo supporto</option></select><div id="shiftActions" class="actions"></div><p class="enterprise-summary">Registrazione volontaria visibile ai responsabili. Nessun tracciamento continuo della posizione. Non sostituisce il sistema presenze.</p></div></div>
    <div class="panel enterprise-section"><div class="panel-head"><h3>Segnala e chiedi supporto</h3></div><div class="panel-body"><form id="peopleSignalForm" class="enterprise-form"><label for="signalType">Tipo</label><select id="signalType"><option value="HAZARD">Pericolo</option><option value="NEAR_MISS">Quasi incidente</option><option value="SUPPORT_REQUEST">Richiesta di supporto</option></select><label for="signalSeverity">Priorità</label><select id="signalSeverity"><option value="LOW">Bassa</option><option value="MEDIUM" selected>Media</option><option value="HIGH">Alta</option><option value="CRITICAL">Critica</option></select><label for="signalDescription">Descrizione</label><textarea id="signalDescription" maxlength="1000" required rows="3"></textarea><label for="signalAsset">ID asset (facoltativo)</label><input id="signalAsset" maxlength="128"><button class="btn primary" type="submit">Registra segnalazione</button></form><p class="enterprise-summary">Visibile ai responsabili del tuo tenant. Non inserire diagnosi o dati sanitari. Per un pericolo immediato usa i canali di emergenza aziendali: questa sezione non è monitorata in continuo.</p></div></div></div>
    <div class="panel enterprise-section" id="peopleTeamPanel"><div class="panel-head"><h3>Disponibilità della squadra</h3><span>Massimo 250 registrazioni; stati non aggiornati evidenziati</span></div><div class="panel-body" id="peopleTeam"></div></div>
    <div class="panel enterprise-section"><div class="panel-head"><h3>Segnalazioni e presa in carico</h3><span>Ultime 100; ciascun operatore vede le proprie</span></div><div class="panel-body" id="peopleSignals"></div></div><p id="peopleStatus" class="enterprise-status" role="status"></p>`);
  async function action(button, fn) {
    if (button.disabled) return; button.disabled = true;
    try { await fn(); } catch (e) { toast("Operazione non riuscita", e.message, "error"); }
    finally { button.disabled = false; }
  }
  function renderRisks() {
    const query = $("aiRiskSearch").value.trim().toLowerCase();
    $("aiRisks").innerHTML = risks.filter(r => `${r.name} ${r.assetId}`.toLowerCase().includes(query)).map(r => `<article class="task-row"><div><strong>${esc(r.name || r.assetId)}</strong><div class="task-meta">${esc(r.assetId)} · ${esc(label(r.level))} · Indicatore ${esc(r.score)}/100</div><ul class="enterprise-summary">${r.reasons.map(reason => `<li>${esc(reason)}</li>`).join("")}</ul><p class="enterprise-summary">${r.dataQuality === "LIMITED" ? "Dati limitati. " : ""}${esc(r.recommendation)}</p><p class="task-meta">${r.estimatedMaintenanceAt ? `Prossima manutenzione stimata: ${esc(new Date(r.estimatedMaintenanceAt).toLocaleDateString("it-IT"))} · Mediana ${esc(r.medianIntervalDays)} giorni · ${esc(r.historyCount)} interventi` : "Storico insufficiente per stimare una data."}</p></div></article>`).join("") || '<div class="enterprise-empty">Nessun asset corrispondente.</div>';
  }
  async function loadAI() {
    const epoch = state.sessionEpoch || 0;
    $("aiInsightStatus").textContent = "Analisi in corso…";
    const [analysis, provider] = await Promise.allSettled([apiFetch("/api/v1/ai/insights"), apiFetch("/api/v1/ai/status")]);
    if (epoch !== (state.sessionEpoch || 0)) return;
    if (analysis.status === "fulfilled") {
      const data = analysis.value; risks = data.risks || [];
      $("aiAssets").textContent = data.assetsObserved; $("aiRiskCount").textContent = data.highRiskAssets;
      $("aiTasks").textContent = data.activeTasks; $("aiOverdue").textContent = data.overdueTasks;
      $("aiInsightStatus").textContent = `${data.notice}${data.partial ? " Analisi parziale: raggiunto il limite del campione." : ""}`; renderRisks();
    } else $("aiInsightStatus").textContent = analysis.reason.message;
    if (provider.status === "fulfilled") {
      $("aiProvider").textContent = provider.value.enabled ? `Modello aziendale: ${provider.value.model}` : "Il responsabile deve attivare il modello aziendale per usare la chat.";
      $("aiSend").disabled = busy || !provider.value.enabled;
    } else { $("aiProvider").textContent = provider.reason.message; $("aiSend").disabled = true; }
  }
  function renderChat() {
    $("aiConversation").innerHTML = history.map(m => `<article class="ai-message ${m.role}"><strong>${m.role === "user" ? "Tu" : "Assistente"}</strong><p>${esc(m.content)}</p></article>`).join("");
    $("aiConversation").scrollTop = $("aiConversation").scrollHeight;
  }
  $("aiChatForm").addEventListener("submit", async event => {
    event.preventDefault(); if (busy) return;
    const message = $("aiMessage").value.trim(); if (!message) return;
    const epoch = state.sessionEpoch || 0; const previous = history.slice(-8).map(m => ({ role: m.role, content: m.content.slice(0, 3000) })); busy = true; $("aiSend").disabled = true;
    history.push({ role: "user", content: message }); renderChat(); $("aiChatStatus").textContent = "L'assistente sta rispondendo…";
    try {
      const result = await apiFetch("/api/v1/ai/chat", { method: "POST", timeoutMs: 55000, body: JSON.stringify({ message, history: previous, includeOperationalContext: $("aiContext").checked }) });
      if (epoch !== (state.sessionEpoch || 0)) return;
      history.push({ role: "assistant", content: result.answer }); history = history.slice(-40); $("aiMessage").value = ""; renderChat(); $("aiChatStatus").textContent = result.notice;
    } catch (e) { if (epoch === (state.sessionEpoch || 0)) { history.pop(); renderChat(); $("aiChatStatus").textContent = e.message; } }
    finally { if (epoch === (state.sessionEpoch || 0)) { busy = false; $("aiSend").disabled = false; } }
  });
  $("aiClear").addEventListener("click", () => { if (!busy) { history = []; renderChat(); $("aiChatStatus").textContent = ""; } });
  $("aiRefresh").addEventListener("click", e => action(e.currentTarget, loadAI)); $("aiRiskSearch").addEventListener("input", renderRisks);
  $("aiExport").addEventListener("click", () => {
    const quote = value => { let text = String(value ?? ""); if (/^\s*[=+@-]/.test(text)) text = "'" + text; return `"${text.replaceAll('"', '""')}"`; };
    const rows = [["Asset", "Nome", "Indicatore", "Livello", "Data stimata", "Motivazioni"], ...risks.map(r => [r.assetId, r.name, r.score, r.level, r.estimatedMaintenanceAt, r.reasons.join(" | ")])];
    const url = URL.createObjectURL(new Blob(["\uFEFF" + rows.map(r => r.map(quote).join(";")).join("\r\n")], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a"); link.href = url; link.download = "radiotech-priorita-manutenzione.csv"; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  function renderShift() {
    if (!shift) return;
    $("shiftSummary").textContent = `${label(shift.status)} · ${shift.workMinutes} min attività · ${shift.restMinutes} min pausa`;
    $("shiftReadiness").value = shift.readiness;
    $("shiftReminder").textContent = shift.stale ? "Registrazione non aggiornata: verifica il turno." : shift.pauseSuggested ? "È il momento di valutare una pausa con il responsabile." : "Ricorda di registrare le pause e la fine del turno.";
    const actions = shift.status === "OFF_DUTY" ? [["START", "Inizia turno"]] : shift.status === "BREAK" ? [["RESUME", "Riprendi"], ["END", "Termina turno"]] : [["BREAK", "Inizia pausa"], ["END", "Termina turno"]];
    $("shiftActions").innerHTML = actions.map(([value, title]) => `<button class="btn" data-shift-action="${value}">${title}</button>`).join("");
  }
  async function loadPeople() {
    const epoch = state.sessionEpoch || 0; $("peopleStatus").textContent = "Aggiornamento in corso…"; $("peopleTeamPanel").hidden = !manager();
    try {
      const jobs = [apiFetch("/api/v1/workforce/me/shift"), apiFetch("/api/v1/workforce/signals")];
      if (manager()) jobs.push(apiFetch("/api/v1/workforce/shifts"));
      const results = await Promise.all(jobs); if (epoch !== (state.sessionEpoch || 0)) return;
      shift = results[0]; signals = results[1]; renderShift();
      if (manager()) $("peopleTeam").innerHTML = results[2].map(s => `<div class="task-row"><div><strong>${esc(s.name)}</strong><div class="task-meta">${esc(label(s.status))} · ${esc(label(s.readiness))} · ${esc(s.workMinutes)} min attività ${s.stale ? "· Da verificare" : s.pauseSuggested ? "· Pausa consigliata" : ""}</div></div></div>`).join("") || '<div class="enterprise-empty">Nessun turno registrato.</div>';
      $("peopleSignals").innerHTML = signals.map(s => `<article class="task-row"><div><strong>${esc(label(s.type))} · ${esc(label(s.severity))}</strong><p class="enterprise-summary">${esc(s.description)}</p><div class="task-meta">${esc(s.name)} · ${esc(label(s.status))} · ${esc(new Date(s.createdAt).toLocaleString("it-IT"))}</div>${s.resolution ? `<p>${esc(s.resolution)}</p>` : ""}</div>${manager() && s.status !== "RESOLVED" ? `<button class="btn" data-review-signal="${esc(s.id)}">${s.status === "OPEN" ? "Prendi in carico" : "Risolvi"}</button>` : ""}</article>`).join("") || '<div class="enterprise-empty">Nessuna segnalazione.</div>';
      $("peopleStatus").textContent = "Dati aggiornati.";
    } catch (e) { if (epoch === (state.sessionEpoch || 0)) $("peopleStatus").textContent = e.message; }
  }
  $("shiftActions").addEventListener("click", event => {
    const button = event.target.closest("[data-shift-action]"); if (!button) return;
    action(button, async () => { await apiFetch("/api/v1/workforce/me/shift", { method: "PUT", body: JSON.stringify({ action: button.dataset.shiftAction, readiness: $("shiftReadiness").value, expectedVersion: shift.version, operationId: crypto.randomUUID() }) }); await loadPeople(); });
  });
  $("peopleSignalForm").addEventListener("submit", event => {
    event.preventDefault(); action(event.submitter, async () => { await apiFetch("/api/v1/workforce/signals", { method: "POST", body: JSON.stringify({ type: $("signalType").value, severity: $("signalSeverity").value, description: $("signalDescription").value.trim(), assetId: $("signalAsset").value.trim() || null, operationId: crypto.randomUUID() }) }); event.target.reset(); await loadPeople(); toast("Segnalazione registrata", "I responsabili possono prenderla in carico."); });
  });
  $("peopleSignals").addEventListener("click", event => {
    const button = event.target.closest("[data-review-signal]"); if (!button) return;
    const signal = signals.find(s => s.id === button.dataset.reviewSignal); let resolution = "";
    if (signal.status === "ACKNOWLEDGED") { resolution = prompt("Descrivi la risoluzione della segnalazione:"); if (!resolution?.trim()) return; }
    action(button, async () => { await apiFetch(`/api/v1/workforce/signals/${encodeURIComponent(signal.id)}`, { method: "PATCH", body: JSON.stringify({ status: signal.status === "OPEN" ? "ACKNOWLEDGED" : "RESOLVED", resolution, expectedVersion: signal.version }) }); await loadPeople(); });
  });
  $("peopleRefresh").addEventListener("click", e => action(e.currentTarget, loadPeople));
  return { reset() {
    history = []; risks = []; signals = []; shift = null; busy = false; renderChat(); $("aiSend").disabled = true;
    for (const id of ["aiRisks", "peopleSignals", "peopleTeam", "shiftActions", "shiftSummary", "shiftReminder", "peopleStatus", "aiProvider", "aiInsightStatus", "aiChatStatus"]) $(id).replaceChildren();
    for (const id of ["aiAssets", "aiRiskCount", "aiTasks", "aiOverdue"]) $(id).textContent = "—";
    $("aiChatForm").reset(); $("peopleSignalForm").reset(); $("aiRiskSearch").value = ""; $("shiftReadiness").value = "READY"; $("peopleTeamPanel").hidden = true;
  } };
};
