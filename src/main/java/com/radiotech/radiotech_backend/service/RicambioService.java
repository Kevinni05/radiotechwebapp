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
                    transaction.update(consumptionReferences.get(key), "quantity", available - consumed, "updatedAt", now);

                    Map<String, Object> movement = new HashMap<>();
                    movement.put("tenantId", tenantId);
                    movement.put("inventoryId", consumptionReferences.get(key).getId());
                    movement.put("sku", item.getString("sku"));
                    movement.put("name", item.getString("name") == null ? lookup.name() : item.getString("name"));
                    movement.put("quantity", consumed);
                    movement.put("type", "CONSUMPTION");
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
