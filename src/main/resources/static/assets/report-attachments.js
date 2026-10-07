(() => {
  'use strict';
  const local = /^radiotech-file:([a-f0-9]{64})$/;
  function reference(value) {
    if (typeof value !== 'string') return null;
    if (local.test(value)) return value;
    try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password && url.hostname === 'firebasestorage.googleapis.com' ? url.href : null; } catch { return null; }
  }
  window.RadioTechReportAttachments = ({apiFetch, escapeHtml}) => {
    let observer, generation = 0, active = 0;
    const urls = new Set(), cache = new Map(), queue = [];
    function clear() {
      generation++; observer?.disconnect(); queue.length = 0;
      for(const promise of cache.values()) promise.then(file=>{file.revoked=true;return file.pdfTask?.destroy();}).catch(()=>{});
      cache.clear();
      for (const url of urls) URL.revokeObjectURL(url);
      urls.clear(); document.querySelector('.report-file-dialog')?.close();
      document.querySelector('.report-file-dialog')?.remove();
    }
    function markup(values) {
      const refs = (Array.isArray(values) ? values : []).map(reference).filter(Boolean);
      return refs.length ? `<section class="report-attachments" aria-label="Allegati del report"><h4>Allegati · ${refs.length}</h4><div class="report-file-grid">${refs.map((ref, i) => `<article class="report-file" data-report-file="${escapeHtml(ref)}"><div class="report-file-heading"><strong class="report-file-name">Allegato ${i+1}</strong><span class="report-file-kind"></span></div><div class="report-file-preview" aria-live="polite">Caricamento anteprima…</div><div class="report-file-actions"><button type="button" class="mini-btn" data-file-open disabled>Ingrandisci</button><button type="button" class="mini-btn" data-file-download disabled>Scarica</button></div></article>`).join('')}</div></section>` : '';
    }
    function kind(bytes) {
      if (bytes[0]===37 && bytes[1]===80 && bytes[2]===68 && bytes[3]===70) return 'application/pdf';
      if (bytes[0]===137 && bytes[1]===80 && bytes[2]===78 && bytes[3]===71) return 'image/png';
      if (bytes[0]===255 && bytes[1]===216 && bytes[2]===255) return 'image/jpeg';
      if (String.fromCharCode(...bytes.slice(0,4))==='RIFF' && String.fromCharCode(...bytes.slice(8,12))==='WEBP') return 'image/webp';
      return 'application/octet-stream';
    }
    async function read(ref) {
      let name, bytes;
      if (local.test(ref)) {
        const file = await apiFetch(`/api/v1/files/${ref.slice(15)}`);
        name = file.name || 'allegato';
        if (typeof file.base64 !== 'string' || file.base64.length > 14000000) throw new Error('Allegato non valido o troppo grande.');
        bytes = Uint8Array.from(atob(file.base64), c => c.charCodeAt(0));
      } else {
        const response = await fetch(ref, {credentials:'omit', referrerPolicy:'no-referrer', signal:AbortSignal.timeout(30000)});
        if (!response.ok) throw new Error('Allegato non disponibile.');
        const blob = await response.blob();
        if (blob.size > 10000000) throw new Error('Anteprima disponibile fino a 10 MB.');
        bytes = new Uint8Array(await blob.arrayBuffer());
        name = decodeURIComponent(new URL(ref).pathname.split('/').at(-1)).split('/').at(-1);
      }
      const type = kind(bytes), url = URL.createObjectURL(new Blob([bytes], {type}));
      urls.add(url); return {name, type, url, bytes};
    }
    let library;
    async function pdfDocument(file) {
      library ??= import('/assets/pdfjs/pdf.min.mjs');
      const pdfjs = await library;
      if(file.revoked) throw new Error('Visualizzazione terminata.');
      pdfjs.GlobalWorkerOptions.workerSrc = '/assets/pdfjs/pdf.worker.min.mjs';
      file.pdfTask ??= pdfjs.getDocument({data:file.bytes.slice(), isEvalSupported:false, useWasm:false, standardFontDataUrl:'/assets/pdfjs/standard_fonts/', cMapUrl:'/assets/pdfjs/cmaps/', cMapPacked:true});
      return file.pdfTask.promise;
    }
    function renderPdf(file, full) {
      const box = document.createElement('div'); box.className = full ? 'report-pdf report-pdf-full' : 'report-pdf';
      const toolbar = document.createElement('div'); toolbar.className = 'report-pdf-toolbar';
      const caption = document.createElement('span'); caption.textContent = 'Caricamento PDF…'; caption.setAttribute('aria-live','polite');
      const viewport = document.createElement('div'); viewport.className = 'report-pdf-viewport';
      const canvas = document.createElement('canvas'); canvas.setAttribute('role','img'); canvas.setAttribute('aria-label',`PDF: ${file.name}`); viewport.append(canvas);
      const previous = document.createElement('button'), next = document.createElement('button'), zoom = document.createElement('select');
      previous.type=next.type='button'; previous.className=next.className='mini-btn'; previous.textContent='Precedente'; next.textContent='Successiva';
      zoom.setAttribute('aria-label','Zoom PDF'); zoom.innerHTML='<option value="1">Adatta</option><option value="1.5">150%</option><option value="2">200%</option>';
      let pageNumber=1, scale=1, request=0, renderTask;
      async function paint() {
        const current=++request; renderTask?.cancel(); previous.disabled=next.disabled=true;
        try {
          const pdf=await pdfDocument(file); if(current!==request||!box.isConnected)return;
          const page=await pdf.getPage(pageNumber); if(current!==request||!box.isConnected)return;
          const base=page.getViewport({scale:1});
          const width=Math.max(100,Math.min(viewport.clientWidth-16,1100));
          const pageViewport=page.getViewport({scale:width/base.width*scale});
          const ratio=Math.min(window.devicePixelRatio||1,2);
          canvas.width=Math.ceil(pageViewport.width*ratio); canvas.height=Math.ceil(pageViewport.height*ratio);
          canvas.style.width=`${pageViewport.width}px`; canvas.style.height=`${pageViewport.height}px`;
          renderTask=page.render({canvasContext:canvas.getContext('2d'),viewport:pageViewport,transform:ratio===1?undefined:[ratio,0,0,ratio,0,0]});
          await renderTask.promise; if(current!==request)return;
          caption.textContent=`Pagina ${pageNumber} di ${pdf.numPages}`;
          previous.disabled=pageNumber<=1; next.disabled=pageNumber>=pdf.numPages;
          canvas.dataset.pdfRendered='true';
          const text=await page.getTextContent(); if(current===request)canvas.setAttribute('aria-label',`PDF ${file.name}, pagina ${pageNumber}: ${text.items.map(i=>i.str||'').join(' ').slice(0,800)}`);
        } catch(error) { if(current===request && error.name!=='RenderingCancelledException' && box.isConnected)caption.textContent=`PDF non disponibile: ${error.message}`; }
      }
      previous.onclick=()=>{if(pageNumber>1){pageNumber--;void paint();}}; next.onclick=()=>{pageNumber++;void paint();}; zoom.onchange=()=>{scale=Number(zoom.value);void paint();};
      if(full)toolbar.append(previous,caption,next,zoom);else toolbar.append(caption);
      box.append(toolbar,viewport); requestAnimationFrame(()=>void paint());
      return box;
    }
    function render(file, full = false) {
      if(file.type==='application/pdf') return renderPdf(file, full);
      const element=document.createElement('img'); element.src=file.url; element.alt=file.name; element.decoding='async';
      element.className=full?'report-file-full':'report-file-content'; return element;
    }
    function open(file, trigger) {
      const dialog = document.createElement('dialog'); dialog.className = 'report-file-dialog';
      const bar = document.createElement('div'); bar.className = 'report-file-heading';
      const title = document.createElement('strong'); title.textContent = file.name;
      const close = document.createElement('button'); close.type = 'button'; close.className = 'mini-btn'; close.textContent = 'Chiudi'; close.onclick = () => dialog.close();
      bar.append(title, close); dialog.append(bar, render(file, true)); document.body.append(dialog);
      dialog.addEventListener('close', () => { dialog.remove(); trigger.focus(); });
      dialog.addEventListener('click', e => { if(e.target===dialog) {const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom) dialog.close();} });
      dialog.showModal(); close.focus();
    }
    async function load(card, token) {
      if (!card.isConnected || token !== generation) return;
      const ref = card.dataset.reportFile, preview = card.querySelector('.report-file-preview');
      try {
        if (!cache.has(ref)) cache.set(ref, read(ref));
        const file = await cache.get(ref);
        if (!card.isConnected || token !== generation) { urls.delete(file.url); URL.revokeObjectURL(file.url); return; }
        card.querySelector('.report-file-name').textContent = file.name;
        card.querySelector('.report-file-kind').textContent = file.type === 'application/pdf' ? 'PDF' : file.type.startsWith('image/') ? 'Immagine' : 'File';
        preview.replaceChildren();
        const supported = file.type === 'application/pdf' || file.type.startsWith('image/');
        if (supported) preview.append(render(file)); else preview.textContent = 'Questo formato è disponibile per il download.';
        const zoom = card.querySelector('[data-file-open]'); zoom.disabled = !supported; zoom.onclick = () => open(file, zoom);
        const download = card.querySelector('[data-file-download]'); download.disabled = false; download.onclick = () => {
          const link = document.createElement('a'); link.href = file.url; link.download = file.name; document.body.append(link); link.click(); link.remove();
        };
      } catch (error) {
        if (!card.isConnected || token !== generation) return;
        cache.delete(ref); preview.textContent = `Anteprima non disponibile: ${error.message}`;
        const retry = document.createElement('button'); retry.type='button'; retry.className='mini-btn'; retry.textContent='Riprova'; retry.onclick=()=>{preview.textContent='Caricamento anteprima…';enqueue(card);}; preview.append(retry);
      }
    }
    function drain() {
      while (active < 3 && queue.length) {
        const [card, token] = queue.shift(); active++;
        load(card, token).finally(() => { active--; drain(); });
      }
    }
    function enqueue(card) { queue.push([card, generation]); drain(); }
    function mount(root) {
      observer?.disconnect();
      observer = new IntersectionObserver(entries => { for (const e of entries) if (e.isIntersecting) { observer.unobserve(e.target); enqueue(e.target); } }, {rootMargin:'200px'});
      root.querySelectorAll('[data-report-file]').forEach(card => observer.observe(card));
    }
    window.addEventListener('pagehide', clear);
    return {markup, mount, clear};
  };
})();
