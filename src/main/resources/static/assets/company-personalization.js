"use strict";
window.RadioTechPersonalization = (() => {
  const $=id=>document.getElementById(id), root=document.documentElement;
  const defaults={name:document.body.dataset.brandName||'RadioTech',subtitle:document.body.dataset.brandSubtitle||'Gestione operativa',copyright:document.querySelector('.login-copyright')?.textContent||'',welcomeTitle:document.querySelector('.overview-head h2')?.textContent||'',welcomeDescription:document.querySelector('.overview-head p')?.textContent||'',logo:'',supportEmail:'',colors:{},labels:{}};
  let prefs={mode:'dark',contrast:false,motion:false,readable:false,scale:'100'}, settings=structuredClone(defaults), saved=structuredClone(defaults), photo, bindings=[],ctx,owner='',revision=0,editVersion=0,dirty=false;
  const allowed=()=>['ADMIN','SUPER_ADMIN','CHIEF_EXECUTIVE','CAPO'].includes(ctx?.state.authRole||ctx?.state.user?.role);
  const system=matchMedia('(prefers-color-scheme: light)');
  try { prefs={...prefs,...JSON.parse(localStorage.getItem('rt-appearance')||'{}')}; } catch(_) {}
  function applyPrefs() {
    root.dataset.scale=prefs.scale;
    root.dataset.appearance=prefs.mode==='system'?(system.matches?'light':'dark'):prefs.mode;
    root.dataset.contrast=prefs.contrast?'high':'normal';root.dataset.motion=prefs.motion?'reduced':'normal';root.dataset.readable=String(!!prefs.readable);
    // Browser zoom enlarges labels and controls together rather than overflowing fixed cards.
    document.body.style.zoom=String(Number(prefs.scale)/100);
    for(const [id,key] of [['highContrast','contrast'],['reduceMotion','motion'],['readableFont','readable']])if($(id))$(id).checked=!!prefs[key];
    if($('textScale'))$('textScale').value=prefs.scale;
    document.querySelectorAll('[data-mode]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.mode===prefs.mode)));
    applyColors();
  }
  const colors={accent:['--primary','--primary-2','--brand-accent'],background:['--bg','--brand-background'],surface:['--panel','--panel-solid','--brand-surface'],text:['--text','--brand-text'],muted:['--muted','--muted-2','--brand-muted'],border:['--border','--glass-border','--brand-border'],success:['--green','--brand-success'],warning:['--yellow','--brand-warning'],danger:['--red','--brand-danger']};
  function applyColors() {
    for(const vars of Object.values(colors))for(const name of vars)root.style.removeProperty(name);
    // High contrast intentionally takes precedence over a client's low-contrast palette.
    for(const name of [...root.style])if(name.startsWith('--rt-'))root.style.removeProperty(name);
    if(prefs.contrast)return;
    for(const [key,vars] of Object.entries(colors)){const stored=root.dataset.appearance==='light'?'light'+key[0].toUpperCase()+key.slice(1):key,value=settings.colors?.[stored];if(/^#[a-f0-9]{6}$/i.test(value))for(const name of vars)root.style.setProperty(name,value);}
    const accent=settings.colors?.[root.dataset.appearance==='light'?'lightAccent':'accent'];
    if(accent)for(const hex of ['ff636309','ff63631a','ff636308','ff63632c','ff636330','ff63631c','ff636370','ff636316','ff63634d','ff636320','ff63633d'])root.style.setProperty('--rt-'+hex,`color-mix(in srgb, ${accent} ${parseInt(hex.slice(-2),16)/255*100}%, transparent)`);
  }
  applyPrefs(); system.addEventListener('change',()=>{if(prefs.mode==='system')applyPrefs();});
  function storePrefs() { applyPrefs();try{localStorage.setItem('rt-appearance',JSON.stringify(prefs));}catch(_){} }
  function syncProfile(user=ctx?.state.user) {
    const value=user?.photoUrl||'';
    for(const id of ['miniAvatar','profileAvatar','topbarAvatar']) {
      const el=$(id); if(!el)continue;
      if(/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(value)||/^\/api\/v1\//.test(value)) {
        if(el.querySelector('img')?.getAttribute('src')===value)continue;
        const img=document.createElement('img');img.src=value;img.alt='';el.replaceChildren(img);
      } else if(el.querySelector('img'))el.textContent=(user?.fullName||user?.name||'C').split(/\s+/).slice(0,2).map(v=>v[0]).join('').toUpperCase();
    }
  }
  function resetProfile(user) { photo=undefined;if($('profilePhotoFile'))$('profilePhotoFile').value='';if($('profilePhotoStatus'))$('profilePhotoStatus').textContent=user?.photoUrl?'Foto attuale salvata':'Nessuna foto caricata'; }
  function captureLabels() {
    const existing=new Set();
    document.querySelectorAll('[data-copy-key]').forEach(el=>{if(!el.closest('script')&&!existing.has(el.dataset.copyKey)){bindings.push({key:el.dataset.copyKey,el,original:el.textContent});existing.add(el.dataset.copyKey);}});
    // Include generated product sections, field labels, column names and button captions.
    // Capture before operational records load, so customer data never becomes editable UI copy.
    const hash=text=>{let h=2166136261;for(const c of text)h=Math.imul(h^c.charCodeAt(0),16777619);return (h>>>0).toString(16);};
    document.querySelectorAll('.view h2,.view h3,.view label,.view th,.view .section-head p,.view button,.footer-group button,.footer-identity p').forEach(el=>{
      if(el.dataset.copyKey||['profileName','pageTitle'].includes(el.id)||el.hasAttribute('data-view')||el.closest('.nav'))return;
      const node=[...el.childNodes].find(n=>n.nodeType===Node.TEXT_NODE&&n.textContent.trim().length>1);
      if(!node)return;const key='ui-'+(el.closest('.view')?.id||'app')+'-'+hash(node.textContent.trim());
      if(!existing.has(key)){bindings.push({key,el:node,original:node.textContent});existing.add(key);}
    });
    // Navigation text nodes preserve icons, badge counters and event handlers.
    document.querySelectorAll('.nav button[data-view]').forEach(el=>{const node=el.querySelector('span:not(.nav-icon)')||[...el.childNodes].find(n=>n.nodeType===Node.TEXT_NODE&&n.textContent.trim());if(node)bindings.push({key:'nav-'+el.dataset.view,el:node,original:node.textContent});});
  }
  function setText(selector,value) { if(!value)return;document.querySelectorAll(selector).forEach(el=>el.textContent=value); }
  function applyBrand() {
    applyColors();
    setText('.brand strong,.login-brand strong,.workspace-label,.footer-brand strong',settings.name||defaults.name);
    setText('.brand span,.login-brand span,.footer-brand small',settings.subtitle||defaults.subtitle);
    setText('.overview-head h2',settings.welcomeTitle||defaults.welcomeTitle);setText('.overview-head p',settings.welcomeDescription||defaults.welcomeDescription);
    setText('.login-copyright,#footerCopyright',settings.copyright||defaults.copyright);
    const brand=settings.name||defaults.name;
    document.title=`${brand} · ${settings.subtitle||defaults.subtitle}`;
    $('brandHome')?.setAttribute('aria-label',`${brand}: torna alla dashboard`);
    document.querySelector('.footer-brand')?.setAttribute('aria-label',`${brand}: torna alla panoramica`);
    document.querySelector('.app-footer')?.setAttribute('aria-label',`${brand} — informazioni e navigazione`);
    root.dataset.customBrand=String(!!settings.logo);
    document.querySelectorAll('.brand-mark img,.footer-brand img').forEach(img=>{if(!img.dataset.originalSrc)img.dataset.originalSrc=img.getAttribute('src');img.src=settings.logo||img.dataset.originalSrc;});
    for(const b of bindings)b.el.textContent=settings.labels?.[b.key]||b.original;
    // Explicit welcome settings override the label catalogue for these two fields.
    setText('.overview-head h2',settings.welcomeTitle||defaults.welcomeTitle);setText('.overview-head p',settings.welcomeDescription||defaults.welcomeDescription);
    document.querySelectorAll('[data-brand-support]').forEach(a=>{a.textContent=settings.supportEmail||'Contatta il responsabile';a.removeAttribute('href');if(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(settings.supportEmail))a.href='mailto:'+settings.supportEmail;});
  }
  function formMarkup() {
    const view=document.createElement('section');view.id='view-branding';view.className='view';
    view.innerHTML=`<div class="section-head"><div><span class="section-kicker">Il prodotto della tua azienda</span><h2>Personalizzazione</h2><p>Identità, colori e testi condivisi per lo spazio aziendale. Le preferenze di accessibilità sono personali.</p></div></div><form id="brandingForm"><div class="branding-grid"><section class="panel"><div class="panel-head"><div><h3>Identità aziendale</h3><span>Nome, logo e comunicazione del prodotto</span></div></div><div class="panel-body branding-form">
    <div class="field"><label for="brandNameInput">Nome del prodotto / azienda</label><input id="brandNameInput" maxlength="100" required></div><div class="field"><label for="brandSubtitleInput">Sottotitolo</label><input id="brandSubtitleInput" maxlength="150"></div><div class="field"><label for="brandSupportInput">Email di assistenza</label><input id="brandSupportInput" type="email" maxlength="150"></div><div class="field"><label for="brandCopyrightInput">Copyright</label><input id="brandCopyrightInput" maxlength="500"></div><div class="field"><label for="brandWelcomeInput">Titolo della dashboard</label><input id="brandWelcomeInput" maxlength="150"></div><div class="field"><label for="brandDescriptionInput">Descrizione della dashboard</label><textarea id="brandDescriptionInput" rows="3" maxlength="500"></textarea></div><div class="field"><label for="brandLogoFile">Logo aziendale</label><input id="brandLogoFile" type="file" accept="image/png,image/jpeg,image/webp"><span class="field-help">PNG, JPEG o WebP. Ottimizzazione automatica fino a 512 px.</span><button class="btn" type="button" id="brandLogoRemove">Ripristina logo originale</button></div></div></section>
    <section class="panel"><div class="panel-head"><div><h3>Palette e anteprima</h3><span>Lo stile Raycast rimane la base del prodotto</span></div></div><div class="panel-body branding-form"><div class="field"><label for="brandPaletteMode">Palette da personalizzare</label><select id="brandPaletteMode"><option value="dark">Tema scuro</option><option value="light">Tema chiaro</option></select></div><div class="branding-colors" id="brandingColors"></div><p id="brandingContrast" class="field-help" role="status"></p><div class="branding-preview"><img id="brandPreviewLogo" alt="Anteprima del logo"><strong id="brandPreviewName"></strong><span id="brandPreviewSubtitle"></span><button class="btn primary" type="button" id="brandPreviewBtn">Anteprima nell’app</button><a data-brand-support></a></div><p class="field-help">L’anteprima è temporanea. Salva per applicare le modifiche a tutti gli utenti di questa azienda.</p></div></section>
    <section class="panel" style="grid-column:1/-1"><div class="panel-head"><div><h3>Testi, etichette e navigazione</h3><span>Personalizza i nomi dei campi e i testi dell’interfaccia. I dati operativi mantengono il loro significato.</span></div></div><div class="panel-body"><label class="sr-only" for="brandLabelSearch">Cerca un testo da personalizzare</label><input id="brandLabelSearch" type="search" placeholder="Cerca un campo, un titolo o una sezione…"><div class="branding-labels" id="brandingLabels"></div></div></section></div><div class="branding-actions"><button class="btn primary" type="submit" id="brandSave">Salva per l’azienda</button><button class="btn" type="button" id="brandCancel">Annulla modifiche</button><button class="btn" type="button" id="brandReset">Ripristina tema originale</button><button class="btn" type="button" id="brandExport">Esporta configurazione</button><label class="btn" for="brandImport">Importa configurazione</label><input type="file" id="brandImport" accept="application/json" hidden><span id="brandStatus" role="status"></span></div></form>`;
    document.querySelector('.content').append(view);
  }
  const fields={name:'brandNameInput',subtitle:'brandSubtitleInput',supportEmail:'brandSupportInput',copyright:'brandCopyrightInput',welcomeTitle:'brandWelcomeInput',welcomeDescription:'brandDescriptionInput'};
  const colorLabels={accent:'Accento',background:'Sfondo',surface:'Pannelli',text:'Testo',muted:'Testo secondario',border:'Bordi',success:'Successo',warning:'Avviso',danger:'Errore'};
  const lightColors={accent:'#b72f43',background:'#f8f9fb',surface:'#ffffff',text:'#17191f',muted:'#535969',border:'#d2d5dd',success:'#087b52',warning:'#8b5800',danger:'#bc2844'};
  const paletteKey=key=>$('brandPaletteMode')?.value==='light'?'light'+key[0].toUpperCase()+key.slice(1):key;
  const colorDefaults={accent:'#ff8383',background:'#08090b',surface:'#121316',text:'#f4f4f5',muted:'#ababb5',border:'#2b2c32',success:'#79ddb4',warning:'#edc389',danger:'#ff959f'};
  function fillForm() {
    for(const [key,id] of Object.entries(fields))$(id).value=settings[key]||'';
    for(const key of Object.keys(colors))$('brandColor-'+key).value=settings.colors?.[paletteKey(key)]||($('brandPaletteMode').value==='light'?lightColors:colorDefaults)[key];
    renderLabels();preview();
  }
  function readForm() { for(const [key,id] of Object.entries(fields))settings[key]=$(id).value.trim(); }
  function renderLabels() {
    const q=$('brandLabelSearch').value.toLocaleLowerCase('it');$('brandingLabels').replaceChildren();
    for(const b of bindings.filter(b=>b.original.toLocaleLowerCase('it').includes(q))) {
      const row=document.createElement('div');row.className='branding-label-row';const label=document.createElement('label');label.textContent=b.original.trim();label.htmlFor='label-'+b.key;
      const input=document.createElement('input');input.id=label.htmlFor;input.value=settings.labels?.[b.key]||'';input.placeholder=b.original.trim();input.maxLength=500;input.disabled=!allowed();
      input.addEventListener('input',()=>{settings.labels??={};if(input.value.trim())settings.labels[b.key]=input.value;else delete settings.labels[b.key];dirty=true;});row.append(label,input);$('brandingLabels').append(row);
    }
  }
  function preview() {
    $('brandPreviewLogo').src=settings.logo||document.querySelector('.brand-mark img').dataset.originalSrc||document.querySelector('.brand-mark img').src;
    $('brandPreviewName').textContent=settings.name||defaults.name;$('brandPreviewSubtitle').textContent=settings.subtitle||defaults.subtitle;
    const lum=hex=>{const v=hex.slice(1).match(/../g).map(v=>parseInt(v,16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return .2126*v[0]+.7152*v[1]+.0722*v[2];};
    const fallback=$('brandPaletteMode').value==='light'?lightColors:colorDefaults,a=lum(settings.colors?.[paletteKey('text')]||fallback.text),b=lum(settings.colors?.[paletteKey('surface')]||fallback.surface),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
    $('brandingContrast').textContent=`Contrasto testo/pannello: ${ratio.toFixed(1)}:1${ratio<4.5?' · Aumenta il contrasto per una lettura più accessibile.':''}`;
  }
  async function imageFile(file,max=512) {
    if(!file||!['image/png','image/jpeg','image/webp'].includes(file.type)||file.size>10*1024*1024)throw new Error('Scegli PNG, JPEG o WebP fino a 10 MB.');
    const image=await createImageBitmap(file);if(image.width*image.height>40000000){image.close();throw new Error('Immagine troppo grande. Riduci la risoluzione.');}
    const canvas=document.createElement('canvas'),scale=Math.min(1,max/Math.max(image.width,image.height));canvas.width=Math.round(image.width*scale);canvas.height=Math.round(image.height*scale);canvas.getContext('2d').drawImage(image,0,0,canvas.width,canvas.height);image.close();return canvas;
  }
  function safeConfig(raw) {
    if(!raw||typeof raw!=='object'||Array.isArray(raw))throw new Error('Configurazione non valida.');
    const value=structuredClone(defaults);
    for(const key of Object.keys(fields))if(typeof raw[key]==='string'&&raw[key].length<=500)value[key]=raw[key];
    if(typeof raw.logo==='string'&&raw.logo.length<=350000&&/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(raw.logo))value.logo=raw.logo;
    for(const key of Object.keys(colors).flatMap(key=>[key,'light'+key[0].toUpperCase()+key.slice(1)]))if(/^#[a-f0-9]{6}$/i.test(raw.colors?.[key]))value.colors[key]=raw.colors[key];
    for(const [key,label] of Object.entries(raw.labels||{}))if(/^[a-zA-Z0-9_.:-]{1,120}$/.test(key)&&typeof label==='string'&&label.length<=500)value.labels[key]=label;
    return value;
  }
  async function loadBrand(expected) {
    const epoch=++revision;
    try { const result=await ctx.apiFetch('/api/v1/branding');if(epoch!==revision||owner!==expected)return;saved=safeConfig(result?.data??result);if(!dirty){settings=structuredClone(saved);applyBrand();fillForm();}$('brandStatus').textContent='Configurazione aziendale caricata.'; }
    catch(e){if(epoch===revision)$('brandStatus').textContent='Configurazione non caricata. '+e.message;}
  }
  function syncOwner() {
    const state=ctx.state,next=state.token?`${state.user?.tenantId||''}:${state.user?.uid||state.user?.email||''}`:'';
    const admin=allowed();$('brandingNav').hidden=!admin;
    $('brandingForm').querySelectorAll('input,textarea,button,select').forEach(el=>{if(el.id!=='brandSave')el.disabled=!admin;});
    if(next!==owner){$('brandSave').disabled=!admin;owner=next;revision++;resetProfile();dirty=false;settings=structuredClone(defaults);saved=structuredClone(defaults);applyBrand();fillForm();if(next)loadBrand(next);else resetProfile();}
    syncProfile();
  }
  function setupCrop() {
    const dialog=document.createElement('dialog');dialog.id='profileCropDialog';dialog.className='profile-crop-dialog';dialog.setAttribute('aria-labelledby','cropTitle');
    dialog.innerHTML='<h2 id="cropTitle">Ritaglia la foto profilo</h2><p>Trascina la foto nel cerchio o usa i controlli per centrare il volto.</p><div class="crop-stage"><canvas id="profileCropCanvas" width="320" height="320" aria-label="Anteprima circolare del ritaglio"></canvas></div><div class="crop-controls"><label for="cropZoom">Zoom<input id="cropZoom" type="range" min="1" max="4" step="0.01" value="1"></label><label for="cropX">Posizione orizzontale<input id="cropX" type="range" min="-100" max="100" value="0"></label><label for="cropY">Posizione verticale<input id="cropY" type="range" min="-100" max="100" value="0"></label></div><div class="branding-actions"><button class="btn" id="cropAuto" type="button">Centra volto</button><button class="btn" id="cropCancel" type="button">Annulla</button><button class="btn primary" id="cropSave" type="button">Usa questa foto</button></div><span id="cropStatus" role="status"></span>';
    document.body.append(dialog);const canvas=$('profileCropCanvas'),g=canvas.getContext('2d');let image=null,drag=null;
    function geometry(){const scale=Math.max(320/image.width,320/image.height)*Number($('cropZoom').value);return {width:image.width*scale,height:image.height*scale};}
    function draw(){if(!image)return;const {width,height}=geometry();g.clearRect(0,0,320,320);g.drawImage(image,(320-width)/2+Number($('cropX').value)/100*(width-320)/2,(320-height)/2+Number($('cropY').value)/100*(height-320)/2,width,height);}
    async function auto(){if(!image)return;$('cropZoom').value='1';$('cropX').value='0';$('cropY').value='0';$('cropStatus').textContent='Ritaglio centrale. Regola posizione e zoom.';
      if('FaceDetector' in window)try{const faces=await new window.FaceDetector({fastMode:true,maxDetectedFaces:1}).detect(image);if(faces[0]){const face=faces[0].boundingBox;$('cropZoom').value=String(Math.min(4,Math.max(1,(320/(Math.max(face.width,face.height)*2))/Math.max(320/image.width,320/image.height))));const {width,height}=geometry();$('cropX').value=String(width===320?0:Math.max(-100,Math.min(100,(.5-(face.x+face.width/2)/image.width)*width*200/(width-320))));$('cropY').value=String(height===320?0:Math.max(-100,Math.min(100,(.5-(face.y+face.height/2)/image.height)*height*200/(height-320))));$('cropStatus').textContent='Volto centrato automaticamente. Verifica l’anteprima.';}}catch(_){}
      draw();
    }
    $('profilePhotoFile').addEventListener('change',async()=>{try{image=await imageFile($('profilePhotoFile').files[0],1600);await auto();dialog.showModal();}catch(e){ctx.toast('Foto non caricata',e.message,'error');}});
    for(const id of ['cropZoom','cropX','cropY'])$(id).addEventListener('input',draw);
    $('cropAuto').onclick=auto;$('cropCancel').onclick=()=>dialog.close();
    $('cropSave').onclick=()=>{if(!image)return;const out=document.createElement('canvas');out.width=320;out.height=320;const cg=out.getContext('2d');cg.beginPath();cg.arc(160,160,160,0,Math.PI*2);cg.clip();cg.drawImage(canvas,0,0);photo=out.toDataURL('image/webp',.85);if(photo.length>350000){ctx.toast('Foto troppo grande','Riduci i dettagli e riprova.','error');return;}$('profilePhotoStatus').textContent='Ritaglio pronto. Salva il profilo per confermare.';dialog.close();};
    $('profilePhotoRemove').onclick=()=>{photo='';$('profilePhotoStatus').textContent='La foto verrà rimossa salvando il profilo.';};
    canvas.addEventListener('pointerdown',e=>{drag={x:e.clientX,y:e.clientY,px:Number($('cropX').value),py:Number($('cropY').value)};canvas.setPointerCapture(e.pointerId);});
    canvas.addEventListener('pointermove',e=>{if(!drag||!image)return;const {width,height}=geometry(),factor=320/canvas.getBoundingClientRect().width;$('cropX').value=String(drag.px+(e.clientX-drag.x)*factor*200/Math.max(1,width-320));$('cropY').value=String(drag.py+(e.clientY-drag.y)*factor*200/Math.max(1,height-320));draw();});
    canvas.addEventListener('pointerup',()=>drag=null);canvas.addEventListener('pointercancel',()=>drag=null);
    dialog.addEventListener('close',()=>{$('profilePhotoFile').value='';image=null;drag=null;});
  }
  function init(context) {
    ctx=context;captureLabels();formMarkup();
    for(const key of Object.keys(colors)){const label=document.createElement('label');label.textContent=colorLabels[key];label.htmlFor='brandColor-'+key;const input=document.createElement('input');input.type='color';input.id=label.htmlFor;input.addEventListener('input',()=>{settings.colors[paletteKey(key)]=input.value;dirty=true;preview();});label.append(input);$('brandingColors').append(label);}
    $('brandingNav').addEventListener('click',()=>ctx.switchView('branding'));
    $('themeMenuBtn').onclick=()=>{const open=$('themeMenu').hidden;$('themeMenu').hidden=!open;$('themeMenuBtn').setAttribute('aria-expanded',String(open));};
    const closeTheme=()=>{$('themeMenu').hidden=true;$('themeMenuBtn').setAttribute('aria-expanded','false');};
    document.addEventListener('click',e=>{if(!e.target.closest('.theme-anchor'))closeTheme();});document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('themeMenu').hidden){closeTheme();$('themeMenuBtn').focus();}});
    document.querySelectorAll('[data-mode]').forEach(b=>b.onclick=()=>{prefs.mode=b.dataset.mode;storePrefs();});
    for(const [id,key] of [['highContrast','contrast'],['reduceMotion','motion'],['readableFont','readable']])$(id).onchange=()=>{prefs[key]=$(id).checked;storePrefs();};$('textScale').onchange=()=>{prefs.scale=$('textScale').value;storePrefs();};applyPrefs();
    $('brandingForm').addEventListener('input',e=>{if(e.target.matches('[type=search]'))return;readForm();dirty=true;editVersion++;preview();});
    $('brandLabelSearch').oninput=renderLabels;$('brandPaletteMode').onchange=()=>{for(const key of Object.keys(colors))$('brandColor-'+key).value=settings.colors?.[paletteKey(key)]||($('brandPaletteMode').value==='light'?lightColors:colorDefaults)[key];preview();};
    $('brandPreviewBtn').onclick=()=>{readForm();applyBrand();$('brandStatus').textContent='Anteprima temporanea applicata.';};
    $('brandLogoFile').onchange=async()=>{try{const canvas=await imageFile($('brandLogoFile').files[0]);const logo=canvas.toDataURL('image/webp',.85);if(logo.length>350000)throw new Error('Logo troppo dettagliato. Riduci la dimensione.');settings.logo=logo;dirty=true;preview();}catch(e){ctx.toast('Logo non caricato',e.message,'error');}};
    $('brandLogoRemove').onclick=()=>{settings.logo='';dirty=true;preview();};
    $('brandCancel').onclick=()=>{settings=structuredClone(saved);dirty=false;applyBrand();fillForm();$('brandStatus').textContent='Modifiche annullate.';};
    $('brandReset').onclick=()=>{settings=structuredClone(defaults);dirty=true;applyBrand();fillForm();$('brandStatus').textContent='Tema originale in anteprima. Salva per confermare.';};
    $('brandingForm').onsubmit=async e=>{e.preventDefault();if(!allowed())return;readForm();const snapshot=structuredClone(settings),expected=owner,epoch=revision,editing=editVersion;$('brandSave').disabled=true;try{const response=await ctx.apiFetch('/api/v1/branding',{method:'PUT',body:JSON.stringify(snapshot)});if(expected!==owner||epoch!==revision)return;saved=safeConfig(response?.data??response);if(editing===editVersion){settings=structuredClone(saved);dirty=false;applyBrand();fillForm();}$('brandStatus').textContent=editing===editVersion?'Configurazione salvata per l’azienda.':'Configurazione salvata. Sono presenti nuove modifiche da salvare.';ctx.toast('Personalizzazione salvata','Identità e tema disponibili per gli utenti di questa azienda.');}catch(error){$('brandStatus').textContent='Salvataggio non riuscito: '+error.message;}finally{$('brandSave').disabled=!allowed();}};
    $('brandExport').onclick=()=>{readForm();const url=URL.createObjectURL(new Blob([JSON.stringify(settings,null,2)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='configurazione-azienda.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
    $('brandImport').onchange=async()=>{try{const file=$('brandImport').files[0];if(!file||file.size>800000)throw new Error('Configurazione troppo grande.');settings=safeConfig(JSON.parse(await file.text()));dirty=true;fillForm();$('brandStatus').textContent='Configurazione importata. Controlla l’anteprima e salva.';}catch(e){ctx.toast('Importazione non riuscita',e.message,'error');}finally{$('brandImport').value='';}};
    setupCrop();fillForm();syncOwner();setInterval(syncOwner,1500);
    // Existing identity sync can replace avatar text; reapply the saved photo afterwards.
    for(const id of ['topbarAvatar','miniAvatar','profileAvatar'])new MutationObserver(()=>{const el=$(id);if(ctx.state.user?.photoUrl&&!el.querySelector('img'))syncProfile();}).observe($(id),{childList:true});
  }
  return {init,syncProfile,syncIdentity:()=>{if(ctx)syncOwner();},resetProfile:user=>{if(ctx)syncOwner();resetProfile(user);},profilePhoto:()=>photo};
})();
