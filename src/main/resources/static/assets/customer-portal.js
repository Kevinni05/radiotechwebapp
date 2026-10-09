"use strict";
(() => {
  let token=null, refreshToken=null, suite=null;
  const state={authRole:"CUSTOMER",sessionEpoch:0};
  const esc=value=>String(value??"").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;");
  const status=document.getElementById("portalStatus");
  document.getElementById("portalBrandHome").onclick=event=>{
    if(!token)return;
    event.preventDefault();document.querySelector('[data-view="pro"]')?.click();
    window.scrollTo({top:0,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'});
  };
  async function apiFetch(path,options={},retry=true) {
    const session=state.sessionEpoch;
    const assertSession=()=>{
      if(session!==state.sessionEpoch||!token)throw new DOMException("Sessione terminata.","AbortError");
    };
    assertSession();
    const response=await fetch(path,{...options,headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},signal:AbortSignal.timeout(20000)});
    assertSession();
    if(response.status===401&&retry&&refreshToken) {
      const r=await fetch("/api/v1/auth/refresh",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({refreshToken}),signal:AbortSignal.timeout(20000)});
      const data=await r.json();
      assertSession();
      if(!r.ok)throw new Error("Sessione scaduta: accedi nuovamente.");
      token=data.token;
      refreshToken=data.refreshToken||refreshToken;
      return apiFetch(path,options,false);
    }
    const data=await response.json();
    assertSession();
    if(!response.ok)throw new Error(data.message||"Operazione non riuscita.");
    return data.data??data;
  }
  const loginForm=document.getElementById("portalLogin");
  const portalApp=document.getElementById("portalApp");
  loginForm.onsubmit=async event=>{
    event.preventDefault();
    const button=loginForm.querySelector("button");
    if(button.disabled)return;
    button.disabled=true;
    loginForm.setAttribute("aria-busy","true");
    status.textContent="Accesso…";
    try {
      const fields=Object.fromEntries(new FormData(loginForm));
      let data;
      if(await window.RadioTechFederation.initialize()) {
        data=await window.RadioTechFederation.login(null,fields.email,fields.password);
      } else {
        const response=await fetch("/api/v1/auth/login",{
          method:"POST",headers:{"Content-Type":"application/json"},
          body:JSON.stringify(fields),signal:AbortSignal.timeout(20000)
        });
        data=await response.json();
        if(!response.ok)throw new Error(data.message||"Accesso non riuscito.");
      }
      if(data.role!=="CUSTOMER")throw new Error("Account cliente non abilitato. Contatta il responsabile.");
      token=data.token;
      refreshToken=data.refreshToken;
      loginForm.reset();
      loginForm.hidden=true;
      portalApp.hidden=false;
      status.textContent="";
      if(!suite)suite=window.RadioTechPro({
        apiFetch,state,escapeHtml:esc,
        toast:(title,message)=>{if(token)status.textContent=`${title}: ${message}`;},
        switchView:view=>document.querySelectorAll(".view").forEach(element=>
          element.classList.toggle("active",element.id===`view-${view}`))
      });
      const destination=document.querySelector('[data-view="pro"]');
      destination.click();
      destination.focus();
    } catch(error) {
      status.textContent=error.message;
      status.focus();
    } finally {
      button.disabled=false;
      loginForm.removeAttribute("aria-busy");
    }
  };
  document.getElementById("portalLogout").onclick=()=>{
    window.RadioTechFederation.logout().catch(()=>{});
    token=null;
    refreshToken=null;
    state.sessionEpoch++;
    suite?.reset();
    portalApp.hidden=true;
    loginForm.hidden=false;
    status.textContent="";
    document.getElementById("portalEmail").focus();
  };
})();
