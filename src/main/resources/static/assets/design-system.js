"use strict";
(() => {
  const paths = {
    search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    antenna: '<circle cx="12" cy="9" r="2"/><path d="m9 21 3-10 3 10M8 17h8M6 4a7 7 0 0 0 0 10M18 4a7 7 0 0 1 0 10M3 1a11 11 0 0 0 0 16M21 1a11 11 0 0 1 0 16"/>',
    briefcase: '<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12a22 22 0 0 0 18 0M12 11v4"/>',
    wave: '<path d="M2 12h3l3-8 4 16 4-16 3 8h3"/>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
    box: '<path d="m12 3 9 5-9 5-9-5 9-5ZM3 8v9l9 5 9-5V8M12 13v9M7 5.8l9 5"/>',
    shield: '<path d="m12 2 8 3v6c0 5-4 8-8 11-4-3-8-6-8-11V5l8-3Z"/><path d="m8 12 3 3 5-6"/>',
    spark: '<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3ZM20 2v4M18 4h4"/>',
    people: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M19 21v-3a6 6 0 0 0-3-5"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    status: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M6 12h3l2-5 3 10 2-5h2"/>',
    refresh: '<path d="M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 14 6M18 18a8 8 0 0 1-14-6"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
    arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    alert: '<path d="m12 3 10 18H2L12 3ZM12 9v5M12 17v.1"/>'
  };
  const navIcons = { dashboard: 'grid', antennas: 'antenna', operations: 'briefcase', operators:'people', reports:'check', planning:'grid', workload:'briefcase', supplies:'box', tools: 'wave', notifications: 'bell', inventory: 'box', enterprise: 'shield', ai: 'spark', people: 'people', profile: 'user', system: 'status' };
  const navLabels = { dashboard: 'Panoramica', antennas: 'Infrastruttura', operations: 'Incarichi', operators:'Operatori', reports:'Centro report', planning:'Agenda interventi', workload:'Carico della squadra', supplies:'Approvvigionamenti', tools: 'Strumenti RF', notifications: 'Comunicazioni', inventory: 'Inventario', enterprise: 'Rischi e competenze', ai: 'Pianificazione preventiva', people: 'Squadra e sicurezza', profile: 'Profilo aziendale', system: 'Stato del sistema' };
  Object.assign(navIcons, { pro: 'grid', access: 'shield' });
  Object.assign(navLabels, { pro: 'Enterprise Pro', access: 'Sicurezza e accessi' });
  const svg = name => `<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">${paths[name] || paths.grid}</svg>`;
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');

  function initialize() {
    const app = document.getElementById('app');
    const sidebar = document.getElementById('sidebar');
    const main = document.getElementById('mainContent');
    const toggle = document.getElementById('mobileMenu');
    const scrim = document.getElementById('navScrim');
    const mobile = window.matchMedia('(max-width: 980px)');
    let activeView = null, queued = false, openBefore = false;
    let activeModal = null, previousFocus = null;
    const motion = 'IntersectionObserver' in window ? new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        entry.target.classList.remove('motion-pending');
        entry.target.classList.add('motion-visible');
        motion.unobserve(entry.target);
      });
    }, { threshold: 0, rootMargin: '0px 0px -18px 0px' }) : null;

    function organizeNavigation() {
      const nav=sidebar.querySelector('.nav');
      const groups=[['Operazioni',['dashboard','antennas','operations','planning','reports','operators','workload']],['Risorse',['inventory','supplies','notifications','tools']],['Analisi e gestione',['enterprise','people','ai','pro','access']]];
      const signature=[...nav.querySelectorAll('button[data-view]')].map(button=>button.dataset.view).sort().join(',');
      if(nav.dataset.grouped===signature)return;
      nav.dataset.grouped=signature;
      nav.querySelectorAll('.nav-group-label').forEach(label=>label.remove());
      for(const [title,views] of groups) {
        const buttons=views.map(view=>nav.querySelector(`button[data-view="${view}"]`)).filter(Boolean);
        if(!buttons.length)continue;
        const label=document.createElement('div');label.className='nav-group-label';label.textContent=title;
        nav.append(label,...buttons);
      }
    }
    function paintIcons() {
      organizeNavigation();
      document.querySelectorAll('[data-icon]:not([data-icon-ready])').forEach(element => {
        element.innerHTML = svg(element.dataset.icon);
        element.dataset.iconReady = 'true';
      });
      document.querySelectorAll('.nav button[data-view]').forEach(button => {
        if (!button.dataset.designed) {
          const icon = document.createElement('span'); icon.className = 'nav-icon'; icon.innerHTML = svg(navIcons[button.dataset.view]);
          const text = document.createElement('span'); text.textContent = navLabels[button.dataset.view] || button.textContent;
          button.replaceChildren(icon, text); button.dataset.designed = 'true';
        }
        if (button.classList.contains('active')) button.setAttribute('aria-current','page');
        else button.removeAttribute('aria-current');
      });
      const statIcons = ['antenna','people','wave','check','box','alert'];
      document.querySelectorAll('.stat-icon:not([data-designed])').forEach((icon,index) => {
        icon.innerHTML = svg(statIcons[index]); icon.dataset.designed = 'true';
      });
      const bell = document.getElementById('notificationBell');
      if (!bell.dataset.designed) {
        const count = document.getElementById('notificationCount');
        bell.innerHTML = svg('bell'); bell.append(count); bell.dataset.designed = 'true';
      }
    }

    function prepareViews() {
      const headers = {
        enterprise: ['Prevenzione e continuità','Il controllo che anticipa.','Incidenti, priorità operative e competenze: coordina le decisioni con una visione condivisa.'],
        ai: ['Pianificazione preventiva','Anticipa le scadenze.','Indicatori spiegabili e priorità operative per organizzare la manutenzione.'],
        people: ['Persone al centro','Una squadra, più consapevole.','Disponibilità, pause e segnalazioni. Uno spazio condiviso per lavorare con attenzione.']
      };
      document.querySelectorAll('.view:not([data-designed-head])').forEach(view => {
        if (headers[view.id.replace('view-','')] && !view.querySelector('.section-head')) {
          const data = headers[view.id.replace('view-','')];
          const header = document.createElement('div'); header.className = 'section-head';
          const group = document.createElement('div');
          const kicker = document.createElement('span'); kicker.className = 'section-kicker'; kicker.textContent = data[0];
          const title = document.createElement('h2'); title.textContent = data[1];
          const copy = document.createElement('p'); copy.textContent = data[2];
          group.append(kicker,title,copy); header.append(group); view.prepend(header);
        }
        view.dataset.designedHead = 'true';
      });
      const current = document.querySelector('.view.active');
      if (current && current !== activeView) {
        activeView = current;
        if (!reduced.matches && typeof current.animate === 'function') current.animate([
          { opacity: .2, transform: 'translateY(7px)' },{ opacity: 1, transform: 'translateY(0)' }
        ], { duration: 320, easing: 'cubic-bezier(.22,1,.36,1)' });
      }
      document.querySelectorAll('.view.active .panel:not([data-motion]), .view.active .stat:not([data-motion]), .view.active .workspace-link:not([data-motion]), .overview-head:not([data-motion])').forEach((element,index) => {
        element.dataset.motion = 'true';
        if (!motion || reduced.matches) return;
        element.style.setProperty('--motion-delay',`${Math.min(index % 6,3) * 45}ms`);
        element.classList.add('motion-pending'); motion.observe(element);
      });
    }

    function syncNavigation() {
      if ((!mobile.matches || !app.classList.contains('ready')) && sidebar.classList.contains('open')) sidebar.classList.remove('open');
      const open = mobile.matches && sidebar.classList.contains('open');
      scrim.hidden = !open; scrim.classList.toggle('open',open);
      toggle.setAttribute('aria-expanded',String(open));
      toggle.setAttribute('aria-label',open ? 'Chiudi navigazione' : 'Apri navigazione');
      sidebar.inert = mobile.matches && !open;
      main.inert = open;
      document.body.classList.toggle('menu-open',open);
      if (open && !openBefore) (sidebar.querySelector('.nav button.active') || sidebar.querySelector('.nav button'))?.focus({ preventScroll:true });
      if (!open && openBefore && sidebar.contains(document.activeElement)) main.focus({ preventScroll:true });
      openBefore = open;
    }
    function closeNavigation() {
      sidebar.classList.remove('open'); syncNavigation(); toggle.focus({ preventScroll:true });
    }
    document.getElementById('brandHome').addEventListener('click',event=>{
      event.preventDefault();
      sidebar.querySelector('[data-view="dashboard"]').click();
      main.focus({preventScroll:true});
      window.scrollTo({top:0,behavior:reduced.matches?'instant':'smooth'});
    });
    document.getElementById('sidebarClose').addEventListener('click',closeNavigation);
    scrim.addEventListener('click',closeNavigation);
    mobile.addEventListener('change',syncNavigation);
    new MutationObserver(syncNavigation).observe(sidebar,{ attributes:true, attributeFilter:['class'] });
    document.querySelectorAll('[data-workspace-link]').forEach(button => button.addEventListener('click',() => {
      document.querySelector(`.nav button[data-view="${button.dataset.workspaceLink}"]`)?.click();
    }));

    function syncModal() {
      const current = document.querySelector('.modal-backdrop.open .modal');
      if (current === activeModal) return;
      if (current) {
        previousFocus = document.activeElement; activeModal = current;
        current.setAttribute('role','dialog'); current.setAttribute('aria-modal','true');
        const heading = current.querySelector('.modal-head h3');
        if (heading) { heading.id ||= `${current.parentElement.id}-title`; current.setAttribute('aria-labelledby',heading.id); }
        (current.querySelector('input:not([type=hidden]),textarea,select') || current.querySelector('button'))?.focus({ preventScroll:true });
      } else {
        activeModal = null;
        if (previousFocus?.isConnected && !previousFocus.closest('[inert]')) previousFocus.focus({ preventScroll:true });
        previousFocus = null;
      }
    }
    document.querySelectorAll('.modal-backdrop').forEach(modal => new MutationObserver(syncModal).observe(modal,{attributes:true,attributeFilter:['class']}));
    document.addEventListener('keydown',event => {
      if (event.key === 'Escape' && openBefore) closeNavigation();
      const root = activeModal || (openBefore ? sidebar : null);
      if (event.key !== 'Tab' || !root) return;
      if(document.querySelector('dialog[open]'))return;
      const controls = [...root.querySelectorAll('button,a[href],input:not([type=hidden]),select,textarea,[tabindex="0"]')].filter(element => !element.disabled && element.getClientRects().length);
      const first = controls[0], last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    });
    document.addEventListener('focusin',event => {
      const panel = event.target.closest('.motion-pending');
      if (panel) { panel.classList.remove('motion-pending'); panel.classList.add('motion-visible'); motion?.unobserve(panel); }
    });
    reduced.addEventListener('change',() => {
      if (!reduced.matches) return;
      motion?.disconnect(); document.querySelectorAll('.motion-pending').forEach(element => element.classList.remove('motion-pending'));
      document.getAnimations().forEach(animation => animation.cancel());
    });
    function scheduleRefresh() {
      if (queued) return; queued = true;
      requestAnimationFrame(() => { queued = false; paintIcons(); prepareViews(); syncNavigation(); });
    }
    new MutationObserver(scheduleRefresh).observe(app,{ childList:true,subtree:true,attributes:true,attributeFilter:['class'] });
    paintIcons(); prepareViews(); syncNavigation(); syncModal();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',initialize,{once:true});
  else initialize();
})();

(() => {
  document.addEventListener('click', event => {
    const button = event.target.closest('button, a.btn');
    if (!button || button.disabled || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    button.classList.remove('press-glow');
    void button.offsetWidth;
    button.classList.add('press-glow');
    window.setTimeout(() => button.classList.remove('press-glow'), 700);
  });
})();
