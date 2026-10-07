package com.radiotech.radiotech_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@Service
public class RicambioService {

    private static final String COLLECTION = "inventory";
    private final AuditService auditService;

    public RicambioService(AuditService auditService) {
        this.auditService = auditService;
    }

    public List<Map<String, Object>> getAll()
            throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant())
                .get();

        List<QueryDocumentSnapshot> docs = future.get().getDocuments();

        List<Map<String, Object>> lista = new ArrayList<>();

        for (QueryDocumentSnapshot doc : docs) {

            Map<String, Object> data = new HashMap<>(doc.getData());

            data.put("id", doc.getId());

            lista.add(data);
        }

        return lista;
    }

    public Map<String, Object> createItem(Map<String, Object> request) throws Exception {
        String tenantId = currentTenant();
        String sku = requiredText(request, "sku", 80);
        String name = requiredText(request, "name", 160);
        ensureUniqueSku(sku, null, tenantId);
        int quantity = nonNegativeInt(request, "quantity", 0);
        int minimum = nonNegativeInt(request, "minimumThreshold", 0);
        int reorder = nonNegativeInt(request, "reorderQuantity", minimum > 0 ? minimum : 1);
        String now = Instant.now().toString();
        DocumentReference reference = FirestoreClient.getFirestore().collection(COLLECTION).document();
        Map<String, Object> item = new HashMap<>();
        item.put("id", reference.getId());
        item.put("tenantId", tenantId);
        item.put("sku", sku);
        item.put("name", name);
        item.put("category", optionalText(request, "category", 80, "GENERALE"));
        item.put("description", optionalText(request, "description", 1000, ""));
        item.put("unit", optionalText(request, "unit", 20, "pz"));
        item.put("location", optionalText(request, "location", 120, ""));
        for (String field : List.of("warehouse", "aisle", "rack", "bin")) item.put(field, optionalText(request, field, 80, ""));
        item.put("supplier", optionalText(request, "supplier", 160, ""));
        item.put("barcode", optionalText(request, "barcode", 100, ""));
        item.put("unitCost", nonNegativeDecimal(request, "unitCost", 0d));
        item.put("quantity", quantity);
        item.put("minimumThreshold", minimum);
        item.put("reorderQuantity", reorder);
        item.put("active", true);
        item.put("createdAt", now);
        item.put("updatedAt", now);

        Firestore db = FirestoreClient.getFirestore();
        DocumentReference movement = db.collection("inventoryMovements").document();
        db.runTransaction(transaction -> {
            transaction.set(reference, item);
            if (quantity > 0) {
                transaction.set(movement, movement(tenantId, item, "RECEIPT", quantity, 0, quantity,
                        optionalText(request, "note", 500, "Carico iniziale"), null, now));
            }
            return null;
        }).get();
        auditService.record("INVENTORY_CREATED", tenantId, SecurityContextAccessor.currentUid(), "INVENTORY",
                reference.getId(), "SUCCESS", null, Map.of("sku", sku, "quantity", quantity));
        return item;
    }

    public Map<String, Object> updateItem(String id, Map<String, Object> request) throws Exception {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ID articolo obbligatorio.");
        String tenantId = currentTenant();
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference reference = db.collection(COLLECTION).document(id.trim());
        DocumentSnapshot snapshot = reference.get().get();
        requireInventoryTenant(snapshot, tenantId);
        if (request.containsKey("sku")) {
            String sku = requiredText(request, "sku", 80);
            ensureUniqueSku(sku, snapshot.getId(), tenantId);
        }
        Map<String, Object> updates = new HashMap<>();
        if (request.containsKey("sku")) updates.put("sku", requiredText(request, "sku", 80));
        if (request.containsKey("name")) updates.put("name", requiredText(request, "name", 160));
        if (request.containsKey("category")) updates.put("category", optionalText(request, "category", 80, "GENERALE"));
        if (request.containsKey("description")) updates.put("description", optionalText(request, "description", 1000, ""));
        if (request.containsKey("unit")) updates.put("unit", optionalText(request, "unit", 20, "pz"));
        if (request.containsKey("location")) updates.put("location", optionalText(request, "location", 120, ""));
        for (String field : List.of("warehouse", "aisle", "rack", "bin")) if (request.containsKey(field)) updates.put(field, optionalText(request, field, 80, ""));
        if (request.containsKey("supplier")) updates.put("supplier", optionalText(request, "supplier", 160, ""));
        if (request.containsKey("barcode")) updates.put("barcode", optionalText(request, "barcode", 100, ""));
        if (request.containsKey("unitCost")) updates.put("unitCost", nonNegativeDecimal(request, "unitCost", 0d));
        if (request.containsKey("minimumThreshold")) updates.put("minimumThreshold", nonNegativeInt(request, "minimumThreshold", 0));
        if (request.containsKey("reorderQuantity")) updates.put("reorderQuantity", nonNegativeInt(request, "reorderQuantity", 1));
        if (updates.isEmpty()) throw new IllegalArgumentException("Nessun dato da aggiornare.");
        updates.put("updatedAt", Instant.now().toString());
        reference.update(updates).get();
        auditService.record("INVENTORY_ITEM_UPDATED", tenantId, SecurityContextAccessor.currentUid(), "INVENTORY",
                id.trim(), "SUCCESS", null, updates);
        Map<String, Object> result = new HashMap<>(snapshot.getData());
        result.putAll(updates);
        result.put("id", snapshot.getId());
        return result;
    }

    public Map<String, Object> recordMovement(String id, Map<String, Object> request) throws Exception {
        String type = text(request.get("type")).toUpperCase(Locale.ROOT);
        if (!List.of("RECEIPT", "ISSUE", "ADJUSTMENT").contains(type)) {
            throw new IllegalArgumentException("Tipo movimento non valido.");
        }
        int quantity = nonNegativeInt(request, "quantity", -1);
        if (quantity < 0 || ("ADJUSTMENT".equals(type) ? false : quantity == 0)) {
            throw new IllegalArgumentException("La quantità del movimento deve essere valida e maggiore di zero.");
        }
        String lotCode = optionalText(request, "lotCode", 100, "");
        if (!lotCode.isBlank() && !lotCode.matches("[A-Za-z0-9._-]{1,100}")) throw new IllegalArgumentException("Il lotto può contenere lettere, numeri, punti, trattini e underscore.");
        String expires = optionalText(request, "lotExpiresOn", 10, "");
        if (!expires.isBlank()) { try { java.time.LocalDate.parse(expires); } catch(Exception e) { throw new IllegalArgumentException("Scadenza lotto non valida."); } }
        String tenantId = currentTenant();
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference itemReference = db.collection(COLLECTION).document(id.trim());
        DocumentReference movementReference = db.collection("inventoryMovements").document();
        String now = Instant.now().toString();
        Map<String, Object> result;
        try { result = db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(itemReference).get();
            requireInventoryTenant(snapshot, tenantId);
            if (Boolean.FALSE.equals(snapshot.getBoolean("active")))
                throw new IllegalArgumentException("Articolo archiviato: riattivalo prima di movimentarlo.");
            Map<String, Object> item = snapshot.getData();
            int previous = number(snapshot.get("quantity"));
            Map<String,Object> lots = snapshot.get("lots") instanceof Map<?,?> raw ? new HashMap<>((Map<String,Object>)raw) : new HashMap<>();
            int tracked = 0;
            for (Object raw : lots.values()) if (raw instanceof Map<?,?> data) tracked = Math.addExact(tracked, number(data.get("quantity")));
            Map<String,Object> lot = !lotCode.isBlank() && lots.get(lotCode) instanceof Map<?,?> raw ? new HashMap<>((Map<String,Object>)raw) : new HashMap<>();
            int lotPrevious = number(lot.get("quantity"));
            if ("ISSUE".equals(type)) {
                int available = lotCode.isBlank() ? previous-tracked : lotPrevious;
                if (quantity > available) throw new IllegalArgumentException(lotCode.isBlank()?"Quantità non disponibile senza lotto. Seleziona il lotto da scaricare.":"Quantità insufficiente nel lotto selezionato.");
                String expiry = text(lot.get("expiresOn"));
                if (!lotCode.isBlank() && !expiry.isBlank() && java.time.LocalDate.parse(expiry).isBefore(java.time.LocalDate.now(java.time.ZoneOffset.UTC))) throw new IllegalArgumentException("Lotto scaduto: non utilizzabile per interventi.");
            }
            if ("ADJUSTMENT".equals(type) && lotCode.isBlank() && tracked > 0) throw new IllegalArgumentException("Articolo con lotti: rettifica il singolo lotto per preservare la tracciabilità.");
            int target;
            if ("RECEIPT".equals(type)) target = Math.addExact(previous, quantity);
            else if ("ISSUE".equals(type)) {
                if (previous < quantity) throw new IllegalArgumentException("Scorta insufficiente: disponibili " + previous + ".");
                target = previous - quantity;
            } else target = lotCode.isBlank() ? quantity : Math.addExact(previous, quantity-lotPrevious);
            int delta = target - previous;
            Map<String,Object> stockUpdate = new HashMap<>(); stockUpdate.put("quantity", target); stockUpdate.put("updatedAt", now);
            if (!lotCode.isBlank()) {
                int lotTarget = "RECEIPT".equals(type)?Math.addExact(lotPrevious,quantity):"ISSUE".equals(type)?lotPrevious-quantity:quantity;
                if (!expires.isBlank() && lot.containsKey("expiresOn") && !expires.equals(lot.get("expiresOn"))) throw new IllegalArgumentException("Il lotto ha già una scadenza diversa. Usa un codice lotto distinto.");
                lot.put("quantity",lotTarget); lot.put("updatedAt",now); if(!expires.isBlank())lot.put("expiresOn",expires);
                lots.put(lotCode,lot);stockUpdate.put("lots",lots);
            }
            transaction.update(itemReference, stockUpdate);
            Map<String, Object> movementData = movement(tenantId, item, type,
                    "ADJUSTMENT".equals(type) ? delta : quantity, previous, target,
                    optionalText(request, "note", 500, ""), optionalText(request, "reference", 100, ""), now);
            movementData.put("actorUid", SecurityContextAccessor.currentUid() == null ? "SYSTEM" : SecurityContextAccessor.currentUid());
            movementData.put("lotCode", lotCode); movementData.put("lotExpiresOn", expires.isBlank()?text(lot.get("expiresOn")):expires);
            movementData.put("warehouse", text(item.get("warehouse")));movementData.put("location", text(item.get("location")));
            transaction.set(movementReference, movementData);
            Map<String, Object> updated = new HashMap<>(item);
            updated.putAll(stockUpdate);
            updated.put("quantity", target);
            updated.put("updatedAt", now);
            updated.put("id", snapshot.getId());
            return updated;
        }).get(); } catch (java.util.concurrent.ExecutionException error) {
            if(error.getCause() instanceof IllegalArgumentException invalid)throw invalid;
            if(error.getCause() instanceof SecurityException denied)throw denied;
            if(error.getCause() instanceof ArithmeticException)throw new IllegalArgumentException("La quantità supera il limite consentito.");
            throw error;
        }
        auditService.record("INVENTORY_" + type, tenantId, SecurityContextAccessor.currentUid(), "INVENTORY",
                id.trim(), "SUCCESS", null, Map.of("movementId", movementReference.getId(), "quantity", quantity));
        return result;
    }

    public List<Map<String, Object>> getMovements(int limit) throws Exception {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String tenantId = currentTenant();
        List<QueryDocumentSnapshot> docs = FirestoreClient.getFirestore().collection("inventoryMovements")
                .whereEqualTo("tenantId", tenantId).orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(safeLimit).get().get().getDocuments();
        List<Map<String, Object>> movements = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            Map<String, Object> data = new HashMap<>(doc.getData());
            data.put("id", doc.getId());
            movements.add(data);
        }
        return movements;
    }

    public Map<String, Object> archive(String id) throws Exception {
        String tenantId = currentTenant();
        DocumentReference reference = FirestoreClient.getFirestore().collection(COLLECTION).document(id.trim());
        DocumentSnapshot snapshot = reference.get().get();
        requireInventoryTenant(snapshot, tenantId);
        reference.update(Map.of("active", false, "updatedAt", Instant.now().toString())).get();
        auditService.record("INVENTORY_ARCHIVED", tenantId, SecurityContextAccessor.currentUid(), "INVENTORY",
                id.trim(), "SUCCESS", null, Map.of("sku", text(snapshot.get("sku"))));
        return Map.of("id", id.trim(), "active", false);
    }

    public Map<String, Object> restore(String id) throws Exception {
        String tenantId = currentTenant();
        DocumentReference reference = FirestoreClient.getFirestore().collection(COLLECTION).document(id.trim());
        DocumentSnapshot snapshot = reference.get().get();
        requireInventoryTenant(snapshot, tenantId);
        reference.update(Map.of("active", true, "updatedAt", Instant.now().toString())).get();
        auditService.record("INVENTORY_RESTORED", tenantId, SecurityContextAccessor.currentUid(), "INVENTORY",
                id.trim(), "SUCCESS", null, Map.of("sku", text(snapshot.get("sku"))));
        return Map.of("id", id.trim(), "active", true);
    }

    private void ensureUniqueSku(String sku, String currentId, String tenantId) throws Exception {
        for (QueryDocumentSnapshot document : FirestoreClient.getFirestore().collection(COLLECTION)
                .whereEqualTo("tenantId", tenantId).get().get().getDocuments()) {
            if (!document.getId().equals(currentId) && sku.equalsIgnoreCase(text(document.get("sku"))))
                throw new IllegalArgumentException("Esiste già un articolo con questo SKU.");
        }
    }

    private void requireInventoryTenant(DocumentSnapshot snapshot, String tenantId) {
        if (snapshot == null || !snapshot.exists()) throw new IllegalArgumentException("Articolo inventario non trovato.");
        if (!tenantId.equals(snapshot.getString("tenantId"))) throw new SecurityException("Articolo non appartenente al tenant autenticato.");
    }

    private Map<String, Object> movement(String tenantId, Map<String, Object> item, String type, int quantity,
            int previous, int resulting, String note, String reference, String now) {
        Map<String, Object> movement = new HashMap<>();
        movement.put("tenantId", tenantId);
        movement.put("inventoryId", text(item.get("id")));
        movement.put("sku", text(item.get("sku")));
        movement.put("name", text(item.get("name")));
        movement.put("type", type);
        movement.put("quantity", quantity);
        movement.put("previousQuantity", previous);
        movement.put("resultingQuantity", resulting);
        movement.put("note", note);
        movement.put("reference", reference);
        movement.put("actorUid", SecurityContextAccessor.currentUid() == null ? "SYSTEM" : SecurityContextAccessor.currentUid());
        movement.put("createdAt", now);
        return movement;
    }

    /** Consume non-expired lots in FEFO order, then unallocated legacy stock. */
    private Map<String,Object> allocateLots(DocumentSnapshot item, int quantity, String now) {
        var lots = item.get("lots") instanceof Map<?,?> raw ? new HashMap<>((Map<String,Object>)raw) : new HashMap<String,Object>();
        var codes = new ArrayList<>(lots.keySet());
        codes.sort(java.util.Comparator.comparing(code -> {
            var data = (Map<?,?>)lots.get(code); String date = text(data.get("expiresOn"));return date.isBlank()?"9999-12-31":date;
        }));
        int remaining = quantity, tracked = 0; var allocations = new ArrayList<Map<String,Object>>();
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        for (String code : codes) {
            var data = new HashMap<>((Map<String,Object>)lots.get(code));int available = number(data.get("quantity"));tracked = Math.addExact(tracked,available);
            String expiry = text(data.get("expiresOn"));
            if (remaining <= 0 || (!expiry.isBlank() && expiry.compareTo(today)<0)) continue;
            int used = Math.min(remaining, available);remaining -= used;data.put("quantity",available-used);data.put("updatedAt",now);lots.put(code,data);
            if(used>0)allocations.add(Map.of("lotCode",code,"quantity",used));
        }
        int unallocated = number(item.get("quantity"))-tracked;
        if(remaining>unallocated)throw new IllegalArgumentException("Scorte utilizzabili insufficienti: alcuni lotti sono scaduti.");
        if(remaining>0)allocations.add(Map.of("lotCode","", "quantity",remaining));
        return Map.of("lots",lots,"allocations",allocations);
    }

    private String requiredText(Map<String, Object> request, String field, int max) {
        String value = request == null ? "" : text(request.get(field));
        if (value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + " obbligatorio (max " + max + " caratteri).");
        return value;
    }

    private String optionalText(Map<String, Object> request, String field, int max, String fallback) {
        String value = request == null ? "" : text(request.get(field));
        if (value.isBlank()) return fallback;
        if (value.length() > max) throw new IllegalArgumentException(field + " supera il limite di " + max + " caratteri.");
        return value;
    }

    private int nonNegativeInt(Map<String, Object> request, String field, int fallback) {
        Object value = request == null ? null : request.get(field);
        if (value == null) return fallback;
        try {
            int parsed = value instanceof Number number ? Math.toIntExact(number.longValue()) : Integer.parseInt(value.toString());
            if (parsed < 0) throw new IllegalArgumentException(field + " non può essere negativo.");
            return parsed;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(field + " deve essere un numero intero valido.");
        }
    }

    private double nonNegativeDecimal(Map<String, Object> request, String field, double fallback) {
        Object value = request == null ? null : request.get(field);
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value.toString());
            if (!Double.isFinite(parsed) || parsed < 0) throw new IllegalArgumentException(field + " deve essere positivo.");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " deve essere un numero valido.");
        }
    }

    public void add(
            String codiceSku,
            String nomePezzo,
            int quantitaDisponibile,
            int sogliaMinima,
            String categoria) throws Exception {

        if (codiceSku == null || codiceSku.isBlank()) {
            throw new IllegalArgumentException(
                    "Codice SKU obbligatorio.");
        }

        if (nomePezzo == null || nomePezzo.isBlank()) {
            throw new IllegalArgumentException(
                    "Nome pezzo obbligatorio.");
        }

        if (quantitaDisponibile < 0) {
            throw new IllegalArgumentException(
                    "La quantità non può essere negativa.");
        }

        if (sogliaMinima < 0) {
            throw new IllegalArgumentException(
                    "La soglia minima non può essere negativa.");
        }

        Firestore db = FirestoreClient.getFirestore();
        String tenantId = currentTenant();

        String now = Instant.now().toString();

        Map<String, Object> item = new HashMap<>();

        item.put("tenantId", tenantId);
        item.put("sku", codiceSku.trim());
        item.put("name", nomePezzo.trim());

        item.put(
                "quantity",
                quantitaDisponibile);

        item.put(
                "minimumThreshold",
                sogliaMinima);

        item.put(
                "category",
                categoria == null
                        ? "GENERALE"
                        : categoria.trim());

        item.put("createdAt", now);
        item.put("updatedAt", now);

        DocumentReference itemReference = db.collection(COLLECTION).document();
        item.put("id", itemReference.getId());
        DocumentReference receiptReference = db.collection("inventoryMovements").document();
        db.runTransaction(transaction -> {
            transaction.set(itemReference, item);
            if (quantitaDisponibile > 0) {
                transaction.set(receiptReference, Map.of(
                        "tenantId", tenantId,
                        "inventoryId", itemReference.getId(),
                        "sku", codiceSku.trim(),
                        "name", nomePezzo.trim(),
                        "type", "RECEIPT",
                        "quantity", quantitaDisponibile,
                        "previousQuantity", 0,
                        "resultingQuantity", quantitaDisponibile,
                        "actorUid", SecurityContextAccessor.currentUid() == null
                                ? "SYSTEM" : SecurityContextAccessor.currentUid(),
                        "createdAt", now));
            }
            return null;
        }).get();
        auditService.record("INVENTORY_CREATED", tenantId, SecurityContextAccessor.currentUid(), "INVENTORY",
                itemReference.getId(), "SUCCESS", null, Map.of(
                        "sku", codiceSku.trim(), "quantity", quantitaDisponibile));
    }

    public Map<String, Object> setQuantity(String inventoryId, int targetQuantity) throws Exception {
        if (inventoryId == null || inventoryId.isBlank() || targetQuantity < 0) {
            throw new IllegalArgumentException("ID articolo e quantità non validi.");
        }

        Firestore db = FirestoreClient.getFirestore();
        String tenantId = currentTenant();
        DocumentReference itemReference = db.collection(COLLECTION).document(inventoryId.trim());
        DocumentReference movementReference = db.collection("inventoryMovements").document();
        String now = Instant.now().toString();
        InventoryAdjustmentResult adjustmentResult;
        try {
            adjustmentResult = db.runTransaction(transaction -> {
                DocumentSnapshot item = transaction.get(itemReference).get();
                if (!item.exists()) {
                    throw new IllegalArgumentException("Articolo inventario non trovato.");
                }
                if (!tenantId.equals(item.getString("tenantId"))) {
                    throw new SecurityException("Articolo non appartenente al tenant autenticato.");
                }
                if (item.get("lots") instanceof Map<?,?> lots && !lots.isEmpty()) throw new IllegalArgumentException("Articolo tracciato per lotto: utilizza i movimenti del singolo lotto.");
                int previousQuantity = number(item.get("quantity"));
                int adjustment = Math.subtractExact(targetQuantity, previousQuantity);
                Map<String, Object> updated = new HashMap<>(item.getData());
                updated.put("id", item.getId());
                updated.put("quantity", targetQuantity);
                if (adjustment != 0) {
                    updated.put("updatedAt", now);
                    transaction.update(itemReference, "quantity", targetQuantity, "updatedAt", now);
                    Map<String, Object> movement = new HashMap<>();
                    movement.put("tenantId", tenantId);
                    movement.put("inventoryId", item.getId());
                    movement.put("sku", item.getString("sku"));
                    movement.put("name", item.getString("name"));
                    movement.put("type", "ADJUSTMENT");
                    movement.put("quantity", adjustment);
                    movement.put("previousQuantity", previousQuantity);
                    movement.put("resultingQuantity", targetQuantity);
                    movement.put("actorUid", SecurityContextAccessor.currentUid());
                    movement.put("createdAt", now);
                    transaction.set(movementReference, movement);
                }
                return new InventoryAdjustmentResult(updated, previousQuantity, adjustment);
            }).get();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }

        if (adjustmentResult.delta() != 0) {
            auditService.record("INVENTORY_ADJUSTED", tenantId, SecurityContextAccessor.currentUid(),
                    "INVENTORY", inventoryId.trim(), "SUCCESS",
                    Map.of("quantity", adjustmentResult.previousQuantity()),
                    Map.of("quantity", targetQuantity, "movementId", movementReference.getId()));
        }
        return adjustmentResult.item();
    }

    public long countLowStock()
            throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        List<QueryDocumentSnapshot> docs = db.collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant())
                .get()
                .get()
                .getDocuments();

        long count = 0;

        for (QueryDocumentSnapshot doc : docs) {

            Number quantita = (Number) doc.get("quantity");

            Number soglia = (Number) doc.get("minimumThreshold");

            if (quantita != null &&
                    soglia != null &&
                    quantita.doubleValue() <= soglia.doubleValue()) {

                count++;
            }
        }

        return count;
    }

    public void consume(List<Map<String, Object>> materials) throws Exception {
        consume(materials, null);
    }

    public void consume(List<Map<String, Object>> materials, String operationId) throws Exception {
        if (materials == null || materials.isEmpty())
            return;
        if (operationId != null && operationId.isBlank()) {
            throw new IllegalArgumentException("Identificativo operazione non valido.");
        }

        Map<String, Integer> requestedByKey = new LinkedHashMap<>();
        Map<String, InventoryLookup> lookupByKey = new LinkedHashMap<>();
        for (Map<String, Object> material : materials) {
            if (material == null) {
                throw new IllegalArgumentException("Materiale non valido.");
            }
            String inventoryId = text(material.get("inventoryId"));
            if (inventoryId.isBlank()) {
                inventoryId = text(material.get("id"));
            }
            String sku = text(material.get("sku"));
            String name = String.valueOf(material.getOrDefault("name", "")).trim();
            int quantity = number(material.get("quantity"));
            if (inventoryId.isBlank() && sku.isBlank() || quantity <= 0) {
                throw new IllegalArgumentException("ID o SKU stabile del materiale e quantità sono obbligatori.");
            }
            String key = !inventoryId.isBlank() ? "id:" + inventoryId
                    : !sku.isBlank() ? "sku:" + sku : "name:" + name;
            requestedByKey.merge(key, quantity, Math::addExact);
            lookupByKey.putIfAbsent(key, new InventoryLookup(inventoryId, sku, name));
        }

        Firestore db = FirestoreClient.getFirestore();
        String tenantId = currentTenant();

        Map<String, DocumentReference> referencesByKey = new LinkedHashMap<>();
        for (String key : requestedByKey.keySet()) {
            InventoryLookup lookup = lookupByKey.get(key);
            DocumentSnapshot item;
            if (!lookup.inventoryId().isBlank()) {
                item = db.collection(COLLECTION).document(lookup.inventoryId()).get().get();
                if (!item.exists()) {
                    throw new IllegalArgumentException("Materiale non trovato: " + lookup.displayValue());
                }
                if (!tenantId.equals(item.getString("tenantId"))) {
                    throw new SecurityException("Materiale non appartenente al tenant autenticato.");
                }
            } else {
                String field = "sku";
                String value = lookup.sku();
                QuerySnapshot snapshot = db.collection(COLLECTION)
                        .whereEqualTo("tenantId", tenantId)
                        .whereEqualTo(field, value)
                        .limit(1).get().get();
                if (snapshot.isEmpty()) {
                    throw new IllegalArgumentException("Materiale non trovato: " + value);
                }
                item = snapshot.getDocuments().get(0);
            }
            referencesByKey.put(key, item.getReference());
        }

        Map<String, Integer> requestedByInventoryId = new LinkedHashMap<>();
        Map<String, InventoryLookup> lookupByInventoryId = new LinkedHashMap<>();
        Map<String, DocumentReference> referenceByInventoryId = new LinkedHashMap<>();
        for (String key : requestedByKey.keySet()) {
            String inventoryId = referencesByKey.get(key).getId();
            requestedByInventoryId.merge(inventoryId, requestedByKey.get(key), Math::addExact);
            lookupByInventoryId.putIfAbsent(inventoryId, lookupByKey.get(key));
            referenceByInventoryId.putIfAbsent(inventoryId, referencesByKey.get(key));
        }
        Map<String, Integer> consumptionQuantities = requestedByInventoryId;
        Map<String, InventoryLookup> consumptionLookups = lookupByInventoryId;
        Map<String, DocumentReference> consumptionReferences = referenceByInventoryId;

        String now = Instant.now().toString();
        DocumentReference operationReference = operationId == null ? null
                : db.collection("inventoryConsumptionOperations")
                        .document(stableId(tenantId + "|" + operationId));
        String operationRequestHash = operationId == null ? null : consumptionRequestHash(consumptionQuantities);
        Map<String, DocumentReference> movementReferences = new LinkedHashMap<>();
        for (String key : consumptionQuantities.keySet()) {
            String inventoryId = consumptionReferences.get(key).getId();
            String movementId = operationId == null ? null : stableMovementId(tenantId, operationId, inventoryId);
            movementReferences.put(key, movementId == null
                    ? db.collection("inventoryMovements").document()
                    : db.collection("inventoryMovements").document(movementId));
        }

        boolean applied;
        try {
            applied = db.runTransaction(transaction -> {
                if (operationReference != null) {
                    DocumentSnapshot operation = transaction.get(operationReference).get();
                    if (operation.exists()) {
                        if (!tenantId.equals(operation.getString("tenantId"))
                                || !operationId.equals(operation.getString("operationId"))
                                || !operationRequestHash.equals(operation.getString("requestHash"))) {
                            throw new IllegalStateException("Operazione inventario idempotente incoerente.");
                        }
                        return false;
                    }
                }

                Map<String, DocumentSnapshot> currentItems = new LinkedHashMap<>();
                Map<String, DocumentSnapshot> existingMovements = new LinkedHashMap<>();
                for (String key : consumptionQuantities.keySet()) {
                    InventoryLookup lookup = consumptionLookups.get(key);
                    DocumentSnapshot item = transaction.get(consumptionReferences.get(key)).get();
                    if (!item.exists() || !tenantId.equals(item.getString("tenantId"))) {
                        throw new SecurityException("Materiale non appartenente al tenant autenticato.");
                    }
                    DocumentSnapshot movement = transaction.get(movementReferences.get(key)).get();
                    if (movement.exists()) {
                        if (operationId == null
                                || !tenantId.equals(movement.getString("tenantId"))
                                || !operationId.equals(movement.getString("operationId"))
                                || !consumptionReferences.get(key).getId().equals(movement.getString("inventoryId"))
                                || !"CONSUMPTION".equals(movement.getString("type"))
                                || number(movement.get("quantity")) != consumptionQuantities.get(key)) {
                            throw new IllegalStateException("Movimento inventario idempotente incoerente.");
                        }
                    } else if (number(item.get("quantity")) < consumptionQuantities.get(key)) {
                        throw new IllegalArgumentException("Scorta insufficiente per: " + lookup.displayValue());
                    }
                    currentItems.put(key, item);
                    existingMovements.put(key, movement);
                }

                for (String key : consumptionQuantities.keySet()) {
                    if (existingMovements.get(key).exists()) {
                        continue;
                    }
                    DocumentSnapshot item = currentItems.get(key);
                    InventoryLookup lookup = consumptionLookups.get(key);
                    int available = number(item.get("quantity"));
                    int consumed = consumptionQuantities.get(key);
                    var allocation = allocateLots(item, consumed, now);
                    var stock = new HashMap<String,Object>(); stock.put("quantity", available-consumed);stock.put("updatedAt",now);
                    if (allocation.containsKey("lots")) stock.put("lots",allocation.get("lots"));
                    transaction.update(consumptionReferences.get(key), stock);

                    Map<String, Object> movement = new HashMap<>();
                    movement.put("tenantId", tenantId);
                    movement.put("inventoryId", consumptionReferences.get(key).getId());
                    movement.put("sku", item.getString("sku"));
                    movement.put("name", item.getString("name") == null ? lookup.name() : item.getString("name"));
                    movement.put("quantity", consumed);
                    movement.put("type", "CONSUMPTION");
                    movement.put("lotAllocations", allocation.get("allocations"));
                    movement.put("warehouse", text(item.get("warehouse")));
                    movement.put("createdAt", now);
                    if (operationId != null) {
                        movement.put("operationId", operationId);
                    }
                    transaction.set(movementReferences.get(key), movement);
                }
                if (operationReference != null) {
                    transaction.set(operationReference, Map.of(
                            "tenantId", tenantId,
                            "operationId", operationId,
                            "requestHash", operationRequestHash,
                            "createdAt", now));
                }
                return true;
            }).get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }

        for (String key : consumptionQuantities.keySet()) {
            if (operationId != null && !applied) {
                continue;
            }
            InventoryLookup lookup = consumptionLookups.get(key);
            DocumentReference inventoryReference = consumptionReferences.get(key);
            DocumentReference movementReference = movementReferences.get(key);
            auditService.record("INVENTORY_CONSUMED", tenantId, SecurityContextAccessor.currentUid(), "INVENTORY",
                    inventoryReference.getId(), "SUCCESS", null, Map.of(
                            "name", lookup.displayValue(),
                            "quantity", consumptionQuantities.get(key),
                            "movementId", movementReference.getId()));
        }
    }

    private String stableMovementId(String tenantId, String operationId, String inventoryId) throws Exception {
        return stableId(tenantId + "|" + operationId + "|" + inventoryId);
    }

    private String consumptionRequestHash(Map<String, Integer> requestedByInventoryId) throws Exception {
        Map<String, Integer> canonical = new java.util.TreeMap<>(requestedByInventoryId);
        return stableId(new com.google.gson.Gson().toJson(canonical));
    }

    private String stableId(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private record InventoryLookup(String inventoryId, String sku, String name) {
        String displayValue() {
            return !name.isBlank() ? name : !sku.isBlank() ? sku : inventoryId;
        }
    }

    private record InventoryAdjustmentResult(Map<String, Object> item, int previousQuantity, int delta) {
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String currentTenant() {
        return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }
}
