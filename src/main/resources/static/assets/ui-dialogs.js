"use strict";
// Shared, keyboard-accessible business confirmations. No native prompt or secret logging.
window.RadioTechDialogs = (() => {
 let active=null,sequence=0;
 function ask(message,{input=false,title=input?'Motivazione richiesta':'Conferma operazione',confirmLabel='Conferma',minLength=1,maxLength=1000}={}) {
  if(active)return Promise.resolve(input?null:false);
  return new Promise(resolve=>{
   const previous=document.activeElement,dialog=document.createElement('dialog'),form=document.createElement('form');
   dialog.className='operation-dialog ui-dialog';form.noValidate=true;
   const heading=document.createElement('h2');heading.id=`ui-dialog-title-${++sequence}`;heading.textContent=title;
   dialog.setAttribute('aria-labelledby',heading.id);
   const copy=document.createElement('p');copy.textContent=message;copy.id=`ui-dialog-copy-${sequence}`;dialog.setAttribute('aria-describedby',copy.id);
   form.append(heading,copy);let field=null;
   const error=document.createElement('p');error.className='field-error';error.setAttribute('role','alert');error.id=`ui-dialog-error-${sequence}`;
   if(input){const label=document.createElement('label');label.className='field';label.textContent='Inserisci il valore richiesto';field=document.createElement('textarea');field.rows=3;field.maxLength=maxLength;field.required=true;field.setAttribute('aria-describedby',error.id);label.append(field);form.append(label);}
   const actions=document.createElement('div');actions.className='toolbar';
   const cancel=document.createElement('button');cancel.type='button';cancel.className='btn';cancel.textContent='Annulla';
   const confirm=document.createElement('button');confirm.type='submit';confirm.className='btn primary';confirm.textContent=confirmLabel;
   actions.append(cancel,confirm);form.append(error,actions);dialog.append(form);document.body.append(dialog);
   let done=false;
   function finish(value){if(done)return;done=true;active=null;dialog.close();dialog.remove();if(previous?.isConnected)previous.focus({preventScroll:true});resolve(value);}
   active=()=>finish(input?null:false);
   cancel.onclick=()=>finish(input?null:false);
   dialog.addEventListener('cancel',event=>{event.preventDefault();finish(input?null:false);});
   form.onsubmit=event=>{event.preventDefault();if(field&&field.value.trim().length<minLength){error.textContent=`Inserisci almeno ${minLength} caratteri.`;field.setAttribute('aria-invalid','true');field.focus();return;}finish(field?field.value.trim():true);};
   dialog.showModal();(field??cancel).focus();
  });
 }
 return {confirm:message=>ask(message),prompt:message=>ask(message,{input:true}),ask,cancelAll:()=>active?.()};
})();
