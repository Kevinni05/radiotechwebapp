// notifications: presentation and operations for this feature.
import {notificationOperation,completeNotificationOperation} from '../notification-operation.js';
export function createFeature(ctx){
let pendingOperation=null;
window.addEventListener('radiotech:auth-ended',()=>{pendingOperation=null;});
window.addEventListener('radiotech:identity-changed',()=>{pendingOperation=null;});
function deliveryDescription(notice){
 const labels={PENDING:'Push in attesa',SENDING:'Tentativo di invio',PARTIAL:'Invio push parziale',SENT:'Accettata dal provider',NO_TOKENS:'Nessun dispositivo registrato',NO_RECIPIENTS:'Nessun destinatario autorizzato',DEAD_LETTER:'Invio push da riprovare'};
 const state=notice.deliveryStatus;
 const accepted=Number(notice.providerAccepted??notice.delivered??0);
 const failed=Number(notice.failedDevices??notice.failedRecipients??0);
 const errors=notice.errorCodes||{};
 const reason=errors.PERMISSION_DENIED?'Configurazione invio push non autorizzata':errors.SENDER_ID_MISMATCH?'Progetto Firebase non corrispondente':errors.UNREGISTERED?'Token dispositivo scaduto':errors.UNAVAILABLE?'Provider temporaneamente non disponibile':Object.keys(errors).length?'Errore provider da verificare':'';
 return `${labels[state]||'Esito push non disponibile'} · ${accepted} dispositivi accettati${failed?` · ${failed} falliti`:''}${reason?` · ${reason}`:''}. Ricezione sul telefono e lettura da verificare.`;
}
function renderRecipients() {
                const recipients = ctx.eligibleOperators();
                if (!recipients.length) {
                    ctx.$("recipientBox").innerHTML =
                        `<div class="empty">Nessun dipendente disponibile dal backend.</div>`;
                    return;
                }

                ctx.$("recipientBox").innerHTML = recipients.map((op, i) => {
                    const id = op.id || op.uid || op.operatorId;
                    const name = op.name || op.fullName || op.displayName || op.email || `Operatore ${i + 1}`;
                    const role = op.role || op.ruolo || "Operatore";

                    return `
                <label class="recipient">
                    <input type="checkbox" value="${ctx.escapeHtml(id)}">
                    <span class="avatar">${ctx.escapeHtml(ctx.initials(name))}</span>
                    <span class="recipient-info">
                        <strong>${ctx.escapeHtml(name)}</strong>
                        <span>${ctx.escapeHtml(role)}</span>
                    </span>
                </label>`;
                }).join("");
            }

            async function loadNotifications() {
                try {
                    const data = await ctx.api("/notifications");
                    ctx.state.notifications = Array.isArray(data) ? data : (data?.notifications || []);
                    renderNotifications();
                    updateNotificationCount();
                } catch (error) {
                    console.warn("Notifiche non disponibili:", error.message);
                    ctx.$("notificationHistory").innerHTML =
                        `<div class="empty">Endpoint notifiche non disponibile: ${ctx.escapeHtml(error.message)}</div>`;
                }
            }

            function renderNotifications() {
                if (!ctx.state.notifications.length) {
                    ctx.$("notificationHistory").innerHTML =
                        `<div class="empty">Nessuna comunicazione recente.</div>`;
                    return;
                }

                ctx.$("notificationHistory").innerHTML = ctx.state.notifications.slice(0, 20).map(n => {
                    const title = n.title || n.titolo || "Comunicazione";
                    const message = n.message || n.messaggio || "";
                    const date = n.createdAt || n.timestamp || n.date;
                    const isSignal = n.type === "WORKFORCE_SIGNAL";
                    const metadata = isSignal
                        ? `<span class="notification-meta">Segnalazione urgente${n.severity ? ` · ${ctx.escapeHtml(n.severity)}` : ""}${n.assetId ? ` · Asset ${ctx.escapeHtml(n.assetId)}` : ""}</span><button class="mini-btn notification-action" type="button" data-open-signals>Apri segnalazioni</button>`
                        : "";
                    const retryable=n.id&&['PENDING','PARTIAL','NO_TOKENS','DEAD_LETTER'].includes(n.deliveryStatus);

                    return `
                <div class="notification-item${isSignal ? " notification-critical" : ""}">
                    <div class="notification-icon">${isSignal ? "!" : "✦"}</div>
                    <div>
                        <strong>${ctx.escapeHtml(title)}</strong>
                        <p>${ctx.escapeHtml(message)}</p>
                        ${metadata}
                        <p class="notification-delivery">${ctx.escapeHtml(deliveryDescription(n))}</p>
                        ${n.operationId?`<span class="subtle">Operazione ${ctx.escapeHtml(n.operationId)}</span>`:''}
                        ${retryable?`<button type="button" class="mini-btn" data-notification-retry="${ctx.escapeHtml(n.id)}">Riprova push</button>`:''}
                    </div>
                    <span class="subtle">${date ? ctx.escapeHtml(new Date(date).toLocaleDateString("it-IT")) : "—"}</span>
                </div>`;
                }).join("");
                ctx.$("notificationHistory").querySelectorAll("[data-open-signals]").forEach(button => {
                    button.addEventListener("click", () => ctx.switchView("people"));
                });
                ctx.$('notificationHistory').querySelectorAll('[data-notification-retry]').forEach(button=>button.addEventListener('click',async()=>{
                  button.disabled=true;
                  try{await ctx.apiFetch(`/api/v1/notifications/${encodeURIComponent(button.dataset.notificationRetry)}/retry`,{method:'POST'});await loadNotifications();}
                  catch(error){ctx.toast('Retry non riuscito',error.message,'error');button.disabled=false;}
                }));
                ctx.applyPermissions();
            }

            function updateNotificationCount() {
                const unread = ctx.state.notifications.filter(n => n.read === false || n.letto === false).length;
                const el = ctx.$("notificationCount");

                if (unread > 0) {
                    el.textContent = unread > 99 ? "99+" : unread;
                    el.classList.remove("hidden");
                } else {
                    el.classList.add("hidden");
                }
            }

            async function sendNotification() {
                const target = ctx.$("notificationTarget").value;
                const title = ctx.$("notificationTitle").value.trim();
                const message = ctx.$("notificationMessage").value.trim();
                const priority = ctx.$("notificationPriority").value;

                if (!title || !message) {
                    ctx.toast("Notifica incompleta", "Inserisci titolo e messaggio.", "error");
                    return;
                }

                let recipients = [];

                if (target === "SELECTED") {
                    recipients = [...ctx.$("recipientBox").querySelectorAll("input[type=checkbox]:checked")]
                        .map(x => x.value);

                    if (!recipients.length) {
                        ctx.toast("Nessun destinatario", "Seleziona almeno un dipendente.", "error");
                        return;
                    }
                }

                const payload = {
                    target,
                    recipients,
                    title,
                    message,
                    priority,
                    type: "TASK_NOTIFICATION"
                };
                const fingerprint=JSON.stringify([ctx.state.user?.uid||ctx.state.user?.id,ctx.state.user?.tenantId,target,[...recipients].sort(),title,message,priority]);
                if(!pendingOperation||pendingOperation.fingerprint!==fingerprint)pendingOperation={fingerprint,...await notificationOperation(fingerprint,sessionStorage)};
                payload.operationId=pendingOperation.id;

                const button = ctx.$("sendNotificationBtn");
                button.disabled = true;
                button.textContent = "Invio...";

                try {
                    const response=await ctx.apiFetch("/api/v1/notifications", {
                        method: "POST",
                        body: JSON.stringify(payload)
                    });
                    const result=response?.data??response;
                    if(result?.persisted!==true&&result?.success!==true)throw new Error('Salvataggio della comunicazione non confermato dal server. Riprova con la stessa operazione.');

                    ctx.$("notificationTitle").value = "";
                    ctx.$("notificationMessage").value = "";
                    completeNotificationOperation(pendingOperation,sessionStorage);
                    pendingOperation=null;
                    ctx.toast("Comunicazione salvata", deliveryDescription(result||{}), result?.deliveryStatus==='SENT'?'success':'warning');
                    await loadNotifications();
                } catch (error) {
                    ctx.toast("Invio fallito", error.message, "error");
                } finally {
                    button.disabled = false;
                    button.textContent = "Invia notifica";
                }
            }
return {get renderRecipients(){return renderRecipients;},get loadNotifications(){return loadNotifications;},get renderNotifications(){return renderNotifications;},get updateNotificationCount(){return updateNotificationCount;},get sendNotification(){return sendNotification;}};
}
