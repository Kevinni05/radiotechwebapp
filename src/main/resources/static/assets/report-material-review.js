"use strict";
window.RadioTechReviewMaterials = async function ({apiFetch, report, escapeHtml: esc}) {
  const response = await apiFetch(`/api/v1/reports/${encodeURIComponent(report.id)}/review-context`);
  const context = response?.data ?? response;
  const inventory = context.inventory || [], materials = context.materials || [];
  const dialog = document.createElement('dialog'); dialog.className = 'report-material-review';
  dialog.setAttribute('aria-labelledby','materialReviewTitle');
  dialog.innerHTML = `<form><h2 id="materialReviewTitle">Verifica i materiali utilizzati</h2>
    <p>Seleziona gli articoli effettivamente utilizzati. Le quantità sono quelle dichiarate nel report.</p>
    ${context.locked ? '<p>Magazzino già contabilizzato. Puoi completare l’approvazione con gli abbinamenti registrati.</p>' : ''}
    ${materials.map((material,index)=>`<div class="field"><label for="reviewMaterial-${index}">${esc(material.name || material.sku || 'Materiale')} · quantità ${esc(material.quantity)}</label>
      <select id="reviewMaterial-${index}" data-material-index="${index}" ${context.locked ? 'disabled' : 'required'}>
      <option value="">Scegli l’articolo corretto…</option>
      ${inventory.map(item=>`<option value="${esc(item.id)}">${esc(item.name)} · ${esc(item.sku || '')} · disponibili: ${esc(item.quantity ?? '—')} ${esc(item.unit || '')}</option>`).join('')}
      <option value="@external">Non prelevato dal magazzino aziendale</option></select></div>`).join('')}
    <p class="subtle">Gli articoli del magazzino saranno scalati una sola volta. Indica “non prelevato” solo per materiali che non provengono dalle scorte aziendali.</p>
    <div class="actions"><button type="button" class="btn" data-cancel>Annulla</button><button type="submit" class="btn primary">Conferma e approva</button></div></form>`;
  materials.forEach((material,index)=>{
    const select=dialog.querySelector(`[data-material-index="${index}"]`);
    const explicit=context.mappings?.[index];
    const id=material.inventoryId || material.inventoryItemId || material.itemId || material.ricambioId || material.id;
    const matches=inventory.filter(item=>id ? item.id===id : material.sku ? item.sku===material.sku : String(item.name||'').trim().toLocaleLowerCase('it')===String(material.name||'').trim().toLocaleLowerCase('it'));
    if(explicit)select.value=explicit;else if(matches.length===1)select.value=matches[0].id;
  });
  const focused=document.activeElement;
  return new Promise(resolve=>{
    let answer=null;
    dialog.addEventListener('close',()=>{dialog.remove();focused?.focus();resolve(answer);},{once:true});
    dialog.querySelector('[data-cancel]').addEventListener('click',()=>dialog.close());
    dialog.querySelector('form').addEventListener('submit',event=>{
      event.preventDefault();
      answer=context.locked ? undefined : Object.fromEntries([...dialog.querySelectorAll('[data-material-index]')].map(select=>[select.dataset.materialIndex,select.value]));
      dialog.close();
    });
    document.body.append(dialog);dialog.showModal();
  });
};
