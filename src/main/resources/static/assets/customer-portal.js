"use strict";
(() => {
  let token=null, refreshToken=null, suite=null;
  const state={authRole:"CUSTOMER",sessionEpoch:0};
  const esc=value=>String(value??"").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;");
  const status=document.getElementById("portalStatus");
  async function apiFetch(path,options={},retry=true) {
    const response=await fetch(path,{...options,headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},signal:AbortSignal.timeout(20000)});
    if(response.status===401&&retry&&refreshToken) { const r=await fetch("/api/v1/auth/refresh",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({refreshToken}),signal:AbortSignal.timeout(20000)});const data=await r.json();if(!r.ok)throw new Error("Sessione scaduta: accedi nuovamente.");token=data.token;refreshToken=data.refreshToken||refreshToken;return apiFetch(path,options,false); }
    const data=await response.json();if(!response.ok)throw new Error(data.message||"Operazione non riuscita.");return data.data??data;
  }
  document.getElementById("portalLogin").onsubmit=async e=>{
    e.preventDefault();const button=e.target.querySelector("button");button.disabled=true;status.textContent="Accesso…";
    try { const fields=Object.fromEntries(new FormData(e.target));let data;if(await window.RadioTechFederation.initialize()){data=await window.RadioTechFederation.login(null,fields.email,fields.password);}else{const r=await fetch("/api/v1/auth/login",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(fields),signal:AbortSignal.timeout(20000)});data=await r.json();if(!r.ok)throw new Error(data.message||"Accesso non riuscito.");}if(data.role!=="CUSTOMER")throw new Error("Account cliente non abilitato. Contatta il responsabile.");token=data.token;refreshToken=data.refreshToken;e.target.reset();e.target.hidden=true;document.getElementById("portalApp").hidden=false;status.textContent="";
      if(!suite)suite=window.RadioTechPro({apiFetch,state,escapeHtml:esc,toast:(title,message)=>status.textContent=`${title}: ${message}`,switchView:view=>document.querySelectorAll(".view").forEach(v=>v.classList.toggle("active",v.id===`view-${view}`))});document.querySelector('[data-view="pro"]').click();
    } catch(error) { status.textContent=error.message; } finally {button.disabled=false;}
  };
  document.getElementById("portalLogout").onclick=()=>{window.RadioTechFederation.logout().catch(()=>{});token=null;refreshToken=null;state.sessionEpoch++;suite?.reset();document.getElementById("portalApp").hidden=true;document.getElementById("portalLogin").hidden=false;};
})();
