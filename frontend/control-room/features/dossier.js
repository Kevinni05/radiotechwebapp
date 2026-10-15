const sections = {
  overview: "Riepilogo",
  access: "Accesso al sito",
  configuration: "Configurazioni",
  measurements: "Misurazioni",
  dependencies: "Dipendenze",
  photos: "Fotografie",
  documents: "Documenti",
  components: "Componenti",
  issues: "Problemi aperti",
  proposals: "Correzioni",
  sessions: "Ponte radio",
  shares: "Condivisione",
};
const accessLabels = {
  address: "Indirizzo e punto di accesso",
  contact: "Referente e contatti",
  hours: "Orari di accesso",
  parking: "Parcheggio",
  keyInstructions: "Ritiro chiavi",
  route: "Percorso e indicazioni",
  restrictions: "Restrizioni e precauzioni",
};
const labels = {
  metric: "Parametro",
  unit: "Unità",
  value: "Valore",
  low: "Soglia minima",
  high: "Soglia massima",
  measuredAt: "Data e ora della misura",
  notes: "Note",
  label: "Nome revisione",
  reason: "Motivazione",
  values: "Parametri (uno per riga: nome = valore)",
  antennaId: "Impianto da cui dipende",
  type: "Tipo di collegamento",
  title: "Titolo",
  name: "Componente",
  model: "Modello",
  serial: "Matricola",
  installedAt: "Installazione",
  warrantyUntil: "Fine garanzia",
  target: "Sezione da correggere",
  field: "Campo",
  status: "Stato",
  peerAntennaId: "Impianto all’altro estremo",
  operatorAId: "Tecnico estremo A",
  operatorBId: "Tecnico estremo B",
};
const stamp = (v) => (v ? new Date(v).toLocaleString("it-IT") : "—");
const op = () =>
  crypto.randomUUID?.() ||
  Array.from(crypto.getRandomValues(new Uint8Array(24)), (v) =>
    v.toString(16).padStart(2, "0"),
  ).join("");
const pairs = (text) =>
  Object.fromEntries(
    text
      .split("\n")
      .filter((l) => l.trim())
      .map((l) => {
        const p = l.indexOf("=");
        if (p < 1) throw Error("Usa una riga per parametro: nome = valore.");
        return [l.slice(0, p).trim(), l.slice(p + 1).trim()];
      }),
  );
const pairText = (v) =>
  Object.entries(v || {})
    .map(([k, x]) => `${k} = ${x}`)
    .join("\n");
export function installDossier({
  apiFetch,
  state,
  escapeHtml: esc,
  toast,
  formatStatus = (v) => v,
}) {
  let data = null,
    assetId = null,
    tab = "overview",
    epoch = 0,
    dialog = null,
    poll = null,
    activeSession = null,
    busy = false;
  const urls = new Set();
  const base = () => `/api/v1/antennas/${encodeURIComponent(assetId)}/dossier`;
  const button = (label, action, extra = "") =>
    `<button type="button" class="btn" data-dossier-action="${action}" ${extra}>${label}</button>`;
  const empty = (message) => `<div class="empty">${esc(message)}</div>`;
  const panel = (title, body, actions = "") =>
    `<section class="panel"><div class="panel-head"><h3>${esc(title)}</h3><div class="actions">${actions}</div></div><div class="panel-body">${body}</div></section>`;
  const facts = (obj) =>
    `<dl class="ds-facts">${Object.entries(obj || {})
      .map(
        ([k, v]) =>
          `<div><dt>${esc(accessLabels[k] || k)}</dt><dd>${esc(typeof v === "object" ? pairText(v) : k === "status" ? formatStatus(v) : v || "—")}</dd></div>`,
      )
      .join("")}</dl>`;
  const cards = (rows, fn) =>
    rows.length
      ? `<div class="ds-stack">${rows.map(fn).join("")}</div>`
      : empty("Nessun elemento registrato.");
  const error = (e) =>
    toast("Fascicolo", e.message || "Operazione non riuscita.", "error");
  async function run(fn) {
    const ownEpoch = epoch;
    if (busy) return;
    busy = true;
    dialog?.setAttribute("aria-busy", "true");
    try {
      await fn();
    } catch (e) {
      if (epoch === ownEpoch) error(e);
    } finally {
      if (epoch === ownEpoch) {
        busy = false;
        dialog?.removeAttribute("aria-busy");
      }
    }
  }
  function reset() {
    epoch++;
    tab = "overview";
    busy = false;
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
    dialog.innerHTML = `<header class="ds-header"><div><span class="ds-eyebrow">RADIOTECH · FASCICOLO IMPIANTO</span><h2>Caricamento…</h2><p data-summary></p></div><div class="actions">${button("Aggiorna", "refresh")}${button("Chiudi", "close")}</div></header><nav class="ds-tabs" aria-label="Sezioni fascicolo"></nav><div class="ds-content" role="region" aria-live="polite"></div>`;
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
      if (epoch === e)
        dialog.querySelector(".ds-content").innerHTML = empty(x.message);
    }
  }
  async function reload(e = epoch) {
    const d = await apiFetch(base());
    if (e !== epoch) return;
    data = d;
    dialog.querySelector("h2").textContent = d.name;
    dialog.querySelector("[data-summary]").textContent =
      `${d.antennaId} · Revisione ${d.version} · Aggiornato ${stamp(d.updatedAt || d.fetchedAt)}`;
    render();
  }
  function render() {
    if (!data || !dialog?.open) return;
    clearInterval(poll);
    const visible = Object.entries(sections).filter(
      ([k]) => k !== "shares" || data.canManage,
    );
    dialog.querySelector(".ds-tabs").innerHTML = visible
      .map(
        ([k, v]) =>
          `<button type="button" class="${tab === k ? "active" : ""}" aria-current="${tab === k ? "page" : "false"}" data-tab="${k}">${v}</button>`,
      )
      .join("");
    const content = dialog.querySelector(".ds-content");
    if (tab === "overview") {
      const latest = data.configuration.at(-1);
      content.innerHTML = `<div class="ds-kpis">${[
        ["Interventi e report", data.history.length],
        ["Misurazioni", data.measurements.length],
        [
          "Problemi aperti",
          data.issues.filter((x) => x.status === "OPEN").length,
        ],
        ["Revisioni", data.configuration.length],
      ]
        .map(
          ([k, v]) =>
            `<div class="panel"><span>${k}</span><strong>${v}</strong></div>`,
        )
        .join(
          "",
        )}</div><div class="ds-columns">${panel("Identità e dati tecnici", facts(data.identity))}${panel("Configurazione attuale", latest ? `<p>${esc(latest.label)} · ${stamp(latest.createdAt)}</p>${facts(latest.values)}` : empty("Nessuna configurazione registrata."))}</div>${panel("Cronologia interventi e report", `${data.historyPartial ? '<p class="ds-notice">Sono mostrati fino a 200 elementi per sorgente. Consulta gli archivi per lo storico completo.</p>' : ""}${cards(data.history, (r) => `<article class="ds-record"><span class="badge">${r.type === "TASK" ? "Incarico" : "Report"}</span><h4>${esc(r.title || r.id)}</h4><p>${esc(r.summary || r.description || "")}</p><small>${esc(formatStatus(r.status || ""))} · ${stamp(r.submittedAt || r.createdAt)}</small></article>`)}`)}`;
    }
    if (tab === "access")
      content.innerHTML = panel(
        "Preparazione dell’accesso",
        facts(data.access),
        data.canManage
          ? button("Modifica indicazioni", "edit-access")
          : button("Proponi una correzione", "add-proposals"),
      );
    if (tab === "configuration") {
      const list = data.configuration;
      content.innerHTML = panel(
        "Revisioni della configurazione",
        `${list.length ? `<div class="ds-compare"><label>Prima<select data-compare="before">${list.map((r, i) => `<option value="${i}" ${i === Math.max(0, list.length - 2) ? "selected" : ""}>${esc(r.label)} · ${stamp(r.createdAt)}</option>`).join("")}</select></label><label>Dopo<select data-compare="after">${list.map((r, i) => `<option value="${i}" ${i === list.length - 1 ? "selected" : ""}>${esc(r.label)} · ${stamp(r.createdAt)}</option>`).join("")}</select></label></div><div data-diff></div>` : empty("Registra la prima configurazione verificata.")}${cards([...list].reverse(), (r) => `<article class="ds-record"><h4>${esc(r.label)}</h4><p>${esc(r.reason)}</p><small>${stamp(r.createdAt)}</small><details><summary>Parametri registrati</summary>${facts(r.values)}</details></article>`)}`,
        data.canManage
          ? button("Nuova revisione", "add-configuration")
          : button("Proponi una correzione", "add-proposals"),
      );
      content
        .querySelectorAll("[data-compare]")
        .forEach((s) => (s.onchange = compare));
      compare();
    }
    if (tab === "measurements") {
      content.innerHTML = panel(
        "Misurazioni e soglie",
        `<p class="subtle">Valori inseriti manualmente. Le soglie vengono conservate con ogni misura.</p><label>Parametro<select data-metric>${[...new Set(data.measurements.map((x) => x.metric))].map((m) => `<option>${esc(m)}</option>`).join("")}</select></label><div data-chart></div>`,
        button("Registra misura", "add-measurements"),
      );
      content.querySelector("[data-metric]").onchange = chart;
      chart();
    }
    if (tab === "dependencies") {
      const rows = data.dependencies.filter((r) => !r.archived);
      content.innerHTML = panel(
        "Dipendenze dirette registrate",
        `<div data-network></div><p class="subtle">Le frecce indicano da quali impianti dipende questo sito. Lo schema descrive i collegamenti registrati, senza simulare la disponibilità della rete.</p>${rows.length ? `<svg class="ds-graph" viewBox="0 0 720 ${Math.max(150, rows.length * 88)}" role="img" aria-label="Mappa delle dipendenze"><defs><marker id="dsArrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="currentColor"/></marker></defs><rect x="10" y="${Math.max(45, rows.length * 44 - 24)}" width="240" height="48" rx="10"/><text x="24" y="${Math.max(45, rows.length * 44 - 24) + 29}">${esc(data.name.slice(0, 26))}</text>${rows.map((r, i) => `<path d="M250 ${Math.max(45, rows.length * 44 - 24) + 24} L440 ${i * 88 + 48}" marker-end="url(#dsArrow)"/><rect x="450" y="${i * 88 + 24}" width="255" height="48" rx="10"/><text x="464" y="${i * 88 + 53}">${esc((r.name || r.antennaId).slice(0, 27))}</text>`).join("")}</svg>` : empty("Nessuna dipendenza registrata.")}${cards(rows, (r) => `<article class="ds-record"><h4>${esc(r.name || r.antennaId)}</h4><p>${esc(r.type)} · ${esc(r.label)} · ${esc(formatStatus(r.status || ""))}</p><p>${esc(r.notes)}</p>${button("Apri fascicolo collegato", "peer", `data-id="${esc(r.antennaId)}"`)}${data.canManage ? button("Rimuovi collegamento", "remove-dependency", `data-id="${esc(r.id)}"`) : ""}</article>`)}`,
        data.canManage
          ? button("Aggiungi collegamento", "add-dependencies")
          : "",
      );
    }
    if (["photos", "documents", "components", "issues"].includes(tab))
      content.innerHTML = panel(
        sections[tab],
        cards(
          [...data[tab]].reverse(),
          (r) =>
            `<article class="ds-record"><h4>${esc(r.title || r.name)}</h4><p>${esc(r.notes || "")}</p>${tab === "photos" ? `<div class="ds-photo" data-photo="${esc(r.id)}"></div>` : ""}${tab === "components" ? facts({ Modello: r.model, Matricola: r.serial, Installazione: r.installedAt, Garanzia: r.warrantyUntil }) : ""}${r.status ? `<span class="badge">${r.status === "OPEN" ? "Aperto" : "Risolto"}</span>` : ""}${tab === "issues" && data.canManage && r.status === "OPEN" ? button("Segna risolto", "resolve-issue", `data-id="${esc(r.id)}"`) : ""}${r.resolutionReason ? `<p>Esito: ${esc(r.resolutionReason)}</p>` : ""}${r.fileId ? button(tab === "photos" ? "Visualizza originale e annotazioni" : "Scarica documento", "file", `data-id="${esc(r.id)}"`) : ""}<small>${stamp(r.createdAt)}</small></article>`,
        ),
        tab === "components" && !data.canManage
          ? ""
          : button("Aggiungi", "add-" + tab),
      );
    if (tab === "proposals")
      content.innerHTML = panel(
        "Correzioni proposte e decisioni",
        cards(
          [...data.proposals].reverse(),
          (p) =>
            `<article class="ds-record"><h4>${esc(p.target === "access" ? "Accesso al sito" : "Configurazione")} · ${esc(accessLabels[p.field] || p.field)}</h4><div class="ds-columns"><div><small>Prima</small><p>${esc(p.before || "—")}</p></div><div><small>Proposta</small><p>${esc(p.value)}</p></div></div><p>${esc(p.reason)}</p><span class="badge">${{ PENDING: "Da verificare", APPROVED: "Approvata", REJECTED: "Respinta" }[p.status]}</span>${p.reviewReason ? `<p>Esito: ${esc(p.reviewReason)}</p>` : ""}${data.canManage && p.status === "PENDING" ? `<div class="actions">${button("Approva", "approve", `data-id="${esc(p.id)}"`)}${button("Respingi", "reject", `data-id="${esc(p.id)}"`)}</div>` : ""}</article>`,
        ),
        button("Proponi correzione", "add-proposals"),
      );
    if (tab === "dependencies") loadGraph().catch(error);
    if (tab === "shares") loadShares().catch(error);
    if (tab === "sessions") {
      loadSessions().catch(error);
      poll = setInterval(() => {
        if (!busy && !document.querySelector(".ds-editor") && dialog?.open)
          run(loadSessions);
      }, 10000);
    }
    if (data.canContribute === false)
      dialog.querySelectorAll('[data-dossier-action^="add-"]').forEach((b) => {
        b.hidden = true;
      });
  }
  function compare() {
    const c = dialog.querySelector("[data-diff]");
    if (!c) return;
    const a =
        data.configuration[
          Number(dialog.querySelector('[data-compare="before"]').value)
        ]?.values || {},
      b =
        data.configuration[
          Number(dialog.querySelector('[data-compare="after"]').value)
        ]?.values || {};
    c.innerHTML = `<div class="table-wrap"><table><thead><tr><th>Parametro</th><th>Prima</th><th>Dopo</th></tr></thead><tbody>${[
      ...new Set([...Object.keys(a), ...Object.keys(b)]),
    ]
      .sort()
      .map(
        (k) =>
          `<tr class="${a[k] !== b[k] ? "ds-changed" : ""}"><td>${esc(k)}</td><td>${esc(a[k] ?? "—")}</td><td>${esc(b[k] ?? "—")}</td></tr>`,
      )
      .join("")}</tbody></table></div>`;
  }
  function chart() {
    const m = dialog.querySelector("[data-metric]").value,
      rows = data.measurements
        .filter((r) => r.metric === m)
        .sort((a, b) => a.measuredAt.localeCompare(b.measuredAt));
    const c = dialog.querySelector("[data-chart]");
    if (!rows.length) {
      c.innerHTML = empty("Nessuna misura registrata.");
      return;
    }
    const units = [...new Set(rows.map((r) => r.unit))];
    const grouped =
      units.length > 1
        ? '<p class="ds-notice">Sono presenti unità diverse: il grafico è separato per unità per evitare confronti impropri.</p>'
        : "";
    c.innerHTML =
      grouped +
      units
        .map((unit) => {
          const items = rows.filter((r) => r.unit === unit);
          const values = items.map((r) => r.value),
            lo = Math.min(...values),
            span = Math.max(...values) - lo || 1;
          const times = items.map((r) => Date.parse(r.measuredAt)),
            from = Math.min(...times),
            duration = Math.max(...times) - from || 1;
          const pts = items.map(
            (r, i) =>
              `${20 + ((times[i] - from) / duration) * 660},${150 - ((r.value - lo) / span) * 120}`,
          );
          return `<figure><figcaption>${esc(m)} · ${esc(unit)} · ${items.length} misure</figcaption><svg class="ds-chart" viewBox="0 0 700 175" role="img" aria-label="Andamento ${esc(m)} in ${esc(unit)}"><polyline points="${pts.join(" ")}"/>${items.map((r, i) => `<circle cx="${pts[i].split(",")[0]}" cy="${pts[i].split(",")[1]}" r="4"><title>${stamp(r.measuredAt)}: ${r.value} ${esc(r.unit)}</title></circle>`).join("")}</svg></figure>`;
        })
        .join("") +
      `<div class="table-wrap"><table><thead><tr><th>Data</th><th>Valore</th><th>Soglie</th><th>Esito</th><th>Note</th></tr></thead><tbody>${[
        ...rows,
      ]
        .reverse()
        .map(
          (r) =>
            `<tr><td>${stamp(r.measuredAt)}</td><td>${r.value} ${esc(r.unit)}</td><td>${r.low ?? "—"} / ${r.high ?? "—"}</td><td>${r.outsideThreshold ? "Fuori soglia" : r.low == null && r.high == null ? "Soglie non definite" : "Nei limiti definiti"}</td><td>${esc(r.notes)}</td></tr>`,
        )
        .join("")}</tbody></table></div>`;
  }
  function editor(title, fields, onSave, description = "") {
    const e = epoch,
      d = document.createElement("dialog");
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
  function field(
    key,
    value = "",
    type = "text",
    required = true,
    options = null,
  ) {
    const label = labels[key] || accessLabels[key] || key;
    return `<label>${esc(label)}${options ? `<select name="${key}" ${required ? "required" : ""}>${options.map(([v, l]) => `<option value="${esc(v)}" ${v === value ? "selected" : ""}>${esc(l)}</option>`).join("")}</select>` : type === "textarea" ? `<textarea name="${key}" maxlength="3000" ${required ? "required" : ""}>${esc(value)}</textarea>` : `<input name="${key}" type="${type}" value="${esc(value)}" ${type === "number" ? 'step="any"' : ""} ${required ? "required" : ""} maxlength="2000">`}</label>`;
  }
  async function save(section, payload) {
    const e = epoch;
    await apiFetch(base() + "/changes", {
      method: "POST",
      body: JSON.stringify({
        expectedVersion: data.version,
        operationId: op(),
        section,
        data: payload,
      }),
    });
    if (epoch === e) await reload(e);
  }
  async function add(section) {
    if (section === "access") {
      editor(
        "Accesso al sito",
        Object.keys(accessLabels)
          .map((k) => field(k, data.access[k] || "", "textarea", false))
          .join(""),
        (f) => save("access", Object.fromEntries(f)),
      );
      return;
    }
    if (section === "configuration") {
      const current = data.configuration.at(-1)?.values || {};
      editor(
        "Nuova revisione",
        field("label") +
          field("reason", "", "textarea") +
          field("values", pairText(current), "textarea"),
        (f) =>
          save(section, {
            label: f.get("label"),
            reason: f.get("reason"),
            values: pairs(f.get("values")),
          }),
        "Ogni salvataggio conserva la revisione precedente.",
      );
      return;
    }
    if (section === "measurements") {
      editor(
        "Registra misurazione",
        field("metric") +
          field("unit", "", "text", false) +
          field("value", "", "number") +
          field("low", "", "number", false) +
          field("high", "", "number", false) +
          field("measuredAt", "", "datetime-local") +
          field("notes", "", "textarea", false),
        (f) =>
          save(section, {
            ...Object.fromEntries(f),
            value: Number(f.get("value")),
            low: f.get("low") === "" ? null : Number(f.get("low")),
            high: f.get("high") === "" ? null : Number(f.get("high")),
            measuredAt: new Date(f.get("measuredAt")).toISOString(),
          }),
      );
      return;
    }
    if (section === "dependencies") {
      const list = state.antennas.filter((a) => a.id !== assetId);
      editor(
        "Collega impianto",
        field(
          "antennaId",
          "",
          "text",
          true,
          list.map((a) => [a.id, a.name || a.id]),
        ) +
          field("label") +
          field("type", "RADIO", "text", true, [
            ["RADIO", "Ponte radio"],
            ["CABLE", "Cavo"],
            ["POWER", "Alimentazione"],
            ["FIBER", "Fibra"],
          ]) +
          field("notes", "", "textarea", false),
        (f) => save(section, Object.fromEntries(f)),
      );
      return;
    }
    if (section === "components") {
      editor(
        "Registra componente",
        field("name") +
          ["model", "serial"].map((k) => field(k, "", "text", false)).join("") +
          ["installedAt", "warrantyUntil"]
            .map((k) => field(k, "", "date", false))
            .join("") +
          field("notes", "", "textarea", false),
        (f) => save(section, Object.fromEntries(f)),
      );
      return;
    }
    if (section === "issues") {
      editor(
        "Registra problema",
        field("title") +
          field("notes", "", "textarea") +
          field("status", "OPEN", "text", true, [
            ["OPEN", "Aperto"],
            ["RESOLVED", "Risolto"],
          ]),
        (f) => save(section, Object.fromEntries(f)),
      );
      return;
    }
    if (section === "proposals") {
      const options = (target) =>
        target === "access"
          ? Object.entries(accessLabels)
          : Object.keys(data.configuration.at(-1)?.values || {}).map((k) => [
              k,
              k,
            ]);
      const d = editor(
        "Proponi una correzione",
        field("target", "access", "text", true, [
          ["access", "Accesso al sito"],
          ["configuration", "Configurazione"],
        ]) +
          field("field", "", "text", true, options("access")) +
          field("value", "", "textarea") +
          field("reason", "", "textarea"),
        (f) => save(section, Object.fromEntries(f)),
        "La modifica sarà applicata solo dopo la verifica del responsabile.",
      );
      d.querySelector("[name=target]").onchange = (e) => {
        d.querySelector("[name=field]").innerHTML = options(e.target.value)
          .map(([v, l]) => `<option value="${esc(v)}">${esc(l)}</option>`)
          .join("");
      };
      return;
    }
    if (section === "documents" || section === "photos") {
      const marks = [];
      const d = editor(
        section === "photos" ? "Fotografia e annotazioni" : "Documento tecnico",
        field("title") +
          `<label>File originale<input type="file" name="file" required accept="${section === "photos" ? "image/png,image/jpeg,image/webp" : ".pdf,.png,.jpg,.jpeg,.webp"}"></label>` +
          field("notes", "", "textarea", false) +
          (section === "photos"
            ? `<label>Annotazione<select data-mark-type><option value="ARROW">Freccia</option><option value="RECT">Riquadro</option><option value="TEXT">Nota</option></select></label><label>Testo annotazione<input data-mark-text maxlength="200"></label><p>Trascina sull’immagine per aggiungere un’annotazione.</p><div class="ds-annotator"><img alt="Anteprima originale"><svg viewBox="0 0 1000 1000" preserveAspectRatio="none"></svg></div><button class="btn" type="button" data-undo>Rimuovi ultima annotazione</button>`
            : ""),
        async (f) => {
          const file = f.get("file");
          if (!file.size || file.size > 10000000)
            throw Error("Scegli un file fino a 10 MB.");
          const e = epoch,
            root = base();
          const uploaded = await apiFetch(
            `/api/v1/files?operationId=${op()}&name=${encodeURIComponent(file.name)}`,
            {
              method: "POST",
              headers: { "Content-Type": "application/octet-stream" },
              body: file,
            },
          );
          if (epoch !== e) throw Error("Sessione cambiata.");
          await apiFetch(root + "/changes", {
            method: "POST",
            body: JSON.stringify({
              expectedVersion: data.version,
              operationId: op(),
              section,
              data: {
                title: f.get("title"),
                notes: f.get("notes"),
                fileId: uploaded.reference.replace("radiotech-file:", ""),
                annotations: marks,
              },
            }),
          });
          if (epoch === e) await reload(e);
        },
      );
      if (section === "photos") {
        const area = d.querySelector(".ds-annotator"),
          img = area.querySelector("img"),
          svg = area.querySelector("svg");
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
          return {
            x: Math.max(0, Math.min(1, (e.clientX - b.left) / b.width)),
            y: Math.max(0, Math.min(1, (e.clientY - b.top) / b.height)),
          };
        };
        svg.onpointerdown = (e) => {
          if (!img.src) return;
          start = point(e);
          svg.setPointerCapture(e.pointerId);
        };
        svg.onpointerup = (e) => {
          if (!start || marks.length >= 40) return;
          const end = point(e);
          marks.push({
            ...start,
            x2: end.x,
            y2: end.y,
            type: d.querySelector("[data-mark-type]").value,
            text: d.querySelector("[data-mark-text]").value,
          });
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
    return (
      `<defs><marker id="dsPhotoArrow" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="#ffb454"/></marker></defs>` +
      marks
        .map((m) => {
          const x = m.x * 1000,
            y = m.y * 1000,
            x2 = m.x2 * 1000,
            y2 = m.y2 * 1000;
          return (
            (m.type === "RECT"
              ? `<rect x="${Math.min(x, x2)}" y="${Math.min(y, y2)}" width="${Math.abs(x2 - x)}" height="${Math.abs(y2 - y)}"/>`
              : m.type === "ARROW"
                ? `<line x1="${x}" y1="${y}" x2="${x2}" y2="${y2}" marker-end="url(#dsPhotoArrow)"/>`
                : "") +
            `<text x="${x}" y="${Math.max(20, y - 10)}">${esc(m.text)}</text>`
          );
        })
        .join("")
    );
  }
  async function showFile(id) {
    const row = data[tab].find((r) => r.id === id);
    if (!row) return;
    const e = epoch,
      response = await apiFetch(`${base()}/files/${row.fileId}`, {
        maxResponseBytes: 15000000,
      });
    if (e !== epoch) return;
    const bytes = Uint8Array.from(atob(response.base64), (c) =>
        c.charCodeAt(0),
      ),
      type = /\.png$/i.test(response.name)
        ? "image/png"
        : /\.webp$/i.test(response.name)
          ? "image/webp"
          : /\.jpe?g$/i.test(response.name)
            ? "image/jpeg"
            : "application/pdf";
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
  async function loadGraph() {
    const e = epoch,
      network = await apiFetch(base() + "/graph");
    if (e !== epoch || tab !== "dependencies") return;
    const host = dialog.querySelector("[data-network]");
    host.innerHTML = `<details><summary>Rete collegata · ${network.nodes.length} impianti · ${network.edges.length} collegamenti</summary><div class="ds-network">${
      network.edges
        .map((edge) => {
          const source = network.nodes.find((n) => n.id === edge.source),
            target = network.nodes.find((n) => n.id === edge.target);
          return `<article class="ds-record"><strong>${esc(source?.name || edge.source)} → ${esc(target?.name || edge.target)}</strong><p>${esc(edge.type)} · ${esc(edge.label)}</p>${button("Apri impianto a monte", "peer", `data-id="${esc(edge.target)}"`)}</article>`;
        })
        .join("") || "<p>Nessun collegamento documentato.</p>"
    }</div></details>`;
  }
  async function loadShares() {
    const e = epoch,
      rows = await apiFetch(base() + "/shares");
    if (e !== epoch || tab !== "shares") return;
    dialog.querySelector(".ds-content").innerHTML = panel(
      "Link temporanei al fascicolo",
      `<p class="subtle">Ogni link contiene una copia delle sole sezioni selezionate. Anche le indicazioni di accesso diventano visibili se le includi. Il link è mostrato una sola volta.</p>${cards(rows, (r) => `<article class="ds-record"><h4>${esc(r.sections.map((k) => sections[k] || "Identità").join(" · "))}</h4><p>Scade ${stamp(r.expiresAt)} · ${r.revoked ? "Revocato" : new Date(r.expiresAt) < new Date() ? "Scaduto" : "Attivo"} · Accessi: ${r.accessCount}</p><div class="actions">${!r.revoked ? button("Revoca", "revoke", `data-id="${r.id}"`) : ""}${button("Registro accessi", "accesses", `data-id="${r.id}"`)}</div></article>`)}`,
      button("Crea link", "share"),
    );
  }
  async function createShare() {
    editor(
      "Crea link temporaneo",
      `<fieldset><legend>Informazioni da condividere</legend>${["identity", "configuration", "measurements", "dependencies", "documents", "photos", "components", "issues", "history", "access"].map((k) => `<label class="ds-check"><input type="checkbox" name="sections" value="${k}">${esc(k === "identity" ? "Identità impianto" : sections[k] || "Cronologia")}</label>`).join("")}</fieldset><label>Durata in ore<input type="number" name="hours" value="24" min="1" max="720" required></label>`,
      async (f, d) => {
        const e = epoch,
          r = await apiFetch(base() + "/shares", {
            method: "POST",
            body: JSON.stringify({
              sections: f.getAll("sections"),
              hours: Number(f.get("hours")),
            }),
          });
        if (e !== epoch) return;
        d.querySelector("form").innerHTML =
          `<p>Conserva il collegamento: non verrà mostrato nuovamente.</p><label>Link<input readonly value="${esc(r.url)}"></label><button type="button" class="btn" data-copy>Copia link</button><button type="button" class="btn" data-done>Chiudi</button>`;
        d.querySelector("[data-copy]").onclick = () =>
          navigator.clipboard.writeText(r.url).catch(error);
        d.querySelector("[data-done]").onclick = () => d.close();
        await loadShares();
        return false;
      },
    );
  }
  async function loadSessions() {
    const e = epoch,
      list = await apiFetch(base() + "/sessions");
    if (e !== epoch || tab !== "sessions") return;
    if (activeSession)
      activeSession = list.find((s) => s.id === activeSession.id) || null;
    const s = activeSession;
    dialog.querySelector(".ds-content").innerHTML =
      panel(
        "Prove coordinate ai due estremi",
        `<p class="subtle">Aggiornamento ogni 10 secondi. Ogni tecnico registra il proprio estremo; le misure confermate restano immutabili.</p>${cards(list, (r) => `<article class="ds-record"><h4>${esc(r.title)}</h4><p>${esc(r.nameA)} ↔ ${esc(r.nameB)} · ${r.state === "OPEN" ? "Aperta" : "Conclusa"}</p>${button("Apri sessione", "session", `data-id="${r.id}"`)}</article>`)}`,
        data.canManage ? button("Crea sessione", "create-session") : "",
      ) +
      (s
        ? panel(
            s.title,
            `<div class="ds-columns">${facts({ Estremo: s.nameA, Tecnico: s.operatorA })}${facts({ Estremo: s.nameB, Tecnico: s.operatorB })}</div>${cards(s.trials, (r) => `<article class="ds-record"><h4>${esc(r.label)}</h4>${facts(r.settings)}<div class="ds-columns">${["A", "B"].map((side) => `<div><h4>Estremo ${side}</h4>${r.results[side] ? facts({ Parametro: r.results[side].metric, Valore: `${r.results[side].value} ${r.results[side].unit}`, Note: r.results[side].notes, Ora: stamp(r.results[side].recordedAt) }) : `<p>In attesa della misura.</p>${s["uid" + side] === data.uid && s.state === "OPEN" ? button("Registra risultato", "result", `data-trial="${r.id}" data-side="${side}"`) : ""}`}</div>`).join("")}</div></article>`)}${s.conclusion ? `<p>Esito: ${esc(s.conclusion)}</p>` : ""}`,
            s.state === "OPEN"
              ? button("Prepara prova", "create-trial") +
                  button("Concludi sessione", "close-session")
              : "",
          )
        : "");
  }
  async function createSession() {
    const e = epoch,
      people = await apiFetch("/api/v1/workforce/roster/members");
    if (e !== epoch) return;
    editor(
      "Sessione ai due estremi",
      field("title") +
        field(
          "peerAntennaId",
          "",
          "text",
          true,
          state.antennas
            .filter((a) => a.id !== assetId)
            .map((a) => [a.id, a.name || a.id]),
        ) +
        field(
          "operatorAId",
          "",
          "text",
          true,
          people.map((p) => [p.operatorId, `A · ${p.name}`]),
        ) +
        field(
          "operatorBId",
          "",
          "text",
          true,
          people.map((p) => [p.operatorId, `B · ${p.name}`]),
        ),
      async (f) => {
        activeSession = await apiFetch(base() + "/sessions", {
          method: "POST",
          body: JSON.stringify({ ...Object.fromEntries(f), operationId: op() }),
        });
        await loadSessions();
      },
    );
  }
  async function trial(action, extra = {}) {
    const e = epoch,
      s = activeSession;
    const r = await apiFetch(`/api/v1/radio-pair-sessions/${s.id}/trials`, {
      method: "POST",
      body: JSON.stringify({
        expectedVersion: s.version,
        operationId: op(),
        action,
        ...extra,
      }),
    });
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
          editor(
            a === "resolve-issue"
              ? "Concludi problema"
              : "Rimuovi collegamento",
            field("reason", "", "textarea"),
            (f) =>
              save(
                a === "resolve-issue" ? "issueResolution" : "dependencyRemoval",
                { entryId: b.dataset.id, reason: f.get("reason") },
              ),
          );
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
          await apiFetch(`${base()}/shares/${b.dataset.id}`, {
            method: "DELETE",
          });
          await loadShares();
          break;
        case "accesses": {
          const rows = await apiFetch(
            `${base()}/shares/${b.dataset.id}/accesses`,
          );
          editor(
            "Ultimi 100 accessi",
            cards(
              rows,
              (r) =>
                `<p>${stamp(r.at)} · ${r.type === "FILE" ? "Allegato" : "Visualizzazione"}</p>`,
            ),
            async () => {},
          );
          break;
        }
        case "approve":
        case "reject":
          editor(
            "Decisione sulla correzione",
            field("reason", "", "textarea"),
            async (f) => {
              await apiFetch(base() + "/reviews", {
                method: "POST",
                body: JSON.stringify({
                  expectedVersion: data.version,
                  operationId: op(),
                  proposalId: b.dataset.id,
                  approve: a === "approve",
                  reason: f.get("reason"),
                }),
              });
              await reload();
            },
          );
          break;
        case "create-session":
          await createSession();
          break;
        case "session":
          activeSession = await apiFetch(
            `/api/v1/radio-pair-sessions/${b.dataset.id}`,
          );
          await loadSessions();
          break;
        case "create-trial":
          editor(
            "Prepara prova coordinata",
            field("label") + field("values", "", "textarea"),
            (f) =>
              trial("CREATE_TRIAL", {
                label: f.get("label"),
                settings: pairs(f.get("values")),
              }),
          );
          break;
        case "result":
          editor(
            "Misura estremo " + b.dataset.side,
            field("metric") +
              field("unit", "", "text", false) +
              field("value", "", "number") +
              field("notes", "", "textarea", false),
            (f) =>
              trial("RESULT", {
                trialId: b.dataset.trial,
                side: b.dataset.side,
                ...Object.fromEntries(f),
                value: Number(f.get("value")),
              }),
          );
          break;
        case "close-session":
          editor("Concludi prove", field("notes", "", "textarea"), (f) =>
            trial("CLOSE", { notes: f.get("notes") }),
          );
          break;
      }
    });
  }
  return { open };
}
