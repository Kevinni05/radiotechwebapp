// tasks: presentation and operations for this feature.
export function createFeature(ctx){
let loadingTasks = false, loadingReports = false, reloadReports = false;
let lastSearch=null,reloadTasks=false;
            async function loadTasks(append = false) {
                append = append === true;
                if (loadingTasks) {if(!append)reloadTasks=true;return;}
                loadingTasks = true; ctx.$('tasksMoreBtn').disabled = true;
                try {
                    const search=ctx.$('taskSearch')?.value.trim()||'';
                    const depth=append?1:search===lastSearch?Math.max(1,Math.ceil(ctx.state.tasks.length/50)):1;
                    let cursor=append?ctx.tasksCursor:null,items=[];
                    for(let page=0;page<depth;page++) {
                        const params=new ctx.URLSearchParams({limit:'50'});if(search)params.set('q',search);if(cursor)params.set('cursor',cursor);
                        const response=await ctx.apiFetch(`/api/v1/tasks/page?${params}`),data=response?.data??response;
                        if(search!==(ctx.$('taskSearch')?.value.trim()||'')){reloadTasks=true;return;}
                        items.push(...(Array.isArray(data)?data:(data?.items||[])));cursor=data?.nextCursor||null;if(!cursor)break;
                    }
                    ctx.state.tasks=[...new Map([...(append?ctx.state.tasks:[]),...items].map(item=>[item.id,item])).values()];
                    ctx.tasksCursor=cursor;lastSearch=search;
                    ctx.$('tasksMoreBtn').hidden = !ctx.tasksCursor;
                    ctx.$('tasksPageStatus').textContent = `${ctx.state.tasks.length} incarichi caricati${ctx.tasksCursor ? ' · altri disponibili' : ' · fine elenco'}`;
                    renderTaskList(); populateTaskSelectors(); ctx.refreshReportTitles();
                } catch (e) { if (append || ctx.state.tasks.length) ctx.toast('Caricamento non riuscito', e.message, 'error'); else ctx.$("tasksList").innerHTML = `<div class="empty">Incarichi non disponibili: ${ctx.escapeHtml(e.message)}</div>`; }
                finally { loadingTasks = false; ctx.$('tasksMoreBtn').disabled = false;if(reloadTasks){reloadTasks=false;if(ctx.state.token)loadTasks();} }
            }
            function eligibleOperators() {
                return ctx.state.operators.filter(operator => {
                    const role = (operator.role || "OPERATOR").toUpperCase();
                    const status = (operator.status || "").toUpperCase();
                    return role === "OPERATOR" && !["IN_ATTESA", "INATTIVO", "DISATTIVATO"].includes(status);
                });
            }
            function populateTaskSelectors() {
                const op = ctx.$("taskOperator"); if (op) op.innerHTML = '<option value="">Seleziona operatore</option>' + eligibleOperators().map(o => `<option value="${ctx.escapeHtml(o.id)}">${ctx.escapeHtml(o.fullName || o.email || o.id)}</option>`).join("");
                const an = ctx.$("taskAntenna"); if (an) an.innerHTML = '<option value="">Seleziona antenna</option>' + ctx.state.antennas.map(a => `<option value="${ctx.escapeHtml(a.id)}">${ctx.escapeHtml(a.name || a.id)}</option>`).join("");
            }
            function renderTaskList() {
                if (!ctx.$('tasksList')) return;
                const normalize=value=>String(value||'').normalize('NFKD').replace(/\p{M}/gu,'').toLowerCase();
                const query = normalize((ctx.$('taskSearch')?.value || '').trim());
                const list = ctx.state.tasks.filter(t => !query || normalize([t.id,t.title, t.description, t.operatorName, t.operatorId, t.status, t.antennaId].join(' ')).includes(query));
                ctx.$('tasksList').innerHTML = list.length ? list.map(t => `<div class="task-row"><div><div class="task-title">${ctx.escapeHtml(t.title || t.id)}</div><div class="task-meta">${ctx.escapeHtml(t.operatorName || t.operatorId || t.operator_uid || "Non assegnato")} · ${ctx.escapeHtml(t.antennaId || "—")} · ${t.dueAt ? `scade ${ctx.escapeHtml(new Date(t.dueAt).toLocaleString('it-IT'))}` : 'senza scadenza'} · ID ${ctx.escapeHtml(t.id)}</div></div><div class="actions"><span class="badge ${ctx.badgeClass(t.status)}">${ctx.escapeHtml(ctx.formatStatus(t.status))}</span>${!["COMPLETED", "REPORT_SUBMITTED", "APPROVED", "CLOSED", "CANCELLED"].includes(t.status) ? `<button class="mini-btn" data-task-cancel="${ctx.escapeHtml(t.id)}">Annulla incarico</button>` : ''}</div></div>`).join('') : '<div class="empty">Nessun incarico.</div>';
                ctx.$('tasksList').querySelectorAll('[data-task-cancel]').forEach(button => {
                    button.hidden = !["ADMIN", "SUPER_ADMIN", "CHIEF_EXECUTIVE", "CAPO", "NETWORK_MANAGER", "ENGINEER"].includes(ctx.state.authRole || ctx.state.user?.role);
                    button.addEventListener('click', async () => {
                        const task = ctx.state.tasks.find(t => t.id === button.dataset.taskCancel);
                        if (!task || !await ctx.confirm(`Annullare l’incarico «${task.title || task.id}»? Resterà visibile nello storico.`)) return;
                        button.disabled = true;
                        try {
                            await ctx.apiFetch(`/api/v1/tasks/${encodeURIComponent(task.id)}/status`, {method: 'PATCH', body: JSON.stringify({status: 'CANCELLED'})});
                            await Promise.all([loadTasks(), ctx.loadStats()]);
                            window.dispatchEvent(new Event('radiotech:tasks-changed'));
                            ctx.toast('Incarico annullato', 'L’incarico non blocca più la rimozione dell’operatore.');
                        } catch (error) { ctx.toast('Annullamento fallito', error.message, 'error'); }
                        finally { button.disabled = false; }
                    });
                });
            }
            async function createTask() {
                const button = ctx.$("createTaskBtn");
                if (button.disabled) return;
                const title = ctx.$("taskTitle").value.trim(), operatorId = ctx.$("taskOperator").value, antennaId = ctx.$("taskAntenna").value;
                if (!title || !operatorId || !antennaId) { ctx.toast("Incarico incompleto", "Titolo, operatore e antenna sono obbligatori per il flusso sul campo.", "error"); return; }
                const dueAt = ctx.$("taskDueAt").value ? new Date(ctx.$("taskDueAt").value).toISOString() : null;
                button.disabled = true;
                try { await ctx.apiFetch("/api/v1/tasks", { method: "POST", body: JSON.stringify({ title, description: ctx.$("taskDescription").value.trim(), operatorId, antennaId: antennaId || null, dueAt, status: "ASSIGNED", priority: ctx.$("taskPriority").value }) }); ctx.$("taskTitle").value = ""; ctx.$("taskDescription").value = ""; ctx.$("taskDueAt").value = ""; await Promise.all([loadTasks(), ctx.loadStats()]); ctx.toast("Incarico assegnato", "L'operatore può ora visualizzarlo nell'app mobile."); } catch (e) { ctx.toast("Assegnazione fallita", e.message, "error"); }
                finally { button.disabled = false; }
            }
return {get loadTasks(){return loadTasks;},get eligibleOperators(){return eligibleOperators;},get populateTaskSelectors(){return populateTaskSelectors;},get renderTaskList(){return renderTaskList;},get createTask(){return createTask;},get loadingTasks(){return loadingTasks;},get loadingReports(){return loadingReports;},get reloadReports(){return reloadReports;},set loadingTasks(value){loadingTasks=value;},set loadingReports(value){loadingReports=value;},set reloadReports(value){reloadReports=value;}};
}
