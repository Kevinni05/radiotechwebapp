(() => {
  // frontend/control-room/features/dossier.js
  var sections = { overview: "Riepilogo", access: "Accesso al sito", configuration: "Configurazioni", measurements: "Misurazioni", dependencies: "Dipendenze", photos: "Fotografie", documents: "Documenti", components: "Componenti", issues: "Problemi aperti", proposals: "Correzioni", sessions: "Ponte radio", shares: "Condivisione" };
  var accessLabels = { address: "Indirizzo e punto di accesso", contact: "Referente e contatti", hours: "Orari di accesso", parking: "Parcheggio", keyInstructions: "Ritiro chiavi", route: "Percorso e indicazioni", restrictions: "Restrizioni e precauzioni" };
  var labels = { metric: "Parametro", unit: "Unit\xE0", value: "Valore", low: "Soglia minima", high: "Soglia massima", measuredAt: "Data e ora della misura", notes: "Note", label: "Nome revisione", reason: "Motivazione", values: "Parametri (uno per riga: nome = valore)", antennaId: "Impianto da cui dipende", type: "Tipo di collegamento", title: "Titolo", name: "Componente", model: "Modello", serial: "Matricola", installedAt: "Installazione", warrantyUntil: "Fine garanzia", target: "Sezione da correggere", field: "Campo", status: "Stato", peerAntennaId: "Impianto all\u2019altro estremo", operatorAId: "Tecnico estremo A", operatorBId: "Tecnico estremo B" };
  var stamp = (v) => v ? new Date(v).toLocaleString("it-IT") : "\u2014";
  var op = () => crypto.randomUUID?.() || Array.from(crypto.getRandomValues(new Uint8Array(24)), (v) => v.toString(16).padStart(2, "0")).join("");
  var pairs = (text) => Object.fromEntries(text.split("\n").filter((l) => l.trim()).map((l) => {
    const p = l.indexOf("=");
    if (p < 1) throw Error("Usa una riga per parametro: nome = valore.");
    return [l.slice(0, p).trim(), l.slice(p + 1).trim()];
  }));
  var pairText = (v) => Object.entries(v || {}).map(([k, x]) => `${k} = ${x}`).join("\n");
  function installDossier({ apiFetch, state, escapeHtml: esc, toast }) {
    let data = null, assetId = null, tab = "overview", epoch = 0, dialog = null, poll = null, activeSession = null, busy = false;
    const urls = /* @__PURE__ */ new Set();
    const base = () => `/api/v1/antennas/${encodeURIComponent(assetId)}/dossier`;
    const button = (label, action, extra = "") => `<button type="button" class="btn" data-dossier-action="${action}" ${extra}>${label}</button>`;
    const empty = (message) => `<div class="empty">${esc(message)}</div>`;
    const panel = (title, body, actions = "") => `<section class="panel"><div class="panel-head"><h3>${esc(title)}</h3><div class="actions">${actions}</div></div><div class="panel-body">${body}</div></section>`;
    const facts = (obj) => `<dl class="ds-facts">${Object.entries(obj || {}).map(([k, v]) => `<div><dt>${esc(accessLabels[k] || k)}</dt><dd>${esc(typeof v === "object" ? pairText(v) : v || "\u2014")}</dd></div>`).join("")}</dl>`;
    const cards = (rows, fn) => rows.length ? `<div class="ds-stack">${rows.map(fn).join("")}</div>` : empty("Nessun elemento registrato.");
    const error = (e) => toast("Fascicolo", e.message || "Operazione non riuscita.", "error");
    async function run(fn) {
      if (busy) return;
      busy = true;
      dialog?.setAttribute("aria-busy", "true");
      try {
        await fn();
      } catch (e) {
        error(e);
      } finally {
        busy = false;
        dialog?.removeAttribute("aria-busy");
      }
    }
    function reset() {
      epoch++;
      clearInterval(poll);
      poll = null;
      activeSession = null;
      data = null;
      assetId = null;
      const old = dialog;
      dialog = null;
      old?.close();
      document.querySelectorAll(".ds-editor").forEach((d) => d.close());
      urls.forEach((u) => URL.revokeObjectURL(u));
      urls.clear();
    }
    window.addEventListener("radiotech:identity-changed", reset);
    document.addEventListener("click", (event) => {
      const b = event.target.closest("[data-dossier]");
      if (b) open(b.dataset.dossier);
    });
    async function open(id) {
      reset();
      assetId = id;
      const e = epoch;
      dialog = document.createElement("dialog");
      dialog.className = "ds-dialog";
      dialog.setAttribute("aria-label", "Fascicolo impianto");
      dialog.innerHTML = `<header class="ds-header"><div><span class="ds-eyebrow">RADIOTECH \xB7 FASCICOLO IMPIANTO</span><h2>Caricamento\u2026</h2><p data-summary></p></div><div class="actions">${button("Aggiorna", "refresh")}${button("Chiudi", "close")}</div></header><nav class="ds-tabs" aria-label="Sezioni fascicolo"></nav><div class="ds-content" role="region" aria-live="polite"></div>`;
      const ownDialog = dialog;
      dialog.addEventListener("close", () => {
        ownDialog.remove();
        if (dialog === ownDialog) dialog = null;
        if (epoch === e) {
          clearInterval(poll);
          data = null;
          epoch++;
        }
      });
      dialog.addEventListener("click", handle);
      document.body.append(dialog);
      dialog.showModal();
      try {
        await reload(e);
      } catch (x) {
        if (epoch === e) dialog.querySelector(".ds-content").innerHTML = empty(x.message);
      }
    }
    async function reload(e = epoch) {
      const d = await apiFetch(base());
      if (e !== epoch) return;
      data = d;
      dialog.querySelector("h2").textContent = d.name;
      dialog.querySelector("[data-summary]").textContent = `${d.antennaId} \xB7 Revisione ${d.version} \xB7 Aggiornato ${stamp(d.updatedAt || d.fetchedAt)}`;
      render();
    }
    function render() {
      if (!data || !dialog?.open) return;
      clearInterval(poll);
      const visible = Object.entries(sections).filter(([k]) => k !== "shares" || data.canManage);
      dialog.querySelector(".ds-tabs").innerHTML = visible.map(([k, v]) => `<button type="button" class="${tab === k ? "active" : ""}" aria-current="${tab === k ? "page" : "false"}" data-tab="${k}">${v}</button>`).join("");
      const content = dialog.querySelector(".ds-content");
      if (tab === "overview") {
        const latest = data.configuration.at(-1);
        content.innerHTML = `<div class="ds-kpis">${[["Interventi e report", data.history.length], ["Misurazioni", data.measurements.length], ["Problemi aperti", data.issues.filter((x) => x.status === "OPEN").length], ["Revisioni", data.configuration.length]].map(([k, v]) => `<div class="panel"><span>${k}</span><strong>${v}</strong></div>`).join("")}</div><div class="ds-columns">${panel("Identit\xE0 e dati tecnici", facts(data.identity))}${panel("Configurazione attuale", latest ? `<p>${esc(latest.label)} \xB7 ${stamp(latest.createdAt)}</p>${facts(latest.values)}` : empty("Nessuna configurazione registrata."))}</div>${panel("Cronologia interventi e report", `${data.historyPartial ? '<p class="ds-notice">Sono mostrati fino a 200 elementi per sorgente. Consulta gli archivi per lo storico completo.</p>' : ""}${cards(data.history, (r) => `<article class="ds-record"><span class="badge">${r.type === "TASK" ? "Incarico" : "Report"}</span><h4>${esc(r.title || r.id)}</h4><p>${esc(r.summary || r.description || "")}</p><small>${esc(r.status || "")} \xB7 ${stamp(r.submittedAt || r.createdAt)}</small></article>`)}`)}`;
      }
      if (tab === "access") content.innerHTML = panel("Preparazione dell\u2019accesso", facts(data.access), data.canManage ? button("Modifica indicazioni", "edit-access") : button("Proponi una correzione", "add-proposals"));
      if (tab === "configuration") {
        const list = data.configuration;
        content.innerHTML = panel("Revisioni della configurazione", `${list.length ? `<div class="ds-compare"><label>Prima<select data-compare="before">${list.map((r, i) => `<option value="${i}" ${i === Math.max(0, list.length - 2) ? "selected" : ""}>${esc(r.label)} \xB7 ${stamp(r.createdAt)}</option>`).join("")}</select></label><label>Dopo<select data-compare="after">${list.map((r, i) => `<option value="${i}" ${i === list.length - 1 ? "selected" : ""}>${esc(r.label)} \xB7 ${stamp(r.createdAt)}</option>`).join("")}</select></label></div><div data-diff></div>` : empty("Registra la prima configurazione verificata.")}${cards([...list].reverse(), (r) => `<article class="ds-record"><h4>${esc(r.label)}</h4><p>${esc(r.reason)}</p><small>${stamp(r.createdAt)}</small><details><summary>Parametri registrati</summary>${facts(r.values)}</details></article>`)}`, data.canManage ? button("Nuova revisione", "add-configuration") : button("Proponi una correzione", "add-proposals"));
        content.querySelectorAll("[data-compare]").forEach((s) => s.onchange = compare);
        compare();
      }
      if (tab === "measurements") {
        content.innerHTML = panel("Misurazioni e soglie", `<p class="subtle">Valori inseriti manualmente. Le soglie vengono conservate con ogni misura.</p><label>Parametro<select data-metric>${[...new Set(data.measurements.map((x) => x.metric))].map((m) => `<option>${esc(m)}</option>`).join("")}</select></label><div data-chart></div>`, button("Registra misura", "add-measurements"));
        content.querySelector("[data-metric]").onchange = chart;
        chart();
      }
      if (tab === "dependencies") {
        const rows = data.dependencies.filter((r) => !r.archived);
        content.innerHTML = panel("Dipendenze dirette registrate", `<p class="subtle">Le frecce indicano da quali impianti dipende questo sito. Lo schema descrive i collegamenti registrati, senza simulare la disponibilit\xE0 della rete.</p>${rows.length ? `<svg class="ds-graph" viewBox="0 0 720 ${Math.max(150, rows.length * 88)}" role="img" aria-label="Mappa delle dipendenze"><defs><marker id="dsArrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="currentColor"/></marker></defs><rect x="10" y="${Math.max(45, rows.length * 44 - 24)}" width="240" height="48" rx="10"/><text x="24" y="${Math.max(45, rows.length * 44 - 24) + 29}">${esc(data.name.slice(0, 26))}</text>${rows.map((r, i) => `<path d="M250 ${Math.max(45, rows.length * 44 - 24) + 24} L440 ${i * 88 + 48}" marker-end="url(#dsArrow)"/><rect x="450" y="${i * 88 + 24}" width="255" height="48" rx="10"/><text x="464" y="${i * 88 + 53}">${esc((r.name || r.antennaId).slice(0, 27))}</text>`).join("")}</svg>` : empty("Nessuna dipendenza registrata.")}${cards(rows, (r) => `<article class="ds-record"><h4>${esc(r.name || r.antennaId)}</h4><p>${esc(r.type)} \xB7 ${esc(r.label)} \xB7 ${esc(r.status || "")}</p><p>${esc(r.notes)}</p>${button("Apri fascicolo collegato", "peer", `data-id="${esc(r.antennaId)}"`)}${data.canManage ? button("Rimuovi collegamento", "remove-dependency", `data-id="${esc(r.id)}"`) : ""}</article>`)}`, data.canManage ? button("Aggiungi collegamento", "add-dependencies") : "");
      }
      if (["photos", "documents", "components", "issues"].includes(tab)) content.innerHTML = panel(sections[tab], cards([...data[tab]].reverse(), (r) => `<article class="ds-record"><h4>${esc(r.title || r.name)}</h4><p>${esc(r.notes || "")}</p>${tab === "photos" ? `<div class="ds-photo" data-photo="${esc(r.id)}"></div>` : ""}${tab === "components" ? facts({ Modello: r.model, Matricola: r.serial, Installazione: r.installedAt, Garanzia: r.warrantyUntil }) : ""}${r.status ? `<span class="badge">${r.status === "OPEN" ? "Aperto" : "Risolto"}</span>` : ""}${tab === "issues" && data.canManage && r.status === "OPEN" ? button("Segna risolto", "resolve-issue", `data-id="${esc(r.id)}"`) : ""}${r.resolutionReason ? `<p>Esito: ${esc(r.resolutionReason)}</p>` : ""}${r.fileId ? button(tab === "photos" ? "Visualizza originale e annotazioni" : "Scarica documento", "file", `data-id="${esc(r.id)}"`) : ""}<small>${stamp(r.createdAt)}</small></article>`), tab === "components" && !data.canManage ? "" : button("Aggiungi", "add-" + tab));
      if (tab === "proposals") content.innerHTML = panel("Correzioni proposte e decisioni", cards([...data.proposals].reverse(), (p) => `<article class="ds-record"><h4>${esc(p.target === "access" ? "Accesso al sito" : "Configurazione")} \xB7 ${esc(accessLabels[p.field] || p.field)}</h4><div class="ds-columns"><div><small>Prima</small><p>${esc(p.before || "\u2014")}</p></div><div><small>Proposta</small><p>${esc(p.value)}</p></div></div><p>${esc(p.reason)}</p><span class="badge">${{ PENDING: "Da verificare", APPROVED: "Approvata", REJECTED: "Respinta" }[p.status]}</span>${p.reviewReason ? `<p>Esito: ${esc(p.reviewReason)}</p>` : ""}${data.canManage && p.status === "PENDING" ? `<div class="actions">${button("Approva", "approve", `data-id="${esc(p.id)}"`)}${button("Respingi", "reject", `data-id="${esc(p.id)}"`)}</div>` : ""}</article>`), button("Proponi correzione", "add-proposals"));
      if (tab === "shares") loadShares().catch(error);
      if (tab === "sessions") {
        loadSessions().catch(error);
        poll = setInterval(() => {
          if (!busy && !document.querySelector(".ds-editor") && dialog?.open) run(loadSessions);
        }, 1e4);
      }
    }
    function compare() {
      const c = dialog.querySelector("[data-diff]");
      if (!c) return;
      const a = data.configuration[Number(dialog.querySelector('[data-compare="before"]').value)]?.values || {}, b = data.configuration[Number(dialog.querySelector('[data-compare="after"]').value)]?.values || {};
      c.innerHTML = `<div class="table-wrap"><table><thead><tr><th>Parametro</th><th>Prima</th><th>Dopo</th></tr></thead><tbody>${[.../* @__PURE__ */ new Set([...Object.keys(a), ...Object.keys(b)])].sort().map((k) => `<tr class="${a[k] !== b[k] ? "ds-changed" : ""}"><td>${esc(k)}</td><td>${esc(a[k] ?? "\u2014")}</td><td>${esc(b[k] ?? "\u2014")}</td></tr>`).join("")}</tbody></table></div>`;
    }
    function chart() {
      const m = dialog.querySelector("[data-metric]").value, rows = data.measurements.filter((r) => r.metric === m).sort((a, b) => a.measuredAt.localeCompare(b.measuredAt));
      const c = dialog.querySelector("[data-chart]");
      if (!rows.length) {
        c.innerHTML = empty("Nessuna misura registrata.");
        return;
      }
      const units = [...new Set(rows.map((r) => r.unit))];
      const grouped = units.length > 1 ? '<p class="ds-notice">Sono presenti unit\xE0 diverse: il grafico \xE8 separato per unit\xE0 per evitare confronti impropri.</p>' : "";
      c.innerHTML = grouped + units.map((unit) => {
        const items = rows.filter((r) => r.unit === unit);
        const values = items.map((r) => r.value), lo = Math.min(...values), span = Math.max(...values) - lo || 1;
        const times = items.map((r) => Date.parse(r.measuredAt)), from = Math.min(...times), duration = Math.max(...times) - from || 1;
        const pts = items.map((r, i) => `${20 + (times[i] - from) / duration * 660},${150 - (r.value - lo) / span * 120}`);
        return `<figure><figcaption>${esc(m)} \xB7 ${esc(unit)} \xB7 ${items.length} misure</figcaption><svg class="ds-chart" viewBox="0 0 700 175" role="img" aria-label="Andamento ${esc(m)} in ${esc(unit)}"><polyline points="${pts.join(" ")}"/>${items.map((r, i) => `<circle cx="${pts[i].split(",")[0]}" cy="${pts[i].split(",")[1]}" r="4"><title>${stamp(r.measuredAt)}: ${r.value} ${esc(r.unit)}</title></circle>`).join("")}</svg></figure>`;
      }).join("") + `<div class="table-wrap"><table><thead><tr><th>Data</th><th>Valore</th><th>Soglie</th><th>Esito</th><th>Note</th></tr></thead><tbody>${[...rows].reverse().map((r) => `<tr><td>${stamp(r.measuredAt)}</td><td>${r.value} ${esc(r.unit)}</td><td>${r.low ?? "\u2014"} / ${r.high ?? "\u2014"}</td><td>${r.outsideThreshold ? "Fuori soglia" : "Nei limiti definiti"}</td><td>${esc(r.notes)}</td></tr>`).join("")}</tbody></table></div>`;
    }
    function editor(title, fields, onSave, description = "") {
      const e = epoch, d = document.createElement("dialog");
      d.className = "ds-editor";
      d.innerHTML = `<header><h3>${esc(title)}</h3><p>${esc(description)}</p></header><form class="ds-form">${fields}<p role="alert" data-error></p><footer><button class="btn primary" type="submit">Salva</button><button class="btn" type="button" data-cancel>Annulla</button></footer></form>`;
      d.querySelector("[data-cancel]").onclick = () => d.close();
      d.addEventListener("close", () => d.remove());
      d.querySelector("form").onsubmit = async (event) => {
        event.preventDefault();
        const submit = d.querySelector("[type=submit]");
        submit.disabled = true;
        try {
          if (epoch !== e) throw Error("Sessione cambiata.");
          const close = await onSave(new FormData(event.target), d);
          if (epoch === e && close !== false) d.close();
        } catch (x) {
          d.querySelector("[data-error]").textContent = x.message;
        } finally {
          submit.disabled = false;
        }
      };
      document.body.append(d);
      d.showModal();
      return d;
    }
    function field(key, value = "", type = "text", required = true, options = null) {
      const label = labels[key] || accessLabels[key] || key;
      return `<label>${esc(label)}${options ? `<select name="${key}" ${required ? "required" : ""}>${options.map(([v, l]) => `<option value="${esc(v)}" ${v === value ? "selected" : ""}>${esc(l)}</option>`).join("")}</select>` : type === "textarea" ? `<textarea name="${key}" maxlength="3000" ${required ? "required" : ""}>${esc(value)}</textarea>` : `<input name="${key}" type="${type}" value="${esc(value)}" ${type === "number" ? 'step="any"' : ""} ${required ? "required" : ""} maxlength="2000">`}</label>`;
    }
    async function save(section, payload) {
      const e = epoch;
      await apiFetch(base() + "/changes", { method: "POST", body: JSON.stringify({ expectedVersion: data.version, operationId: op(), section, data: payload }) });
      if (epoch === e) await reload(e);
    }
    async function add(section) {
      if (section === "access") {
        editor("Accesso al sito", Object.keys(accessLabels).map((k) => field(k, data.access[k] || "", "textarea", false)).join(""), (f) => save("access", Object.fromEntries(f)));
        return;
      }
      if (section === "configuration") {
        const current = data.configuration.at(-1)?.values || {};
        editor("Nuova revisione", field("label") + field("reason", "", "textarea") + field("values", pairText(current), "textarea"), (f) => save(section, { label: f.get("label"), reason: f.get("reason"), values: pairs(f.get("values")) }), "Ogni salvataggio conserva la revisione precedente.");
        return;
      }
      if (section === "measurements") {
        editor("Registra misurazione", field("metric") + field("unit", "", "text", false) + field("value", "", "number") + field("low", "", "number", false) + field("high", "", "number", false) + field("measuredAt", "", "datetime-local") + field("notes", "", "textarea", false), (f) => save(section, { ...Object.fromEntries(f), value: Number(f.get("value")), low: f.get("low") === "" ? null : Number(f.get("low")), high: f.get("high") === "" ? null : Number(f.get("high")), measuredAt: new Date(f.get("measuredAt")).toISOString() }));
        return;
      }
      if (section === "dependencies") {
        const list = state.antennas.filter((a) => a.id !== assetId);
        editor("Collega impianto", field("antennaId", "", "text", true, list.map((a) => [a.id, a.name || a.id])) + field("label") + field("type", "RADIO", "text", true, [["RADIO", "Ponte radio"], ["CABLE", "Cavo"], ["POWER", "Alimentazione"], ["FIBER", "Fibra"]]) + field("notes", "", "textarea", false), (f) => save(section, Object.fromEntries(f)));
        return;
      }
      if (section === "components") {
        editor("Registra componente", field("name") + ["model", "serial"].map((k) => field(k, "", "text", false)).join("") + ["installedAt", "warrantyUntil"].map((k) => field(k, "", "date", false)).join("") + field("notes", "", "textarea", false), (f) => save(section, Object.fromEntries(f)));
        return;
      }
      if (section === "issues") {
        editor("Registra problema", field("title") + field("notes", "", "textarea") + field("status", "OPEN", "text", true, [["OPEN", "Aperto"], ["RESOLVED", "Risolto"]]), (f) => save(section, Object.fromEntries(f)));
        return;
      }
      if (section === "proposals") {
        const options = (target) => target === "access" ? Object.entries(accessLabels) : Object.keys(data.configuration.at(-1)?.values || {}).map((k) => [k, k]);
        const d = editor("Proponi una correzione", field("target", "access", "text", true, [["access", "Accesso al sito"], ["configuration", "Configurazione"]]) + field("field", "", "text", true, options("access")) + field("value", "", "textarea") + field("reason", "", "textarea"), (f) => save(section, Object.fromEntries(f)), "La modifica sar\xE0 applicata solo dopo la verifica del responsabile.");
        d.querySelector("[name=target]").onchange = (e) => {
          d.querySelector("[name=field]").innerHTML = options(e.target.value).map(([v, l]) => `<option value="${esc(v)}">${esc(l)}</option>`).join("");
        };
        return;
      }
      if (section === "documents" || section === "photos") {
        const marks = [];
        const d = editor(section === "photos" ? "Fotografia e annotazioni" : "Documento tecnico", field("title") + `<label>File originale<input type="file" name="file" required accept="${section === "photos" ? "image/png,image/jpeg,image/webp" : ".pdf,.png,.jpg,.jpeg,.webp"}"></label>` + field("notes", "", "textarea", false) + (section === "photos" ? `<label>Annotazione<select data-mark-type><option value="ARROW">Freccia</option><option value="RECT">Riquadro</option><option value="TEXT">Nota</option></select></label><label>Testo annotazione<input data-mark-text maxlength="200"></label><p>Trascina sull\u2019immagine per aggiungere un\u2019annotazione.</p><div class="ds-annotator"><img alt="Anteprima originale"><svg viewBox="0 0 1000 1000" preserveAspectRatio="none"></svg></div><button class="btn" type="button" data-undo>Rimuovi ultima annotazione</button>` : ""), async (f) => {
          const file = f.get("file");
          if (!file.size || file.size > 1e7) throw Error("Scegli un file fino a 10 MB.");
          const e = epoch, root = base();
          const uploaded = await apiFetch(`/api/v1/files?operationId=${op()}&name=${encodeURIComponent(file.name)}`, { method: "POST", headers: { "Content-Type": "application/octet-stream" }, body: file });
          if (epoch !== e) throw Error("Sessione cambiata.");
          await apiFetch(root + "/changes", { method: "POST", body: JSON.stringify({ expectedVersion: data.version, operationId: op(), section, data: { title: f.get("title"), notes: f.get("notes"), fileId: uploaded.reference.replace("radiotech-file:", ""), annotations: marks } }) });
          if (epoch === e) await reload(e);
        });
        if (section === "photos") {
          const area = d.querySelector(".ds-annotator"), img = area.querySelector("img"), svg = area.querySelector("svg");
          d.querySelector("[type=file]").onchange = (event) => {
            const file = event.target.files[0];
            if (!file) return;
            const url = URL.createObjectURL(file);
            urls.add(url);
            img.src = url;
            marks.length = 0;
            svg.innerHTML = "";
          };
          let start = null;
          const point = (e) => {
            const b = svg.getBoundingClientRect();
            return { x: Math.max(0, Math.min(1, (e.clientX - b.left) / b.width)), y: Math.max(0, Math.min(1, (e.clientY - b.top) / b.height)) };
          };
          svg.onpointerdown = (e) => {
            if (!img.src) return;
            start = point(e);
            svg.setPointerCapture(e.pointerId);
          };
          svg.onpointerup = (e) => {
            if (!start || marks.length >= 40) return;
            const end = point(e);
            marks.push({ ...start, x2: end.x, y2: end.y, type: d.querySelector("[data-mark-type]").value, text: d.querySelector("[data-mark-text]").value });
            start = null;
            svg.innerHTML = annotationSvg(marks);
          };
          d.querySelector("[data-undo]").onclick = () => {
            marks.pop();
            svg.innerHTML = annotationSvg(marks);
          };
        }
        return;
      }
    }
    function annotationSvg(marks) {
      return `<defs><marker id="dsPhotoArrow" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="#ffb454"/></marker></defs>` + marks.map((m) => {
        const x = m.x * 1e3, y = m.y * 1e3, x2 = m.x2 * 1e3, y2 = m.y2 * 1e3;
        return (m.type === "RECT" ? `<rect x="${Math.min(x, x2)}" y="${Math.min(y, y2)}" width="${Math.abs(x2 - x)}" height="${Math.abs(y2 - y)}"/>` : m.type === "ARROW" ? `<line x1="${x}" y1="${y}" x2="${x2}" y2="${y2}" marker-end="url(#dsPhotoArrow)"/>` : "") + `<text x="${x}" y="${Math.max(20, y - 10)}">${esc(m.text)}</text>`;
      }).join("");
    }
    async function showFile(id) {
      const row = data[tab].find((r) => r.id === id);
      if (!row) return;
      const e = epoch, response = await apiFetch(`${base()}/files/${row.fileId}`, { maxResponseBytes: 15e6 });
      if (e !== epoch) return;
      const bytes = Uint8Array.from(atob(response.base64), (c) => c.charCodeAt(0)), type = /\.png$/i.test(response.name) ? "image/png" : /\.webp$/i.test(response.name) ? "image/webp" : /\.jpe?g$/i.test(response.name) ? "image/jpeg" : "application/pdf";
      const url = URL.createObjectURL(new Blob([bytes], { type }));
      urls.add(url);
      if (tab === "photos") {
        const holder = dialog.querySelector(`[data-photo="${row.id}"]`);
        holder.innerHTML = `<div class="ds-annotator"><img src="${url}" alt="${esc(row.title)}"><svg viewBox="0 0 1000 1000" preserveAspectRatio="none">${annotationSvg(row.annotations || [])}</svg></div><a class="btn" href="${url}" download="${esc(response.name)}">Scarica originale</a>`;
      } else {
        const a = document.createElement("a");
        a.href = url;
        a.download = response.name;
        a.click();
      }
    }
    async function loadShares() {
      const e = epoch, rows = await apiFetch(base() + "/shares");
      if (e !== epoch || tab !== "shares") return;
      dialog.querySelector(".ds-content").innerHTML = panel("Link temporanei al fascicolo", `<p class="subtle">Ogni link contiene una copia delle sole sezioni selezionate. Anche le indicazioni di accesso diventano visibili se le includi. Il link \xE8 mostrato una sola volta.</p>${cards(rows, (r) => `<article class="ds-record"><h4>${esc(r.sections.map((k) => sections[k] || "Identit\xE0").join(" \xB7 "))}</h4><p>Scade ${stamp(r.expiresAt)} \xB7 ${r.revoked ? "Revocato" : new Date(r.expiresAt) < /* @__PURE__ */ new Date() ? "Scaduto" : "Attivo"} \xB7 Accessi: ${r.accessCount}</p><div class="actions">${!r.revoked ? button("Revoca", "revoke", `data-id="${r.id}"`) : ""}${button("Registro accessi", "accesses", `data-id="${r.id}"`)}</div></article>`)}`, button("Crea link", "share"));
    }
    async function createShare() {
      editor("Crea link temporaneo", `<fieldset><legend>Informazioni da condividere</legend>${["identity", "configuration", "measurements", "dependencies", "documents", "photos", "components", "issues", "history", "access"].map((k) => `<label class="ds-check"><input type="checkbox" name="sections" value="${k}">${esc(k === "identity" ? "Identit\xE0 impianto" : sections[k] || "Cronologia")}</label>`).join("")}</fieldset><label>Durata in ore<input type="number" name="hours" value="24" min="1" max="720" required></label>`, async (f, d) => {
        const e = epoch, r = await apiFetch(base() + "/shares", { method: "POST", body: JSON.stringify({ sections: f.getAll("sections"), hours: Number(f.get("hours")) }) });
        if (e !== epoch) return;
        d.querySelector("form").innerHTML = `<p>Conserva il collegamento: non verr\xE0 mostrato nuovamente.</p><label>Link<input readonly value="${esc(r.url)}"></label><button type="button" class="btn" data-copy>Copia link</button><button type="button" class="btn" data-done>Chiudi</button>`;
        d.querySelector("[data-copy]").onclick = () => navigator.clipboard.writeText(r.url).catch(error);
        d.querySelector("[data-done]").onclick = () => d.close();
        await loadShares();
        return false;
      });
    }
    async function loadSessions() {
      const e = epoch, list = await apiFetch(base() + "/sessions");
      if (e !== epoch || tab !== "sessions") return;
      if (activeSession) activeSession = list.find((s2) => s2.id === activeSession.id) || null;
      const s = activeSession;
      dialog.querySelector(".ds-content").innerHTML = panel("Prove coordinate ai due estremi", `<p class="subtle">Aggiornamento ogni 10 secondi. Ogni tecnico registra il proprio estremo; le misure confermate restano immutabili.</p>${cards(list, (r) => `<article class="ds-record"><h4>${esc(r.title)}</h4><p>${esc(r.nameA)} \u2194 ${esc(r.nameB)} \xB7 ${r.state === "OPEN" ? "Aperta" : "Conclusa"}</p>${button("Apri sessione", "session", `data-id="${r.id}"`)}</article>`)}`, data.canManage ? button("Crea sessione", "create-session") : "") + (s ? panel(s.title, `<div class="ds-columns">${facts({ Estremo: s.nameA, Tecnico: s.operatorA })}${facts({ Estremo: s.nameB, Tecnico: s.operatorB })}</div>${cards(s.trials, (r) => `<article class="ds-record"><h4>${esc(r.label)}</h4>${facts(r.settings)}<div class="ds-columns">${["A", "B"].map((side) => `<div><h4>Estremo ${side}</h4>${r.results[side] ? facts({ Parametro: r.results[side].metric, Valore: `${r.results[side].value} ${r.results[side].unit}`, Note: r.results[side].notes, Ora: stamp(r.results[side].recordedAt) }) : `<p>In attesa della misura.</p>${s["uid" + side] === data.uid && s.state === "OPEN" ? button("Registra risultato", "result", `data-trial="${r.id}" data-side="${side}"`) : ""}`}</div>`).join("")}</div></article>`)}${s.conclusion ? `<p>Esito: ${esc(s.conclusion)}</p>` : ""}`, s.state === "OPEN" ? button("Prepara prova", "create-trial") + button("Concludi sessione", "close-session") : "") : "");
    }
    async function createSession() {
      const e = epoch, people = await apiFetch("/api/v1/workforce/roster/members");
      if (e !== epoch) return;
      editor("Sessione ai due estremi", field("title") + field("peerAntennaId", "", "text", true, state.antennas.filter((a) => a.id !== assetId).map((a) => [a.id, a.name || a.id])) + field("operatorAId", "", "text", true, people.map((p) => [p.operatorId, `A \xB7 ${p.name}`])) + field("operatorBId", "", "text", true, people.map((p) => [p.operatorId, `B \xB7 ${p.name}`])), async (f) => {
        activeSession = await apiFetch(base() + "/sessions", { method: "POST", body: JSON.stringify({ ...Object.fromEntries(f), operationId: op() }) });
        await loadSessions();
      });
    }
    async function trial(action, extra = {}) {
      const e = epoch, s = activeSession;
      const r = await apiFetch(`/api/v1/radio-pair-sessions/${s.id}/trials`, { method: "POST", body: JSON.stringify({ expectedVersion: s.version, operationId: op(), action, ...extra }) });
      if (e === epoch) {
        activeSession = r;
        await loadSessions();
      }
    }
    function handle(event) {
      const t = event.target.closest("[data-tab]");
      if (t) {
        tab = t.dataset.tab;
        activeSession = null;
        render();
        return;
      }
      const b = event.target.closest("[data-dossier-action]");
      if (!b) return;
      const a = b.dataset.dossierAction;
      if (a === "close") {
        reset();
        return;
      }
      if (a.startsWith("add-")) {
        run(() => add(a.slice(4)));
        return;
      }
      run(async () => {
        switch (a) {
          case "refresh":
            await reload();
            break;
          case "resolve-issue":
          case "remove-dependency":
            editor(a === "resolve-issue" ? "Concludi problema" : "Rimuovi collegamento", field("reason", "", "textarea"), (f) => save(a === "resolve-issue" ? "issueResolution" : "dependencyRemoval", { entryId: b.dataset.id, reason: f.get("reason") }));
            break;
          case "edit-access":
            await add("access");
            break;
          case "peer":
            await open(b.dataset.id);
            break;
          case "file":
            await showFile(b.dataset.id);
            break;
          case "share":
            await createShare();
            break;
          case "revoke":
            await apiFetch(`${base()}/shares/${b.dataset.id}`, { method: "DELETE" });
            await loadShares();
            break;
          case "accesses": {
            const rows = await apiFetch(`${base()}/shares/${b.dataset.id}/accesses`);
            editor("Ultimi 100 accessi", cards(rows, (r) => `<p>${stamp(r.at)} \xB7 ${r.type === "FILE" ? "Allegato" : "Visualizzazione"}</p>`), async () => {
            });
            break;
          }
          case "approve":
          case "reject":
            editor("Decisione sulla correzione", field("reason", "", "textarea"), async (f) => {
              await apiFetch(base() + "/reviews", { method: "POST", body: JSON.stringify({ expectedVersion: data.version, operationId: op(), proposalId: b.dataset.id, approve: a === "approve", reason: f.get("reason") }) });
              await reload();
            });
            break;
          case "create-session":
            await createSession();
            break;
          case "session":
            activeSession = await apiFetch(`/api/v1/radio-pair-sessions/${b.dataset.id}`);
            await loadSessions();
            break;
          case "create-trial":
            editor("Prepara prova coordinata", field("label") + field("values", "", "textarea"), (f) => trial("CREATE_TRIAL", { label: f.get("label"), settings: pairs(f.get("values")) }));
            break;
          case "result":
            editor("Misura estremo " + b.dataset.side, field("metric") + field("unit", "", "text", false) + field("value", "", "number") + field("notes", "", "textarea", false), (f) => trial("RESULT", { trialId: b.dataset.trial, side: b.dataset.side, ...Object.fromEntries(f), value: Number(f.get("value")) }));
            break;
          case "close-session":
            editor("Concludi prove", field("notes", "", "textarea"), (f) => trial("CLOSE", { notes: f.get("notes") }));
            break;
        }
      });
    }
    return { open };
  }

  // frontend/control-room/features/workforce.js
  function installWorkforce({ apiFetch, state, escapeHtml: esc, toast, switchView }) {
    const $ = (id) => document.getElementById(id), base = "/api/v1/workforce";
    const manager = () => ["SUPER_ADMIN", "ADMIN", "CAPO", "CHIEF_EXECUTIVE", "NETWORK_MANAGER", "ENGINEER"].includes(state.authRole || state.user?.role);
    const month = () => new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Rome", year: "numeric", month: "2-digit" }).format(/* @__PURE__ */ new Date());
    const hours = (n) => `${Math.floor(Number(n) / 60)}:${String(Number(n) % 60).padStart(2, "0")}`;
    const labels2 = { UNREVIEWED: "Da validare", PENDING: "Correzione in attesa", APPROVED: "Approvato", REJECTED: "Correzione respinta", ACTIVE: "In servizio", BREAK: "In pausa", OFF_DUTY: "Concluso", LATE: "Ritardo", EARLY_EXIT: "Uscita anticipata", EXTRA_TIME: "Prolungamento", DURATION: "Durata", MISSING_ENTRY: "Entrata mancante", MISSING_EXIT: "Uscita mancante", OPEN_SESSION: "Turno aperto", UNPLANNED: "Fuori pianificazione", DELAYED_SYNC: "Sincronizzazione tardiva" };
    let people = [], plan = null, report = null, cfg = null, loadedMonth = "", busy = false, dirty = false, serial = 0, recoveries = [];
    function view(id, title, html) {
      const button = document.createElement("button");
      button.dataset.view = id;
      button.textContent = title;
      document.querySelector(".nav").append(button);
      button.addEventListener("click", () => switchView(id));
      const section = document.createElement("section");
      section.id = `view-${id}`;
      section.className = "view workforce-view";
      section.innerHTML = `<div class="section-head"><div><div class="section-kicker">PERSONALE</div><h2>${id === "attendance" ? "Presenze" : "Turnistica"}</h2><p>${id === "attendance" ? "Timbrature, validazione e confronto con i turni previsti" : "Calendario mensile, vincoli e copertura del servizio"}</p></div></div>` + html;
      const container = document.querySelector(".view").parentElement;
      container.insertBefore(section, container.querySelector(":scope > .footer"));
    }
    const select = (id, label, options) => `<label>${label}<select id="${id}">${options}</select></label>`;
    view("attendance", "\u25F7 Presenze", `<div class="panel"><div class="panel-head"><div><h3>Registro presenze</h3><span>Timbrature, ore validate e scostamenti dalla pianificazione</span></div></div><div class="panel-body"><form id="attendanceFilters" class="wf-filters"><label>Mese<input id="attendanceMonth" type="month" value="${month()}" required></label>${select("attendanceOperator", "Operatore", '<option value="">Tutti gli operatori</option>')}${select("attendanceStatus", "Validazione", '<option value="">Tutti gli stati</option>' + Object.entries(labels2).slice(0, 4).map(([k, v]) => `<option value="${k}">${v}</option>`).join(""))}<label>Cerca<input id="attendanceSearch" type="search" placeholder="Nome operatore"></label>${select("attendanceAnomaly", "Anomalie", '<option value="">Tutte le anomalie</option>' + Object.entries(labels2).slice(7).map(([k, v]) => `<option value="${k}">${v}</option>`).join(""))}<button class="btn primary">Aggiorna registro</button><button type="button" class="btn" id="attendanceExport">Esporta CSV</button></form><p id="attendanceNotice" role="status"></p></div></div><div id="attendanceKpis" class="enterprise-kpis"></div><div class="panel"><div class="panel-head"><h3>Sessioni di lavoro</h3></div><div class="panel-body wf-scroll table-wrap"><table class="wf-table"><thead><tr><th>Operatore</th><th>Entrata / uscita</th><th>Attivit\xE0 / pausa</th><th>Stato</th><th>Validazione</th><th>Azioni</th></tr></thead><tbody id="attendanceRows"></tbody></table></div></div><div class="panel"><div class="panel-head"><h3>Incongruenze da verificare</h3><span>Le timbrature mancanti richiedono verifica del responsabile</span></div><div class="panel-body" id="attendanceAnomalies"></div></div><div class="panel"><div class="panel-head"><h3>Timbrature da riconciliare</h3><span>Code trasferite dagli operatori; gli eventi originali restano conservati</span></div><div class="panel-body" id="attendanceRecoveries"></div></div>`);
    view("rostering", "\u25A6 Turnistica", `<div class="panel"><div class="panel-head"><div><h3>Pianificazione del personale</h3><span>Bozze, vincoli, generazione e pubblicazione mensile</span></div><div class="actions"><input aria-label="Mese pianificazione" id="rosterMonth" type="month" value="${month()}"><button class="btn" id="rosterLoad">Carica mese</button></div></div><div class="panel-body"><p id="rosterNotice" role="status">Carica un mese per configurare il calendario.</p><div class="actions"><button class="btn" id="rosterSave">Salva bozza</button><button class="btn primary" id="rosterGenerate">Genera turni</button><button class="btn primary" id="rosterPublish">Pubblica calendario</button><button class="btn" id="rosterNotify">Invia notifiche versione pubblicata</button></div></div></div><div class="panel"><div class="panel-head"><h3>Vincoli generali</h3><span>Obbligatori; le preferenze individuali sono ottimizzate separatamente</span></div><div class="panel-body wf-fields" id="rosterRules"></div></div><div class="panel"><div class="panel-head"><h3>Modelli di turno e copertura</h3><button class="btn" id="rosterTemplateAdd">Aggiungi modello</button></div><div class="panel-body" id="rosterTemplates"></div></div><div class="panel"><div class="panel-head"><h3>Vincoli individuali</h3><span>Seleziona gli operatori che partecipano alla pianificazione</span></div><div class="panel-body" id="rosterMembers"></div></div><div class="panel"><div class="panel-head"><h3>Calendario mensile</h3><span>Un turno al giorno per operatore \xB7 blocca le assegnazioni da preservare</span></div><div class="panel-body wf-scroll table-wrap" id="rosterCalendar"></div></div><div class="panel"><div class="panel-head"><h3>PDF personali della versione pubblicata</h3></div><div class="panel-body actions" id="rosterPdfs"></div></div>`);
    const stamp2 = (s) => s ? new Date(s).toLocaleString("it-IT", { timeZone: report?.zone || "Europe/Rome" }) : "\u2014";
    async function action(fn) {
      if (busy) return;
      busy = true;
      document.querySelectorAll(".workforce-view button").forEach((b) => b.disabled = true);
      try {
        await fn();
      } catch (e) {
        toast("Operazione non completata", e.message, "error");
      } finally {
        busy = false;
        document.querySelectorAll(".workforce-view button").forEach((b) => b.disabled = false);
      }
    }
    async function loadPeople() {
      const selected = $("attendanceOperator").value;
      people = await apiFetch(`${base}/roster/members`);
      $("attendanceOperator").innerHTML = '<option value="">Tutti gli operatori</option>' + people.map((p) => `<option value="${esc(p.uid)}">${esc(p.name)}</option>`).join("");
      $("attendanceOperator").value = selected;
    }
    async function loadAttendance() {
      if (!manager()) throw Error("Sezione riservata ai responsabili.");
      const epoch = state.sessionEpoch || 0, request = ++serial;
      await loadPeople();
      const query = new URLSearchParams({ month: $("attendanceMonth").value });
      if ($("attendanceOperator").value) query.set("uid", $("attendanceOperator").value);
      const [data, recoveryData] = await Promise.all([apiFetch(`${base}/attendance?${query}`), apiFetch(`${base}/attendance/recoveries`)]);
      if (epoch !== (state.sessionEpoch || 0) || request !== serial) return;
      report = data;
      recoveries = Array.isArray(recoveryData) ? recoveryData : [];
      renderAttendance();
    }
    function filteredSessions() {
      const search = $("attendanceSearch").value.toLowerCase(), status = $("attendanceStatus").value;
      return (report?.sessions || []).filter((s) => (!status || s.reviewStatus === status) && String(s.name).toLowerCase().includes(search));
    }
    function renderAttendance() {
      $("attendanceNotice").textContent = `Versione calendario ${report.publishedRevision || "non pubblicata"} \xB7 ${report.notice}`;
      $("attendanceKpis").innerHTML = [["Ore pianificate", hours(report.plannedMinutes)], ["Ore registrate", hours(report.registeredMinutes)], ["Ore approvate", hours(report.approvedMinutes)], ["Anomalie", report.anomalies.length]].map(([label, value]) => `<div class="panel"><span>${label}</span><strong>${esc(value)}</strong></div>`).join("");
      $("attendanceRows").innerHTML = filteredSessions().map((s) => `<tr><td><strong>${esc(s.name)}</strong>${s.boundarySession ? "<br><small>Iniziato nel mese precedente</small>" : ""}</td><td>${esc(stamp2(s.startedAt))}<br>${esc(stamp2(s.endedAt))}${s.approved ? `<br><small>Validato: ${esc(stamp2(s.approved.startedAt))} / ${esc(stamp2(s.approved.endedAt))}</small>` : ""}</td><td>${hours(s.workMinutes)} / ${hours(s.breakMinutes)}</td><td>${esc(labels2[s.status] || s.status)}</td><td>${esc(labels2[s.reviewStatus] || s.reviewStatus)}${s.correction ? `<br><small>${esc(s.correction.reason)}<br>Proposta: ${esc(stamp2(s.correction.startedAt))} / ${esc(stamp2(s.correction.endedAt))} \xB7 ${s.correction.breakMinutes} min pausa</small>` : ""}</td><td><button class="btn" data-events="${s.id}">Timbrature</button>${s.status === "OFF_DUTY" ? `<button class="btn" data-review="${s.id}" data-decision="APPROVE">Approva</button>${s.reviewStatus === "PENDING" ? `<button class="btn" data-review="${s.id}" data-decision="REJECT">Respingi</button>` : ""}` : ""}</td></tr>`).join("") || '<tr><td colspan="6">Nessuna sessione corrispondente.</td></tr>';
      renderRecoveries();
      const type = $("attendanceAnomaly").value, search = $("attendanceSearch").value.toLowerCase();
      $("attendanceAnomalies").innerHTML = report.anomalies.filter((a) => (!type || a.type === type) && a.name.toLowerCase().includes(search)).map((a) => `<article class="task-row"><div><strong>${esc(a.name)} \xB7 ${esc(labels2[a.type] || a.type)}</strong><p>${esc(a.date)} \xB7 ${esc(a.message)}</p></div></article>`).join("") || "<p>Nessuna anomalia corrispondente.</p>";
    }
    function renderRecoveries() {
      $("attendanceRecoveries").innerHTML = recoveries.filter((r) => r.status !== "RESOLVED").map((r) => `<article class="task-row"><div><strong>${esc(people.find((p) => p.uid === r.uid)?.name || r.name)}</strong><p>Ricevuta ${esc(stamp2(r.createdAt))} \xB7 ${r.events.length} eventi \xB7 ${r.status === "PENDING" ? "Da verificare" : "In verifica"}</p><details><summary>Eventi originali del dispositivo</summary>${r.events.map((e) => `<p>${esc({ START: "Entrata", END: "Uscita", BREAK: "Inizio pausa", RESUME: "Fine pausa" }[e.action] || e.action)} \xB7 ${esc(stamp2(e.occurredAt))}</p>`).join("")}</details><div class="actions"><button class="btn" data-recovery-interval="${r.id}">Registra intervallo validato</button><button class="btn" data-recovery-close="${r.id}">Concludi verifica</button></div></div></article>`).join("") || "<p>Nessuna coda da riconciliare.</p>";
    }
    $("attendanceRecoveries").addEventListener("click", (event) => {
      const close = event.target.closest("[data-recovery-close]"), interval = event.target.closest("[data-recovery-interval]");
      if (close) action(async () => {
        const r = recoveries.find((r2) => r2.id === close.dataset.recoveryClose), reason = await window.RadioTechDialogs.prompt("Esito della verifica e motivazione");
        if (!reason?.trim()) return;
        await apiFetch(`${base}/attendance/recoveries/${r.id}/close`, { method: "POST", body: JSON.stringify({ reason, expectedVersion: r.version }) });
        await loadAttendance();
      });
      if (interval) {
        const r = recoveries.find((r2) => r2.id === interval.dataset.recoveryInterval), dialog = document.createElement("dialog");
        dialog.className = "wf-dialog";
        dialog.innerHTML = `<h3>Registra intervallo validato</h3><p>Indica gli orari verificati nel fuso di questo dispositivo. Gli eventi ricevuti restano conservati. Gli intervalli sovrapposti sono respinti.</p><form class="wf-fields"><label>Entrata<input name="start" type="datetime-local" required></label><label>Uscita<input name="end" type="datetime-local" required></label><label>Pausa (minuti)<input name="breaks" type="number" min="0" max="1440" value="0" required></label><label>Motivazione<textarea name="reason" required maxlength="1000"></textarea></label><button class="btn primary">Registra e valida</button><button class="btn" type="button" data-cancel>Annulla</button></form>`;
        dialog.querySelector("[data-cancel]").onclick = () => dialog.close();
        dialog.addEventListener("close", () => dialog.remove());
        dialog.querySelector("form").onsubmit = (e) => {
          e.preventDefault();
          action(async () => {
            const f = new FormData(e.target);
            await apiFetch(`${base}/attendance/recoveries/${r.id}/interval`, { method: "POST", body: JSON.stringify({ startedAt: new Date(f.get("start")).toISOString(), endedAt: new Date(f.get("end")).toISOString(), breakMinutes: Number(f.get("breaks")), reason: f.get("reason"), expectedVersion: r.version }) });
            dialog.close();
            await loadAttendance();
          });
        };
        document.body.append(dialog);
        dialog.showModal();
      }
    });
    async function save() {
      if (!cfg || loadedMonth !== $("rosterMonth").value) throw Error("Carica prima il mese selezionato.");
      collect();
      plan = await apiFetch(`${base}/roster/${loadedMonth}`, { method: "PUT", body: JSON.stringify({ ...cfg, expectedVersion: plan?.version || 0 }) });
      dirty = false;
      renderPlan();
    }
    const field = (key, label, value, type = "number", extra = "") => `<label>${label}<input data-field="${key}" type="${type}" value="${esc(value)}" ${extra}></label>`;
    const days = (values, key) => `<div class="wf-days" data-days="${key}">${["Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"].map((d, i) => `<label><input type="checkbox" value="${i + 1}" ${values.includes(i + 1) ? "checked" : ""}>${d}</label>`).join("")}</div>`;
    const defaultMember = (id) => ({ operatorId: id, minMonthlyMinutes: 0, maxMonthlyMinutes: 11040, targetMonthlyMinutes: 9600, maxWeeklyMinutes: 2400, maxConsecutiveDays: 5, allowNight: true, days: [1, 2, 3, 4, 5, 6, 7], unavailableDates: [], allowedTemplates: [], skills: [], preferredDays: [] });
    function renderPlan() {
      cfg = structuredClone(plan.config || { rules: { zone: "Europe/Rome", minRestMinutes: 660, maxConsecutiveDays: 5, maxWeeklyMinutes: 2400, toleranceMinutes: 10, solveSeconds: 10 }, templates: [{ id: "DAY", label: "Diurno", start: "08:00", end: "16:30", breakMinutes: 30, coverage: 1, days: [1, 2, 3, 4, 5], site: "", skill: "", night: false }], members: people.map((p) => defaultMember(p.operatorId)), assignments: [], notes: "" });
      $("rosterNotice").textContent = `${loadedMonth} \xB7 ${plan.status === "EMPTY" ? "Nuova bozza" : plan.status === "PUBLISHED" ? "Pubblicato" : "Bozza"} \xB7 Revisione bozza ${plan.version || 0} \xB7 Versione pubblicata ${plan.publishedRevision || "\u2014"}`;
      $("rosterRules").innerHTML = field("zone", "Fuso orario IANA", cfg.rules.zone, "text") + field("minRestMinutes", "Riposo minimo (min)", cfg.rules.minRestMinutes) + field("maxConsecutiveDays", "Giorni consecutivi massimi", cfg.rules.maxConsecutiveDays) + field("maxWeeklyMinutes", "Ore settimanali massime (min)", cfg.rules.maxWeeklyMinutes) + field("toleranceMinutes", "Tolleranza timbrature (min)", cfg.rules.toleranceMinutes) + field("solveSeconds", "Tempo ricerca (1\u201320 secondi)", cfg.rules.solveSeconds) + field("notes", "Note responsabile", cfg.notes || "", "text");
      $("rosterTemplates").innerHTML = cfg.templates.map((t, i) => `<fieldset data-template="${i}"><legend>${esc(t.label || `Modello ${i + 1}`)}</legend><div class="wf-fields">${field("label", "Nome", t.label, "text")}${field("start", "Entrata", t.start, "time")}${field("end", "Uscita", t.end, "time")}${field("breakMinutes", "Pausa (min)", t.breakMinutes)}${field("coverage", "Operatori necessari", t.coverage)}${field("site", "Sede / squadra", t.site || "", "text")}${field("skill", "Competenza richiesta", t.skill || "", "text")}<label><input data-field="night" type="checkbox" ${t.night ? "checked" : ""}>Turno notturno</label></div>${days(t.days, "days")}<button class="btn" data-remove-template="${i}">Rimuovi modello</button></fieldset>`).join("");
      $("rosterMembers").innerHTML = people.map((p) => {
        const active = cfg.members.find((m2) => m2.operatorId === p.operatorId), m = active || defaultMember(p.operatorId);
        return `<details data-member="${esc(p.operatorId)}"><summary>${esc(p.name)} ${active ? "\xB7 Incluso" : "\xB7 Escluso"}</summary><label><input data-field="included" type="checkbox" ${active ? "checked" : ""}>Includi nella pianificazione</label><div class="wf-fields">${field("minMonthlyMinutes", "Minimo mensile (min)", m.minMonthlyMinutes)}${field("targetMonthlyMinutes", "Obiettivo mensile (min)", m.targetMonthlyMinutes)}${field("maxMonthlyMinutes", "Massimo mensile (min)", m.maxMonthlyMinutes)}${field("maxWeeklyMinutes", "Massimo settimanale (min)", m.maxWeeklyMinutes)}${field("maxConsecutiveDays", "Giorni consecutivi massimi", m.maxConsecutiveDays)}<label><input data-field="allowNight" type="checkbox" ${m.allowNight ? "checked" : ""}>Disponibile per notti</label>${field("skills", "Competenze (separate da virgola)", m.skills.join(", "), "text")}<div><span>Modelli ammessi (nessuna selezione = tutti)</span><div class="wf-days" data-list="allowedTemplates">${cfg.templates.map((t) => `<label><input type="checkbox" value="${esc(t.id)}" ${m.allowedTemplates.includes(t.id) ? "checked" : ""}>${esc(t.label)}</label>`).join("")}</div></div>${field("unavailableDates", "Ferie / indisponibilit\xE0 (AAAA-MM-GG, separate da virgola)", m.unavailableDates.join(", "), "text")}</div><p>Giorni disponibili</p>${days(m.days, "days")}<p>Giorni preferiti (facoltativi)</p>${days(m.preferredDays, "preferredDays")}</details>`;
      }).join("");
      renderCalendar();
      $("rosterPdfs").innerHTML = (plan.published?.slots || []).reduce((rows, s) => rows.some((r) => r.operatorId === s.operatorId) ? rows : [...rows, s], []).map((p) => `<button class="btn" data-pdf="${esc(p.operatorId)}">PDF \xB7 ${esc(p.name)}</button>`).join("") || "<p>Pubblica il calendario per rendere disponibili i PDF.</p>";
    }
    function collectFields(root) {
      const out = {};
      root.querySelectorAll("[data-field]").forEach((input) => out[input.dataset.field] = input.type === "checkbox" ? input.checked : input.type === "number" ? Number(input.value) : input.value);
      root.querySelectorAll("[data-days]").forEach((group) => out[group.dataset.days] = [...group.querySelectorAll("input:checked")].map((x) => Number(x.value)));
      root.querySelectorAll("[data-list]").forEach((group) => out[group.dataset.list] = [...group.querySelectorAll("input:checked")].map((x) => x.value));
      return out;
    }
    function collect() {
      const fields = collectFields($("rosterRules"));
      cfg.notes = fields.notes;
      delete fields.notes;
      cfg.rules = fields;
      cfg.templates = [...$("rosterTemplates").querySelectorAll("[data-template]")].map((el) => ({ ...cfg.templates[Number(el.dataset.template)], ...collectFields(el) }));
      cfg.members = [...$("rosterMembers").querySelectorAll("[data-member]")].map((el) => {
        const v = collectFields(el);
        if (!v.included) return null;
        delete v.included;
        for (const key of ["skills", "unavailableDates"]) v[key] = v[key].split(",").map((s) => s.trim()).filter(Boolean);
        return { ...v, operatorId: el.dataset.member };
      }).filter(Boolean);
      cfg.assignments = [...$("rosterCalendar").querySelectorAll("select[data-assignment]")].filter((s) => s.value).map((s) => ({ operatorId: s.dataset.operator, date: s.dataset.date, templateId: s.value, locked: s.closest("td").querySelector("input").checked }));
    }
    function renderCalendar() {
      const [year, monthNumber] = loadedMonth.split("-").map(Number), count = new Date(year, monthNumber, 0).getDate();
      $("rosterCalendar").innerHTML = `<table class="wf-table wf-calendar"><thead><tr><th>Operatore</th>${Array.from({ length: count }, (_, i) => `<th>${i + 1}</th>`).join("")}</tr></thead><tbody>${cfg.members.map((m) => `<tr><th>${esc(people.find((p) => p.operatorId === m.operatorId)?.name || m.operatorId)}</th>${Array.from({ length: count }, (_, i) => {
        const date = `${loadedMonth}-${String(i + 1).padStart(2, "0")}`, a = cfg.assignments.find((a2) => a2.operatorId === m.operatorId && a2.date === date);
        return `<td><select aria-label="Turno ${esc(m.operatorId)} ${date}" data-assignment data-operator="${esc(m.operatorId)}" data-date="${date}"><option value="">Riposo</option>${cfg.templates.map((t) => `<option value="${esc(t.id)}" ${a?.templateId === t.id ? "selected" : ""}>${esc(t.label)}</option>`).join("")}</select><label><input type="checkbox" ${a?.locked ? "checked" : ""}>Blocca</label></td>`;
      }).join("")}</tr>`).join("")}</tbody></table>`;
    }
    async function loadPlan() {
      if (!manager()) throw Error("Sezione riservata ai responsabili.");
      if (dirty && !await window.RadioTechDialogs.confirm("Abbandonare le modifiche non salvate?")) return;
      await loadPeople();
      loadedMonth = $("rosterMonth").value;
      plan = await apiFetch(`${base}/roster/${loadedMonth}`);
      dirty = false;
      renderPlan();
    }
    $("attendanceFilters").addEventListener("submit", (e) => {
      e.preventDefault();
      action(loadAttendance);
    });
    for (const id of ["attendanceStatus", "attendanceSearch", "attendanceAnomaly"]) $(id).addEventListener("input", () => report && renderAttendance());
    $("attendanceRows").addEventListener("click", (e) => action(async () => {
      const events = e.target.closest("[data-events]"), review = e.target.closest("[data-review]");
      if (events) {
        const data = await apiFetch(`${base}/attendance/${events.dataset.events}/events`);
        const dialog = document.createElement("dialog");
        dialog.className = "wf-dialog";
        dialog.innerHTML = `<h3>Timbrature originali</h3>${data.map((r) => `<p>${esc(r.action)} \xB7 ${esc(stamp2(r.occurredAt || r.recordedAt))} \xB7 ricevuta ${esc(stamp2(r.recordedAt))}</p>`).join("")}<button class="btn">Chiudi</button>`;
        dialog.querySelector("button").onclick = () => dialog.close();
        dialog.addEventListener("close", () => dialog.remove());
        document.body.append(dialog);
        dialog.showModal();
      }
      if (review) {
        const s = report.sessions.find((s2) => s2.id === review.dataset.review), reason = await window.RadioTechDialogs.prompt("Motivazione della validazione");
        if (!reason?.trim()) return;
        await apiFetch(`${base}/attendance/${s.id}/review`, { method: "POST", body: JSON.stringify({ decision: review.dataset.decision, reason, expectedVersion: s.version }) });
        await loadAttendance();
      }
    }));
    $("attendanceExport").onclick = () => {
      if (!report) return;
      const quote = (v) => `"${String(v ?? "").replace(/^\s*([=+@-])/, "'$1").replaceAll('"', '""')}"`;
      const rows = [["Operatore", "Entrata", "Uscita", "Minuti attivit\xE0", "Minuti pausa", "Validazione", "Minuti approvati"], ...filteredSessions().map((s) => [s.name, s.startedAt, s.endedAt, s.workMinutes, s.breakMinutes, labels2[s.reviewStatus], s.approved?.workMinutes])];
      download(new TextEncoder().encode("\uFEFF" + rows.map((r) => r.map(quote).join(";")).join("\r\n")), `presenze-${report.month}.csv`, "text/csv");
    };
    $("rosterLoad").onclick = () => action(loadPlan);
    $("rosterSave").onclick = () => action(save);
    $("rosterGenerate").onclick = () => action(async () => {
      await save();
      const r = await apiFetch(`${base}/roster/${loadedMonth}/generate`, { method: "POST", body: JSON.stringify({ expectedVersion: plan.version }), timeoutMs: 6e4 });
      if (r.generated) {
        plan = r.plan;
        renderPlan();
        toast("Calendario generato", r.result.status === "OPTIMAL" ? "Soluzione ottimizzata." : "Soluzione valida trovata entro il limite.");
      } else $("rosterNotice").textContent = r.result.diagnostics.join(" ");
    });
    $("rosterPublish").onclick = () => action(async () => {
      if (!await window.RadioTechDialogs.confirm("Pubblicare questa versione del calendario per gli operatori?")) return;
      await save();
      const r = await apiFetch(`${base}/roster/${loadedMonth}/publish`, { method: "POST", body: JSON.stringify({ expectedVersion: plan.version }), timeoutMs: 6e4 });
      plan = r.plan;
      renderPlan();
      await notify();
    });
    async function notify() {
      if (!loadedMonth) throw Error("Carica prima un calendario.");
      const r = await apiFetch(`${base}/roster/${loadedMonth}/notify`, { method: "POST", timeoutMs: 6e4 });
      toast("Calendario pubblicato", `Consegne a dispositivi: ${r.deliveredDevices}. Errori operatori: ${r.failedOperators.length}. Consulta il centro notifiche per il dettaglio.`, r.failedOperators.length ? "error" : "success");
    }
    $("rosterNotify").onclick = () => action(notify);
    $("rosterTemplateAdd").onclick = () => {
      if (!cfg) return;
      collect();
      if (cfg.templates.length >= 8) {
        toast("Limite modelli", "Sono ammessi fino a 8 modelli.", "error");
        return;
      }
      cfg.templates.push({ id: `TURN_${crypto.randomUUID().slice(0, 8)}`, label: "Nuovo turno", start: "08:00", end: "16:00", breakMinutes: 0, coverage: 1, days: [1, 2, 3, 4, 5], site: "", skill: "", night: false });
      plan.config = cfg;
      renderPlan();
      dirty = true;
    };
    $("rosterTemplates").onclick = (e) => {
      const b = e.target.closest("[data-remove-template]");
      if (!b) return;
      collect();
      const id = cfg.templates[Number(b.dataset.removeTemplate)].id;
      cfg.templates = cfg.templates.filter((t) => t.id !== id);
      cfg.assignments = cfg.assignments.filter((a) => a.templateId !== id);
      plan.config = cfg;
      renderPlan();
      dirty = true;
    };
    $("rosterMembers").addEventListener("change", (e) => {
      if (e.target.dataset.field === "included") {
        collect();
        renderCalendar();
      }
      dirty = true;
    });
    for (const id of ["rosterRules", "rosterTemplates", "rosterCalendar"]) $(id).addEventListener("input", () => dirty = true);
    $("rosterPdfs").onclick = (e) => action(async () => {
      const b = e.target.closest("[data-pdf]");
      if (!b) return;
      const r = await apiFetch(`${base}/roster/${loadedMonth}/pdf/${encodeURIComponent(b.dataset.pdf)}`, { responseType: "bytes" });
      download(r.bytes, `turni-${b.dataset.pdf}-${loadedMonth}.pdf`, "application/pdf");
    });
    function download(bytes, name, type) {
      const url = URL.createObjectURL(new Blob([bytes], { type })), a = document.createElement("a");
      a.href = url;
      a.download = name;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1e3);
    }
    window.addEventListener("radiotech:view-changed", (e) => {
      if (e.detail.view === "attendance") action(loadAttendance);
      if (e.detail.view === "rostering" && !plan) action(loadPlan);
    });
    window.addEventListener("radiotech:identity-changed", () => {
      serial++;
      document.querySelectorAll(".wf-dialog").forEach((d) => d.close());
      $("attendanceOperator").innerHTML = '<option value="">Tutti gli operatori</option>';
      $("attendanceSearch").value = "";
      people = [];
      recoveries = [];
      plan = null;
      cfg = null;
      report = null;
      dirty = false;
      loadedMonth = "";
      document.querySelectorAll(".workforce-view [id]").forEach((el) => {
        if (!["INPUT", "SELECT", "FORM", "BUTTON"].includes(el.tagName)) el.replaceChildren();
      });
    });
    window.addEventListener("radiotech:session-ready", () => document.querySelectorAll('.nav button[data-view="attendance"],.nav button[data-view="rostering"]').forEach((b) => b.hidden = !manager()));
  }

  // frontend/control-room/features/profile.js
  function createFeature(ctx) {
    function applyPermissions() {
      const allowed = ["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(ctx.state.authRole || ctx.state.user?.role);
      for (const id of ["quickAntennaBtn", "quickNotificationBtn", "newAntennaBtn", "newOperatorBtn", "syncOperatorBtn", "createTaskBtn", "newNotificationBtn", "sendNotificationBtn", "newInventoryItemBtn", "editProfileBtn"]) {
        if (ctx.$(id)) ctx.$(id).hidden = !allowed;
      }
      document.querySelectorAll("[data-edit], [data-delete], [data-delete-operator], [data-reset-password], [data-validity], [data-save-role], [data-approve], [data-badge], [data-regenerate], [data-inventory-move], [data-inventory-edit], [data-inventory-archive], [data-inventory-restore]").forEach((element) => {
        element.hidden = !allowed;
      });
      document.querySelectorAll("[data-report-approve], [data-report-reject], [data-report-delete]").forEach((element) => {
        element.hidden = !ctx.canManageReports();
      });
      const accountAdministration = allowed && (ctx.state.authRole || ctx.state.user?.role) !== "ENGINEER";
      for (const id of ["newOperatorBtn", "syncOperatorBtn"]) if (ctx.$(id)) ctx.$(id).hidden = !accountAdministration;
      document.querySelectorAll("[data-delete-operator],[data-reset-password],[data-validity],[data-save-role],[data-approve],[data-regenerate]").forEach((element) => {
        element.hidden = !accountAdministration;
      });
      document.querySelectorAll("[data-operator-role]").forEach((element) => {
        element.disabled = !accountAdministration || element.dataset.roleLocked === "true";
      });
    }
    function hydrateUser() {
      applyPermissions();
      ctx.signatureWorkspace?.syncIdentity();
      const u = ctx.state.user || {};
      const name = u.name || u.displayName || u.fullName || "Capo RadioTech";
      const email = u.email || "\u2014";
      const role = u.role || u.ruolo || "Chief Executive \xB7 RadioTech";
      ctx.setText("workspaceTenant", `Azienda ${u.tenantId || "\u2014"}`);
      if (ctx.$("workspaceTenant")) ctx.$("workspaceTenant").title = u.tenantId || "Azienda non disponibile";
      const avatar = ctx.initials(name);
      ctx.setText("miniAvatar", avatar);
      ctx.setText("miniName", name);
      ctx.setText("miniEmail", email);
      ctx.setText("profileAvatar", avatar);
      window.RadioTechPersonalization?.syncProfile(ctx.state.user);
      ctx.setText("profileName", name);
      ctx.setText("profileFullName", name);
      ctx.setText("profileEmail", email);
      ctx.setText("profilePhone", u.phone || u.telefono || "\u2014");
      ctx.setText("profileRole", role);
      ctx.setText("profileRoleField", role);
      ctx.setText("profileUid", u.uid || u.id || "\u2014");
      ctx.setText("profileLastLogin", u.lastLogin ? new Date(u.lastLogin).toLocaleString("it-IT") : "Sessione corrente");
      ctx.setText("sessionUser", email);
      ctx.setText("sessionTokenStatus", ctx.state.token ? "Token presente" : "Nessun token");
      ctx.setText("sessionPersistence", localStorage.getItem(ctx.CONFIG.STORAGE_REMEMBER) === "true" ? "ATTIVA" : "SESSIONE");
    }
    function persistUser() {
    }
    async function loadProfile() {
      try {
        const profile = await ctx.api("/profile");
        if (profile) {
          const data = profile.data || profile.user || profile;
          ctx.state.user = { ...ctx.state.user, ...data, name: data.fullName || data.name || ctx.state.user?.name, role: ctx.state.authRole || ctx.state.user?.role };
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
        fullName: ctx.$("editProfileName").value.trim(),
        phone: ctx.$("editProfilePhone").value.trim(),
        company: ctx.$("editProfileCompany").value.trim(),
        photoUrl: window.RadioTechPersonalization?.profilePhoto() ?? ctx.state.user?.photoUrl ?? ""
      };
      try {
        const result = await ctx.apiFetch("/api/v1/capo/profile", {
          method: "PUT",
          body: JSON.stringify(payload)
        });
        const data = result?.data || result?.user || result || payload;
        ctx.state.user = { ...ctx.state.user, ...data, name: data.fullName || ctx.state.user?.name, role: ctx.state.authRole || ctx.state.user?.role };
        persistUser();
        hydrateUser();
        ctx.closeModal("profileModal");
        ctx.toast("Profilo aggiornato", "Le informazioni del Capo sono state salvate.");
      } catch (error) {
        window.RadioTechForms.errors(ctx.$("profileForm"), error.fieldErrors);
        ctx.toast("Profilo non aggiornato", error.message, "error");
      }
    }
    function openProfileModal() {
      applyPermissions();
      const u = ctx.state.user || {};
      ctx.$("editProfileName").value = u.name || u.displayName || u.fullName || "";
      ctx.$("editProfilePhone").value = u.phone || u.telefono || "";
      ctx.$("editProfileCompany").value = u.company || "";
      window.RadioTechPersonalization?.resetProfile(u);
      ctx.openModal("profileModal");
    }
    return { get applyPermissions() {
      return applyPermissions;
    }, get hydrateUser() {
      return hydrateUser;
    }, get persistUser() {
      return persistUser;
    }, get loadProfile() {
      return loadProfile;
    }, get saveProfile() {
      return saveProfile;
    }, get openProfileModal() {
      return openProfileModal;
    } };
  }

  // frontend/control-room/features/map.js
  function createFeature2(ctx) {
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
      ctx.state.markerByAntennaId.forEach((marker) => marker.remove());
      ctx.state.markerByAntennaId.clear();
      ctx.state.markers = [];
      const valid = [];
      ctx.state.antennas.forEach((a) => {
        const lat = Number(a.lat), lng = Number(a.lng);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
        const status = String(a.status || "OFFLINE").toUpperCase();
        const color = status === "ATTIVA" ? "#2bd576" : status === "MANUTENZIONE" ? "#ffb648" : status === "CRITICA" ? "#b66cff" : "#ff5f6d";
        const icon = L.divIcon({ className: "", html: `<div role="img" aria-label="${ctx.escapeHtml(a.name || a.id)} \xB7 ${ctx.escapeHtml(ctx.formatStatus(status))}" style="width:28px;height:28px;border-radius:50%;display:grid;place-items:center;position:relative;color:#2bd576;background:#14241b;border:3px solid rgba(255,255,255,.95);box-shadow:0 0 0 6px ${color}22,0 5px 18px #0009"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 8v13M7 21l5-10 5 10M4 4a11 11 0 0 0 0 12M20 4a11 11 0 0 1 0 12M8 7a5 5 0 0 0 0 6M16 7a5 5 0 0 1 0 6"/><circle cx="12" cy="7" r="1"/></svg><span aria-hidden="true" style="position:absolute;right:-7px;bottom:-7px;min-width:15px;height:15px;border-radius:50%;display:grid;place-items:center;background:${color};color:#111;font:bold 10px system-ui;border:1px solid white">${status === "ATTIVA" ? "A" : status === "MANUTENZIONE" ? "M" : status === "CRITICA" ? "!" : "O"}</span></div>`, iconSize: [28, 28], iconAnchor: [14, 14] });
        const marker = L.marker([lat, lng], { icon }).addTo(ctx.state.map);
        const specs = ctx.antennaSpecs(a);
        marker.bindPopup(`<div style="min-width:220px;color:#111827;font-family:system-ui"><strong>${ctx.escapeHtml(a.name || a.id)}</strong><br><small>Stato: ${ctx.escapeHtml(ctx.formatStatus(status))}</small><br><small>Frequenza: ${ctx.escapeHtml(specs.Frequenza ?? "\u2014")}</small><br><small>Potenza: ${ctx.escapeHtml(specs.Potenza ?? "\u2014")}</small><br><small>ROS: ${ctx.escapeHtml(specs.ROS ?? "\u2014")}</small><br><small>Temperatura: ${ctx.escapeHtml(specs.Temperatura ?? "\u2014")}</small></div>`);
        marker.on("click", () => {
          ctx.switchView("antennas");
          const row = [...ctx.$("antennasTableBody").querySelectorAll("[data-focus]")].find((button) => button.dataset.focus === a.id)?.closest("tr");
          if (row) {
            row.scrollIntoView({ block: "center" });
            row.tabIndex = -1;
            row.focus();
          }
        });
        ctx.state.markerByAntennaId.set(a.id, marker);
        ctx.state.markers.push(marker);
        valid.push([lat, lng]);
      });
      ctx.setText("mapSubstatus", `${valid.length} antenne geolocalizzate`);
      if (valid.length && ctx.state.mapFittedScope !== (ctx.state.sessionEpoch || 0)) {
        ctx.state.map.fitBounds(valid, { padding: [35, 35], maxZoom: 12 });
        ctx.state.mapFittedScope = ctx.state.sessionEpoch || 0;
      }
      setTimeout(() => ctx.state.map?.invalidateSize(), 50);
    }
    return { get initMap() {
      return initMap;
    }, get updateMap() {
      return updateMap;
    } };
  }

  // frontend/control-room/features/dashboard.js
  function createFeature3(ctx) {
    async function loadStats() {
      const stats = await ctx.api("/stats");
      ctx.state.stats = stats || {};
      ctx.setText("statAntennas", stats?.antennas ?? 0);
      ctx.setText("statOperators", stats?.operators ?? 0);
      ctx.setText("statInterventions", stats?.interventions ?? 0);
      ctx.setText("statTasks", stats?.tasks ?? 0);
      ctx.setText("statLowStock", stats?.lowStock ?? 0);
      ctx.setText("statOverdueTasks", stats?.taskStats?.overdue ?? 0);
      const availability = Number(stats?.availability || 0);
      const active = Number(stats?.activeAntennas || 0);
      const inactive = Number(stats?.inactiveAntennas || 0);
      const total = Number(stats?.antennas || 0);
      ctx.setText("availabilityValue", `${ctx.number(availability, 2)}%`);
      ctx.setText("availabilityLabel", `${ctx.number(availability, 2)}%`);
      ctx.setText("activeCount", `${active} / ${total}`);
      ctx.setText("inactiveCount", `${inactive} / ${total}`);
      ctx.$("activeProgress").style.width = `${Math.min(100, availability)}%`;
      ctx.$("inactiveProgress").style.width = `${total ? Math.min(100, inactive / total * 100) : 0}%`;
      const availabilityBadge = ctx.$("availabilityBadge");
      availabilityBadge.textContent = total ? `${active} / ${total} attive` : "NESSUN ASSET";
      availabilityBadge.className = `badge ${availability >= 95 ? "green" : availability >= 80 ? "yellow" : "red"}`;
      const tasks = stats?.taskStats || {};
      ctx.setText("taskAssigned", tasks.assigned ?? 0);
      ctx.setText("taskProgress", tasks.inProgress ?? 0);
      ctx.setText("taskCompleted", tasks.completed ?? 0);
      ctx.setText("taskCancelled", tasks.cancelled ?? 0);
      ctx.setText("opsAssigned", tasks.assigned ?? 0);
      ctx.setText("opsProgress", tasks.inProgress ?? 0);
      ctx.setText("opsCompleted", tasks.completed ?? 0);
      ctx.setText("opsCancelled", tasks.cancelled ?? 0);
      ctx.setText("opsAvailability", `${ctx.number(availability, 2)}%`);
      ctx.$("opsAvailabilityProgress").style.width = `${Math.min(100, availability)}%`;
      ctx.setText("opsActive", active);
      ctx.setText("opsInactive", inactive);
      const calculatedAt = stats?.asOf ? new Date(stats.asOf) : null;
      ctx.setText("lastUpdate", calculatedAt && Number.isFinite(calculatedAt.getTime()) ? calculatedAt.toLocaleString("it-IT") : "Ora del dato non disponibile");
      setBackendState(true);
      loadSystemHealth().catch(() => {
      });
    }
    async function loadSystemHealth() {
      try {
        const r = await ctx.apiFetch("/api/v1/health/firebase");
        const ok = r?.status === "UP";
        ctx.setText("firestoreStatus", ok ? "OK" : "DOWN");
        ctx.$("firestoreBadge").textContent = ok ? "CONNECTED" : "OFFLINE";
        ctx.$("firestoreBadge").className = `badge ${ok ? "green" : "red"}`;
      } catch (e) {
        ctx.setText("firestoreStatus", "DOWN");
        ctx.$("firestoreBadge").textContent = "OFFLINE";
        ctx.$("firestoreBadge").className = "badge red";
      }
    }
    function setBackendState(ok) {
      ctx.setText("backendStatus", ok ? "OK" : "ERROR");
      ctx.$("backendBadge").textContent = ok ? "ONLINE" : "OFFLINE";
      ctx.$("backendBadge").className = `badge ${ok ? "green" : "red"}`;
      if (!ok) {
        ctx.setText("firestoreStatus", "UNKNOWN");
        ctx.$("firestoreBadge").textContent = "UNKNOWN";
        ctx.$("firestoreBadge").className = "badge gray";
      }
    }
    return { get loadStats() {
      return loadStats;
    }, get loadSystemHealth() {
      return loadSystemHealth;
    }, get setBackendState() {
      return setBackendState;
    } };
  }

  // frontend/control-room/paged-resource.js
  function createPagedResource(ctx, { path, legacy, stateKey, tableId, onUpdated, pageSize = 100, buttonId = `${tableId}-more`, buttonText = "Carica altri elementi", filters = () => ({}) }) {
    let cursor = null, pages = 1, lastScope = null, lastFilters = null;
    async function load(more = false) {
      more = more === true;
      const scope = ctx.state.sessionEpoch || 0, selected = filters(), signature = JSON.stringify(selected), same = scope === lastScope && signature === lastFilters;
      const depth = !more && same ? pages : 1;
      let next = more && same ? cursor : null, rows = more && same ? [...ctx.state[stateKey]] : [], count = more && same ? pages : 0;
      for (let page = 0; page < depth; page++) {
        let response;
        const params = new URLSearchParams({ limit: String(pageSize), ...selected });
        if (next) params.set("cursor", next);
        try {
          response = await ctx.apiFetch(`${path}?${params}`);
        } catch (error) {
          if (error.status !== 404) throw error;
          response = await ctx.apiFetch(legacy);
        }
        if (signature !== JSON.stringify(filters())) return;
        if (scope !== (ctx.state.sessionEpoch || 0)) throw new Error("Sessione cambiata.");
        const data = response?.data ?? response, items = Array.isArray(data) ? data : data?.items ?? data?.[stateKey] ?? [];
        rows = [...new Map([...rows, ...items].map((item) => [item.id, item])).values()];
        next = data?.nextCursor ?? null;
        count++;
        if (!next) break;
      }
      ctx.state[stateKey] = rows;
      cursor = next;
      pages = count;
      lastScope = scope;
      lastFilters = signature;
      let button = ctx.$(buttonId);
      if (!button) {
        button = document.createElement("button");
        button.id = buttonId;
        button.type = "button";
        button.className = "btn";
        button.textContent = buttonText;
        ctx.$(tableId)?.closest(".table-wrap")?.after(button);
        button.addEventListener("click", async () => {
          button.disabled = true;
          try {
            await load(true);
            onUpdated();
          } catch (error) {
            ctx.toast("Elementi non caricati", error.message, "error");
          } finally {
            button.disabled = false;
          }
        });
      }
      button.hidden = !cursor;
    }
    return load;
  }
  function preserveTableInteraction(table, render) {
    const wrap = table?.closest(".table-wrap"), active = document.activeElement;
    const attributes = table?.contains(active) ? [...active.attributes].filter((attr) => attr.name === "id" || attr.name.startsWith("data-")) : [];
    const top = wrap?.scrollTop, left = wrap?.scrollLeft;
    render();
    if (wrap) {
      wrap.scrollTop = top;
      wrap.scrollLeft = left;
    }
    if (attributes.length && !active.isConnected && !document.querySelector("dialog[open],.modal-backdrop.open")) {
      const selector = attributes.map((attr) => `[${attr.name}="${CSS.escape(attr.value)}"]`).join("");
      (table.querySelector(selector) ?? wrap)?.focus({ preventScroll: true });
    }
  }

  // frontend/control-room/features/assets.js
  function createFeature4(ctx) {
    const pages = createPagedResource(ctx, { path: "/api/v1/dashboard/antenne/page", legacy: "/api/v1/dashboard/antenne", stateKey: "antennas", tableId: "antennasTableBody", pageSize: 200, buttonId: "antennaLoadMore", buttonText: "Carica altri impianti", filters: () => ({ q: ctx.$("antennaSearch")?.value || "", status: ctx.$("antennaStatusFilter")?.value || "" }), onUpdated: () => {
      renderAntennas();
      ctx.populateTaskSelectors();
      ctx.updateMap();
    } });
    async function loadAntennas(more = false) {
      try {
        await pages(more);
        renderAntennas();
        ctx.populateTaskSelectors();
        ctx.updateMap();
      } catch (error) {
        console.error(error);
        ctx.$("antennasTableBody").innerHTML = `<tr><td colspan="6"><div class="empty">Impossibile caricare le antenne: ${ctx.escapeHtml(error.message)}</div></td></tr>`;
        throw error;
      }
    }
    function renderAntennas() {
      preserveTableInteraction(ctx.$("antennasTableBody"), renderAntennaRows);
    }
    function renderAntennaRows() {
      const normalize = (value) => String(value || "").normalize("NFKD").replace(/\p{M}/gu, "").toLowerCase();
      const query = normalize(ctx.$("antennaSearch").value.trim());
      const filter = ctx.$("antennaStatusFilter").value.toUpperCase();
      const list = ctx.state.antennas.filter((a) => {
        const haystack = normalize([a.id, a.name, a.status, a.lat, a.lng].join(" "));
        return (!query || haystack.includes(query)) && (!filter || String(a.status || "").toUpperCase() === filter);
      });
      ctx.setText("antennaCountLabel", `${list.length} di ${ctx.state.antennas.length} antenne`);
      if (!list.length) {
        ctx.$("antennasTableBody").innerHTML = `<tr><td colspan="6"><div class="empty">Nessuna antenna trovata.</div></td></tr>`;
        return;
      }
      ctx.$("antennasTableBody").innerHTML = list.map((a) => {
        const specs = antennaSpecs(a);
        return `
                <tr>
                    <td>
                        <div class="antenna-name">${ctx.escapeHtml(a.name || a.id)}</div>
                        <div class="coordinates">${ctx.escapeHtml(a.id || "")}</div>
                    </td>
                    <td class="asset-coordinates"><span title="Latitudine">${ctx.number(a.lat, 5)}</span> <span title="Longitudine">${ctx.number(a.lng, 5)}</span></td>
                    <td><span class="badge ${ctx.badgeClass(a.status)}">${ctx.escapeHtml(ctx.formatStatus(a.status))}</span></td>
                    <td><div class="asset-telemetry"><span>Freq.</span><strong>${antennaReading(specs.Frequenza, "MHz")}</strong><span>Potenza</span><strong>${antennaReading(specs.Potenza, "W")}</strong></div></td>
                    <td><div class="asset-telemetry"><span>ROS</span><strong>${antennaReading(specs.ROS, "")}</strong><span>Temp.</span><strong>${antennaReading(specs.Temperatura, "\xB0C")}</strong></div></td>
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
      ctx.$("antennasTableBody").querySelectorAll("[data-qr]").forEach(
        (b) => b.addEventListener("click", () => ctx.showAntennaQr(b.dataset.qr))
      );
      ctx.$("antennasTableBody").querySelectorAll("[data-edit]").forEach(
        (b) => b.addEventListener("click", () => openAntennaModal(b.dataset.edit))
      );
      ctx.applyPermissions();
      ctx.$("antennasTableBody").querySelectorAll("[data-focus]").forEach(
        (b) => b.addEventListener("click", () => focusAntenna(b.dataset.focus))
      );
      ctx.applyPermissions();
      ctx.$("antennasTableBody").querySelectorAll("[data-delete]").forEach(
        (b) => b.addEventListener("click", () => deleteAntenna(b.dataset.delete))
      );
    }
    function focusAntenna(id) {
      const a = ctx.state.antennas.find((x) => x.id === id);
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
        const a = ctx.state.antennas.find((x) => x.id === id);
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
    function antennaReading(value, unit) {
      if (value === null || value === void 0 || value === "") return "\u2014";
      return `${ctx.escapeHtml(value)}${unit && Number.isFinite(Number(value)) ? ` <small class="asset-unit">${unit}</small>` : ""}`;
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
        window.RadioTechForms.errors(ctx.$("antennaForm"), error.fieldErrors);
        ctx.toast("Salvataggio fallito", error.message, "error");
      } finally {
        button.disabled = false;
        button.textContent = "Salva antenna";
      }
    }
    async function deleteAntenna(id) {
      const a = ctx.state.antennas.find((x) => x.id === id);
      const name = a?.name || id;
      if (!await ctx.confirm(`Eliminare definitivamente "${name}"?`)) return;
      try {
        await ctx.api(`/antenne/${encodeURIComponent(id)}`, { method: "DELETE" });
        await Promise.all([loadAntennas(), ctx.loadStats()]);
        ctx.toast("Antenna eliminata", `${name} \xE8 stata rimossa.`);
      } catch (error) {
        ctx.toast("Eliminazione fallita", error.message, "error");
      }
    }
    let tasksCursor = null, reportsCursor = null;
    return { get loadAntennas() {
      return loadAntennas;
    }, get renderAntennas() {
      return renderAntennas;
    }, get focusAntenna() {
      return focusAntenna;
    }, get openAntennaModal() {
      return openAntennaModal;
    }, get antennaSpecs() {
      return antennaSpecs;
    }, get saveAntenna() {
      return saveAntenna;
    }, get deleteAntenna() {
      return deleteAntenna;
    }, get tasksCursor() {
      return tasksCursor;
    }, get reportsCursor() {
      return reportsCursor;
    }, set tasksCursor(value) {
      tasksCursor = value;
    }, set reportsCursor(value) {
      reportsCursor = value;
    } };
  }

  // frontend/control-room/features/tasks.js
  function createFeature5(ctx) {
    let loadingTasks = false, loadingReports = false, reloadReports = false;
    let lastSearch = null, reloadTasks = false;
    async function loadTasks(append = false) {
      append = append === true;
      if (loadingTasks) {
        if (!append) reloadTasks = true;
        return;
      }
      loadingTasks = true;
      ctx.$("tasksMoreBtn").disabled = true;
      try {
        const search = ctx.$("taskSearch")?.value.trim() || "";
        const depth = append ? 1 : search === lastSearch ? Math.max(1, Math.ceil(ctx.state.tasks.length / 50)) : 1;
        let cursor = append ? ctx.tasksCursor : null, items = [];
        for (let page = 0; page < depth; page++) {
          const params = new ctx.URLSearchParams({ limit: "50" });
          if (search) params.set("q", search);
          if (cursor) params.set("cursor", cursor);
          const response = await ctx.apiFetch(`/api/v1/tasks/page?${params}`), data = response?.data ?? response;
          if (search !== (ctx.$("taskSearch")?.value.trim() || "")) {
            reloadTasks = true;
            return;
          }
          items.push(...Array.isArray(data) ? data : data?.items || []);
          cursor = data?.nextCursor || null;
          if (!cursor) break;
        }
        ctx.state.tasks = [...new Map([...append ? ctx.state.tasks : [], ...items].map((item) => [item.id, item])).values()];
        ctx.tasksCursor = cursor;
        lastSearch = search;
        ctx.$("tasksMoreBtn").hidden = !ctx.tasksCursor;
        ctx.$("tasksPageStatus").textContent = `${ctx.state.tasks.length} incarichi caricati${ctx.tasksCursor ? " \xB7 altri disponibili" : " \xB7 fine elenco"}`;
        renderTaskList();
        populateTaskSelectors();
        ctx.refreshReportTitles();
      } catch (e) {
        if (append || ctx.state.tasks.length) ctx.toast("Caricamento non riuscito", e.message, "error");
        else ctx.$("tasksList").innerHTML = `<div class="empty">Incarichi non disponibili: ${ctx.escapeHtml(e.message)}</div>`;
      } finally {
        loadingTasks = false;
        ctx.$("tasksMoreBtn").disabled = false;
        if (reloadTasks) {
          reloadTasks = false;
          if (ctx.state.token) loadTasks();
        }
      }
    }
    function eligibleOperators() {
      return ctx.state.operators.filter((operator) => {
        const role = (operator.role || "OPERATOR").toUpperCase();
        const status = (operator.status || "").toUpperCase();
        return role === "OPERATOR" && !["IN_ATTESA", "INATTIVO", "DISATTIVATO"].includes(status);
      });
    }
    function populateTaskSelectors() {
      const op2 = ctx.$("taskOperator");
      if (op2) op2.innerHTML = '<option value="">Seleziona operatore</option>' + eligibleOperators().map((o) => `<option value="${ctx.escapeHtml(o.id)}">${ctx.escapeHtml(o.fullName || o.email || o.id)}</option>`).join("");
      const an = ctx.$("taskAntenna");
      if (an) an.innerHTML = '<option value="">Seleziona antenna</option>' + ctx.state.antennas.map((a) => `<option value="${ctx.escapeHtml(a.id)}">${ctx.escapeHtml(a.name || a.id)}</option>`).join("");
    }
    function renderTaskList() {
      if (!ctx.$("tasksList")) return;
      const normalize = (value) => String(value || "").normalize("NFKD").replace(/\p{M}/gu, "").toLowerCase();
      const query = normalize((ctx.$("taskSearch")?.value || "").trim());
      const list = ctx.state.tasks.filter((t) => !query || normalize([t.id, t.title, t.description, t.operatorName, t.operatorId, t.status, t.antennaId].join(" ")).includes(query));
      ctx.$("tasksList").innerHTML = list.length ? list.map((t) => `<div class="task-row"><div><div class="task-title">${ctx.escapeHtml(t.title || t.id)}</div><div class="task-meta">${ctx.escapeHtml(t.operatorName || t.operatorId || t.operator_uid || "Non assegnato")} \xB7 ${ctx.escapeHtml(t.antennaId || "\u2014")} \xB7 ${t.dueAt ? `scade ${ctx.escapeHtml(new Date(t.dueAt).toLocaleString("it-IT"))}` : "senza scadenza"} \xB7 ID ${ctx.escapeHtml(t.id)}</div></div><div class="actions"><span class="badge ${ctx.badgeClass(t.status)}">${ctx.escapeHtml(ctx.formatStatus(t.status))}</span>${!["COMPLETED", "REPORT_SUBMITTED", "APPROVED", "CLOSED", "CANCELLED"].includes(t.status) ? `<button class="mini-btn" data-task-cancel="${ctx.escapeHtml(t.id)}">Annulla incarico</button>` : ""}</div></div>`).join("") : '<div class="empty">Nessun incarico.</div>';
      ctx.$("tasksList").querySelectorAll("[data-task-cancel]").forEach((button) => {
        button.hidden = !["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(ctx.state.authRole || ctx.state.user?.role);
        button.addEventListener("click", async () => {
          const task = ctx.state.tasks.find((t) => t.id === button.dataset.taskCancel);
          if (!task || !await ctx.confirm(`Annullare l\u2019incarico \xAB${task.title || task.id}\xBB? Rester\xE0 visibile nello storico.`)) return;
          button.disabled = true;
          try {
            await ctx.apiFetch(`/api/v1/tasks/${encodeURIComponent(task.id)}/status`, { method: "PATCH", body: JSON.stringify({ status: "CANCELLED" }) });
            await Promise.all([loadTasks(), ctx.loadStats()]);
            window.dispatchEvent(new Event("radiotech:tasks-changed"));
            ctx.toast("Incarico annullato", "L\u2019incarico non blocca pi\xF9 la rimozione dell\u2019operatore.");
          } catch (error) {
            ctx.toast("Annullamento fallito", error.message, "error");
          } finally {
            button.disabled = false;
          }
        });
      });
    }
    async function createTask() {
      const button = ctx.$("createTaskBtn");
      if (button.disabled) return;
      const title = ctx.$("taskTitle").value.trim(), operatorId = ctx.$("taskOperator").value, antennaId = ctx.$("taskAntenna").value;
      if (!title || !operatorId || !antennaId) {
        ctx.toast("Incarico incompleto", "Titolo, operatore e antenna sono obbligatori per il flusso sul campo.", "error");
        return;
      }
      const dueAt = ctx.$("taskDueAt").value ? new Date(ctx.$("taskDueAt").value).toISOString() : null;
      button.disabled = true;
      try {
        await ctx.apiFetch("/api/v1/tasks", { method: "POST", body: JSON.stringify({ title, description: ctx.$("taskDescription").value.trim(), operatorId, antennaId: antennaId || null, dueAt, status: "ASSIGNED", priority: ctx.$("taskPriority").value }) });
        ctx.$("taskTitle").value = "";
        ctx.$("taskDescription").value = "";
        ctx.$("taskDueAt").value = "";
        await Promise.all([loadTasks(), ctx.loadStats()]);
        ctx.toast("Incarico assegnato", "L'operatore pu\xF2 ora visualizzarlo nell'app mobile.");
      } catch (e) {
        ctx.toast("Assegnazione fallita", e.message, "error");
      } finally {
        button.disabled = false;
      }
    }
    return { get loadTasks() {
      return loadTasks;
    }, get eligibleOperators() {
      return eligibleOperators;
    }, get populateTaskSelectors() {
      return populateTaskSelectors;
    }, get renderTaskList() {
      return renderTaskList;
    }, get createTask() {
      return createTask;
    }, get loadingTasks() {
      return loadingTasks;
    }, get loadingReports() {
      return loadingReports;
    }, get reloadReports() {
      return reloadReports;
    }, set loadingTasks(value) {
      loadingTasks = value;
    }, set loadingReports(value) {
      loadingReports = value;
    }, set reloadReports(value) {
      reloadReports = value;
    } };
  }

  // frontend/control-room/features/reports.js
  function createFeature6(ctx) {
    let reportAttachments, lastStatusFilter = null;
    const reportFilters = () => ({ status: ctx.$("reportStatusFilter")?.value || "", q: ctx.$("reportSearch")?.value.trim() || "", operator: ctx.$("reportOperatorFilter")?.value || "", from: ctx.$("reportDateFrom")?.value || "", to: ctx.$("reportDateTo")?.value || "" });
    function attachmentViewer() {
      return reportAttachments ??= window.RadioTechReportAttachments({ apiFetch: ctx.apiFetch, escapeHtml: ctx.escapeHtml });
    }
    function reportDisplayTitle(report) {
      const task = ctx.state.tasks.find((t) => t.id === report.taskId);
      return report.taskTitle || task?.title || "Intervento senza compito assegnato";
    }
    function reportDisplaySummary(report) {
      const operator = ctx.state.operators.find((o) => o.id === report.operatorId || o.firebaseUid === report.operatorFirebaseUid);
      const name = report.operatorName || operator?.fullName || "Operatore non disponibile";
      const instant = new Date(report.submittedAt || report.completedAt || report.createdAt || "");
      const date = Number.isNaN(instant.getTime()) ? "Data non disponibile" : instant.toLocaleDateString("it-IT", { day: "2-digit", month: "2-digit", year: "numeric" });
      return `ID ${report.id} \xB7 ${name} \xB7 ${date}`;
    }
    function refreshReportTitles() {
      for (const report of ctx.state.reports || []) {
        const row = [...ctx.$("reportsList").querySelectorAll("[data-report-id]")].find((n) => n.dataset.reportId === report.id);
        const title = row?.querySelector(".task-title");
        if (title) {
          title.textContent = reportDisplayTitle(report);
          title.title = title.textContent;
        }
        const summary = row?.querySelector(".report-summary");
        if (summary) summary.textContent = reportDisplaySummary(report);
      }
    }
    async function loadReports(append = false) {
      append = append === true;
      if (ctx.loadingReports) {
        if (!append) ctx.reloadReports = true;
        return;
      }
      const epoch = ctx.state.sessionEpoch || 0;
      ctx.loadingReports = true;
      ctx.$("reportsMoreBtn").disabled = true;
      try {
        const filters = reportFilters(), statusFilter = JSON.stringify(filters);
        const depth = append ? 1 : statusFilter === lastStatusFilter ? Math.max(1, Math.ceil((ctx.state.reports || []).length / 50)) : 1;
        let cursor = append ? ctx.reportsCursor : null, items = [], data;
        for (let page = 0; page < depth; page++) {
          const params = new ctx.URLSearchParams({ limit: "50" });
          for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value);
          if (cursor) params.set("cursor", cursor);
          const response = await ctx.apiFetch(`/api/v1/reports/page?${params}`);
          data = response?.data ?? response;
          if (epoch !== (ctx.state.sessionEpoch || 0)) return;
          if (statusFilter !== JSON.stringify(reportFilters())) {
            ctx.reloadReports = true;
            return;
          }
          items.push(...Array.isArray(data) ? data : data?.items || []);
          cursor = data?.nextCursor || null;
          if (!cursor) break;
        }
        const reports = [...new Map([...append ? ctx.state.reports || [] : [], ...items].map((item) => [item.id, item])).values()];
        ctx.state.reports = reports;
        ctx.reportsCursor = cursor;
        lastStatusFilter = statusFilter;
        ctx.$("reportsMoreBtn").hidden = !ctx.reportsCursor;
        ctx.$("reportsPageStatus").textContent = `${reports.length} report caricati${ctx.reportsCursor ? " \xB7 altri disponibili" : " \xB7 fine elenco"}`;
        const list = ctx.$("reportsList");
        const previous = new Map([...list.querySelectorAll("[data-report-id]")].map((row) => [row.dataset.reportId, { collapsed: row.classList.contains("report-collapsed"), details: row.querySelector(".report-details")?.open }]));
        const focused = document.activeElement, focusedRow = focused?.closest("[data-report-id]");
        const focusedAction = focusedRow ? [...focused.attributes].find((attribute) => attribute.name.startsWith("data-report-"))?.name : null;
        const scrollY = window.scrollY;
        attachmentViewer().clear();
        ctx.$("reportsList").innerHTML = reports.length ? reports.map(renderReportRow).join("") : `<div class="empty">${data?.hasMore ? "Nessun report visibile in questa pagina. Carica altri report per continuare." : "Nessun report ricevuto."}</div>`;
        attachmentViewer().mount(ctx.$("reportsList"));
        for (const row of list.querySelectorAll("[data-report-id]")) {
          const saved = previous.get(row.dataset.reportId);
          if (!saved) continue;
          row.classList.toggle("report-collapsed", saved.collapsed);
          const toggle = row.querySelector("[data-report-toggle]");
          toggle?.setAttribute("aria-expanded", String(!saved.collapsed));
          toggle?.setAttribute("aria-label", saved.collapsed ? "Espandi report" : "Comprimi report");
          if (toggle) toggle.textContent = saved.collapsed ? "\u2304" : "\u2303";
          const details = row.querySelector(".report-details");
          if (details) details.open = !!saved.details;
          if (focusedRow?.dataset.reportId === row.dataset.reportId && focusedAction && !document.querySelector("dialog[open]")) row.querySelector(`[${focusedAction}]`)?.focus({ preventScroll: true });
        }
        window.scrollTo({ top: scrollY, behavior: "instant" });
        ctx.applyPermissions();
        ctx.$("reportsList").querySelectorAll("[data-report-approve]").forEach((b) => b.addEventListener("click", () => reviewReport(b.dataset.reportApprove, true)));
        ctx.$("reportsList").querySelectorAll("[data-report-reject]").forEach((b) => b.addEventListener("click", () => reviewReport(b.dataset.reportReject, false)));
        ctx.$("reportsList").querySelectorAll("[data-report-delete]").forEach((b) => b.addEventListener("click", () => deleteReport(b.dataset.reportDelete, b)));
      } catch (e) {
        if (append || ctx.state.reports.length) ctx.toast("Caricamento non riuscito", e.message, "error");
        else ctx.$("reportsList").innerHTML = `<div class="empty">Report non disponibili: ${ctx.escapeHtml(e.message)}</div>`;
      } finally {
        ctx.loadingReports = false;
        ctx.$("reportsMoreBtn").disabled = false;
        if (ctx.reloadReports) {
          ctx.reloadReports = false;
          if (ctx.state.token) loadReports();
        }
      }
    }
    function canManageReports() {
      return ["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(ctx.state.authRole || ctx.state.user?.role);
    }
    function renderReportRow(report) {
      const id = ctx.escapeHtml(report.id || ""), status = report.status || "SUBMITTED";
      const date = report.submittedAt || report.completedAt || report.createdAt;
      return `<article class="task-row report-row report-collapsed" data-report-id="${id}" data-status="${ctx.escapeHtml(status)}" data-operator="${ctx.escapeHtml(report.operatorName || report.operatorId || "")}" data-date="${ctx.escapeHtml((date || "").slice(0, 10))}">
                    <div class="report-main"><div class="report-copy"><h3 class="task-title">${ctx.escapeHtml(reportDisplayTitle(report))}</h3>
                    <div class="task-meta report-summary">${ctx.escapeHtml(reportDisplaySummary(report))}</div>
                    <div class="task-meta report-note">${ctx.escapeHtml(report.operatorNotes || report.description || "Nessuna nota")}</div>
                    </div>
                    <div class="actions"><span class="badge ${ctx.badgeClass(status)}">${ctx.escapeHtml(ctx.formatStatus(status))}</span>
                    ${canManageReports() && ["SUBMITTED", "APPROVAL_PENDING"].includes(status) ? `<button type="button" class="mini-btn" data-report-approve="${id}">${status === "APPROVAL_PENDING" ? "Riprendi approvazione" : "Approva"}</button>${status === "SUBMITTED" ? `<button type="button" class="mini-btn" data-report-reject="${id}">Rifiuta</button>` : ""}` : ""}
                    ${canManageReports() ? `<button type="button" class="btn danger mini-btn" data-report-delete="${id}">Elimina</button>` : ""}
                    <button type="button" class="mini-btn" data-report-toggle aria-expanded="false" aria-label="Espandi report">\u2304</button></div></div>
                    <div class="report-content">${reportDetails(report)}${attachmentViewer().markup(report.attachments)}</div></article>`;
    }
    async function deleteReport(id, trigger) {
      if (!canManageReports() || trigger.disabled) return;
      const report = ctx.state.reports.find((item) => item.id === id);
      if (!report) return;
      const rowButtons = [...trigger.closest("[data-report-id]").querySelectorAll("[data-report-approve], [data-report-reject], [data-report-delete]")];
      if (rowButtons.some((button) => button.disabled)) return;
      const epoch = ctx.state.sessionEpoch || 0;
      const dialog = document.createElement("dialog");
      dialog.className = "report-material-review report-delete-dialog";
      dialog.setAttribute("aria-labelledby", "reportDeleteTitle");
      dialog.setAttribute("aria-describedby", "reportDeleteConsequences");
      dialog.innerHTML = `<form novalidate><h2 id="reportDeleteTitle">Elimina report</h2>
                    <p class="report-delete-name">${ctx.escapeHtml(reportDisplayTitle(report))}</p>
                    <dl><dt>Identificativo</dt><dd>${ctx.escapeHtml(id)}</dd><dt>Stato</dt><dd>${ctx.escapeHtml(ctx.formatStatus(report.status || "SUBMITTED"))}</dd></dl>
                    <p id="reportDeleteConsequences">Il report sar\xE0 rimosso dall\u2019elenco operativo. Documento, allegati, firme e storico delle operazioni rimarranno conservati per la tracciabilit\xE0. L\u2019incarico e i movimenti di magazzino gi\xE0 registrati non saranno modificati.</p>
                    <label class="field" for="reportDeleteReason">Motivo della rimozione<textarea id="reportDeleteReason" name="reason" required minlength="3" maxlength="500" rows="3"></textarea></label>
                    <p role="alert" data-delete-error hidden></p>
                    <div class="actions"><button type="button" class="btn" data-delete-cancel>Annulla</button><button type="submit" class="btn danger">Conferma eliminazione</button></div></form>`;
      let busy = false;
      rowButtons.forEach((button) => {
        button.disabled = true;
      });
      dialog.addEventListener("close", () => {
        dialog.remove();
        rowButtons.forEach((button) => {
          button.disabled = false;
        });
        if (trigger.isConnected) trigger.focus();
        else ctx.$("reportsRefreshBtn")?.focus();
      }, { once: true });
      dialog.addEventListener("cancel", (event) => {
        if (busy) event.preventDefault();
      });
      dialog.querySelector("[data-delete-cancel]").addEventListener("click", () => dialog.close());
      dialog.querySelector("form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (busy) return;
        if (epoch !== (ctx.state.sessionEpoch || 0) || !canManageReports()) {
          dialog.close();
          return;
        }
        const reasonField = dialog.querySelector('[name="reason"]');
        if (reasonField.value.trim().length < 3) {
          const message = dialog.querySelector("[data-delete-error]");
          message.textContent = "Specifica una motivazione di almeno 3 caratteri.";
          message.hidden = false;
          reasonField.setAttribute("aria-invalid", "true");
          reasonField.focus();
          return;
        }
        reasonField.removeAttribute("aria-invalid");
        busy = true;
        dialog.querySelectorAll("button").forEach((button) => {
          button.disabled = true;
        });
        const errorBox = dialog.querySelector("[data-delete-error]");
        errorBox.hidden = true;
        try {
          await ctx.apiFetch(`/api/v1/reports/${encodeURIComponent(id)}`, { method: "DELETE", body: JSON.stringify({ reason: dialog.querySelector('[name="reason"]').value.trim() }) });
          if (epoch !== (ctx.state.sessionEpoch || 0)) {
            dialog.close();
            return;
          }
          ctx.state.reports = ctx.state.reports.filter((item) => item.id !== id);
          trigger.closest("[data-report-id]")?.remove();
          dialog.close();
          await Promise.all([loadReports(), ctx.loadStats()]);
          ctx.toast("Report eliminato", "Rimosso dall\u2019elenco operativo. Documenti e storico conservati.");
        } catch (error) {
          if (epoch !== (ctx.state.sessionEpoch || 0)) {
            dialog.close();
            return;
          }
          errorBox.textContent = `Eliminazione non riuscita: ${error.message}`;
          errorBox.hidden = false;
          ctx.toast("Eliminazione fallita", error.message, "error");
        } finally {
          busy = false;
          dialog.querySelectorAll("button").forEach((button) => {
            button.disabled = false;
          });
        }
      });
      document.body.append(dialog);
      dialog.showModal();
      dialog.querySelector("[data-delete-cancel]").focus();
    }
    function reportDetails(report) {
      const measurements = Object.entries(report.measurements || {}).map(([key, value]) => `<li>${ctx.escapeHtml(key)}: ${ctx.escapeHtml(value)}</li>`).join("");
      const materials = (report.materialsUsed || []).map((item) => `<li>${ctx.escapeHtml(item.name || item.itemId || item.inventoryItemId || item.ricambioId || "Materiale")}: ${ctx.escapeHtml(item.quantity ?? item.quantita ?? "\u2014")}</li>`).join("");
      const checklist = Object.entries(report.checklist || {}).map(([key, value]) => `<li>${ctx.escapeHtml(key)}: ${value === true ? "Verificato" : "Non verificato"}</li>`).join("");
      return `<details class="report-details"><summary>Dettagli intervento</summary><p>Antenna: ${ctx.escapeHtml(ctx.state.antennas.find((a) => a.id === report.antennaId)?.name || report.antennaId || "\u2014")} \xB7 Inviato: ${ctx.escapeHtml(report.submittedAt ? new Date(report.submittedAt).toLocaleString("it-IT") : "Data non disponibile")}</p><p><strong>Lavoro eseguito</strong><br>${ctx.escapeHtml(report.workPerformed || report.description || "Non indicato")}</p><p><strong>Rilievi</strong><br>${ctx.escapeHtml(report.findings || "Nessun rilievo indicato")}</p>${measurements ? `<strong>Misure</strong><ul>${measurements}</ul>` : ""}${materials ? `<strong>Materiali</strong><ul>${materials}</ul>` : ""}${checklist ? `<strong>Lista di controllo</strong><ul>${checklist}</ul>` : ""}${report.reviewNote ? `<p><strong>Nota di revisione</strong><br>${ctx.escapeHtml(report.reviewNote)}</p>` : ""}</details>`;
    }
    async function reviewReport(id, approve) {
      const currentReport = ctx.state.reports.find((item) => item.id === id);
      if (!canManageReports() || !currentReport || !(approve ? ["SUBMITTED", "APPROVAL_PENDING"] : ["SUBMITTED"]).includes(currentReport.status)) return;
      const buttons = [...ctx.$("reportsList").querySelectorAll("[data-report-approve], [data-report-reject], [data-report-delete]")].filter((b) => b.dataset.reportApprove === id || b.dataset.reportReject === id || b.dataset.reportDelete === id);
      if (buttons.some((b) => b.disabled)) return;
      const note = approve ? "Report approvato dalla centrale." : await ctx.prompt("Motivo del rifiuto:") || "";
      if (!approve && !note.trim()) return;
      buttons.forEach((b) => {
        b.disabled = true;
      });
      try {
        const epoch = ctx.state.sessionEpoch || 0, report = ctx.state.reports.find((item) => item.id === id);
        let materialMappings;
        if (approve && report?.materialsUsed?.some((material) => !["inventoryId", "inventoryItemId", "itemId", "ricambioId", "id", "sku"].some((field) => material[field]))) {
          materialMappings = await window.RadioTechReviewMaterials({ apiFetch: ctx.apiFetch, report, escapeHtml: ctx.escapeHtml });
          if (materialMappings === null) return;
        }
        if (epoch !== (ctx.state.sessionEpoch || 0)) return;
        await ctx.apiFetch(`/api/v1/reports/${encodeURIComponent(id)}/${approve ? "approve" : "reject"}`, { method: "POST", body: JSON.stringify({ note, materialMappings }) });
        await Promise.all([loadReports(), ctx.loadTasks(), ctx.loadStats()]);
        window.dispatchEvent(new Event("radiotech:tasks-changed"));
        ctx.toast(approve ? "Report approvato" : "Report rifiutato", approve ? "Materiali aggiornati e incarico completato." : "L\u2019operatore pu\xF2 correggere e inviare nuovamente il rapporto dagli incarichi attivi.");
      } catch (e) {
        ctx.toast("Revisione fallita", e.message, "error");
      } finally {
        buttons.forEach((b) => {
          b.disabled = false;
        });
      }
    }
    return { get attachmentViewer() {
      return attachmentViewer;
    }, get reportDisplayTitle() {
      return reportDisplayTitle;
    }, get reportDisplaySummary() {
      return reportDisplaySummary;
    }, get refreshReportTitles() {
      return refreshReportTitles;
    }, get loadReports() {
      return loadReports;
    }, get canManageReports() {
      return canManageReports;
    }, get renderReportRow() {
      return renderReportRow;
    }, get deleteReport() {
      return deleteReport;
    }, get reportDetails() {
      return reportDetails;
    }, get reviewReport() {
      return reviewReport;
    }, get reportAttachments() {
      return reportAttachments;
    }, set reportAttachments(value) {
      reportAttachments = value;
    } };
  }

  // frontend/control-room/features/operators.js
  function createFeature7(ctx) {
    const pages = createPagedResource(ctx, { path: "/api/v1/dashboard/resources/operators/page", legacy: "/api/v1/operators", stateKey: "operators", tableId: "operatorsTableBody", filters: () => ({ q: ctx.$("operatorSearch")?.value || "", role: ctx.$("operatorRoleFilter")?.value || "" }), onUpdated: () => {
      ctx.renderRecipients();
      renderOperatorTable();
      ctx.populateTaskSelectors();
      ctx.refreshReportTitles();
    } });
    let presenceStop = null, presenceScope = null, presenceRevision = 0, presenceRecords = {}, presenceReady = false;
    function stopPresence() {
      presenceRevision++;
      presenceStop?.();
      presenceStop = null;
      presenceScope = null;
      presenceRecords = {};
      presenceReady = false;
    }
    async function syncPresence() {
      const tenant = ctx.state.user?.tenantId;
      const scope = JSON.stringify([tenant, ctx.state.user?.uid || ctx.state.user?.id]);
      const visible = !document.hidden && ctx.$("view-operators")?.classList.contains("active");
      if (!tenant || !visible) {
        stopPresence();
        return;
      }
      if (presenceScope === scope) return;
      stopPresence();
      presenceScope = scope;
      const revision = presenceRevision;
      try {
        const stop = await window.RadioTechFederation?.watchOperatorPresence(tenant, (records) => {
          if (revision !== presenceRevision) return;
          presenceRecords = records;
          presenceReady = true;
          updatePresenceIndicators();
        }, () => {
          if (revision !== presenceRevision) return;
          presenceReady = false;
          presenceRecords = {};
          updatePresenceIndicators();
        });
        if (revision !== presenceRevision) {
          stop?.();
          return;
        }
        presenceStop = stop || null;
      } catch {
        presenceReady = false;
        renderOperatorTable();
      }
    }
    window.addEventListener("radiotech:view-changed", () => {
      syncPresence();
      updatePresenceIndicators();
    });
    window.addEventListener("radiotech:identity-changed", () => {
      stopPresence();
      updatePresenceIndicators();
    });
    window.addEventListener("radiotech:auth-ended", () => {
      stopPresence();
      updatePresenceIndicators();
    });
    document.addEventListener("visibilitychange", () => {
      syncPresence();
      updatePresenceIndicators();
    });
    function livePresence(operator) {
      const record = presenceRecords[operator.firebaseUid || operator.id];
      return {
        online: presenceReady && Object.values(record?.connections || {}).some((value) => value === true),
        lastSeen: record?.lastSeen,
        known: presenceReady
      };
    }
    function updatePresenceIndicators() {
      for (const row of ctx.$("operatorsTableBody")?.querySelectorAll("[data-operator-record]") || []) {
        const operator = ctx.state.operators.find((item) => item.id === row.dataset.operatorRecord);
        if (!operator) continue;
        const presence = livePresence(operator), indicator = row.querySelector(".operator-presence");
        if (indicator) {
          indicator.classList.toggle("is-online", presence.online);
          indicator.innerHTML = `<span aria-hidden="true"></span>${!presence.known ? "Presenza non disponibile" : presence.online ? "Online \xB7 app aperta" : "Offline"}`;
        }
        const last = row.querySelector(".operator-last-seen");
        const timestamp = presence.lastSeen || operator.lastSeen;
        if (last) last.textContent = timestamp && Number.isFinite(new Date(timestamp).getTime()) ? new Date(timestamp).toLocaleString("it-IT") : "Nessuna presenza registrata";
      }
      const metric = ctx.$("operatorOverview")?.querySelector("[data-presence-metric]");
      if (metric) {
        metric.querySelector(".stat-value").textContent = presenceReady ? ctx.state.operators.filter((operator) => livePresence(operator).online).length : "\u2014";
        metric.querySelector(".stat-foot").textContent = presenceReady ? "App aperta in primo piano" : "Presenza non disponibile";
      }
    }
    async function loadOperators() {
      try {
        await pages();
        await syncPresence();
        ctx.state.operatorLoadError = "";
        ctx.renderRecipients();
        renderOperatorTable();
        ctx.populateTaskSelectors();
        ctx.refreshReportTitles();
      } catch (error) {
        console.warn("Operatori non disponibili:", error.message);
        ctx.state.operatorLoadError = error.message || "Errore di caricamento";
        renderOperatorTable();
      }
    }
    function operatorOnline(lastSeen) {
      if (!lastSeen) return false;
      const timestamp = Date.parse(lastSeen);
      return Number.isFinite(timestamp) && Date.now() - timestamp < 5 * 60 * 1e3;
    }
    function renderOperatorTable() {
      const table = ctx.$("operatorsTableBody");
      if (!table) return;
      preserveTableInteraction(table, renderOperatorTableRows);
    }
    function renderOperatorTableRows() {
      const table = ctx.$("operatorsTableBody");
      if (!table) return;
      const search = (ctx.$("operatorSearch")?.value || "").trim().toLocaleLowerCase("it-IT");
      const roleFilter = ctx.$("operatorRoleFilter")?.value || "";
      const visibleOperators = ctx.state.operators.filter((operator) => {
        const name = `${operator.fullName || ""} ${operator.email || ""}`.toLocaleLowerCase("it-IT");
        const role = (operator.role || "OPERATOR").toUpperCase();
        return (!search || name.includes(search)) && (!roleFilter || role === roleFilter);
      });
      const active = ctx.state.operators.filter((operator) => (operator.status || "").toUpperCase() === "ATTIVO").length;
      const overview = ctx.$("operatorOverview");
      if (overview) {
        const pending = ctx.state.operators.filter((operator) => (operator.status || "").toUpperCase() === "IN_ATTESA").length;
        const online = ctx.state.operators.filter((operator) => livePresence(operator).online).length;
        overview.innerHTML = [["Account caricati", ctx.state.operators.length, "Nell\u2019elenco corrente"], ["Attivi", active, "Fra gli account caricati"], ["Da approvare", pending, "Attendono abilitazione"], ["Online", presenceReady ? online : "\u2014", presenceReady ? "App aperta in primo piano" : "Presenza non disponibile"]].map(([label, value, description]) => `<article class="stat-card" ${label === "Online" ? "data-presence-metric" : ""}><div class="stat-label">${label}</div><div class="stat-value">${ctx.state.operatorLoadError ? "\u2014" : value}</div><div class="stat-foot">${description}</div></article>`).join("");
      }
      ctx.$("operatorsSummary").textContent = ctx.state.operatorLoadError ? "Elenco non disponibile" : `${visibleOperators.length} visibili \xB7 ${ctx.state.operators.length} caricati \xB7 ${active} attivi`;
      if (ctx.state.operatorLoadError) {
        table.innerHTML = `<tr><td colspan="5"><div class="empty">Impossibile caricare gli operatori: ${ctx.escapeHtml(ctx.state.operatorLoadError)}</div></td></tr>`;
        return;
      }
      if (!visibleOperators.length) {
        table.innerHTML = `<tr><td colspan="5"><div class="empty">${ctx.state.operators.length ? "Nessun operatore corrisponde ai filtri." : "Nessun operatore registrato."}</div></td></tr>`;
        return;
      }
      table.innerHTML = visibleOperators.map((operator) => {
        const id = operator.id || "";
        const name = operator.fullName || operator.email || id;
        const presence = livePresence(operator);
        const online = presence.online;
        const lastSeen = presence.lastSeen ? new Date(presence.lastSeen).toISOString() : operator.lastSeen;
        const role = (operator.role || "OPERATOR").toUpperCase();
        const linked = Boolean(operator.firebaseUid);
        const pending = (operator.status || "").toUpperCase() === "IN_ATTESA";
        const roleLocked = !linked || pending;
        const status = (operator.status || "OFFLINE").toUpperCase();
        const statusTone = pending ? "yellow" : status === "ATTIVO" ? "green" : "red";
        const unlimited = operator.qrValidityMode === "UNLIMITED";
        const expiry = unlimited ? null : operator.qrExpiresAt || operator.qrFixedExpiresAt;
        const expiryTime = expiry ? Date.parse(expiry) : NaN;
        const expired = Number.isFinite(expiryTime) && expiryTime < Date.now();
        const badgeDescription = Number.isFinite(expiryTime) ? `${expired ? "Scaduto il" : "Scade il"} ${new Date(expiryTime).toLocaleDateString("it-IT")}` : unlimited ? "Validit\xE0 illimitata" : "Scadenza non indicata";
        const safeId = ctx.escapeHtml(id);
        const safeName = ctx.escapeHtml(name);
        return `<tr role="row" class="operator-record" data-operator-record="${safeId}">
                        <td role="cell" data-label="Operatore"><div class="operator-identity"><span class="operator-monogram" aria-hidden="true">${ctx.escapeHtml(name.trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase())}</span><div><strong>${safeName}</strong><div class="subtle operator-contact">${ctx.escapeHtml(operator.email || "Email non collegata")}</div>${operator.phone ? `<div class="subtle operator-contact">${ctx.escapeHtml(operator.phone)}</div>` : ""}<div class="operator-account-state"><span class="operator-account-dot ${linked ? "is-linked" : ""}" aria-hidden="true"></span>${linked ? "Account collegato" : "Account da collegare"}</div></div></div></td>
                        <td role="cell" data-label="Profilo professionale"><strong>${ctx.escapeHtml(operator.level || "TECNICO")}</strong><div class="subtle">${ctx.escapeHtml(operator.specialization || "Specializzazione non indicata")}</div><div class="operator-shift">${ctx.escapeHtml(operator.shift || "Turno non indicato")}</div></td>
                        <td role="cell" data-label="Ruolo applicativo"><div class="operator-role-control"><fieldset class="operator-role-options" aria-label="Ruolo di ${safeName}"><legend class="sr-only">Ruolo applicativo</legend>
                            <label><input type="radio" name="operator-role-${safeId}" value="OPERATOR" data-operator-role="${safeId}" data-role-option="OPERATOR" data-role-locked="${roleLocked}" ${role === "OPERATOR" ? "checked" : ""} ${roleLocked ? "disabled" : ""}><span>Operatore</span></label>
                            <label><input type="radio" name="operator-role-${safeId}" value="VIEWER" data-operator-role="${safeId}" data-role-option="VIEWER" data-role-locked="${roleLocked}" ${role === "VIEWER" ? "checked" : ""} ${roleLocked ? "disabled" : ""}><span>Sola lettura</span></label>
                        </fieldset><button type="button" class="mini-btn" data-save-role="${safeId}" ${roleLocked ? "disabled" : ""}>Salva ruolo</button></div><div class="operator-role-hint">${roleLocked ? pending ? "Approvazione account necessaria" : "Collegamento account necessario" : role === "VIEWER" ? "Consultazione senza azioni operative" : "Accesso alle funzioni operative"}</div></td>
                        <td role="cell" data-label="Stato e presenza"><span class="badge ${statusTone}">${ctx.escapeHtml(ctx.formatStatus(status))}</span><div class="operator-presence ${online ? "is-online" : ""}"><span aria-hidden="true"></span>${!presence.known ? "Presenza non disponibile" : online ? "Online \xB7 app aperta" : "Offline"}</div><div class="subtle operator-last-seen">${lastSeen && Number.isFinite(Date.parse(lastSeen)) ? ctx.escapeHtml(new Date(lastSeen).toLocaleString("it-IT")) : "Nessuna presenza registrata"}</div></td>
                        <td role="cell" data-label="Badge di accesso"><strong class="operator-badge-state ${expired ? "is-expired" : ""}">${expired ? "QR scaduto" : "Badge QR"}</strong><div class="subtle">${ctx.escapeHtml(badgeDescription)}</div><div class="operator-role-hint">${operator.qrLastUsedAt && Number.isFinite(Date.parse(operator.qrLastUsedAt)) ? `Ultimo uso: ${ctx.escapeHtml(new Date(operator.qrLastUsedAt).toLocaleDateString("it-IT"))}` : "Nessun utilizzo registrato"}</div></td>
                    </tr><tr role="row" class="operator-record-actions"><td role="cell" colspan="5"><div class="operator-actions" role="group" aria-label="Azioni per ${safeName}">
                        <div class="operator-action-group"><span class="operator-action-label">Anagrafica</span><button type="button" class="mini-btn operator-action-main" data-operator-details="${safeId}">Scheda operatore</button>${pending ? `<button type="button" class="mini-btn primary" data-approve="${safeId}">Approva account</button>` : ""}</div>
                        <div class="operator-action-group"><span class="operator-action-label">Badge</span><button type="button" class="mini-btn" data-badge="${safeId}">Visualizza badge</button><button type="button" class="mini-btn" data-regenerate="${safeId}">Rigenera QR</button><button type="button" class="mini-btn" data-validity="${safeId}">Validit\xE0 QR</button></div>
                        <div class="operator-action-group operator-action-security"><span class="operator-action-label">Sicurezza</span><button type="button" class="mini-btn" data-reset-password="${safeId}">Reimposta password</button><button type="button" class="mini-btn danger" data-delete-operator="${safeId}">Elimina</button></div>
                    </div></td></tr>`;
      }).join("");
      ctx.applyPermissions();
      ctx.refreshReportTitles();
      table.querySelectorAll("[data-save-role]").forEach((button) => button.addEventListener("click", () => saveOperatorRole(button.dataset.saveRole)));
      table.querySelectorAll("[data-approve]").forEach((button) => button.addEventListener("click", () => approveOperator(button.dataset.approve)));
      table.querySelectorAll("[data-badge]").forEach((button) => button.addEventListener("click", () => showBadge(button.dataset.badge)));
      table.querySelectorAll("[data-regenerate]").forEach((button) => button.addEventListener("click", () => regenerateQr(button.dataset.regenerate)));
      table.querySelectorAll("[data-operator-details]").forEach((button) => button.addEventListener("click", () => showOperatorDetails(button.dataset.operatorDetails)));
    }
    function showOperatorDetails(id) {
      const operator = ctx.state.operators.find((item) => item.id === id);
      if (!operator) return;
      const dialog = document.createElement("dialog");
      dialog.className = "operation-dialog operator-detail-dialog";
      dialog.setAttribute("aria-labelledby", "operatorDetailTitle");
      const fields = [["ID operatore", operator.id], ["Email", operator.email], ["Telefono", operator.phone], ["Azienda", operator.company], ["Specializzazione", operator.specialization], ["Livello", operator.level], ["Turno", operator.shift], ["Ruolo", ctx.formatStatus(operator.role || "OPERATOR")], ["Stato account", ctx.formatStatus(operator.status || "OFFLINE")], ["Collegamento account", operator.firebaseUid ? "Collegato" : "Da collegare"], ["Ultima presenza", operator.lastSeen && Number.isFinite(Date.parse(operator.lastSeen)) ? new Date(operator.lastSeen).toLocaleString("it-IT") : null]];
      dialog.innerHTML = `<form method="dialog"><div class="operator-detail-heading"><span class="section-kicker">Anagrafica della squadra</span><h2 id="operatorDetailTitle">${ctx.escapeHtml(operator.fullName || operator.email || "Operatore")}</h2></div><dl class="operator-detail-grid">${fields.map(([label, value]) => `<div><dt>${label}</dt><dd>${ctx.escapeHtml(value || "Non indicato")}</dd></div>`).join("")}</dl><div class="toolbar"><button class="btn" type="submit">Chiudi scheda</button></div></form>`;
      dialog.addEventListener("close", () => dialog.remove(), { once: true });
      document.body.append(dialog);
      dialog.showModal();
    }
    async function saveOperatorRole(id) {
      const select = document.querySelector(`[data-operator-role="${ctx.CSS.escape(id)}"]:checked`);
      if (!select) return;
      const operator = ctx.state.operators.find((item) => item.id === id);
      if (!operator) return;
      const role = select.value;
      if (role === "VIEWER" && (operator.role || "OPERATOR").toUpperCase() !== "VIEWER" && !await ctx.confirm("Il ruolo Sola lettura disabilita l'accesso alle funzioni operative dell'app mobile. Continuare?")) return;
      try {
        await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/role`, {
          method: "PATCH",
          body: JSON.stringify({ role })
        });
        await loadOperators();
        ctx.toast("Ruolo aggiornato", "Il nuovo ruolo sar\xE0 attivo dopo il prossimo accesso dell'operatore.");
      } catch (error) {
        ctx.toast("Aggiornamento ruolo non riuscito", error.message, "error");
      }
    }
    async function approveOperator(id) {
      if (!await ctx.confirm("Approvare l'operatore e abilitarne l'accesso?")) return;
      try {
        await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/approve`, { method: "POST" });
        await loadOperators();
        ctx.toast("Operatore approvato", "L'accesso sar\xE0 attivo al prossimo login.");
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
          method: "POST",
          body: JSON.stringify({
            fullName: ctx.$("operatorFullName").value.trim(),
            email: ctx.$("operatorEmail").value.trim() || null,
            phone: ctx.$("operatorPhone").value.trim() || null,
            level: ctx.$("operatorLevel").value,
            shift: ctx.$("operatorShift").value.trim(),
            password: ctx.$("operatorPassword").value.trim() || null
          })
        });
        const data = result?.data ?? result;
        ctx.$("operatorCredentialsResult").innerHTML = `Email: <strong>${ctx.escapeHtml(data.email)}</strong> \xB7 Password: <strong>${ctx.escapeHtml(data.password)}</strong> \xB7 QR: <strong>${ctx.escapeHtml(data.qrCodeToken)}</strong>`;
        await loadOperators();
        ctx.toast("Operatore creato", "Credenziali generate. Conserva la password e stampa il badge.");
        if (data.operator?.id) showBadge(data.operator.id);
      } catch (error) {
        ctx.toast("Creazione operatore fallita", error.message, "error");
      } finally {
        button.disabled = false;
      }
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
      } catch (error) {
        ctx.toast("QR antenna non disponibile", error.message, "error");
      }
    }
    async function showBadge(id) {
      const operator = ctx.state.operators.find((item) => item.id === id);
      if (!operator) return;
      try {
        const result = await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/badge`);
        const data = result?.data ?? result;
        ctx.$("badgeName").textContent = operator.fullName || operator.email || "Operatore RadioTech";
        ctx.$("badgeDetails").textContent = `${operator.level || "TECNICO"} \xB7 ${operator.shift || "GIORNALIERO"}`;
        ctx.$("badgeToken").textContent = data.qrCodeToken;
        ctx.$("badgeQr").src = data.imageDataUrl;
        ctx.$("badgeResetPassword").dataset.resetPassword = id;
        ctx.$("badgeValidity").dataset.validity = id;
        ctx.openModal("badgeModal");
      } catch (error) {
        ctx.toast("Badge non disponibile", error.message, "error");
      }
    }
    async function regenerateQr(id) {
      if (!await ctx.confirm("Rigenerare il QR? Il badge precedente verr\xE0 invalidato.")) return;
      try {
        await ctx.apiFetch(`/api/v1/operators/${encodeURIComponent(id)}/regenerate-qr`, { method: "POST" });
        await loadOperators();
        ctx.toast("QR rigenerato", "Il badge precedente non \xE8 pi\xF9 valido.");
        await showBadge(id);
      } catch (error) {
        ctx.toast("Rigenerazione fallita", error.message, "error");
      }
    }
    async function syncOperatorFromFirebase() {
      const email = ctx.$("syncOperatorEmail").value.trim();
      const fullName = ctx.$("syncOperatorName").value.trim();
      if (!email) {
        ctx.toast("Email obbligatoria", "Inserisci l\u2019email dell\u2019utente Firebase.", "error");
        return;
      }
      const button = ctx.$("syncOperatorBtn");
      button.disabled = true;
      try {
        await ctx.apiFetch("/api/v1/operators/sync-firebase-user", {
          method: "POST",
          body: JSON.stringify({ email, fullName })
        });
        ctx.$("syncOperatorEmail").value = "";
        ctx.$("syncOperatorName").value = "";
        await loadOperators();
        ctx.toast("Operatore sincronizzato", "Ora \xE8 disponibile tra i destinatari delle notifiche.");
      } catch (error) {
        ctx.toast("Sincronizzazione fallita", error.message, "error");
      } finally {
        button.disabled = false;
      }
    }
    return { get loadOperators() {
      return loadOperators;
    }, get operatorOnline() {
      return operatorOnline;
    }, get renderOperatorTable() {
      return renderOperatorTable;
    }, get saveOperatorRole() {
      return saveOperatorRole;
    }, get approveOperator() {
      return approveOperator;
    }, get createOperator() {
      return createOperator;
    }, get showAntennaQr() {
      return showAntennaQr;
    }, get showBadge() {
      return showBadge;
    }, get regenerateQr() {
      return regenerateQr;
    }, get syncOperatorFromFirebase() {
      return syncOperatorFromFirebase;
    } };
  }

  // frontend/control-room/features/notifications.js
  function createFeature8(ctx) {
    function renderRecipients() {
      const recipients = ctx.eligibleOperators();
      if (!recipients.length) {
        ctx.$("recipientBox").innerHTML = `<div class="empty">Nessun dipendente disponibile dal backend.</div>`;
        return;
      }
      ctx.$("recipientBox").innerHTML = recipients.map((op2, i) => {
        const id = op2.id || op2.uid || op2.operatorId;
        const name = op2.name || op2.fullName || op2.displayName || op2.email || `Operatore ${i + 1}`;
        const role = op2.role || op2.ruolo || "Operatore";
        return `
                <label class="recipient">
                    <input type="checkbox" value="${ctx.escapeHtml(id)}">
                    <span class="avatar">${ctx.escapeHtml(ctx.initials(name))}</span>
                    <span class="recipient-info">
                        <strong>${ctx.escapeHtml(name)}</strong>
                        <span>${ctx.escapeHtml(role)}</span>
                    </span>
                </label>`;
      }).join("");
    }
    async function loadNotifications() {
      try {
        const data = await ctx.api("/notifications");
        ctx.state.notifications = Array.isArray(data) ? data : data?.notifications || [];
        renderNotifications();
        updateNotificationCount();
      } catch (error) {
        console.warn("Notifiche non disponibili:", error.message);
        ctx.$("notificationHistory").innerHTML = `<div class="empty">Endpoint notifiche non disponibile: ${ctx.escapeHtml(error.message)}</div>`;
      }
    }
    function renderNotifications() {
      if (!ctx.state.notifications.length) {
        ctx.$("notificationHistory").innerHTML = `<div class="empty">Nessuna comunicazione recente.</div>`;
        return;
      }
      ctx.$("notificationHistory").innerHTML = ctx.state.notifications.slice(0, 20).map((n) => {
        const title = n.title || n.titolo || "Comunicazione";
        const message = n.message || n.messaggio || "";
        const date = n.createdAt || n.timestamp || n.date;
        const isSignal = n.type === "WORKFORCE_SIGNAL";
        const metadata = isSignal ? `<span class="notification-meta">Segnalazione urgente${n.severity ? ` \xB7 ${ctx.escapeHtml(n.severity)}` : ""}${n.assetId ? ` \xB7 Asset ${ctx.escapeHtml(n.assetId)}` : ""}</span><button class="mini-btn notification-action" type="button" data-open-signals>Apri segnalazioni</button>` : "";
        return `
                <div class="notification-item${isSignal ? " notification-critical" : ""}">
                    <div class="notification-icon">${isSignal ? "!" : "\u2726"}</div>
                    <div>
                        <strong>${ctx.escapeHtml(title)}</strong>
                        <p>${ctx.escapeHtml(message)}</p>
                        ${metadata}
                    </div>
                    <span class="subtle">${date ? ctx.escapeHtml(new Date(date).toLocaleDateString("it-IT")) : "\u2014"}</span>
                </div>`;
      }).join("");
      ctx.$("notificationHistory").querySelectorAll("[data-open-signals]").forEach((button) => {
        button.addEventListener("click", () => ctx.switchView("people"));
      });
    }
    function updateNotificationCount() {
      const unread = ctx.state.notifications.filter((n) => n.read === false || n.letto === false).length;
      const el = ctx.$("notificationCount");
      if (unread > 0) {
        el.textContent = unread > 99 ? "99+" : unread;
        el.classList.remove("hidden");
      } else {
        el.classList.add("hidden");
      }
    }
    async function sendNotification() {
      const target = ctx.$("notificationTarget").value;
      const title = ctx.$("notificationTitle").value.trim();
      const message = ctx.$("notificationMessage").value.trim();
      const priority = ctx.$("notificationPriority").value;
      if (!title || !message) {
        ctx.toast("Notifica incompleta", "Inserisci titolo e messaggio.", "error");
        return;
      }
      let recipients = [];
      if (target === "SELECTED") {
        recipients = [...ctx.$("recipientBox").querySelectorAll("input[type=checkbox]:checked")].map((x) => x.value);
        if (!recipients.length) {
          ctx.toast("Nessun destinatario", "Seleziona almeno un dipendente.", "error");
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
      const button = ctx.$("sendNotificationBtn");
      button.disabled = true;
      button.textContent = "Invio...";
      try {
        await ctx.apiFetch("/api/v1/notifications", {
          method: "POST",
          body: JSON.stringify(payload)
        });
        ctx.$("notificationTitle").value = "";
        ctx.$("notificationMessage").value = "";
        ctx.toast("Comunicazione salvata", "Disponibile nello storico. L\u2019accettazione della push non conferma la lettura.");
        await loadNotifications();
      } catch (error) {
        ctx.toast("Invio fallito", error.message, "error");
      } finally {
        button.disabled = false;
        button.textContent = "Invia notifica";
      }
    }
    return { get renderRecipients() {
      return renderRecipients;
    }, get loadNotifications() {
      return loadNotifications;
    }, get renderNotifications() {
      return renderNotifications;
    }, get updateNotificationCount() {
      return updateNotificationCount;
    }, get sendNotification() {
      return sendNotification;
    } };
  }

  // frontend/control-room/features/inventory.js
  function createFeature9(ctx) {
    const pages = createPagedResource(ctx, { path: "/api/v1/dashboard/resources/inventory/page", legacy: "/api/v1/inventory", stateKey: "inventory", tableId: "inventoryTableBody", filters: () => ({ q: ctx.$("inventorySearch")?.value || "", category: ctx.$("inventoryCategoryFilter")?.value || "", stock: ctx.$("inventoryStockFilter")?.value || "" }), onUpdated: renderInventory });
    async function loadInventory() {
      try {
        await pages();
        renderInventory();
      } catch (error) {
        ctx.$("inventoryTableBody").innerHTML = `<tr><td colspan="7"><div class="empty">Magazzino non disponibile: ${ctx.escapeHtml(error.message)}</div></td></tr>`;
        ctx.$("inventoryStatus").textContent = "Errore di sincronizzazione";
      }
    }
    function renderInventory() {
      preserveTableInteraction(ctx.$("inventoryTableBody"), renderInventoryRows);
    }
    function renderInventoryRows() {
      const activeItems = ctx.state.inventory.filter((item) => item.active !== false);
      const totalUnits = activeItems.reduce((sum, item) => sum + inventoryQty(item), 0);
      const lowItems = activeItems.filter(inventoryIsLow);
      const totalValue = activeItems.reduce((sum, item) => sum + inventoryQty(item) * Number(item.unitCost || 0), 0);
      const stats = ctx.$("inventoryStats");
      if (stats) stats.innerHTML = `
                    <div class="stat-card"><div class="stat-label">Articoli attivi</div><div class="stat-value">${activeItems.length}</div><div class="stat-foot">articoli caricati</div></div>
                    <div class="stat-card"><div class="stat-label">Unit\xE0 disponibili</div><div class="stat-value">${ctx.number(totalUnits, 0)}</div><div class="stat-foot">quantit\xE0 negli articoli caricati</div></div>
                    <div class="stat-card"><div class="stat-label">Sotto scorta</div><div class="stat-value">${lowItems.length}</div><div class="stat-foot">fra gli articoli caricati</div></div>
                    <div class="stat-card"><div class="stat-label">Valore stimato</div><div class="stat-value">${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR", maximumFractionDigits: 0 }).format(totalValue)}</div><div class="stat-foot">stima degli articoli caricati</div></div>`;
      const categories = [...new Set([ctx.$("inventoryCategoryFilter")?.value, ...ctx.state.inventory.map((item) => item.category)].filter(Boolean))].sort((a, b) => a.localeCompare(b, "it"));
      const categoryFilter = ctx.$("inventoryCategoryFilter");
      if (categoryFilter) {
        const selected = categoryFilter.value;
        categoryFilter.innerHTML = '<option value="">Tutte le categorie</option>' + categories.map((category2) => `<option value="${ctx.escapeHtml(category2)}">${ctx.escapeHtml(category2)}</option>`).join("");
        categoryFilter.value = categories.includes(selected) ? selected : "";
      }
      ctx.$("inventoryStatus").textContent = `${activeItems.length} articoli attivi \xB7 ${lowItems.length} fra gli articoli caricati`;
      const search = (ctx.$("inventorySearch")?.value || "").trim().toLocaleLowerCase("it-IT");
      const category = categoryFilter?.value || "";
      const stockFilter = ctx.$("inventoryStockFilter")?.value || "ACTIVE";
      const visible = ctx.state.inventory.filter((item) => {
        const active = item.active !== false;
        const haystack = [item.sku, item.name, item.barcode, item.location, item.warehouse, item.aisle, item.rack, item.bin, item.category, item.supplier].join(" ").toLocaleLowerCase("it-IT");
        return (stockFilter === "ALL" || (stockFilter === "ARCHIVED" ? !active : active)) && (stockFilter !== "LOW" || active && inventoryIsLow(item)) && (!category || item.category === category) && (!search || haystack.includes(search));
      }).sort((a, b) => String(a.name || "").localeCompare(String(b.name || ""), "it"));
      const table = ctx.$("inventoryTableBody");
      if (!visible.length) {
        table.innerHTML = `<tr><td colspan="7"><div class="empty">${ctx.state.inventory.length ? "Nessun articolo corrisponde ai filtri." : "Il magazzino \xE8 vuoto. Aggiungi il primo articolo."}</div></td></tr>`;
        return;
      }
      table.innerHTML = visible.map((item) => {
        const low = inventoryIsLow(item);
        const active = item.active !== false;
        const qty = inventoryQty(item);
        const value = qty * Number(item.unitCost || 0);
        const stockLabel = low ? "RIORDINO" : "DISPONIBILE";
        return `<tr>
                        <td><strong>${ctx.escapeHtml(item.name || "Articolo")}</strong><div class="subtle">${ctx.escapeHtml(item.sku || item.id || "\u2014")}${item.barcode ? ` \xB7 ${ctx.escapeHtml(item.barcode)}` : ""}</div></td>
                        <td>${ctx.escapeHtml(item.category || "Generale")}<div class="subtle">${ctx.escapeHtml(item.unit || "pz")}</div></td>
                        <td><strong>${ctx.number(qty, 0)}</strong> ${ctx.escapeHtml(item.unit || "pz")}<div class="subtle">Soglia ${ctx.number(Number(item.minimumThreshold || item.threshold || item.soglia || 0), 0)} \xB7 Riordino ${ctx.number(Number(item.reorderQuantity || 0), 0)}</div></td>
                        <td>${ctx.escapeHtml([item.warehouse, item.aisle, item.rack, item.bin, item.location].filter(Boolean).join(" \xB7 ") || "\u2014")}${item.supplier ? `<div class="subtle">${ctx.escapeHtml(item.supplier)}</div>` : ""}</td>
                        <td>${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(value)}<div class="subtle">${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(item.unitCost || 0))} / ${ctx.escapeHtml(item.unit || "pz")}</div></td>
                        <td><span class="badge ${!active ? "red" : low ? "yellow" : "green"}">${!active ? "ARCHIVIATO" : stockLabel}</span></td>
                        <td><div class="actions">${active ? `<button class="mini-btn" data-inventory-edit="${ctx.escapeHtml(item.id)}">Modifica</button><button class="mini-btn" data-inventory-archive="${ctx.escapeHtml(item.id)}">Archivia</button><button class="mini-btn" data-inventory-move="${ctx.escapeHtml(item.id)}">Movimenta</button>` : `<button class="mini-btn" data-inventory-restore="${ctx.escapeHtml(item.id)}">Ripristina</button>`}</div></td>
                    </tr>`;
      }).join("");
      ctx.applyPermissions();
      table.querySelectorAll("[data-inventory-move]").forEach((button) => button.addEventListener("click", () => openInventoryMovement(button.dataset.inventoryMove)));
      table.querySelectorAll("[data-inventory-edit]").forEach((button) => button.addEventListener("click", () => openInventoryItem(button.dataset.inventoryEdit)));
      table.querySelectorAll("[data-inventory-archive]").forEach((button) => button.addEventListener("click", () => archiveInventoryItem(button.dataset.inventoryArchive)));
      table.querySelectorAll("[data-inventory-restore]").forEach((button) => button.addEventListener("click", () => restoreInventoryItem(button.dataset.inventoryRestore)));
    }
    function inventoryQty(item) {
      return Math.max(0, Number(item.quantity ?? item.quantitaDisponibile ?? item.stock ?? 0));
    }
    function inventoryIsLow(item) {
      const threshold = Number(item.minimumThreshold ?? item.threshold ?? item.soglia ?? item.sogliaMinima ?? 0);
      return item.active !== false && threshold >= 0 && inventoryQty(item) <= threshold;
    }
    function openInventoryItem(id = "") {
      const item = id ? ctx.state.inventory.find((row) => row.id === id) : null;
      ctx.$("inventoryItemForm").reset();
      ctx.$("inventoryItemId").value = item?.id || "";
      ctx.$("inventorySku").value = item?.sku || "";
      ctx.$("inventoryName").value = item?.name || "";
      ctx.$("inventoryCategory").value = item?.category || "";
      ctx.$("inventoryUnit").value = item?.unit || "pz";
      ctx.$("inventoryBarcode").value = item?.barcode || "";
      ctx.$("inventoryLocation").value = item?.location || "";
      for (const [id2, field] of [["Warehouse", "warehouse"], ["Aisle", "aisle"], ["Rack", "rack"], ["Bin", "bin"]]) ctx.$("inventory" + id2).value = item?.[field] || "";
      ctx.$("inventorySupplier").value = item?.supplier || "";
      ctx.$("inventoryUnitCost").value = Number(item?.unitCost || 0);
      ctx.$("inventoryMinimum").value = Number(item?.minimumThreshold || item?.threshold || 0);
      ctx.$("inventoryReorder").value = Number(item?.reorderQuantity || item?.minimumThreshold || 1);
      ctx.$("inventoryInitialQuantity").value = 0;
      ctx.$("inventoryDescription").value = item?.description || "";
      ctx.$("inventoryItemModalTitle").textContent = item ? `Modifica ${item.name}` : "Nuovo articolo";
      ctx.$("inventoryInitialQuantityField").classList.toggle("hidden", Boolean(item));
      ctx.$("inventoryItemFormStatus").textContent = item ? `Disponibilit\xE0 attuale: ${inventoryQty(item)} ${item.unit || "pz"}. Usa \u201CMovimenta\u201D per modificare le scorte.` : "La quantit\xE0 iniziale sar\xE0 registrata come carico nel registro movimenti.";
      ctx.openModal("inventoryItemModal");
    }
    async function saveInventoryItem(event) {
      event.preventDefault();
      const id = ctx.$("inventoryItemId").value;
      const payload = {
        sku: ctx.$("inventorySku").value.trim(),
        name: ctx.$("inventoryName").value.trim(),
        category: ctx.$("inventoryCategory").value.trim(),
        unit: ctx.$("inventoryUnit").value.trim() || "pz",
        barcode: ctx.$("inventoryBarcode").value.trim(),
        location: ctx.$("inventoryLocation").value.trim(),
        supplier: ctx.$("inventorySupplier").value.trim(),
        warehouse: ctx.$("inventoryWarehouse").value.trim(),
        aisle: ctx.$("inventoryAisle").value.trim(),
        rack: ctx.$("inventoryRack").value.trim(),
        bin: ctx.$("inventoryBin").value.trim(),
        unitCost: Number(ctx.$("inventoryUnitCost").value || 0),
        minimumThreshold: Number(ctx.$("inventoryMinimum").value || 0),
        reorderQuantity: Number(ctx.$("inventoryReorder").value || 0),
        description: ctx.$("inventoryDescription").value.trim()
      };
      if (!id) payload.quantity = Number(ctx.$("inventoryInitialQuantity").value || 0);
      const button = ctx.$("saveInventoryItem");
      button.disabled = true;
      try {
        await ctx.apiFetch(id ? `/api/v1/inventory/${encodeURIComponent(id)}` : "/api/v1/inventory", {
          method: id ? "PUT" : "POST",
          body: JSON.stringify(payload)
        });
        ctx.closeModal("inventoryItemModal");
        await loadInventory();
        ctx.toast(id ? "Articolo aggiornato" : "Articolo creato", "Magazzino sincronizzato con il backend.");
      } catch (error) {
        ctx.toast("Salvataggio non riuscito", error.message, "error");
      } finally {
        button.disabled = false;
      }
    }
    function openInventoryMovement(id) {
      const item = ctx.state.inventory.find((row) => row.id === id);
      if (!item) return;
      ctx.$("inventoryMovementForm").reset();
      ctx.$("inventoryMovementItemId").value = id;
      ctx.$("inventoryMovementItemName").textContent = `${item.name} \xB7 ${item.sku} \xB7 giacenza ${inventoryQty(item)} ${item.unit || "pz"}`;
      ctx.$("inventoryMovementQuantity").value = "";
      updateInventoryMovementHint();
      ctx.openModal("inventoryMovementModal");
    }
    function updateInventoryMovementHint() {
      const type = ctx.$("inventoryMovementType").value;
      ctx.$("inventoryMovementQuantityLabel").textContent = type === "ADJUSTMENT" ? "Giacenza reale conteggiata" : "Quantit\xE0 da movimentare";
      ctx.$("inventoryMovementQuantity").min = type === "ADJUSTMENT" ? "0" : "1";
    }
    async function saveInventoryMovement(event) {
      event.preventDefault();
      const button = event.submitter || event.target.querySelector('[type="submit"]');
      if (button.disabled) return;
      button.disabled = true;
      const id = ctx.$("inventoryMovementItemId").value;
      try {
        await ctx.apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}/movements`, {
          method: "POST",
          body: JSON.stringify({
            type: ctx.$("inventoryMovementType").value,
            quantity: Number(ctx.$("inventoryMovementQuantity").value),
            lotCode: ctx.$("inventoryMovementLot").value.trim(),
            lotExpiresOn: ctx.$("inventoryMovementLotExpiry").value,
            reference: ctx.$("inventoryMovementReference").value.trim(),
            note: ctx.$("inventoryMovementNote").value.trim()
          })
        });
        ctx.closeModal("inventoryMovementModal");
        await loadInventory();
        ctx.toast("Movimento registrato", "Giacenza e registro sono stati aggiornati.");
      } catch (error) {
        ctx.toast("Movimento non riuscito", error.message, "error");
      } finally {
        button.disabled = false;
      }
    }
    async function loadInventoryMovements() {
      const body = ctx.$("inventoryMovementsBody");
      body.innerHTML = '<tr><td colspan="6"><div class="empty"><span class="spinner"></span> Caricamento movimenti...</div></td></tr>';
      try {
        const data = await ctx.apiFetch("/api/v1/inventory/movements?limit=250");
        ctx.state.inventoryMovements = Array.isArray(data) ? data : data?.data || [];
        renderInventoryMovements();
      } catch (error) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty">Registro non disponibile: ${ctx.escapeHtml(error.message)}</div></td></tr>`;
      }
    }
    function renderInventoryMovements() {
      const body = ctx.$("inventoryMovementsBody");
      if (!ctx.state.inventoryMovements.length) {
        body.innerHTML = '<tr><td colspan="6"><div class="empty">Nessun movimento registrato.</div></td></tr>';
        return;
      }
      const labels2 = { RECEIPT: "CARICO", ISSUE: "SCARICO", ADJUSTMENT: "RETTIFICA", CONSUMPTION: "CONSUMO TASK" };
      body.innerHTML = ctx.state.inventoryMovements.map((movement) => {
        const quantity = Number(movement.quantity || 0);
        const signed = ["ISSUE", "CONSUMPTION"].includes(movement.type) ? -Math.abs(quantity) : quantity;
        const when = movement.createdAt ? new Date(movement.createdAt).toLocaleString("it-IT") : "\u2014";
        return `<tr><td>${ctx.escapeHtml(when)}</td><td><strong>${ctx.escapeHtml(movement.name || "Articolo")}</strong><div class="subtle">${ctx.escapeHtml(movement.sku || "")}</div></td><td>${ctx.escapeHtml(labels2[movement.type] || movement.type || "\u2014")}</td><td class="${signed < 0 ? "text-danger" : ""}">${signed > 0 ? "+" : ""}${ctx.number(signed, 0)}</td><td>${movement.previousQuantity == null ? "\u2014" : `${ctx.number(Number(movement.previousQuantity), 0)} \u2192 ${ctx.number(Number(movement.resultingQuantity), 0)}`}</td><td>${ctx.escapeHtml([movement.reference, movement.note, movement.lotCode ? "Lotto " + movement.lotCode : "", movement.warehouse, ...(movement.lotAllocations || []).map((l) => (l.lotCode || "Senza lotto") + ": " + l.quantity)].filter(Boolean).join(" \xB7 ") || "\u2014")}</td></tr>`;
      }).join("");
    }
    async function archiveInventoryItem(id) {
      const item = ctx.state.inventory.find((row) => row.id === id);
      if (!item || !await ctx.confirm(`Archiviare \u201C${item.name}\u201D? Lo storico resta consultabile e l'articolo sar\xE0 ripristinabile.`)) return;
      try {
        await ctx.apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}`, { method: "DELETE" });
        await loadInventory();
        ctx.toast("Articolo archiviato", "L'articolo \xE8 stato mantenuto nello storico.");
      } catch (error) {
        ctx.toast("Archiviazione non riuscita", error.message, "error");
      }
    }
    async function restoreInventoryItem(id) {
      try {
        await ctx.apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}/restore`, { method: "POST" });
        await loadInventory();
        ctx.toast("Articolo ripristinato", "L'articolo \xE8 di nuovo disponibile.");
      } catch (error) {
        ctx.toast("Ripristino non riuscito", error.message, "error");
      }
    }
    function exportInventoryCsv() {
      const columns = ["sku", "name", "category", "quantity", "unit", "minimumThreshold", "reorderQuantity", "location", "supplier", "barcode", "unitCost", "active"];
      const quote = (value) => {
        let text = String(value ?? "");
        if (/^[\s]*[=+@-]/.test(text)) text = "'" + text;
        return `"${text.replaceAll('"', '""')}"`;
      };
      const rows = [columns.join(";"), ...ctx.state.inventory.map((item) => columns.map((column) => quote(column === "quantity" ? inventoryQty(item) : column === "minimumThreshold" ? item.minimumThreshold ?? item.threshold ?? 0 : item[column])).join(";"))];
      const blob = new Blob(["\uFEFF" + rows.join("\r\n")], { type: "text/csv;charset=utf-8" });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `radiotech-magazzino-${(/* @__PURE__ */ new Date()).toISOString().slice(0, 10)}.csv`;
      link.click();
      URL.revokeObjectURL(link.href);
    }
    return { get loadInventory() {
      return loadInventory;
    }, get renderInventory() {
      return renderInventory;
    }, get inventoryQty() {
      return inventoryQty;
    }, get inventoryIsLow() {
      return inventoryIsLow;
    }, get openInventoryItem() {
      return openInventoryItem;
    }, get saveInventoryItem() {
      return saveInventoryItem;
    }, get openInventoryMovement() {
      return openInventoryMovement;
    }, get updateInventoryMovementHint() {
      return updateInventoryMovementHint;
    }, get saveInventoryMovement() {
      return saveInventoryMovement;
    }, get loadInventoryMovements() {
      return loadInventoryMovements;
    }, get renderInventoryMovements() {
      return renderInventoryMovements;
    }, get archiveInventoryItem() {
      return archiveInventoryItem;
    }, get restoreInventoryItem() {
      return restoreInventoryItem;
    }, get exportInventoryCsv() {
      return exportInventoryCsv;
    } };
  }

  // frontend/control-room/features/system.js
  function createFeature10(ctx) {
    async function loadOperationalHealth() {
      loadPrivateRelease();
      ctx.$("operationsHealthRefresh").disabled = true;
      try {
        const response = await ctx.apiFetch("/api/v1/operations/health");
        const health = response?.data ?? response, usage = health.firestore || {}, files = health.attachments || {};
        const number = (value) => value == null ? "Non disponibile" : Number(value).toLocaleString("it-IT");
        const alarms = (health.alarms || []).map((alarm) => `<li>${ctx.escapeHtml(alarm.message)}</li>`).join("");
        const backupLabels = { OK: "Verificato", NOT_CONFIGURED: "Da configurare", STALE: "Backup scaduto", RESTORE_REQUIRED: "Ripristino da verificare", INVALID: "Stato da verificare" };
        ctx.$("operationsHealth").innerHTML = `<p>Allegati: <strong>${number(files.count)}</strong> \xB7 ${(Number(files.bytes || 0) / 1048576).toFixed(2)} MiB \xB7 ${ctx.escapeHtml(health.backend || "")}</p><p>Report da revisionare: ${number(health.pendingReports)} \xB7 Backup: <strong>${ctx.escapeHtml(backupLabels[health.backup?.state] || "Non disponibile")}</strong></p><p>Letture di oggi: ${number(usage.reads)} \xB7 Scritture: ${number(usage.writes)} \xB7 Eliminazioni: ${number(usage.deletes)}</p><p>Spazio database e indici: ${usage.storageBytes == null ? "Non disponibile" : (Number(usage.storageBytes) / 1048576).toFixed(2) + " MiB"}</p><p class="subtle">${ctx.escapeHtml(usage.note || "")} \xB7 Giornata quota: ${ctx.escapeHtml(usage.quotaDayTimezone || "")}</p>${alarms ? `<ul role="status">${alarms}</ul>` : "<p>Nessun avviso dalle verifiche disponibili.</p>"}`;
      } catch (error) {
        ctx.$("operationsHealth").textContent = `Monitoraggio non disponibile: ${error.message}`;
      } finally {
        ctx.$("operationsHealthRefresh").disabled = false;
      }
    }
    async function loadPrivateRelease() {
      const panel = ctx.$("privateDistribution");
      if (!panel) return;
      try {
        const release = await ctx.apiFetch("/api/v1/distribution/latest.json");
        if (!release.available) {
          panel.textContent = "Nessuna release pubblicata per questa azienda.";
          return;
        }
        panel.innerHTML = `<p>RadioTech ${ctx.escapeHtml(release.versionName)} (${ctx.escapeHtml(release.versionCode)}) \xB7 ${(Number(release.size) / 1048576).toFixed(1)} MiB</p><p>APK firmato. Dopo il download Android verifica il pacchetto prima dell\u2019installazione.</p><button type="button" class="btn" data-private-apk>Scarica APK</button><p role="status" data-private-status></p>`;
        const epoch = ctx.state.sessionEpoch || 0;
        panel.querySelector("[data-private-apk]").onclick = async (event) => {
          const button = event.currentTarget, status = panel.querySelector("[data-private-status]");
          button.disabled = true;
          status.textContent = "Download e verifica in corso\u2026";
          try {
            const file = await ctx.apiFetch(`/api/v1/distribution/${encodeURIComponent(release.filename)}`, { responseType: "bytes", maxResponseBytes: 150 * 1024 * 1024, timeoutMs: 18e4 });
            if (epoch !== (ctx.state.sessionEpoch || 0)) return;
            const hash = [...new Uint8Array(await crypto.subtle.digest("SHA-256", file.bytes))].map((byte) => byte.toString(16).padStart(2, "0")).join("");
            if (epoch !== (ctx.state.sessionEpoch || 0)) return;
            if (hash !== release.sha256) throw new Error("Integrit\xE0 APK non valida.");
            const url = URL.createObjectURL(new Blob([file.bytes], { type: "application/vnd.android.package-archive" })), link = document.createElement("a");
            link.href = url;
            link.download = release.filename;
            link.click();
            setTimeout(() => URL.revokeObjectURL(url), 3e4);
            status.textContent = "APK verificato e scaricato.";
          } catch (error) {
            status.textContent = `Download non riuscito: ${error.message}`;
          } finally {
            button.disabled = false;
          }
        };
      } catch (error) {
        panel.textContent = `Canale aggiornamenti non disponibile: ${error.message}`;
      }
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
        const entries = await ctx.api("/audit?limit=30");
        const list = Array.isArray(entries) ? entries : [];
        ctx.$("auditList").innerHTML = list.length ? list.map((entry) => `<div class="task-row"><div><div class="task-title">${ctx.escapeHtml(ctx.formatStatus(entry.action || "EVENTO"))}</div><div class="task-meta">${ctx.escapeHtml(ctx.formatStatus(entry.resource || ""))} ${ctx.escapeHtml(entry.resourceId || "")} \xB7 ${ctx.escapeHtml(entry.actor || "Sistema")}</div></div><span class="subtle">${entry.timestamp ? ctx.escapeHtml(new Date(entry.timestamp).toLocaleString("it-IT")) : "\u2014"}</span></div>`).join("") : '<div class="empty">Nessun evento registrato.</div>';
      } catch (error) {
        ctx.$("auditList").innerHTML = `<div class="empty">Audit non disponibile: ${ctx.escapeHtml(error.message)}</div>`;
      }
    }
    return { get loadOperationalHealth() {
      return loadOperationalHealth;
    }, get checkSystem() {
      return checkSystem;
    }, get loadAudit() {
      return loadAudit;
    } };
  }

  // frontend/control-room/main.js
  (() => {
    "use strict";
    function debounce(callback, delay) {
      let timer;
      return () => {
        clearTimeout(timer);
        timer = setTimeout(callback, delay);
      };
    }
    const featureContext = {
      get $() {
        return $;
      },
      get CONFIG() {
        return CONFIG;
      },
      get CSS() {
        return CSS;
      },
      get URLSearchParams() {
        return URLSearchParams;
      },
      get antennaSpecs() {
        return antennaSpecs;
      },
      get api() {
        return api;
      },
      get apiFetch() {
        return apiFetch;
      },
      get applyPermissions() {
        return applyPermissions;
      },
      get badgeClass() {
        return badgeClass;
      },
      get canManageReports() {
        return canManageReports;
      },
      get closeModal() {
        return closeModal;
      },
      get confirm() {
        return window.RadioTechDialogs.confirm;
      },
      get eligibleOperators() {
        return eligibleOperators;
      },
      get escapeHtml() {
        return escapeHtml;
      },
      get formatStatus() {
        return formatStatus;
      },
      get initMap() {
        return initMap;
      },
      get initials() {
        return initials;
      },
      get loadStats() {
        return loadStats;
      },
      get loadTasks() {
        return loadTasks;
      },
      get loadingReports() {
        return feature_tasks.loadingReports;
      },
      set loadingReports(value) {
        feature_tasks.loadingReports = value;
      },
      get loadingTasks() {
        return feature_tasks.loadingTasks;
      },
      set loadingTasks(value) {
        feature_tasks.loadingTasks = value;
      },
      get number() {
        return number;
      },
      get numberOrNull() {
        return numberOrNull;
      },
      get openModal() {
        return openModal;
      },
      get populateTaskSelectors() {
        return populateTaskSelectors;
      },
      get prompt() {
        return window.RadioTechDialogs.prompt;
      },
      get refreshReportTitles() {
        return refreshReportTitles;
      },
      get reloadReports() {
        return feature_tasks.reloadReports;
      },
      set reloadReports(value) {
        feature_tasks.reloadReports = value;
      },
      get renderRecipients() {
        return renderRecipients;
      },
      get reportAttachments() {
        return feature_reports.reportAttachments;
      },
      set reportAttachments(value) {
        feature_reports.reportAttachments = value;
      },
      get reportsCursor() {
        return feature_assets.reportsCursor;
      },
      set reportsCursor(value) {
        feature_assets.reportsCursor = value;
      },
      get setBackendState() {
        return setBackendState;
      },
      get setText() {
        return setText;
      },
      get showAntennaQr() {
        return showAntennaQr;
      },
      get signatureWorkspace() {
        return signatureWorkspace;
      },
      get state() {
        return state;
      },
      get switchView() {
        return switchView;
      },
      get tasksCursor() {
        return feature_assets.tasksCursor;
      },
      set tasksCursor(value) {
        feature_assets.tasksCursor = value;
      },
      get toast() {
        return toast;
      },
      get updateMap() {
        return updateMap;
      }
    };
    const CONFIG = Object.freeze({
      API: "/api/v1/dashboard",
      AUTH_LOGIN: "/api/v1/auth/login",
      AUTH_REFRESH: "/api/v1/auth/refresh",
      AUTH_VERIFY: "/api/v1/auth/verify",
      REQUEST_TIMEOUT: 12e3,
      REFRESH_INTERVAL: 6e4,
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
      markerByAntennaId: /* @__PURE__ */ new Map(),
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
    const $ = (id) => document.getElementById(id);
    function escapeHtml(value) {
      return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
    }
    function number(value, digits = 1) {
      const n = Number(value);
      if (!Number.isFinite(n)) return "\u2014";
      return n.toLocaleString("it-IT", {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits
      });
    }
    function initials(name) {
      const parts = String(name || "Capo RadioTech").trim().split(/\s+/).filter(Boolean);
      return parts.slice(0, 2).map((x) => x[0].toUpperCase()).join("") || "C";
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
      if (el) el.textContent = value ?? "\u2014";
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
          try {
            storage.removeItem(key);
          } catch (_) {
          }
        }
      }
    }
    async function getStoredSession() {
      purgeLegacyTokens();
      const epoch = state.sessionEpoch || 0;
      try {
        const result = await window.RadioTechFederation?.restoreSession();
        if (!result || epoch !== (state.sessionEpoch || 0)) return false;
        if (result.role === "CUSTOMER") {
          window.location.assign("/portal");
          return false;
        }
        saveSession(result.token, { ...result.user, role: result.role }, localStorage.getItem(CONFIG.STORAGE_REMEMBER) === "true");
        return true;
      } catch (_) {
        return false;
      }
    }
    function saveSession(token, user, remember, refreshToken = null) {
      const identityOf = (account) => JSON.stringify([account?.tenantId, account?.uid || account?.id || account?.email]);
      if (state.user && identityOf(state.user) !== identityOf(user)) {
        state.sessionEpoch = (state.sessionEpoch || 0) + 1;
        window.dispatchEvent(new Event("radiotech:identity-changed"));
        featureContext.reportAttachments?.clear();
        document.querySelectorAll(".report-delete-dialog, .report-material-review").forEach((dialog) => dialog.close());
      }
      state.token = token;
      state.refreshToken = refreshToken;
      state.user = user || null;
      state.authRole = user?.role || null;
      chiefAccess?.syncRole();
      signatureWorkspace?.syncIdentity();
      window.dispatchEvent(new Event("radiotech:session-ready"));
      purgeLegacyTokens();
      try {
        localStorage.setItem(CONFIG.STORAGE_REMEMBER, String(remember));
      } catch (_) {
      }
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
      window.dispatchEvent(new Event("radiotech:identity-changed"));
      document.querySelectorAll(".report-delete-dialog, .report-material-review").forEach((dialog) => dialog.close());
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
      try {
        localStorage.removeItem(CONFIG.STORAGE_REMEMBER);
      } catch (_) {
      }
    }
    function getSessionToken() {
      return state.token;
    }
    let sessionRefresh = null;
    function refreshSession() {
      if (!sessionRefresh) sessionRefresh = performSessionRefresh().finally(() => {
        sessionRefresh = null;
      });
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
        } catch (_) {
          return false;
        }
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
      const privatePackage = url.startsWith("/api/v1/distribution/");
      const dossierFile = /^\/api\/v1\/antennas\/[A-Za-z0-9_-]+\/dossier\/files\/[a-f0-9]{64}$/.test(url);
      const responseLimit = privatePackage && maxResponseBytes === 150 * 1024 * 1024 ? maxResponseBytes : dossierFile && maxResponseBytes === 15e6 ? 15e6 : 1e7;
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), Math.min(privatePackage ? 18e4 : 6e4, timeoutMs));
      const token = getSessionToken();
      const epoch = state.sessionEpoch || 0;
      const headers = {
        "Accept": "application/json",
        ...options.body ? { "Content-Type": "application/json" } : {},
        ...options.headers || {}
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
        if (response.ok && responseType === "bytes" && !response.headers.get("Content-Type")?.includes("json")) {
          if (Number(response.headers.get("Content-Length")) > responseLimit) throw new Error("File troppo grande.");
          const reader = response.body.getReader(), chunks = [];
          let length = 0;
          while (true) {
            const part = await reader.read();
            if (part.done) break;
            length += part.value.length;
            if (length > responseLimit) {
              await reader.cancel();
              throw new Error("File troppo grande.");
            }
            chunks.push(part.value);
          }
          if (epoch !== (state.sessionEpoch || 0)) throw new Error("Sessione cambiata.");
          const bytes = new Uint8Array(length);
          let offset = 0;
          for (const chunk of chunks) {
            bytes.set(chunk, offset);
            offset += chunk.length;
          }
          const disposition = response.headers.get("Content-Disposition") || "";
          const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
          const name = encoded ? decodeURIComponent(encoded) : /filename="([^"]+)"/i.exec(disposition)?.[1] || "allegato";
          return { name, bytes };
        }
        let body = null;
        const text = await response.text();
        if (text) {
          try {
            body = JSON.parse(text);
          } catch (_) {
            body = text;
          }
        }
        if (response.status === 401) {
          if (!_retried && await refreshSession()) return apiFetch(url, { ...options, _retried: true });
          clearSession();
          showLogin("Sessione scaduta. Effettua nuovamente l'accesso.");
          throw new Error("Sessione non autorizzata o scaduta.");
        }
        if (!response.ok) {
          const message = (body?.message || `HTTP ${response.status}`) + (response.headers.get("X-Request-Id") ? ` \xB7 Rif. ${response.headers.get("X-Request-Id")}` : "");
          const error = new Error(message);
          error.status = response.status;
          error.fieldErrors = body?.fieldErrors || {};
          error.retryable = body?.retryable === true;
          throw error;
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
        const federationReady = window.RadioTechFederation && await window.RadioTechFederation.initialize();
        const result = federationReady ? new Response(JSON.stringify(await window.RadioTechFederation.login(null, email, password, remember)), { status: 200, headers: { "Content-Type": "application/json" } }) : await fetch(CONFIG.AUTH_LOGIN, {
          method: "POST",
          headers: { "Content-Type": "application/json", "Accept": "application/json" },
          body: JSON.stringify({ email, password })
        });
        const body = await result.json().catch(() => null);
        if (!result.ok) {
          const fallback = result.status === 403 ? "Il server blocca questo indirizzo web. Verifica la configurazione del link HTTPS." : result.status >= 500 ? "Il server non \xE8 disponibile. Verifica che il backend e il tunnel siano attivi." : result.status === 401 ? "Credenziali non valide." : `Accesso non riuscito (HTTP ${result.status}).`;
          throw new Error(body?.message || fallback);
        }
        const token = body?.token || body?.idToken || body?.firebaseToken || body?.data?.token;
        if (!token) {
          throw new Error("Il backend non ha restituito un Firebase ID token.");
        }
        const user = { ...body?.user || body?.data?.user || { email }, role: body?.role || body?.data?.role || body?.user?.role };
        if (user.role === "CUSTOMER") {
          window.location.assign("/portal");
          return;
        }
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
    window.addEventListener("radiotech:sso", async (event) => {
      const body = event.detail;
      if (body.role === "CUSTOMER") {
        window.location.assign("/portal");
        return;
      }
      saveSession(body.token, { ...body.user, role: body.role }, false, body.refreshToken);
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
    const feature_profile = createFeature(featureContext);
    function applyPermissions(...args) {
      return feature_profile.applyPermissions(...args);
    }
    function hydrateUser(...args) {
      return feature_profile.hydrateUser(...args);
    }
    function persistUser(...args) {
      return feature_profile.persistUser(...args);
    }
    function loadProfile(...args) {
      return feature_profile.loadProfile(...args);
    }
    function saveProfile(...args) {
      return feature_profile.saveProfile(...args);
    }
    function openProfileModal(...args) {
      return feature_profile.openProfileModal(...args);
    }
    const feature_map = createFeature2(featureContext);
    function initMap(...args) {
      return feature_map.initMap(...args);
    }
    function updateMap(...args) {
      return feature_map.updateMap(...args);
    }
    const feature_dashboard = createFeature3(featureContext);
    function loadStats(...args) {
      return feature_dashboard.loadStats(...args);
    }
    function loadSystemHealth(...args) {
      return feature_dashboard.loadSystemHealth(...args);
    }
    function setBackendState(...args) {
      return feature_dashboard.setBackendState(...args);
    }
    const feature_assets = createFeature4(featureContext);
    function loadAntennas(...args) {
      return feature_assets.loadAntennas(...args);
    }
    function renderAntennas(...args) {
      return feature_assets.renderAntennas(...args);
    }
    function focusAntenna(...args) {
      return feature_assets.focusAntenna(...args);
    }
    function openAntennaModal(...args) {
      return feature_assets.openAntennaModal(...args);
    }
    function antennaSpecs(...args) {
      return feature_assets.antennaSpecs(...args);
    }
    function saveAntenna(...args) {
      return feature_assets.saveAntenna(...args);
    }
    function deleteAntenna(...args) {
      return feature_assets.deleteAntenna(...args);
    }
    const feature_tasks = createFeature5(featureContext);
    function loadTasks(...args) {
      return feature_tasks.loadTasks(...args);
    }
    function eligibleOperators(...args) {
      return feature_tasks.eligibleOperators(...args);
    }
    function populateTaskSelectors(...args) {
      return feature_tasks.populateTaskSelectors(...args);
    }
    function renderTaskList(...args) {
      return feature_tasks.renderTaskList(...args);
    }
    function createTask(...args) {
      return feature_tasks.createTask(...args);
    }
    const feature_reports = createFeature6(featureContext);
    function attachmentViewer(...args) {
      return feature_reports.attachmentViewer(...args);
    }
    function reportDisplayTitle(...args) {
      return feature_reports.reportDisplayTitle(...args);
    }
    function reportDisplaySummary(...args) {
      return feature_reports.reportDisplaySummary(...args);
    }
    function refreshReportTitles(...args) {
      return feature_reports.refreshReportTitles(...args);
    }
    function loadReports(...args) {
      return feature_reports.loadReports(...args);
    }
    function canManageReports(...args) {
      return feature_reports.canManageReports(...args);
    }
    function renderReportRow(...args) {
      return feature_reports.renderReportRow(...args);
    }
    function deleteReport(...args) {
      return feature_reports.deleteReport(...args);
    }
    function reportDetails(...args) {
      return feature_reports.reportDetails(...args);
    }
    function reviewReport(...args) {
      return feature_reports.reviewReport(...args);
    }
    async function telecomPost(path, payload) {
      return apiFetch(`/api/v1/telecom-tools${path}`, { method: "POST", body: JSON.stringify(payload) });
    }
    async function calculateLinkBudget() {
      try {
        const r = await telecomPost("/link-budget", { frequencyMHz: Number($("tbFreq").value), distanceKm: Number($("tbDist").value), txPowerDbm: Number($("tbTx").value), txGainDbi: Number($("tbGt").value), rxGainDbi: Number($("tbGr").value), txCableLossDb: Number($("tbLt").value || 0), rxCableLossDb: Number($("tbLr").value || 0), otherLossDb: Number($("tbLo").value || 0), rxSensitivityDbm: $("tbSens").value === "" ? void 0 : Number($("tbSens").value) });
        $("tbResult").innerHTML = `<strong>FSPL:</strong> ${number(r.fsplDb, 2)} dB<br><strong>EIRP:</strong> ${number(r.eirpDbm, 2)} dBm<br><strong>Potenza ricevuta:</strong> ${number(r.receivedPowerDbm, 2)} dBm<br><strong>Margine:</strong> ${r.linkMarginDb == null ? "\u2014" : number(r.linkMarginDb, 2) + " dB"}<br><span class="subtle">${escapeHtml(r.formula)}</span>`;
      } catch (e) {
        toast("Calcolo non valido", e.message, "error");
      }
    }
    async function calculatePower() {
      try {
        const payload = $("tbDbm").value !== "" ? { dbm: Number($("tbDbm").value) } : { w: Number($("tbW").value) };
        const r = await telecomPost("/power", payload);
        $("tbPowerResult").textContent = `${number(r.dbm, 2)} dBm \xB7 ${number(r.mw, 3)} mW \xB7 ${number(r.w, 6)} W`;
      } catch (e) {
        toast("Conversione non valida", e.message, "error");
      }
    }
    async function calculateVswr() {
      try {
        const r = await telecomPost("/vswr", { vswr: Number($("tbVswr").value) });
        $("tbVswrResult").textContent = `\u0393 ${number(r.reflectionCoefficient, 4)} \xB7 Perdita di ritorno ${number(r.returnLossDb, 2)} dB`;
      } catch (e) {
        toast("VSWR non valido", e.message, "error");
      }
    }
    const feature_operators = createFeature7(featureContext);
    function loadOperators(...args) {
      return feature_operators.loadOperators(...args);
    }
    function operatorOnline(...args) {
      return feature_operators.operatorOnline(...args);
    }
    function renderOperatorTable(...args) {
      return feature_operators.renderOperatorTable(...args);
    }
    function saveOperatorRole(...args) {
      return feature_operators.saveOperatorRole(...args);
    }
    function approveOperator(...args) {
      return feature_operators.approveOperator(...args);
    }
    function createOperator(...args) {
      return feature_operators.createOperator(...args);
    }
    function showAntennaQr(...args) {
      return feature_operators.showAntennaQr(...args);
    }
    function showBadge(...args) {
      return feature_operators.showBadge(...args);
    }
    function regenerateQr(...args) {
      return feature_operators.regenerateQr(...args);
    }
    function syncOperatorFromFirebase(...args) {
      return feature_operators.syncOperatorFromFirebase(...args);
    }
    const feature_notifications = createFeature8(featureContext);
    function renderRecipients(...args) {
      return feature_notifications.renderRecipients(...args);
    }
    function loadNotifications(...args) {
      return feature_notifications.loadNotifications(...args);
    }
    function renderNotifications(...args) {
      return feature_notifications.renderNotifications(...args);
    }
    function updateNotificationCount(...args) {
      return feature_notifications.updateNotificationCount(...args);
    }
    function sendNotification(...args) {
      return feature_notifications.sendNotification(...args);
    }
    const feature_inventory = createFeature9(featureContext);
    function loadInventory(...args) {
      return feature_inventory.loadInventory(...args);
    }
    function renderInventory(...args) {
      return feature_inventory.renderInventory(...args);
    }
    function inventoryQty(...args) {
      return feature_inventory.inventoryQty(...args);
    }
    function inventoryIsLow(...args) {
      return feature_inventory.inventoryIsLow(...args);
    }
    function openInventoryItem(...args) {
      return feature_inventory.openInventoryItem(...args);
    }
    function saveInventoryItem(...args) {
      return feature_inventory.saveInventoryItem(...args);
    }
    function openInventoryMovement(...args) {
      return feature_inventory.openInventoryMovement(...args);
    }
    function updateInventoryMovementHint(...args) {
      return feature_inventory.updateInventoryMovementHint(...args);
    }
    function saveInventoryMovement(...args) {
      return feature_inventory.saveInventoryMovement(...args);
    }
    function loadInventoryMovements(...args) {
      return feature_inventory.loadInventoryMovements(...args);
    }
    function renderInventoryMovements(...args) {
      return feature_inventory.renderInventoryMovements(...args);
    }
    function archiveInventoryItem(...args) {
      return feature_inventory.archiveInventoryItem(...args);
    }
    function restoreInventoryItem(...args) {
      return feature_inventory.restoreInventoryItem(...args);
    }
    function exportInventoryCsv(...args) {
      return feature_inventory.exportInventoryCsv(...args);
    }
    function switchView(view) {
      document.querySelectorAll(".view").forEach((v) => v.classList.remove("active"));
      document.querySelectorAll(".nav button").forEach((b) => b.classList.remove("active"));
      const target = $(`view-${view}`);
      const nav = document.querySelector(`.nav button[data-view="${view}"]`);
      if (target) target.classList.add("active");
      if (nav) nav.classList.add("active");
      window.dispatchEvent(new CustomEvent("radiotech:view-changed", { detail: { view } }));
      const titles = {
        dashboard: ["Panoramica", "Monitoraggio infrastruttura radio e operazioni tecniche"],
        antennas: ["Antenne", "Gestione e controllo dell'infrastruttura"],
        operations: ["Operativit\xE0", "Incarichi e indicatori della squadra"],
        notifications: ["Centro notifiche", "Comunicazioni operative ai dipendenti"],
        inventory: ["Inventario", "Materiali e componenti disponibili"],
        branding: ["Personalizzazione", "Identit\xE0 aziendale, colori ed etichette del prodotto"],
        profile: ["Profilo del Capo", "Identit\xE0 e impostazioni del responsabile"],
        system: ["Stato sistema", "Backend, Firestore e sessione"],
        tools: ["TelcoTools", "Calcolo e verifica dei collegamenti radio"],
        enterprise: ["Rischi e competenze", "Incidenti, SLA e abilitazioni della squadra"],
        ai: ["Pianificazione preventiva", "Scadenze, priorit\xE0 e manutenzione programmata"],
        attendance: ["Presenze", "Timbrature, validazione e incongruenze"],
        rostering: ["Turnistica", "Pianificazione mensile, vincoli e copertura"],
        people: ["Squadra e sicurezza", "Turni, pause, segnalazioni e supporto alla squadra"],
        operators: ["Operatori", "Anagrafica, accessi e abilitazione della squadra"],
        reports: ["Centro report", "Revisione dei resoconti degli interventi"],
        planning: ["Agenda interventi", "Scadenze, priorit\xE0 e pianificazione"],
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
      if (view === "antennas") loadAntennas().catch(() => {
      });
      if (view === "operations") Promise.all([loadOperators(), loadTasks()]).catch(() => {
      });
      if (view === "notifications") {
        loadOperators().catch(() => {
        });
        loadNotifications().catch(() => {
        });
      }
      if (view === "inventory") loadInventory().catch(() => {
      });
      if (view === "profile") loadProfile().catch(() => {
      });
      if (view === "system") {
        loadAudit().catch(() => {
        });
        loadOperationalHealth();
      }
      $("sidebar").classList.remove("open");
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
    const feature_system = createFeature10(featureContext);
    function loadOperationalHealth(...args) {
      return feature_system.loadOperationalHealth(...args);
    }
    function checkSystem(...args) {
      return feature_system.checkSystem(...args);
    }
    function loadAudit(...args) {
      return feature_system.loadAudit(...args);
    }
    const refreshCoordinator = window.RadioTechRefreshCoordinator({
      run: runRefresh,
      canRefresh: () => {
        const editing = document.activeElement?.matches('input, textarea, select, [contenteditable="true"]');
        return Boolean(state.token) && !document.hidden && !editing && !document.querySelector(".modal-backdrop.open, dialog[open]");
      }
    });
    function refreshAll(silent = false) {
      return refreshCoordinator.refresh(silent);
    }
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && state.token) refreshAll(true);
    });
    async function runRefresh(silent = false) {
      if (!state.token) return;
      const view = document.querySelector(".view.active")?.id.replace("view-", "") || "dashboard";
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
      const loaders = silent ? [...visibleLoaders[view] || [], loadNotifications] : [loadStats, loadAntennas, loadNotifications, loadProfile, loadReports, loadTasks, loadOperators];
      const tasks = [...new Set(loaders)].map((loader) => Promise.resolve().then(() => loader()));
      tasks.push(management?.refreshCurrent());
      const results = await Promise.allSettled(tasks);
      const failed = results.filter((x) => x.status === "rejected");
      if (!silent) {
        if (failed.length) {
          toast(
            "Sincronizzazione parziale",
            `${failed.length} servizio/i non hanno risposto.`,
            "error"
          );
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
      if (newPassword.length < 8 || !/[A-Z]/.test(newPassword) || !/[a-z]/.test(newPassword) || !/\d/.test(newPassword) || !/[^A-Za-z0-9]/.test(newPassword)) {
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
          method: "POST",
          body: JSON.stringify({ currentPassword, newPassword })
        });
        closeModal("passwordModal");
        signOut();
        if (window.RadioTechFederation) await window.RadioTechFederation.logout().catch(() => {
        });
        showLogin("Password aggiornata. Accedi con la nuova password.");
        toast("Password aggiornata", "Usa la nuova password per accedere.");
      } catch (error) {
        status.textContent = error.message;
      } finally {
        $("passwordForm").reset();
        button.disabled = false;
      }
    }
    function openModal(id) {
      $(id).classList.add("open");
      document.body.classList.add("modal-open");
    }
    function closeModal(id) {
      if (id === "passwordModal") {
        $("passwordForm").reset();
        $("passwordStatus").textContent = "";
      }
      $(id).classList.remove("open");
      if (!document.querySelector(".modal-backdrop.open")) {
        document.body.classList.remove("modal-open");
      }
    }
    $("loginForm").addEventListener("submit", login);
    $("changePasswordBtn").addEventListener("click", () => {
      window.RadioTechChiefAccess.open("password", () => {
        $("passwordForm").reset();
        $("passwordStatus").textContent = "";
        openModal("passwordModal");
        $("currentPassword").focus();
      }).catch((e) => toast("Cambio password", e.message, "error"));
    });
    $("passwordForm").addEventListener("submit", savePassword);
    $("protectedChiefLogin").addEventListener("click", async () => {
      const button = $("protectedChiefLogin");
      button.disabled = true;
      $("loginStatus").textContent = "Completa l'accesso nella finestra protetta.";
      try {
        const idToken = await window.RadioTechChiefAccess.open("login");
        const response = await fetch(CONFIG.AUTH_LOGIN, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ idToken })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || "Accesso non autorizzato.");
        saveSession(idToken, { ...data.user, role: data.role }, false, null);
        $("loginPassword").value = "";
        await startApplication();
      } catch (e) {
        $("loginStatus").textContent = e.message;
      } finally {
        button.disabled = false;
      }
    });
    $("togglePassword").addEventListener("click", () => {
      const input = $("loginPassword");
      input.type = input.type === "password" ? "text" : "password";
    });
    $("mobileMenu").addEventListener("click", () => $("sidebar").classList.toggle("open"));
    document.querySelectorAll(".nav button[data-view]").forEach((button) => {
      button.addEventListener("click", () => switchView(button.dataset.view));
    });
    window.RadioTechScrollTop = function() {
      const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth";
      document.scrollingElement?.scrollTo({ top: 0, left: 0, behavior });
      window.scrollTo({ top: 0, left: 0, behavior });
    };
    document.querySelectorAll("[data-footer-view]").forEach((button) => button.addEventListener("click", () => {
      switchView(button.dataset.footerView);
      window.RadioTechScrollTop();
    }));
    $("footerBackTop").addEventListener("click", () => window.RadioTechScrollTop());
    $("footerYear").textContent = String((/* @__PURE__ */ new Date()).getFullYear());
    function updateFooterStatus() {
      const status = $("backendStatus").textContent.trim().toUpperCase();
      const unavailable = ["ERROR", "ERRORE"].includes(status);
      $("footerServiceStatus").textContent = status === "OK" ? "Backend disponibile" : unavailable ? "Backend non disponibile" : "Verifica del servizio in corso";
      document.querySelector(".footer-service").dataset.status = status === "OK" ? "up" : unavailable ? "down" : "pending";
      $("footerLastUpdate").textContent = $("lastUpdate").textContent;
    }
    const footerObserver = new MutationObserver(updateFooterStatus);
    for (const id of ["backendStatus", "lastUpdate"]) footerObserver.observe($(id), { childList: true, subtree: true, characterData: true });
    updateFooterStatus();
    $("refreshBtn").addEventListener("click", () => refreshAll(false));
    $("systemRefreshBtn").addEventListener("click", checkSystem);
    $("operationsHealthRefresh").addEventListener("click", loadOperationalHealth);
    $("tasksMoreBtn").addEventListener("click", () => loadTasks(true));
    $("reportsMoreBtn").addEventListener("click", () => loadReports(true));
    $("auditRefreshBtn")?.addEventListener("click", () => loadAudit().catch(() => {
    }));
    $("quickAntennaBtn").addEventListener("click", () => openAntennaModal());
    $("newAntennaBtn").addEventListener("click", () => openAntennaModal());
    $("quickNotificationBtn").addEventListener("click", () => switchView("notifications"));
    $("newNotificationBtn").addEventListener("click", () => switchView("notifications"));
    $("reloadAntennasBtn").addEventListener("click", () => loadAntennas().catch(() => {
    }));
    $("antennaSearch").addEventListener("input", debounce(() => loadAntennas().catch(() => {
    }), 350));
    $("antennaStatusFilter").addEventListener("change", () => loadAntennas().catch(() => {
    }));
    $("antennaForm").addEventListener("submit", saveAntenna);
    $("closeAntennaModal").addEventListener("click", () => closeModal("antennaModal"));
    $("cancelAntenna").addEventListener("click", () => closeModal("antennaModal"));
    $("editProfileBtn").addEventListener("click", openProfileModal);
    $("profileForm").addEventListener("submit", saveProfile);
    $("closeProfileModal").addEventListener("click", () => closeModal("profileModal"));
    $("cancelProfile").addEventListener("click", () => closeModal("profileModal"));
    $("reportsRefreshBtn")?.addEventListener("click", loadReports);
    $("createTaskBtn")?.addEventListener("click", createTask);
    $("operatorsRefreshBtn")?.addEventListener("click", () => loadOperators().catch(() => {
    }));
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
    $("taskSearch")?.addEventListener("input", () => {
      renderTaskList();
      clearTimeout(window.radioTechTaskSearchTimer);
      window.radioTechTaskSearchTimer = setTimeout(() => loadTasks(), 350);
    });
    $("tbLinkBtn")?.addEventListener("click", calculateLinkBudget);
    $("tbPowerBtn")?.addEventListener("click", calculatePower);
    $("tbVswrBtn")?.addEventListener("click", calculateVswr);
    $("notificationTarget").addEventListener("change", (event) => {
      $("recipientBox").classList.toggle("hidden", event.target.value !== "SELECTED");
      if (event.target.value === "SELECTED") loadOperators().catch(() => {
      });
    });
    $("sendNotificationBtn").addEventListener("click", sendNotification);
    $("syncOperatorBtn")?.addEventListener("click", syncOperatorFromFirebase);
    $("reloadNotificationsBtn").addEventListener("click", () => loadNotifications().catch(() => {
    }));
    $("inventoryRefreshBtn").addEventListener("click", () => loadInventory().catch(() => {
    }));
    $("newInventoryItemBtn")?.addEventListener("click", () => openInventoryItem());
    $("inventoryItemForm")?.addEventListener("submit", saveInventoryItem);
    $("inventoryMovementForm")?.addEventListener("submit", saveInventoryMovement);
    $("inventoryMovementType")?.addEventListener("change", updateInventoryMovementHint);
    $("inventoryMovementsBtn")?.addEventListener("click", async () => {
      openModal("inventoryMovementsHistoryModal");
      await loadInventoryMovements();
    });
    $("inventoryExportBtn")?.addEventListener("click", exportInventoryCsv);
    $("inventorySearch")?.addEventListener("input", debounce(() => loadInventory(), 350));
    $("inventoryCategoryFilter")?.addEventListener("change", () => loadInventory());
    $("inventoryStockFilter")?.addEventListener("change", () => loadInventory());
    document.querySelectorAll("[data-close-modal]").forEach((button) => button.addEventListener("click", () => closeModal(button.dataset.closeModal)));
    $("notificationBell").addEventListener("click", () => switchView("notifications"));
    $("centerMapBtn").addEventListener("click", () => {
      if (!state.map) initMap();
      updateMap();
    });
    function signOut() {
      window.RadioTechFederation?.logout().catch(() => {
      });
      clearSession();
      state.tasks = [];
      state.antennas = [];
      state.operators = [];
      state.notifications = [];
      state.inventory = [];
      $("loginPassword").value = "";
      if (state.refreshTimer) clearInterval(state.refreshTimer);
      state.refreshTimer = null;
      showLogin("Sei uscito dal Centro operativo.");
      toast("Sessione chiusa", "L'accesso \xE8 stato rimosso da questo dispositivo.");
    }
    $("logoutBtn")?.addEventListener("click", signOut);
    $("topbarLogoutBtn")?.addEventListener("click", signOut);
    $("operatorSearch")?.addEventListener("input", debounce(() => loadOperators(), 350));
    $("operatorRoleFilter")?.addEventListener("change", () => loadOperators());
    document.querySelectorAll(".modal-backdrop").forEach((backdrop) => {
      backdrop.addEventListener("click", (event) => {
        if (event.target === backdrop) closeModal(backdrop.id);
      });
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        document.querySelectorAll(".modal-backdrop.open").forEach((x) => closeModal(x.id));
      }
    });
    window.addEventListener("resize", () => {
      if (state.map) setTimeout(() => state.map.invalidateSize(), 100);
    });
    async function boot() {
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
      chiefAccess = window.RadioTechChiefAccess.install({ apiFetch, toast, currentRole: () => state.authRole });
      enterprise = window.RadioTechEnterprise({ apiFetch, state, escapeHtml, toast, switchView });
      companyHub = window.RadioTechCompanyHub({ apiFetch, state, escapeHtml, toast, switchView });
      installWorkforce({ apiFetch, state, escapeHtml, toast, switchView });
      installDossier({ apiFetch, state, escapeHtml, toast });
      proSuite = window.RadioTechPro({ apiFetch, state, escapeHtml, toast, switchView });
      proSecurity = window.RadioTechProSecurity({ apiFetch, toast, switchView });
      window.RadioTechTelcoTools();
      window.RadioTechOperations({ apiFetch, state, escapeHtml, toast, loadOperators, loadInventory });
      management = window.RadioTechManagement({ apiFetch, state, escapeHtml, switchView, loadOperators, loadReports });
      signatureWorkspace = window.RadioTechSignatureWorkspace({ state, switchView, initials, apiFetch });
      setTimeout(() => window.RadioTechPersonalization.init({ apiFetch, state, switchView, toast }), 0);
      const skip = document.createElement("a");
      skip.href = "#mainContent";
      skip.className = "skip-link";
      skip.textContent = "Vai al contenuto";
      document.body.prepend(skip);
      boot();
    });
  })();
})();
