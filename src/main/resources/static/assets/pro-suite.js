"use strict";
window.RadioTechPro = function ({apiFetch,state,escapeHtml:esc,toast,switchView}) {
  const customer=state.authRole==="CUSTOMER";
  const labels={ARCHIVE:"Archivia",GENERATE:"Genera incarico in scadenza",SUBMIT:"Invia per approvazione",APPROVE:"Approva",REJECT:"Rifiuta",RECEIVE:"Registra consegna",PUBLISH:"Pubblica",ACKNOWLEDGE:"Conferma presa in carico",LOAD:"Carica dal magazzino",RESERVE:"Riserva",CONSUME:"Registra consumo",RETURN:"Restituisci",ASSIGN:"Crea incarico",RESOLVE:"Chiudi",PAUSE:"Sospendi SLA",RESUME:"Riprendi SLA",REVOKE:"Revoca dispositivo",ENABLE:"Attiva integrazione",DISABLE:"Disattiva integrazione"};
  let catalog=[],current=null,records=[],editing=null,epoch=0,editingOperationId=null;
  const actionOperations=new Map(); const display=value=>value!=null&&typeof value==="object"?JSON.stringify(value):String(value??"");
  const section=document.createElement("section"); section.id="view-pro"; section.className="view";
  section.innerHTML=`<header class="section-head workspace-heading"><div><span class="section-kicker">${customer?"Portale clienti":"Gestione aziendale"}</span><h2>${customer?"Il tuo spazio":"Enterprise Pro"}</h2><p>${customer?"Le tue sedi, le richieste di assistenza e i documenti condivisi.":"Organizza clienti, risorse e processi in un unico spazio di lavoro."}</p></div><button class="btn" id="proRefresh"><span data-icon="refresh"></span>Aggiorna</button></header><div class="panel pro-workspace"><div class="pro-toolbar"><div class="pro-field"><label for="proModule">${customer?"Cosa vuoi consultare?":"Area di gestione"}</label><select id="proModule"></select></div><div class="pro-field"><label for="proSearch">Cerca nelle registrazioni</label><input type="search" id="proSearch" placeholder="Nome, stato o riferimento…"></div><div class="actions"><button class="btn primary" id="proNew">＋ Nuovo</button><button class="btn" id="proExport">Esporta CSV</button></div></div><div class="pro-context"><h3 id="proTitle">Registrazioni</h3><p id="proStatus" role="status" aria-live="polite"></p></div><form id="proForm" class="enterprise-form" hidden></form><div id="proRecords"></div></div>`;
  const container=document.querySelector(".view").parentElement;
  container.insertBefore(section,container.querySelector(":scope > .footer"));
  const nav=document.createElement("button"); nav.dataset.view="pro"; nav.textContent=customer?"Le tue attività":"▦ Enterprise Pro"; const navContainer=document.querySelector(".nav");navContainer.insertBefore(nav,navContainer.querySelector('[data-view="system"]'));
  const $=id=>document.getElementById(id);
  async function load() {
    const revision=++epoch, session=state.sessionEpoch;
    $("proStatus").textContent="Caricamento…";
    try {
      if(!catalog.length) { catalog=await apiFetch("/api/v1/pro/catalog"); if(revision!==epoch||session!==state.sessionEpoch)return; $("proModule").innerHTML=catalog.map(m=>`<option value="${esc(m.id)}">${esc(m.title)}</option>`).join(""); }
      current=catalog.find(m=>m.id===$("proModule").value); if(!current)return;
      $("proTitle").textContent=current.title;
      $("proNew").hidden=current.writable===false;
      const data=await apiFetch(`/api/v1/pro/${current.id}`); if(revision!==epoch||session!==state.sessionEpoch)return;
      records=data.records; $("proStatus").textContent=`${records.length} registrazioni${data.partial?" · Risultati parziali: limite 1000":""}`; render();
    } catch(e) { if(revision===epoch) $("proStatus").textContent=e.message; }
  }
  function render() {
    const search=$("proSearch").value.toLowerCase();
    $("proRecords").innerHTML=records.filter(r=>JSON.stringify(r).toLowerCase().includes(search)).map(r=>`<article class="pro-record"><div class="pro-record-head"><h4>${esc(r.name)}</h4><span class="badge">${esc(window.RadioTechItalian.label(r.status))}</span></div><p class="task-meta">Revisione ${esc(r.version)} · ${esc(r.id)}</p><dl class="pro-details">${current.fields.filter(f=>r[f.key]!=null&&f.key!=="name").map(f=>`<div><dt>${esc(f.label)}</dt><dd>${esc(display(r[f.key]))}</dd></div>`).join("")}${Object.entries(r.summary||{}).map(([label,value])=>`<div><dt>${esc(label)}</dt><dd>${esc(display(value))}</dd></div>`).join("")}</dl>${r.responseOverdue||r.resolutionOverdue?'<p class="pro-warning" role="alert">⚠ SLA oltre scadenza</p>':""}<div class="actions pro-record-actions">${current.writable===false?"":`<button class="btn" data-edit="${esc(r.id)}">Modifica</button>`}${current.actions.map(action=>`<button class="btn" data-action="${esc(action)}" data-id="${esc(r.id)}">${esc(labels[action]||action)}</button>`).join("")}</div></article>`).join("")||`<div class="enterprise-empty"><span data-icon="box"></span><strong>${search?"Nessun risultato":"Ancora nessuna registrazione"}</strong><p>${search?"Prova con un altro nome o riferimento.":"Le registrazioni di questa area appariranno qui."}</p></div>`;
  }
  async function form(record=null) {
    editingOperationId=crypto.randomUUID();
    if(!current)return; const module=current.id,session=state.sessionEpoch; editing=record; const form=$("proForm"); form.hidden=false; form.innerHTML="Caricamento campi…";
    try {
      const optionEntries=await Promise.all(current.fields.filter(f=>f.type.startsWith("ref:")).map(async f=>[f.key,await apiFetch(`/api/v1/pro/options/${f.type.slice(4)}`)]));
      if(module!==current?.id||session!==state.sessionEpoch)return; const options=Object.fromEntries(optionEntries);
      form.innerHTML=`<div class="pro-form-heading"><span class="section-kicker">${esc(current.title)}</span><h4>${record?"Modifica registrazione":"Nuova registrazione"}</h4><p>I campi contrassegnati con * sono obbligatori.</p></div>`+current.fields.map(f=>{
        const value=record?.[f.key]??"",required=f.required?"required":"",id=`proField-${f.key}`;
        let input;
        if(f.type.startsWith("ref:")||f.type.startsWith("select:")) { const values=options[f.key]||f.type.slice(7).split(",").map(v=>({id:v,name:v})); input=`<select name="${esc(f.key)}" id="${id}" ${required}><option value="">Seleziona…</option>${values.map(v=>`<option value="${esc(v.id)}" ${String(value)===v.id?"selected":""}>${esc(v.name)}</option>`).join("")}</select>`; }
        else if(f.type==="textarea") input=`<textarea name="${esc(f.key)}" id="${id}" rows="4" maxlength="32000" ${required}>${esc(value)}</textarea>`;
        else { const numeric=["number","positive"].includes(f.type),type=numeric?"number":f.type==="datetime"?"datetime-local":f.type==="url"?"url":f.type==="email"?"email":"text"; const display=f.type==="datetime"&&value?new Date(new Date(value).getTime()-new Date(value).getTimezoneOffset()*60000).toISOString().slice(0,16):value; input=`<input name="${esc(f.key)}" id="${id}" type="${type}" value="${esc(display)}" ${numeric?'min="0" step="any"':'maxlength="500"'} ${required}>`; }
        return `<div class="pro-field${f.type==="textarea"?" pro-field-wide":""}"><label for="${id}">${esc(f.label)}${f.required?" *":""}</label>${input}</div>`;
      }).join("")+`<div class="actions pro-form-footer"><button class="btn primary" type="submit">Salva</button><button class="btn" type="button" id="proCancel">Annulla</button></div><p id="proFormStatus" class="pro-field-wide" role="status" aria-live="polite"></p>`;
      $("proCancel").onclick=()=>{form.hidden=true;editing=null;};
    } catch(e) { form.textContent=e.message; }
  }
  $("proForm").onsubmit=async e=>{
    e.preventDefault(); const button=e.target.querySelector('button[type="submit"]'); if(button.disabled)return; button.disabled=true;
    const fields=Object.fromEntries(new FormData(e.target)); for(const f of current.fields) { if(!fields[f.key])delete fields[f.key]; else if(["number","positive"].includes(f.type))fields[f.key]=Number(fields[f.key]); else if(f.type==="datetime")fields[f.key]=new Date(fields[f.key]).toISOString(); }
    try { await apiFetch(`/api/v1/pro/${current.id}${editing?`/${editing.id}`:""}`,{method:editing?"PUT":"POST",body:JSON.stringify({fields,expectedVersion:editing?.version||0,operationId:editingOperationId})}); e.target.hidden=true;editing=null;await load(); } catch(error) { $("proFormStatus").textContent=error.message; } finally { button.disabled=false; }
  };
  $("proRecords").onclick=async e=>{
    const button=e.target.closest("button"); if(!button)return;
    if(button.dataset.edit) { if(current.writable===false)return; await form(records.find(r=>r.id===button.dataset.edit)); return; }
    const record=records.find(r=>r.id===button.dataset.id),action=button.dataset.action; if(!record||button.disabled)return;
    let quantity=0,reason="";
    if(["LOAD","RESERVE","CONSUME","RETURN"].includes(action)) { const answer=prompt("Quantità intera:"); if(answer===null)return; quantity=Number(answer); if(!Number.isInteger(quantity)||quantity<=0) { toast("Quantità non valida","Inserisci un intero positivo","error");return; } }
    if(action==="PAUSE") { reason=prompt("Motivo della sospensione:"); if(!reason)return; }
    if(!confirm(`${labels[action]}: ${record.name}?`))return; button.disabled=true;
    const key=JSON.stringify([current.id,record.id,record.version,action,quantity,reason]);if(!actionOperations.has(key))actionOperations.set(key,crypto.randomUUID());
    try { await apiFetch(`/api/v1/pro/${current.id}/${record.id}/actions`,{method:"POST",body:JSON.stringify({action,quantity,reason,expectedVersion:record.version,operationId:actionOperations.get(key)})});await load(); } catch(error) { toast("Azione non riuscita",error.message,"error"); } finally {button.disabled=false;}
  };
  $("proExport").onclick=()=>{ if(!current)return; const keys=["id","status",...current.fields.map(f=>f.key),"summary"]; const cell=v=>'"'+display(v).replace(/^[=+@-]/,"'$&").replaceAll('"','""')+'"'; const blob=new Blob(["\ufeff"+[keys,...records.map(r=>keys.map(k=>r[k]))].map(row=>row.map(cell).join(";")).join("\r\n")],{type:"text/csv;charset=utf-8"}); const url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download=`radiotech-${current.id}.csv`;a.click();URL.revokeObjectURL(url); };
  nav.onclick=()=>{switchView("pro");load();}; $("proRefresh").onclick=load; $("proNew").onclick=()=>form(); $("proSearch").oninput=render;
  $("proModule").onchange=()=>{$("proForm").hidden=true;editing=null;load();};
  return {reset(){epoch++;actionOperations.clear();catalog=[];records=[];current=null;editing=null;$("proRecords").replaceChildren();$("proForm").replaceChildren();$("proForm").hidden=true;}};
};
