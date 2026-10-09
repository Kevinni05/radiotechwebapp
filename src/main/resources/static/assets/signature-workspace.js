"use strict";

/* Workspace navigation and private, tab-scoped notes. No remote note storage. */
window.RadioTechSignatureWorkspace = function ({ state, switchView, initials }) {
  const $ = id => document.getElementById(id);
  const editor = $('sessionNotesEditor');
  const status = $('sessionNotesStatus');
  const dialog = $('workspaceCommandDialog');
  const results = $('workspaceCommandResults');
  const search = $('workspaceCommandInput');
  const prefix = 'radiotech_session_notes_v1:';
  let owner = null, timer = null, updatedAt = null, dirty = false;
  let commands = [], returnFocus = null;
  const identity = () => {
    const user = state.user;
    const actor = user?.uid || user?.id || user?.email;
    return state.token && actor && user?.tenantId ? prefix + JSON.stringify([user.tenantId, actor]) : null;
  };
  const authorized = button => button && !button.hidden && !button.disabled && !button.closest('[hidden]:not(.workspace-menu)');
  const visible = button => authorized(button) && getComputedStyle(button).display !== 'none';
  const setStatus = (text, error = false) => { status.textContent = text; status.dataset.error = String(error); $('sessionNotesRetry').hidden = !(error && dirty && owner); };
  function metadata() {
    $('sessionNotesCount').textContent = `${editor.value.length} / 8000`;
    $('sessionNotesClear').disabled = !owner || !editor.value;
    $('sessionNotesUpdated').textContent = updatedAt ? `Ultima modifica ${new Date(updatedAt).toLocaleString('it-IT', { dateStyle: 'short', timeStyle: 'short' })}` : 'Nessuna modifica';
  }
  function save() {
    clearTimeout(timer); timer = null;
    if (!owner || !dirty) return;
    if (identity() !== owner) { syncIdentity(); return; }
    try {
      const timestamp = Date.now();
      if (editor.value) sessionStorage.setItem(owner, JSON.stringify({ text: editor.value, updatedAt: timestamp }));
      else sessionStorage.removeItem(owner);
      updatedAt = editor.value ? timestamp : null;
      dirty = false;
      setStatus(editor.value ? 'Salvato nella sessione' : 'Nessuna nota');
      metadata();
    } catch (_) {
      setStatus('Salvataggio non riuscito. La nota resta aperta; premi Riprova.', true);
    }
  }
  function syncIdentity() {
    const next = identity();
    const user = state.user || {};
    const name = user.name || user.displayName || user.fullName || user.email || 'Account';
    $('topbarName').textContent = name;
    $('topbarAvatar').textContent = initials(name);
    $('topbarRole').textContent = window.RadioTechItalian?.label(state.authRole || user.role) || user.role || 'Account';
    $('topbarAccountEmail').textContent = user.email || name;
    if (next === owner) return;
    clearTimeout(timer); timer = null; dirty = false;
    owner = next; editor.value = ''; updatedAt = null;
    editor.disabled = !owner;
    closeMenus();
    if (dialog.open) dialog.close();
    if ($('sessionNotesConfirm').open) $('sessionNotesConfirm').close();
    if (owner) {
      try {
        const raw = sessionStorage.getItem(owner);
        const note = raw ? JSON.parse(raw) : null;
        if (note && typeof note.text === 'string') {
          editor.value = note.text.slice(0, 8000);
          updatedAt = Number.isFinite(note.updatedAt) ? note.updatedAt : null;
        }
        setStatus(editor.value ? 'Salvato nella sessione' : 'Nessuna nota');
      } catch (_) { setStatus('Storage non disponibile. Le note potrebbero non restare dopo un refresh.', true); }
    } else setStatus('Identità e tenant verificati richiesti per usare le note.');
    metadata();
  }
  function reset() {
    clearTimeout(timer); timer = null;
    if (owner) { try { sessionStorage.removeItem(owner); } catch (_) { /* An inaccessible key cannot be read by another identity. */ } }
    owner = null; dirty = false; updatedAt = null; editor.value = ''; editor.disabled = true;
    $('topbarName').textContent = 'Account'; $('topbarAccountEmail').textContent = '—'; $('topbarAvatar').textContent = '—'; $('topbarRole').textContent = '—';
    setStatus('Accedi per usare le note.'); metadata(); closeMenus();
    if (dialog.open) dialog.close();
    if ($('sessionNotesConfirm').open) $('sessionNotesConfirm').close();
  }
  editor.addEventListener('input', () => {
    if (identity() !== owner) { syncIdentity(); return; }
    dirty = true; metadata(); setStatus('Salvataggio in corso…');
    clearTimeout(timer); timer = setTimeout(save, 350);
  });
  $('sessionNotesRetry').addEventListener('click', save);
  editor.addEventListener('blur', save);
  window.addEventListener('pagehide', save);
  document.addEventListener('visibilitychange', () => { if (document.hidden) save(); });
  // Native dialog provides focus containment, Escape and screen-reader semantics.
  const confirm = document.createElement('dialog');
  confirm.id = 'sessionNotesConfirm'; confirm.className = 'workspace-notes-dialog';
  confirm.setAttribute('aria-labelledby', 'sessionNotesConfirmTitle');
  confirm.innerHTML = '<h2 id="sessionNotesConfirmTitle">Svuotare le note rapide?</h2><p>Gli appunti di questa identità saranno rimossi dalla sessione della scheda.</p><div class="actions"><button class="btn" id="sessionNotesCancel" type="button" autofocus>Annulla</button><button class="btn danger" id="sessionNotesDelete" type="button">Svuota note</button></div>';
  document.body.append(confirm);
  $('sessionNotesClear').addEventListener('click', () => { save(); confirm.showModal(); });
  $('sessionNotesCancel').addEventListener('click', () => confirm.close());
  $('sessionNotesDelete').addEventListener('click', () => {
    if (!owner || identity() !== owner) { confirm.close(); syncIdentity(); return; }
    clearTimeout(timer); timer = null;
    try { sessionStorage.removeItem(owner); editor.value = ''; dirty = false; updatedAt = null; setStatus('Note svuotate'); metadata(); confirm.close(); editor.focus(); }
    catch (_) { confirm.close(); setStatus('Impossibile svuotare lo storage. Gli appunti sono ancora presenti.', true); }
  });
  confirm.addEventListener('close', () => { if (!editor.disabled) editor.focus(); });

  const menus = [[$('workspaceActionsBtn'), $('workspaceActionsMenu')], [$('workspaceAccountBtn'), $('workspaceAccountMenu')]];
  function closeMenus(restore = false) {
    menus.forEach(([button, menu]) => { if (!menu.hidden && restore) button.focus(); menu.hidden = true; button.setAttribute('aria-expanded', 'false'); });
  }
  menus.forEach(([button, menu]) => {
    button.addEventListener('click', () => {
      const open = menu.hidden; closeMenus();
      if (open) { menu.hidden = false; button.setAttribute('aria-expanded', 'true'); menu.querySelector('button:not([hidden])')?.focus(); }
    });
    menu.addEventListener('click', event => { if (event.target.closest('button')) closeMenus(); });
    menu.addEventListener('keydown', event => {
      const items = [...menu.querySelectorAll('button')].filter(visible);
      const index = items.indexOf(document.activeElement);
      if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
        event.preventDefault();
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
        items[next]?.focus();
      }
      if (event.key === 'Escape') { event.preventDefault(); closeMenus(true); }
      if (event.key === 'Tab') closeMenus();
    });
  });
  document.addEventListener('click', event => { if (!event.target.closest('.workspace-menu-anchor')) closeMenus(); });
  function navigate(view) {
    save(); closeMenus();
    const nav = [...document.querySelectorAll('.nav button[data-view]')].find(button => button.dataset.view === view && visible(button));
    if (nav) nav.click(); else switchView(view);
  }
  $('workspaceReportsBtn').addEventListener('click', () => navigate('reports'));
  $('workspaceProfileBtn').addEventListener('click', () => navigate('profile'));
  $('workspaceSystemBtn').addEventListener('click', () => navigate('system'));
  function collectCommands() {
    const entries = [...document.querySelectorAll('.nav button[data-view]')].filter(visible).map(button => {
      const copy = button.cloneNode(true); copy.querySelectorAll('.nav-icon,svg').forEach(icon => icon.remove());
      return { label: copy.textContent.trim(), category: 'Sezione', button, available: () => visible(button) };
    });
    for (const [id, label] of [['quickAntennaBtn', 'Nuova antenna'], ['quickNotificationBtn', 'Invia notifica'], ['refreshBtn', 'Aggiorna dati'], ['newInventoryItemBtn', 'Nuovo articolo magazzino'], ['newOperatorBtn', 'Nuovo operatore'], ['editProfileBtn', 'Modifica profilo']]) {
      const button = $(id); if (authorized(button)) entries.push({label, category: 'Azione', button, available: () => authorized(button)});
    }
    return entries;
  }
  function renderCommands() {
    const term = search.value.trim().toLocaleLowerCase('it');
    commands = collectCommands().filter(command => command.label.toLocaleLowerCase('it').includes(term));
    results.replaceChildren();
    if (!commands.length) { const empty = document.createElement('p'); empty.className = 'empty'; empty.textContent = 'Nessuna sezione o azione disponibile con questo nome.'; results.append(empty); return; }
    commands.forEach(command => {
      const button = document.createElement('button'); button.type = 'button'; button.className = 'workspace-command-result';
      const label = document.createElement('span'); label.textContent = command.label;
      const category = document.createElement('small'); category.textContent = command.category;
      button.append(label, category);
      button.addEventListener('click', () => { if (!command.available()) return; dialog.close(); save(); command.button.click(); });
      results.append(button);
    });
  }
  function openSearch() {
    if (!state.token || !$('app').classList.contains('ready') || document.querySelector('dialog[open]') || document.querySelector('.modal-backdrop.open')) return;
    closeMenus(); returnFocus = document.activeElement; search.value = ''; renderCommands(); dialog.showModal(); search.focus();
  }
  $('workspaceShortcut').textContent = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘ K' : 'Ctrl K';
  $('workspaceSearchBtn').addEventListener('click', openSearch);
  $('workspaceCommandClose').addEventListener('click', () => dialog.close());
  // Search inputs consume Escape to clear their value before native dialog cancellation.
  dialog.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); dialog.close(); }
  });
  dialog.addEventListener('close', () => { if (returnFocus?.isConnected && $('app').classList.contains('ready')) returnFocus.focus(); });
  dialog.addEventListener('click', event => { if (event.target === dialog) { const r = dialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close(); } });
  search.addEventListener('input', renderCommands);
  search.addEventListener('keydown', event => {
    if (event.key === 'ArrowDown') { event.preventDefault(); results.querySelector('button')?.focus(); }
    if (event.key === 'Enter') { event.preventDefault(); results.querySelector('button')?.click(); }
  });
  results.addEventListener('keydown', event => {
    const items = [...results.querySelectorAll('button')]; const index = items.indexOf(document.activeElement);
    if (event.key === 'ArrowDown') { event.preventDefault(); items[(index + 1) % items.length]?.focus(); }
    if (event.key === 'ArrowUp') { event.preventDefault(); if (index <= 0) search.focus(); else items[index - 1]?.focus(); }
  });
  document.addEventListener('keydown', event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && $('app').classList.contains('ready')) { event.preventDefault(); if (dialog.open) dialog.close(); else openSearch(); }
    if (event.key === 'Escape') closeMenus(true);
  });
  return { syncIdentity, reset, onNavigate() { save(); closeMenus(); } };
};
