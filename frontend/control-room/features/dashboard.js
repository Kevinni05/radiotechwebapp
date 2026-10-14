// dashboard: presentation and operations for this feature.
export function createFeature(ctx){
/* ---------- STATS ---------- */

            async function loadStats() {
                const stats = await ctx.api("/stats");
                ctx.state.stats = stats || {};

                const numeric = value => value !== null && value !== undefined &&
                    (typeof value === 'number' || typeof value === 'string' && value.trim() !== '') &&
                    Number.isFinite(Number(value)) && Number(value) >= 0 ? Number(value) : null;
                const count = value => { const parsed = numeric(value); return Number.isInteger(parsed) ? parsed : null; };
                const metric = (id, value) => ctx.setText(id, count(value) ?? '—');
                metric('statAntennas', stats?.antennas);
                metric('statOperators', stats?.operators);
                metric('statInterventions', stats?.interventions);
                metric('statTasks', stats?.tasks);
                metric('statLowStock', stats?.lowStock);
                metric('statOverdueTasks', stats?.taskStats?.overdue);
                const rawAvailability = numeric(stats?.availability);
                const availability = rawAvailability !== null && rawAvailability <= 100 ? rawAvailability : null;
                const active = count(stats?.activeAntennas);
                const inactive = count(stats?.inactiveAntennas);
                const total = count(stats?.antennas);
                const availabilityText = availability === null ? '—' : `${ctx.number(availability, 2)}%`;
                const ratio = value => `${value ?? '—'} / ${total ?? '—'}`;

                ctx.setText("availabilityValue", availabilityText);
                ctx.setText("availabilityLabel", availabilityText);
                ctx.setText("activeCount", ratio(active));
                ctx.setText("inactiveCount", ratio(inactive));

                ctx.$("activeProgress").style.width = `${availability ?? 0}%`;
                ctx.$("inactiveProgress").style.width = `${total && inactive !== null ? Math.min(100, (inactive / total) * 100) : 0}%`;

                const availabilityBadge = ctx.$("availabilityBadge");
                availabilityBadge.textContent = total === 0 ? 'NESSUN ASSET' :
                    total === null || active === null ? 'Conteggio non disponibile' : `${active} / ${total} attive`;
                availabilityBadge.className =
                    `badge ${availability === null || total === null || total === 0 || active === null ? 'gray' : availability >= 95 ? "green" : availability >= 80 ? "yellow" : "red"}`;

                const tasks = stats?.taskStats || {};
                metric('taskAssigned', tasks.assigned);
                metric('taskProgress', tasks.inProgress);
                metric('taskCompleted', tasks.completed);
                metric('taskCancelled', tasks.cancelled);

                metric('opsAssigned', tasks.assigned);
                metric('opsProgress', tasks.inProgress);
                metric('opsCompleted', tasks.completed);
                metric('opsCancelled', tasks.cancelled);
                ctx.setText("opsAvailability", availabilityText);
                ctx.$("opsAvailabilityProgress").style.width = `${availability ?? 0}%`;
                ctx.setText("opsActive", active ?? '—');
                ctx.setText("opsInactive", inactive ?? '—');

                const calculatedAt = stats?.asOf ? new Date(stats.asOf) : null;
                ctx.setText("lastUpdate", calculatedAt && Number.isFinite(calculatedAt.getTime())
                    ? calculatedAt.toLocaleString("it-IT") : "Ora del dato non disponibile");
                setBackendState(true);
                loadSystemHealth().catch(() => { });
            }

            async function loadSystemHealth() {
                try {
                    const r = await ctx.apiFetch("/api/v1/health/firebase");
                    const ok = r?.status === "UP";
                    ctx.setText("firestoreStatus", ok ? "OK" : "DOWN");
                    ctx.$("firestoreBadge").textContent = ok ? "CONNECTED" : "OFFLINE";
                    ctx.$("firestoreBadge").className = `badge ${ok ? "green" : "red"}`;
                } catch (e) {
                    ctx.setText("firestoreStatus", "DOWN");
                    ctx.$("firestoreBadge").textContent = "OFFLINE";
                    ctx.$("firestoreBadge").className = "badge red";
                }
            }

            function setBackendState(ok) {
                ctx.setText("backendStatus", ok ? "OK" : "ERROR");
                ctx.$("backendBadge").textContent = ok ? "ONLINE" : "OFFLINE";
                ctx.$("backendBadge").className = `badge ${ok ? "green" : "red"}`;

                if (!ok) {
                    ctx.setText("firestoreStatus", "UNKNOWN");
                    ctx.$("firestoreBadge").textContent = "UNKNOWN";
                    ctx.$("firestoreBadge").className = "badge gray";
                }
            }
return {get loadStats(){return loadStats;},get loadSystemHealth(){return loadSystemHealth;},get setBackendState(){return setBackendState;}};
}
