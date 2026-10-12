// dashboard: presentation and operations for this feature.
export function createFeature(ctx){
/* ---------- STATS ---------- */

            async function loadStats() {
                const stats = await ctx.api("/stats");
                ctx.state.stats = stats || {};

                ctx.setText("statAntennas", stats?.antennas ?? 0);
                ctx.setText("statOperators", stats?.operators ?? 0);
                ctx.setText("statInterventions", stats?.interventions ?? 0);
                ctx.setText("statTasks", stats?.tasks ?? 0);
                ctx.setText("statLowStock", stats?.lowStock ?? 0);
                ctx.setText("statOverdueTasks", stats?.taskStats?.overdue ?? 0);

                const availability = Number(stats?.availability || 0);
                const active = Number(stats?.activeAntennas || 0);
                const inactive = Number(stats?.inactiveAntennas || 0);
                const total = Number(stats?.antennas || 0);

                ctx.setText("availabilityValue", `${ctx.number(availability, 2)}%`);
                ctx.setText("availabilityLabel", `${ctx.number(availability, 2)}%`);
                ctx.setText("activeCount", `${active} / ${total}`);
                ctx.setText("inactiveCount", `${inactive} / ${total}`);

                ctx.$("activeProgress").style.width = `${Math.min(100, availability)}%`;
                ctx.$("inactiveProgress").style.width = `${total ? Math.min(100, (inactive / total) * 100) : 0}%`;

                const availabilityBadge = ctx.$("availabilityBadge");
                availabilityBadge.textContent = total ? `${active} / ${total} attive` : "NESSUN ASSET";
                availabilityBadge.className =
                    `badge ${availability >= 95 ? "green" : availability >= 80 ? "yellow" : "red"}`;

                const tasks = stats?.taskStats || {};
                ctx.setText("taskAssigned", tasks.assigned ?? 0);
                ctx.setText("taskProgress", tasks.inProgress ?? 0);
                ctx.setText("taskCompleted", tasks.completed ?? 0);
                ctx.setText("taskCancelled", tasks.cancelled ?? 0);

                ctx.setText("opsAssigned", tasks.assigned ?? 0);
                ctx.setText("opsProgress", tasks.inProgress ?? 0);
                ctx.setText("opsCompleted", tasks.completed ?? 0);
                ctx.setText("opsCancelled", tasks.cancelled ?? 0);
                ctx.setText("opsAvailability", `${ctx.number(availability, 2)}%`);
                ctx.$("opsAvailabilityProgress").style.width = `${Math.min(100, availability)}%`;
                ctx.setText("opsActive", active);
                ctx.setText("opsInactive", inactive);

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
