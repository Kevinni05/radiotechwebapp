import {createPagedResource,preserveTableInteraction} from '../paged-resource.js';
// inventory: presentation and operations for this feature.
export function createFeature(ctx){
const pages=createPagedResource(ctx,{path:'/api/v1/dashboard/resources/inventory/page',legacy:'/api/v1/inventory',stateKey:'inventory',tableId:'inventoryTableBody',filters:()=>({q:ctx.$('inventorySearch')?.value||'',category:ctx.$('inventoryCategoryFilter')?.value||'',stock:ctx.$('inventoryStockFilter')?.value||''}),onUpdated:renderInventory});
/* ---------- INVENTORY ---------- */

            async function loadInventory() {
                try {
                    await pages();
                    renderInventory();
                } catch (error) {
                    ctx.$("inventoryTableBody").innerHTML = `<tr><td colspan="7"><div class="empty">Magazzino non disponibile: ${ctx.escapeHtml(error.message)}</div></td></tr>`;
                    ctx.$("inventoryStatus").textContent = "Errore di sincronizzazione";
                }
            }

            function renderInventory(){preserveTableInteraction(ctx.$("inventoryTableBody"),renderInventoryRows);}
            function renderInventoryRows() {
                const activeItems = ctx.state.inventory.filter(item => item.active !== false);
                const totalUnits = activeItems.reduce((sum, item) => sum + inventoryQty(item), 0);
                const lowItems = activeItems.filter(inventoryIsLow);
                const totalValue = activeItems.reduce((sum, item) => sum + inventoryQty(item) * Number(item.unitCost || 0), 0);
                const stats = ctx.$("inventoryStats");
                if (stats) stats.innerHTML = `
                    <div class="stat-card"><div class="stat-label">Articoli attivi</div><div class="stat-value">${activeItems.length}</div><div class="stat-foot">articoli caricati</div></div>
                    <div class="stat-card"><div class="stat-label">Unità disponibili</div><div class="stat-value">${ctx.number(totalUnits, 0)}</div><div class="stat-foot">quantità negli articoli caricati</div></div>
                    <div class="stat-card"><div class="stat-label">Sotto scorta</div><div class="stat-value">${lowItems.length}</div><div class="stat-foot">fra gli articoli caricati</div></div>
                    <div class="stat-card"><div class="stat-label">Valore stimato</div><div class="stat-value">${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR", maximumFractionDigits: 0 }).format(totalValue)}</div><div class="stat-foot">stima degli articoli caricati</div></div>`;

                const categories = [...new Set([ctx.$('inventoryCategoryFilter')?.value,...ctx.state.inventory.map(item => item.category)].filter(Boolean))].sort((a, b) => a.localeCompare(b, "it"));
                const categoryFilter = ctx.$("inventoryCategoryFilter");
                if (categoryFilter) {
                    const selected = categoryFilter.value;
                    categoryFilter.innerHTML = '<option value="">Tutte le categorie</option>' + categories.map(category => `<option value="${ctx.escapeHtml(category)}">${ctx.escapeHtml(category)}</option>`).join("");
                    categoryFilter.value = categories.includes(selected) ? selected : "";
                }
                ctx.$("inventoryStatus").textContent = `${activeItems.length} articoli attivi · ${lowItems.length} fra gli articoli caricati`;

                const search = (ctx.$("inventorySearch")?.value || "").trim().toLocaleLowerCase("it-IT");
                const category = categoryFilter?.value || "";
                const stockFilter = ctx.$("inventoryStockFilter")?.value || "ACTIVE";
                const visible = ctx.state.inventory.filter(item => {
                    const active = item.active !== false;
                    const haystack = [item.sku, item.name, item.barcode, item.location, item.warehouse, item.aisle, item.rack, item.bin, item.category, item.supplier].join(" ").toLocaleLowerCase("it-IT");
                    return (stockFilter === "ALL" || (stockFilter === "ARCHIVED" ? !active : active))
                        && (stockFilter !== "LOW" || (active && inventoryIsLow(item)))
                        && (!category || item.category === category)
                        && (!search || haystack.includes(search));
                }).sort((a, b) => String(a.name || "").localeCompare(String(b.name || ""), "it"));
                const table = ctx.$("inventoryTableBody");
                if (!visible.length) {
                    table.innerHTML = `<tr><td colspan="7"><div class="empty">${ctx.state.inventory.length ? "Nessun articolo corrisponde ai filtri." : "Il magazzino è vuoto. Aggiungi il primo articolo."}</div></td></tr>`;
                    return;
                }
                table.innerHTML = visible.map(item => {
                    const low = inventoryIsLow(item);
                    const active = item.active !== false;
                    const qty = inventoryQty(item);
                    const value = qty * Number(item.unitCost || 0);
                    const stockLabel = low ? "RIORDINO" : "DISPONIBILE";
                    return `<tr>
                        <td><strong>${ctx.escapeHtml(item.name || "Articolo")}</strong><div class="subtle">${ctx.escapeHtml(item.sku || item.id || "—")}${item.barcode ? ` · ${ctx.escapeHtml(item.barcode)}` : ""}</div></td>
                        <td>${ctx.escapeHtml(item.category || "Generale")}<div class="subtle">${ctx.escapeHtml(item.unit || "pz")}</div></td>
                        <td><strong>${ctx.number(qty, 0)}</strong> ${ctx.escapeHtml(item.unit || "pz")}<div class="subtle">Soglia ${ctx.number(Number(item.minimumThreshold || item.threshold || item.soglia || 0), 0)} · Riordino ${ctx.number(Number(item.reorderQuantity || 0), 0)}</div></td>
                        <td>${ctx.escapeHtml([item.warehouse,item.aisle,item.rack,item.bin,item.location].filter(Boolean).join(" · ") || "—")}${item.supplier ? `<div class="subtle">${ctx.escapeHtml(item.supplier)}</div>` : ""}</td>
                        <td>${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(value)}<div class="subtle">${new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(item.unitCost || 0))} / ${ctx.escapeHtml(item.unit || "pz")}</div></td>
                        <td><span class="badge ${!active ? "red" : low ? "yellow" : "green"}">${!active ? "ARCHIVIATO" : stockLabel}</span></td>
                        <td><div class="actions">${active ? `<button class="mini-btn" data-inventory-edit="${ctx.escapeHtml(item.id)}">Modifica</button><button class="mini-btn" data-inventory-archive="${ctx.escapeHtml(item.id)}">Archivia</button><button class="mini-btn" data-inventory-move="${ctx.escapeHtml(item.id)}">Movimenta</button>` : `<button class="mini-btn" data-inventory-restore="${ctx.escapeHtml(item.id)}">Ripristina</button>`}</div></td>
                    </tr>`;
                }).join("");
                ctx.applyPermissions();
                table.querySelectorAll("[data-inventory-move]").forEach(button => button.addEventListener("click", () => openInventoryMovement(button.dataset.inventoryMove)));
                table.querySelectorAll("[data-inventory-edit]").forEach(button => button.addEventListener("click", () => openInventoryItem(button.dataset.inventoryEdit)));
                table.querySelectorAll("[data-inventory-archive]").forEach(button => button.addEventListener("click", () => archiveInventoryItem(button.dataset.inventoryArchive)));
                table.querySelectorAll("[data-inventory-restore]").forEach(button => button.addEventListener("click", () => restoreInventoryItem(button.dataset.inventoryRestore)));
            }

            function inventoryQty(item) { return Math.max(0, Number(item.quantity ?? item.quantitaDisponibile ?? item.stock ?? 0)); }
            function inventoryIsLow(item) {
                const threshold = Number(item.minimumThreshold ?? item.threshold ?? item.soglia ?? item.sogliaMinima ?? 0);
                return item.active !== false && threshold >= 0 && inventoryQty(item) <= threshold;
            }

            function openInventoryItem(id = "") {
                const item = id ? ctx.state.inventory.find(row => row.id === id) : null;
                ctx.$("inventoryItemForm").reset();
                ctx.$("inventoryItemId").value = item?.id || "";
                ctx.$("inventorySku").value = item?.sku || "";
                ctx.$("inventoryName").value = item?.name || "";
                ctx.$("inventoryCategory").value = item?.category || "";
                ctx.$("inventoryUnit").value = item?.unit || "pz";
                ctx.$("inventoryBarcode").value = item?.barcode || "";
                ctx.$("inventoryLocation").value = item?.location || "";
                for (const [id,field] of [ ["Warehouse","warehouse"],["Aisle","aisle"],["Rack","rack"],["Bin","bin"]]) ctx.$("inventory"+id).value=item?.[field]||"";
                ctx.$("inventorySupplier").value = item?.supplier || "";
                ctx.$("inventoryUnitCost").value = Number(item?.unitCost || 0);
                ctx.$("inventoryMinimum").value = Number(item?.minimumThreshold || item?.threshold || 0);
                ctx.$("inventoryReorder").value = Number(item?.reorderQuantity || item?.minimumThreshold || 1);
                ctx.$("inventoryInitialQuantity").value = 0;
                ctx.$("inventoryDescription").value = item?.description || "";
                ctx.$("inventoryItemModalTitle").textContent = item ? `Modifica ${item.name}` : "Nuovo articolo";
                ctx.$("inventoryInitialQuantityField").classList.toggle("hidden", Boolean(item));
                ctx.$("inventoryItemFormStatus").textContent = item ? `Disponibilità attuale: ${inventoryQty(item)} ${item.unit || "pz"}. Usa “Movimenta” per modificare le scorte.` : "La quantità iniziale sarà registrata come carico nel registro movimenti.";
                ctx.openModal("inventoryItemModal");
            }

            async function saveInventoryItem(event) {
                event.preventDefault();
                const id = ctx.$("inventoryItemId").value;
                const payload = {
                    sku: ctx.$("inventorySku").value.trim(), name: ctx.$("inventoryName").value.trim(),
                    category: ctx.$("inventoryCategory").value.trim(), unit: ctx.$("inventoryUnit").value.trim() || "pz",
                    barcode: ctx.$("inventoryBarcode").value.trim(), location: ctx.$("inventoryLocation").value.trim(),
                    supplier: ctx.$("inventorySupplier").value.trim(),
                        warehouse: ctx.$("inventoryWarehouse").value.trim(), aisle: ctx.$("inventoryAisle").value.trim(), rack: ctx.$("inventoryRack").value.trim(), bin: ctx.$("inventoryBin").value.trim(), unitCost: Number(ctx.$("inventoryUnitCost").value || 0),
                    minimumThreshold: Number(ctx.$("inventoryMinimum").value || 0), reorderQuantity: Number(ctx.$("inventoryReorder").value || 0),
                    description: ctx.$("inventoryDescription").value.trim()
                };
                if (!id) payload.quantity = Number(ctx.$("inventoryInitialQuantity").value || 0);
                const button = ctx.$("saveInventoryItem");
                button.disabled = true;
                try {
                    await ctx.apiFetch(id ? `/api/v1/inventory/${encodeURIComponent(id)}` : "/api/v1/inventory", {
                        method: id ? "PUT" : "POST", body: JSON.stringify(payload)
                    });
                    ctx.closeModal("inventoryItemModal");
                    await loadInventory();
                    ctx.toast(id ? "Articolo aggiornato" : "Articolo creato", "Magazzino sincronizzato con il backend.");
                } catch (error) {
                    ctx.toast("Salvataggio non riuscito", error.message, "error");
                } finally { button.disabled = false; }
            }

            function openInventoryMovement(id) {
                const item = ctx.state.inventory.find(row => row.id === id);
                if (!item) return;
                ctx.$("inventoryMovementForm").reset();
                ctx.$("inventoryMovementItemId").value = id;
                ctx.$("inventoryMovementItemName").textContent = `${item.name} · ${item.sku} · giacenza ${inventoryQty(item)} ${item.unit || "pz"}`;
                ctx.$("inventoryMovementQuantity").value = "";
                updateInventoryMovementHint();
                ctx.openModal("inventoryMovementModal");
            }

            function updateInventoryMovementHint() {
                const type = ctx.$("inventoryMovementType").value;
                ctx.$("inventoryMovementQuantityLabel").textContent = type === "ADJUSTMENT" ? "Giacenza reale conteggiata" : "Quantità da movimentare";
                ctx.$("inventoryMovementQuantity").min = type === "ADJUSTMENT" ? "0" : "1";
            }

            async function saveInventoryMovement(event) {
                event.preventDefault();
                const button = event.submitter || event.target.querySelector('[type="submit"]');
                if (button.disabled) return;
                button.disabled = true;
                const id = ctx.$("inventoryMovementItemId").value;
                try {
                    await ctx.apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}/movements`, {
                        method: "POST", body: JSON.stringify({
                            type: ctx.$("inventoryMovementType").value,
                            quantity: Number(ctx.$("inventoryMovementQuantity").value),
                            lotCode: ctx.$("inventoryMovementLot").value.trim(), lotExpiresOn: ctx.$("inventoryMovementLotExpiry").value,
                            reference: ctx.$("inventoryMovementReference").value.trim(),
                            note: ctx.$("inventoryMovementNote").value.trim()
                        })
                    });
                    ctx.closeModal("inventoryMovementModal");
                    await loadInventory();
                    ctx.toast("Movimento registrato", "Giacenza e registro sono stati aggiornati.");
                } catch (error) { ctx.toast("Movimento non riuscito", error.message, "error"); }
                finally { button.disabled = false; }
            }

            async function loadInventoryMovements() {
                const body = ctx.$("inventoryMovementsBody");
                body.innerHTML = '<tr><td colspan="6"><div class="empty"><span class="spinner"></span> Caricamento movimenti...</div></td></tr>';
                try {
                    const data = await ctx.apiFetch("/api/v1/inventory/movements?limit=250");
                    ctx.state.inventoryMovements = Array.isArray(data) ? data : (data?.data || []);
                    renderInventoryMovements();
                } catch (error) {
                    body.innerHTML = `<tr><td colspan="6"><div class="empty">Registro non disponibile: ${ctx.escapeHtml(error.message)}</div></td></tr>`;
                }
            }

            function renderInventoryMovements() {
                const body = ctx.$("inventoryMovementsBody");
                if (!ctx.state.inventoryMovements.length) {
                    body.innerHTML = '<tr><td colspan="6"><div class="empty">Nessun movimento registrato.</div></td></tr>';
                    return;
                }
                const labels = { RECEIPT: "CARICO", ISSUE: "SCARICO", ADJUSTMENT: "RETTIFICA", CONSUMPTION: "CONSUMO TASK" };
                body.innerHTML = ctx.state.inventoryMovements.map(movement => {
                    const quantity = Number(movement.quantity || 0);
                    const signed = ["ISSUE", "CONSUMPTION"].includes(movement.type) ? -Math.abs(quantity) : quantity;
                    const when = movement.createdAt ? new Date(movement.createdAt).toLocaleString("it-IT") : "—";
                    return `<tr><td>${ctx.escapeHtml(when)}</td><td><strong>${ctx.escapeHtml(movement.name || "Articolo")}</strong><div class="subtle">${ctx.escapeHtml(movement.sku || "")}</div></td><td>${ctx.escapeHtml(labels[movement.type] || movement.type || "—")}</td><td class="${signed < 0 ? "text-danger" : ""}">${signed > 0 ? "+" : ""}${ctx.number(signed, 0)}</td><td>${movement.previousQuantity == null ? "—" : `${ctx.number(Number(movement.previousQuantity), 0)} → ${ctx.number(Number(movement.resultingQuantity), 0)}`}</td><td>${ctx.escapeHtml([movement.reference, movement.note, movement.lotCode ? "Lotto "+movement.lotCode : "", movement.warehouse, ...(movement.lotAllocations || []).map(l => (l.lotCode || "Senza lotto")+": "+l.quantity)].filter(Boolean).join(" · ") || "—")}</td></tr>`;
                }).join("");
            }

            async function archiveInventoryItem(id) {
                const item = ctx.state.inventory.find(row => row.id === id);
                if (!item || !await ctx.confirm(`Archiviare “${item.name}”? Lo storico resta consultabile e l'articolo sarà ripristinabile.`)) return;
                try {
                    await ctx.apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}`, { method: "DELETE" });
                    await loadInventory();
                    ctx.toast("Articolo archiviato", "L'articolo è stato mantenuto nello storico.");
                } catch (error) { ctx.toast("Archiviazione non riuscita", error.message, "error"); }
            }

            async function restoreInventoryItem(id) {
                try {
                    await ctx.apiFetch(`/api/v1/inventory/${encodeURIComponent(id)}/restore`, { method: "POST" });
                    await loadInventory();
                    ctx.toast("Articolo ripristinato", "L'articolo è di nuovo disponibile.");
                } catch (error) { ctx.toast("Ripristino non riuscito", error.message, "error"); }
            }

            function exportInventoryCsv() {
                const columns = ["sku", "name", "category", "quantity", "unit", "minimumThreshold", "reorderQuantity", "location", "supplier", "barcode", "unitCost", "active"];
                const quote = value => {
                    let text = String(value ?? "");
                    if (/^[\s]*[=+@-]/.test(text)) text = "'" + text;
                    return `"${text.replaceAll('"', '""')}"`;
                };
                const rows = [columns.join(";"), ...ctx.state.inventory.map(item => columns.map(column => quote(column === "quantity" ? inventoryQty(item) : column === "minimumThreshold" ? item.minimumThreshold ?? item.threshold ?? 0 : item[column])).join(";"))];
                const blob = new Blob(["\uFEFF" + rows.join("\r\n")], { type: "text/csv;charset=utf-8" });
                const link = document.createElement("a");
                link.href = URL.createObjectURL(blob);
                link.download = `radiotech-magazzino-${new Date().toISOString().slice(0, 10)}.csv`;
                link.click();
                URL.revokeObjectURL(link.href);
            }
return {get loadInventory(){return loadInventory;},get renderInventory(){return renderInventory;},get inventoryQty(){return inventoryQty;},get inventoryIsLow(){return inventoryIsLow;},get openInventoryItem(){return openInventoryItem;},get saveInventoryItem(){return saveInventoryItem;},get openInventoryMovement(){return openInventoryMovement;},get updateInventoryMovementHint(){return updateInventoryMovementHint;},get saveInventoryMovement(){return saveInventoryMovement;},get loadInventoryMovements(){return loadInventoryMovements;},get renderInventoryMovements(){return renderInventoryMovements;},get archiveInventoryItem(){return archiveInventoryItem;},get restoreInventoryItem(){return restoreInventoryItem;},get exportInventoryCsv(){return exportInventoryCsv;}};
}
