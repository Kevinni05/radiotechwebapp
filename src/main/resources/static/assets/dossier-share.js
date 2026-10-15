(() => {
  "use strict";
  const token = location.hash.slice(1);
  history.replaceState(null, "", location.pathname);
  const esc = (v) =>
    String(v ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
  const labels = {
    identity: "Identità e dati tecnici",
    access: "Accesso al sito",
    configuration: "Configurazioni",
    measurements: "Misurazioni",
    dependencies: "Dipendenze",
    documents: "Documenti",
    photos: "Fotografie",
    components: "Componenti",
    issues: "Problemi",
    history: "Cronologia",
    address: "Indirizzo",
    contact: "Referente",
    hours: "Orari",
    parking: "Parcheggio",
    keyInstructions: "Ritiro chiavi",
    route: "Percorso",
    restrictions: "Restrizioni",
    metric: "Parametro",
    value: "Valore",
    unit: "Unità",
    low: "Minimo",
    high: "Massimo",
    measuredAt: "Misurata il",
    name: "Nome",
    model: "Modello",
    serial: "Matricola",
    label: "Descrizione",
    reason: "Motivazione",
    notes: "Note",
    createdAt: "Registrato il",
    status: "Stato",
    manufacturer: "Produttore",
    site: "Sede",
    code: "Codice",
    lat: "Latitudine",
    lng: "Longitudine",
    title: "Titolo",
    fileName: "Nome file",
    installedAt: "Installazione",
    warrantyUntil: "Garanzia",
    source: "Origine",
    outsideThreshold: "Fuori soglia",
    type: "Tipo",
  };
  const facts = (value) =>
    `<dl class="ds-facts">${Object.entries(value)
      .filter(
        ([k]) =>
          ![
            "id",
            "fileId",
            "annotations",
            "antennaId",
            "operatorName",
          ].includes(k),
      )
      .map(
        ([k, v]) =>
          `<div><dt>${esc(labels[k] || k)}</dt><dd>${v && typeof v === "object" ? facts(v) : esc(typeof v === "boolean" ? (v ? "Sì" : "No") : v)}</dd></div>`,
      )
      .join("")}</dl>`;
  let snapshot, until, expiryTimer;
  const blobs = [];
  function clear() {
    snapshot = null;
    document.getElementById("shareContent").replaceChildren();
    document.getElementById("shareStatus").textContent =
      "Collegamento scaduto o non disponibile.";
    blobs.forEach((u) => URL.revokeObjectURL(u));
  }
  async function api(fileId) {
    const r = await fetch("/api/v1/dossier-shares/read", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, ...(fileId ? { fileId } : {}) }),
      cache: "no-store",
      referrerPolicy: "no-referrer",
    });
    if (!r.ok) {
      clear();
      throw Error("Collegamento scaduto, revocato o non disponibile.");
    }
    return r.json();
  }
  async function load() {
    if (!/^[A-Za-z0-9_-]{43}$/.test(token))
      throw Error("Collegamento non valido.");
    const r = await api();
    snapshot = r.snapshot;
    until = Date.parse(r.expiresAt);
    document.querySelector("h1").textContent = snapshot.name;
    document.getElementById("shareStatus").textContent =
      `Copia del ${new Date(snapshot.snapshotAt).toLocaleString("it-IT")} · Scade ${new Date(until).toLocaleString("it-IT")}`;
    document.getElementById("shareContent").innerHTML = Object.entries(snapshot)
      .filter(([k]) => labels[k])
      .map(
        ([k, v]) =>
          `<section class="panel"><div class="panel-head"><h2>${esc(labels[k])}</h2></div><div class="panel-body">${Array.isArray(v) ? v.map((row) => `<article class="ds-record">${facts(row)}${row.fileId ? `<button class="btn" data-file="${esc(row.fileId)}" data-kind="${k}">${k === "photos" ? "Visualizza fotografia" : "Scarica documento"}</button>${k === "photos" ? (row.annotations || []).map((a) => `<p>Annotazione ${esc(a.type)}: ${esc(a.text || "Segno grafico")} · posizione ${Math.round(a.x * 100)}%, ${Math.round(a.y * 100)}%</p>`).join("") : ""}` : ""}</article>`).join("") || "<p>Nessun elemento.</p>" : facts(v)}</div></section>`,
      )
      .join("");
    expiryTimer = setTimeout(
      clear,
      Math.max(0, Math.min(until - Date.now(), 2147483647)),
    );
  }
  document.addEventListener("click", async (e) => {
    const b = e.target.closest("[data-file]");
    if (!b) return;
    b.disabled = true;
    try {
      if (!snapshot || Date.now() >= until) {
        clear();
        return;
      }
      const r = await api(b.dataset.file);
      if (!snapshot) return;
      const bytes = Uint8Array.from(atob(r.base64), (c) => c.charCodeAt(0));
      const u = URL.createObjectURL(
        new Blob([bytes], {
          type:
            b.dataset.kind === "photos"
              ? /\.png$/i.test(r.name)
                ? "image/png"
                : /\.webp$/i.test(r.name)
                  ? "image/webp"
                  : "image/jpeg"
              : "application/octet-stream",
        }),
      );
      blobs.push(u);
      if (b.dataset.kind === "photos") {
        const row = snapshot.photos.find(
          (row) => row.fileId === b.dataset.file,
        );
        const marks = (row.annotations || [])
          .map((m) => {
            const x = m.x * 1000,
              y = m.y * 1000,
              x2 = m.x2 * 1000,
              y2 = m.y2 * 1000;
            return (
              (m.type === "RECT"
                ? `<rect x="${Math.min(x, x2)}" y="${Math.min(y, y2)}" width="${Math.abs(x2 - x)}" height="${Math.abs(y2 - y)}"/>`
                : m.type === "ARROW"
                  ? `<line x1="${x}" y1="${y}" x2="${x2}" y2="${y2}" marker-end="url(#shareArrow)"/>`
                  : "") +
              `<text x="${x}" y="${Math.max(20, y - 10)}">${esc(m.text)}</text>`
            );
          })
          .join("");
        const view = document.createElement("div");
        view.innerHTML = `<div class="ds-annotator"><img src="${u}" alt="${esc(row.title)}"><svg viewBox="0 0 1000 1000" preserveAspectRatio="none"><defs><marker id="shareArrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L8,3 z" fill="#ffb454"/></marker></defs>${marks}</svg></div><a class="btn" href="${u}" download="${esc(r.name)}">Scarica originale</a>`;
        b.after(view);
        b.remove();
      } else {
        const a = document.createElement("a");
        a.href = u;
        a.download = r.name;
        a.click();
      }
    } catch (x) {
      document.getElementById("shareStatus").textContent = x.message;
    } finally {
      b.disabled = false;
    }
  });
  window.addEventListener("pagehide", () => {
    clearTimeout(expiryTimer);
    clear();
  });
  load().catch((e) => {
    document.getElementById("shareStatus").textContent = e.message;
  });
})();
