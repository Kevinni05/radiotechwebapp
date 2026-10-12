// notifications: presentation and operations for this feature.
export function createFeature(ctx){
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

                    return `
                <div class="notification-item${isSignal ? " notification-critical" : ""}">
                    <div class="notification-icon">${isSignal ? "!" : "✦"}</div>
                    <div>
                        <strong>${ctx.escapeHtml(title)}</strong>
                        <p>${ctx.escapeHtml(message)}</p>
                        ${metadata}
                    </div>
                    <span class="subtle">${date ? ctx.escapeHtml(new Date(date).toLocaleDateString("it-IT")) : "—"}</span>
                </div>`;
                }).join("");
                ctx.$("notificationHistory").querySelectorAll("[data-open-signals]").forEach(button => {
                    button.addEventListener("click", () => ctx.switchView("people"));
                });
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

                const button = ctx.$("sendNotificationBtn");
                button.disabled = true;
                button.textContent = "Invio...";

                try {
                    await ctx.apiFetch("/api/v1/notifications", {
                        method: "POST",
                        body: JSON.stringify(payload)
                    });

                    ctx.$("notificationTitle").value = "";
                    ctx.$("notificationMessage").value = "";
                    ctx.toast("Comunicazione salvata", "Disponibile nello storico. L’accettazione della push non conferma la lettura.");
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
