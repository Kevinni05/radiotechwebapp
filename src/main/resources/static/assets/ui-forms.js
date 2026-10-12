"use strict";
window.RadioTechForms = (() => {
 function clear(form){if(!form)return;form.querySelectorAll('[data-field-error]').forEach(node=>node.remove());form.querySelectorAll('[aria-invalid="true"]').forEach(field=>{field.removeAttribute('aria-invalid');const ids=(field.getAttribute('aria-describedby')||'').split(' ').filter(id=>!id.startsWith('rt-error-'));if(ids.length)field.setAttribute('aria-describedby',ids.join(' '));else field.removeAttribute('aria-describedby');});}
 function errors(form,fields){clear(form);if(!form||!fields||typeof fields!=='object')return;
  for(const [name,message]of Object.entries(fields)){const field=form.querySelector(`[name="${CSS.escape(name)}"]`);if(!field)continue;const error=document.createElement('p');error.className='field-error';error.dataset.fieldError='';error.id=`rt-error-${field.id||name}`;error.setAttribute('role','alert');error.textContent=String(message);field.setAttribute('aria-invalid','true');field.setAttribute('aria-describedby',`${field.getAttribute('aria-describedby')||''} ${error.id}`.trim());field.closest('.field')?.append(error);}
  form.querySelector('[aria-invalid="true"]')?.focus();
 }
 document.addEventListener('input',event=>{if(event.target.closest('form')&&event.target.getAttribute('aria-invalid')==='true')clear(event.target.closest('form'));});
 return {clear,errors};
})();
