window.RadioTechChiefAccess = (() => {
  let configuration = null;
  async function config() {
    if (!configuration) configuration = fetch('/api/v1/auth/public-config', { cache: 'no-store', signal: AbortSignal.timeout(10000) })
      .then(async response => response.ok ? response.json() : {}).catch(() => ({}));
    return configuration;
  }
  async function open(intent = 'login', legacyPassword) {
    const popup = window.open('about:blank', 'radiotech-secure-access', 'popup,width=620,height=800');
    if (!popup) throw new Error('Consenti l’apertura della finestra di accesso protetto.');
    const options = await config();
    if (intent === 'password' && options.identityRequired === false && legacyPassword) {
      popup.close(); legacyPassword(); return null;
    }
    if (intent === 'password' && !options.identityOrigin && legacyPassword && options.identityRequired !== true) {
      popup.close(); legacyPassword(); return null;
    }
    if (!options.identityOrigin) { popup.close(); throw new Error('Il servizio accessi protetti deve essere configurato dal proprietario.'); }
    const origin = new URL(options.identityOrigin).origin;
    if (!origin.startsWith('https://')) { popup.close(); throw new Error('Indirizzo del servizio accessi non valido.'); }
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    const requestId = btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
    if (intent !== 'login') {
      popup.location.href = `${origin}/?intent=${encodeURIComponent(intent)}`;
      return null;
    }
    return new Promise((resolve, reject) => {
      let finished = false;
      const finish = (error, data) => {
        if (finished) return; finished = true;
        window.removeEventListener('message', listener); clearTimeout(timeout); clearInterval(closed);
        if (error) { popup.close(); reject(error); } else resolve(data);
      };
      const listener = event => {
        if (event.origin !== origin || event.source !== popup || event.data?.type !== 'radiotech:identity-session'
          || event.data.requestId !== requestId || typeof event.data.idToken !== 'string') return;
        finish(null, event.data.idToken);
      };
      const timeout = setTimeout(() => finish(new Error('Accesso protetto scaduto. Riprova.')), 300000);
      const closed = setInterval(() => { if (popup.closed) finish(new Error('Accesso protetto annullato.')); }, 1000);
      window.addEventListener('message', listener);
      popup.location.href = `${origin}/?intent=login&request=${encodeURIComponent(requestId)}`;
    });
  }
  function install({ apiFetch, toast, currentRole = () => '' }) {
    const profile = document.getElementById('view-profile');
    const panel = document.createElement('div'); panel.className = 'panel';
    panel.innerHTML = `<div class="panel-head"><div><h3>Altri profili Capo</h3><span>Account personali con gli stessi permessi</span></div><button class="btn" id="chiefApprovalPortal">Apri approvazioni protette</button></div><div class="panel-body"><p>Invita una persona tramite la sua email reale. Dovrà verificarla, configurare il secondo fattore e ricevere la tua approvazione protetta.</p><button class="btn" type="button" id="chiefRefreshIdentity">Riconferma la mia identità con MFA</button><form id="chiefInviteForm"><div class="form-grid"><div class="field"><label for="chiefInviteName">Nome completo</label><input id="chiefInviteName" maxlength="100" minlength="2" required></div><div class="field"><label for="chiefInviteEmail">Email del nuovo capo</label><input id="chiefInviteEmail" type="email" maxlength="254" required></div></div><div class="actions"><button class="btn primary" type="submit">Invia invito</button><button class="btn" id="chiefInviteRefresh" type="button">Aggiorna inviti</button></div></form><p id="chiefInviteStatus" role="status" aria-live="polite"></p><div id="chiefInviteList"></div></div>`;
    profile.append(panel);
    const syncRole = () => { panel.hidden = !['CAPO','CHIEF_EXECUTIVE','ADMIN','SUPER_ADMIN'].includes(currentRole()); };
    syncRole(); window.addEventListener('radiotech:identity-changed', syncRole);
    const $ = id => document.getElementById(id);
    const labels = { PENDING: 'Email e MFA da verificare', READY: 'In attesa della tua approvazione', ACTIVE: 'Account attivo',
      REVOKED: 'Invito revocato', EXPIRED: 'Invito scaduto', DELIVERY_FAILED: 'Email non recapitata', ACTIVATING: 'Attivazione in corso', GRANT_FAILED: 'Attivazione da riprovare nel servizio protetto' };
    async function refresh() {
      $('chiefInviteRefresh').disabled = true;
      try {
        const data = await apiFetch('/api/v1/chief-invitations');
        $('chiefInviteList').replaceChildren();
        for (const item of data.items || []) {
          const row = document.createElement('div'); row.className = 'task-row';
          const text = document.createElement('div'); text.textContent = `${item.name} · ${item.email} · ${labels[item.status] || item.status}`;
          row.append(text);
          if (['PENDING', 'READY'].includes(item.status)) {
            const cancel = document.createElement('button'); cancel.className = 'mini-btn danger'; cancel.textContent = 'Revoca invito';
            cancel.onclick = async () => { cancel.disabled = true; try { await apiFetch(`/api/v1/chief-invitations/${encodeURIComponent(item.id)}`, { method: 'DELETE' }); await refresh(); } catch (e) { $('chiefInviteStatus').textContent = e.message; cancel.disabled = false; } };
            row.append(cancel);
          }
          $('chiefInviteList').append(row);
        }
        $('chiefInviteStatus').textContent = data.items?.length ? '' : 'Nessun invito ancora inviato.';
      } catch (e) { $('chiefInviteStatus').textContent = e.message; }
      finally { $('chiefInviteRefresh').disabled = false; }
    }
    $('chiefInviteForm').onsubmit = async event => {
      event.preventDefault(); event.submitter.disabled = true;
      try {
        await apiFetch('/api/v1/chief-invitations', { method: 'POST', body: JSON.stringify({ name: $('chiefInviteName').value.trim(), email: $('chiefInviteEmail').value.trim() }) });
        $('chiefInviteForm').reset(); await refresh(); $('chiefInviteStatus').textContent = 'Invito inviato. Nessun privilegio verrà assegnato prima della tua approvazione protetta.';
      } catch (e) { $('chiefInviteStatus').textContent = e.message; }
      finally { event.submitter.disabled = false; }
    };
    $('chiefInviteRefresh').onclick = refresh;
    $('chiefApprovalPortal').onclick = () => open('owner').catch(e => toast('Accessi protetti', e.message, 'error'));
    $('chiefRefreshIdentity').onclick = async event => {
      event.target.disabled = true;
      try {
        const idToken = await open('login');
        const response = await fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ idToken }) });
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || 'Accesso non autorizzato.');
        window.dispatchEvent(new CustomEvent('radiotech:sso', { detail: { ...result, token: idToken } }));
        $('chiefInviteStatus').textContent = 'Identità riconfermata. Puoi inviare gli inviti.';
      } catch (e) { $('chiefInviteStatus').textContent = e.message; }
      finally { event.target.disabled = false; }
    };
    return { syncRole, reset() { $('chiefInviteForm').reset(); $('chiefInviteList').replaceChildren(); $('chiefInviteStatus').textContent = ''; syncRole(); } };
  }
  return { config, open, install };
})();
