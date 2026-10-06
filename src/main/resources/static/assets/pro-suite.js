"use strict";
window.RadioTechPro = function ({apiFetch,state,escapeHtml:esc,toast,switchView}) {
  const labels={ARCHIVE:"Archivia",GENERATE:"Genera incarico in scadenza",SUBMIT:"Invia per approvazione",APPROVE:"Approva",REJECT:"Rifiuta",RECEIVE:"Registra consegna",PUBLISH:"Pubblica",ACKNOWLEDGE:"Conferma presa in carico",LOAD:"Carica dal magazzino",RESERVE:"Riserva",CONSUME:"Registra consumo",RETURN:"Restituisci",ASSIGN:"Crea incarico",RESOLVE:"Chiudi",PAUSE:"Sospendi SLA",RESUME:"Riprendi SLA",REVOKE:"Revoca dispositivo",ENABLE:"Attiva integrazione",DISABLE:"Disattiva integrazione"};
  let catalog=[],current=null,records=[],editing=null,epoch=0,editingOperationId=null;
  const actionOperations=new Map(); const display=value=>value!=null&&typeof value==="object"?JSON.stringify(value):String(value??"");
  const section=document.createElement("section"); section.id="view-pro"; section.className="view";
  section.innerHTML=`<div class="panel enterprise-section"><div class="panel-head"><h3>Gestione Enterprise Pro</h3><button class="btn" id="proRefresh">Aggiorna</button></div><div class="panel-body"><label for="proModule">Sezione</label><select id="proModule"></select><p id="proStatus" role="status"></p><div class="actions"><button class="btn primary" id="proNew">Nuovo</button><button class="btn" id="proExport">Esporta CSV</button></div><input type="search" id="proSearch" placeholder="Cerca nei risultati" aria-label="Cerca"><form id="proForm" class="enterprise-form" hidden></form><div id="proRecords"></div></div></div>`;
  document.querySelector(".view").parentElement.append(section);
  const nav=document.createElement("button"); nav.dataset.view="pro"; nav.textContent="▦ Enterprise Pro"; const navContainer=document.querySelector(".nav");navContainer.insertBefore(nav,navContainer.querySelector('[data-view="system"]'));
  const $=id=>document.getElementById(id);
  async function load() {
    const revision=++epoch, session=state.sessionEpoch;
    $("proStatus").textContent="Caricamento…";
    try {
      if(!catalog.length) { catalog=await apiFetch("/api/v1/pro/catalog"); if(revision!==epoch||session!==state.sessionEpoch)return; $("proModule").innerHTML=catalog.map(m=>`<option value="${esc(m.id)}">${esc(m.title)}</option>`).join(""); }
      current=catalog.find(m=>m.id===$("proModule").value); if(!current)return;
      $("proNew").hidden=current.writable===false;
      const data=await apiFetch(`/api/v1/pro/${current.id}`); if(revision!==epoch||session!==state.sessionEpoch)return;
      records=data.records; $("proStatus").textContent=`${records.length} registrazioni${data.partial?" · Risultati parziali: limite 1000":""}`; render();
    } catch(e) { if(revision===epoch) $("proStatus").textContent=e.message; }
  }
  function render() {
    const search=$("proSearch").value.toLowerCase();
    $("proRecords").innerHTML=records.filter(r=>JSON.stringify(r).toLowerCase().includes(search)).map(r=>`<article class="task-row"><div style="min-width:0;overflow-wrap:anywhere"><strong>${esc(r.name)}</strong><p class="task-meta">${esc(r.status)} · Revisione ${esc(r.version)} · ${esc(r.id)}</p>${current.fields.filter(f=>r[f.key]!=null&&f.key!=="name").map(f=>`<p class="enterprise-summary"><strong>${esc(f.label)}:</strong> ${esc(display(r[f.key]))}</p>`).join("")}${Object.entries(r.summary||{}).map(([label,value])=>`<p class="enterprise-summary"><strong>${esc(label)}:</strong> ${esc(display(value))}</p>`).join("")}${r.responseOverdue||r.resolutionOverdue?'<p role="alert">⚠ SLA oltre scadenza</p>':""}<div class="actions">${current.writable===false?"":`<button class="btn" data-edit="${esc(r.id)}">Modifica</button>`}${current.actions.map(action=>`<button class="btn" data-action="${esc(action)}" data-id="${esc(r.id)}">${esc(labels[action]||action)}</button>`).join("")}</div></div></article>`).join("")||'<p class="enterprise-empty">Nessuna registrazione.</p>';
  }
  async function form(record=null) {
    editingOperationId=crypto.randomUUID();
    if(!current)return; const module=current.id,session=state.sessionEpoch; editing=record; const form=$("proForm"); form.hidden=false; form.innerHTML="Caricamento campi…";
    try {
      const optionEntries=await Promise.all(current.fields.filter(f=>f.type.startsWith("ref:")).map(async f=>[f.key,await apiFetch(`/api/v1/pro/options/${f.type.slice(4)}`)]));
      if(module!==current?.id||session!==state.sessionEpoch)return; const options=Object.fromEntries(optionEntries);
      form.innerHTML=`<h4>${record?"Modifica":"Nuova registrazione"} · ${esc(current.title)}</h4>`+current.fields.map(f=>{
        const value=record?.[f.key]??"",required=f.required?"required":"",id=`proField-${f.key}`;
        let input;
        if(f.type.startsWith("ref:")||f.type.startsWith("select:")) { const values=options[f.key]||f.type.slice(7).split(",").map(v=>({id:v,name:v})); input=`<select name="${esc(f.key)}" id="${id}" ${required}><option value="">Seleziona…</option>${values.map(v=>`<option value="${esc(v.id)}" ${String(value)===v.id?"selected":""}>${esc(v.name)}</option>`).join("")}</select>`; }
        else if(f.type==="textarea") input=`<textarea name="${esc(f.key)}" id="${id}" rows="4" maxlength="32000" ${required}>${esc(value)}</textarea>`;
        else { const numeric=["number","positive"].includes(f.type),type=numeric?"number":f.type==="datetime"?"datetime-local":f.type==="url"?"url":f.type==="email"?"email":"text"; const display=f.type==="datetime"&&value?new Date(new Date(value).getTime()-new Date(value).getTimezoneOffset()*60000).toISOString().slice(0,16):value; input=`<input name="${esc(f.key)}" id="${id}" type="${type}" value="${esc(display)}" ${numeric?'min="0" step="any"':'maxlength="500"'} ${required}>`; }
        return `<label for="${id}">${esc(f.label)}${f.required?" *":""}</label>${input}`;
      }).join("")+`<div class="actions"><button class="btn primary" type="submit">Salva</button><button class="btn" type="button" id="proCancel">Annulla</button></div><p id="proFormStatus" role="status"></p>`;
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
