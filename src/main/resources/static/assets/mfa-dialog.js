/* Authentication secrets stay in this dialog and are removed on every exit. */
(() => {
  let active = false;
  window.RadioTechMfaDialog = ({title, description, label = 'Codice a 6 cifre', password = false, setup, submit}) => {
    if (active) return Promise.reject(new Error('Completa la verifica già aperta.'));
    active = true;
    return new Promise((resolve, reject) => {
      const previous = document.activeElement;
      const dialog = document.createElement('dialog');
      dialog.className = 'mfa-dialog';
      dialog.setAttribute('aria-labelledby', 'mfa-dialog-title');
      dialog.setAttribute('aria-describedby', 'mfa-dialog-description');
      const style = document.createElement('style');
      style.textContent = '.mfa-dialog{color:var(--text,#eee);background:var(--surface,#15151b);border:1px solid #52525b;border-radius:16px;padding:24px;width:min(440px,calc(100vw - 32px));max-height:calc(100dvh - 32px);overflow:auto;box-sizing:border-box}.mfa-dialog::backdrop{background:#000b}.mfa-dialog form{display:grid;gap:16px}.mfa-dialog h2,.mfa-dialog p{margin:0}.mfa-dialog input{box-sizing:border-box;width:100%;min-height:44px;background:#202027;color:#fff;border:1px solid #71717a;border-radius:8px;padding:10px}.mfa-dialog input:focus-visible,.mfa-dialog button:focus-visible{outline:2px solid #a78bfa;outline-offset:3px}.mfa-dialog label{display:grid;gap:8px}.mfa-dialog button{min-height:44px;padding:10px 16px;border-radius:8px;cursor:pointer}.mfa-dialog .mfa-actions{display:flex;flex-wrap:wrap;gap:12px}.mfa-dialog canvas{max-width:100%;height:auto;background:white}.mfa-dialog .mfa-key{overflow-wrap:anywhere;user-select:all}.mfa-dialog [role=status]{color:#fca5a5;min-height:1.5em}';
      const form = document.createElement('form');
      const heading = document.createElement('h2'); heading.id = 'mfa-dialog-title'; heading.textContent = title;
      const descriptionNode = document.createElement('p'); descriptionNode.id = 'mfa-dialog-description'; descriptionNode.textContent = description;
      const setupNode = document.createElement('div');
      const fieldLabel = document.createElement('label'); fieldLabel.textContent = label;
      const input = document.createElement('input'); input.required = true;
      input.type = password ? 'password' : 'text';
      input.autocomplete = password ? 'current-password' : 'one-time-code';
      if (!password) { input.inputMode = 'numeric'; input.pattern = '[0-9]{6}'; input.maxLength = 6; }
      fieldLabel.append(input);
      const status = document.createElement('p'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
      const actions = document.createElement('div'); actions.className = 'mfa-actions';
      const confirm = document.createElement('button'); confirm.type = 'submit'; confirm.textContent = 'Verifica e continua';
      const cancel = document.createElement('button'); cancel.type = 'button'; cancel.textContent = 'Annulla';
      const recovery = document.createElement('details');
      const summary = document.createElement('summary'); summary.textContent = 'Non riesco ad accedere';
      const help = document.createElement('p'); help.textContent = 'Controlla che data e ora del telefono siano automatiche e usa il codice più recente. Se hai perso il secondo fattore, annulla e contatta il responsabile aziendale: il ripristino richiede una verifica della tua identità. Non condividere password, chiavi o codici.';
      recovery.append(summary, help);
      actions.append(confirm, cancel); form.append(heading, descriptionNode, setupNode, fieldLabel, status, actions, recovery); dialog.append(style, form);
      let busy = false, done = false;
      const finish = (result, error) => {
        if (done) return; done = true; active = false;
        input.value = ''; setupNode.replaceChildren(); dialog.close(); dialog.remove();
        if (previous?.isConnected) previous.focus();
        if (error) reject(error); else resolve(result);
      };
      const abort = () => { if (!busy) finish(null, new DOMException('Verifica annullata.', 'AbortError')); };
      cancel.onclick = abort;
      dialog.addEventListener('cancel', event => { event.preventDefault(); abort(); });
      form.onsubmit = async event => {
        event.preventDefault(); if (busy || !form.reportValidity()) return;
        busy = true; confirm.disabled = true; cancel.disabled = true; status.textContent = 'Verifica in corso…';
        try { const result = await submit(input.value); finish(result); }
        catch (error) { input.value = ''; status.textContent = error?.message || 'Verifica non riuscita. Riprova.'; }
        finally { busy = false; confirm.disabled = false; cancel.disabled = false; if (!done) input.focus(); }
      };
      document.body.append(dialog); dialog.showModal(); input.focus();
      if (setup) Promise.resolve().then(() => setup(setupNode)).catch(() => finish(null, new Error('Configurazione MFA non disponibile. Riprova.')));
    });
  };
})();
