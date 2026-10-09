package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    @Value("${radiotech.sla.warning-minutes:60}")
    private long slaWarningMinutes;

    private Firestore db() {
        return FirestoreClient.getFirestore();
    }

    public Map<String, Object> getDashboardStats() throws Exception {

        Firestore firestore = db();

        QuerySnapshot antennasSnapshot = tenantQuery("antennas").get().get();

        QuerySnapshot operatorsSnapshot = tenantQuery("operators").get().get();

        QuerySnapshot interventionsSnapshot = tenantQuery("interventions").get().get();

        QuerySnapshot tasksSnapshot = tenantQuery("tasks").get().get();

        QuerySnapshot inventorySnapshot = tenantQuery("inventory").get().get();

        int antennas = antennasSnapshot.size();
        int operators = operatorsSnapshot.size();
        int interventions = interventionsSnapshot.size();
        int tasks = tasksSnapshot.size();

        int activeAntennas = 0;
        int maintenanceAntennas = 0;
        int offlineAntennas = 0;

        for (DocumentSnapshot document : antennasSnapshot.getDocuments()) {

            String status = document.getString("status");

            if ("ATTIVA".equalsIgnoreCase(status)) {
                activeAntennas++;
            } else if ("MANUTENZIONE".equalsIgnoreCase(status)) {
                maintenanceAntennas++;
            } else if ("OFFLINE".equalsIgnoreCase(status)) {
                offlineAntennas++;
            }
        }

        int activeOperators = 0;

        for (DocumentSnapshot document : operatorsSnapshot.getDocuments()) {

            String status = OperatorService.mapOperator(document).getStatus();

            if ("ATTIVO".equalsIgnoreCase(status)) {
                activeOperators++;
            }
        }

        int lowStock = 0;
        int inventoryItems = inventorySnapshot.size();

        for (DocumentSnapshot document : inventorySnapshot.getDocuments()) {

            Long quantity = document.getLong("quantity");
            Long minimumThreshold = document.getLong("minimumThreshold");

            if (quantity != null
                    && minimumThreshold != null
                    && quantity <= minimumThreshold) {

                lowStock++;
            }
        }

        int assignedTasks = 0;
        int inProgressTasks = 0;
        int completedTasks = 0;
        int cancelledTasks = 0;
        int overdueTasks = 0;
        int atRiskTasks = 0;
        int activeTasks = 0;
        Instant now = Instant.now();

        for (DocumentSnapshot document : tasksSnapshot.getDocuments()) {

            var parsed = com.radiotech.radiotech_backend.model.TaskStatus.parse(document.getString("status"));
            if (parsed == null) continue;
            String status = parsed.name();

            String dueAt = document.getString("dueAt");
            if (!List.of("COMPLETED", "CANCELLED", "APPROVED", "CLOSED", "REPORT_SUBMITTED").contains(status)) {
                activeTasks++;
                SlaPolicy.Status slaStatus = SlaPolicy.evaluate(dueAt, now,
                        Duration.ofMinutes(Math.max(0, slaWarningMinutes)));
                if (slaStatus == SlaPolicy.Status.BREACHED) {
                    overdueTasks++;
                } else if (slaStatus == SlaPolicy.Status.AT_RISK) {
                    atRiskTasks++;
                }
            }

            if (status == null) {
                continue;
            }

            switch (status.toUpperCase()) {

                case "ASSIGNED" -> assignedTasks++;

                case "IN_PROGRESS" -> inProgressTasks++;

                case "COMPLETED", "APPROVED", "CLOSED" -> completedTasks++;

                case "CANCELLED" -> cancelledTasks++;
            }
        }

        double availability = antennas == 0
                ? 0
                : ((double) activeAntennas / antennas) * 100;

        Map<String, Object> stats = new HashMap<>();

        stats.put("antennas", antennas);
        stats.put("operators", operators);
        stats.put("activeOperators", activeOperators);

        stats.put("interventions", interventions);
        stats.put("tasks", tasks);
        stats.put("activeTasks", activeTasks);
        stats.put("pendingReports", tenantQuery("maintenanceReports")
                .whereIn("status", List.of("SUBMITTED", "APPROVAL_PENDING")).count().get().get().getCount());

        stats.put("inventoryItems", inventoryItems);
        stats.put("lowStock", lowStock);

        stats.put("activeAntennas", activeAntennas);
        stats.put("maintenanceAntennas", maintenanceAntennas);
        stats.put("offlineAntennas", offlineAntennas);

        stats.put(
                "availability",
                Math.round(availability * 100.0) / 100.0);

        Map<String, Object> taskStats = new HashMap<>();

        taskStats.put("assigned", assignedTasks);
        taskStats.put("inProgress", inProgressTasks);
        taskStats.put("completed", completedTasks);
        taskStats.put("cancelled", cancelledTasks);
        taskStats.put("overdue", overdueTasks);
        taskStats.put("slaBreached", overdueTasks);
        taskStats.put("slaAtRisk", atRiskTasks);

        stats.put("taskStats", taskStats);

        return stats;
    }

    public List<Map<String, Object>> getAntennas() throws Exception {
        return getCollection("antennas");
    }

    public List<Map<String, Object>> getOperators() throws Exception {
        List<Map<String, Object>> records = getCollection("operators");
        records.forEach(record -> {
            record.remove("qrCodeToken");
            record.remove("fcmTokens");
        });
        return records;
    }

    public List<Map<String, Object>> getTasks() throws Exception {
        return getCollection("tasks");
    }

    public List<Map<String, Object>> getInterventions() throws Exception {
        return getCollection("interventions");
    }

    public List<Map<String, Object>> getInventory() throws Exception {
        return getCollection("inventory");
    }

    public List<Map<String, Object>> getLowStockInventory()
            throws Exception {

        QuerySnapshot snapshot = tenantQuery("inventory").get().get();

        List<Map<String, Object>> result = new ArrayList<>();

        for (DocumentSnapshot document : snapshot.getDocuments()) {

            Long quantity = document.getLong("quantity");
            Long minimumThreshold = document.getLong("minimumThreshold");

            if (quantity == null || minimumThreshold == null) {
                continue;
            }

            if (quantity <= minimumThreshold) {

                Map<String, Object> item = new HashMap<>(document.getData());

                item.put("id", document.getId());

                result.add(item);
            }
        }

        return result;
    }

    private List<Map<String, Object>> getCollection(
            String collectionName) throws Exception {

        QuerySnapshot snapshot = tenantQuery(collectionName).get().get();

        List<Map<String, Object>> result = new ArrayList<>();

        for (DocumentSnapshot document : snapshot.getDocuments()) {

            Map<String, Object> data = new HashMap<>(document.getData());

            data.put("id", document.getId());

            result.add(data);
        }

        return result;
    }

    public Map<String, Object> updateAntenna(
            String id,
            Map<String, Object> updates) throws Exception {

        DocumentSnapshot document = db()
                .collection("antennas")
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            throw new IllegalArgumentException(
                    "Antenna non trovata: " + id);
        }
        requireDocumentTenant(document);

        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nessun dato da aggiornare.");
        }

        updates.remove("id");
        updates.remove("createdAt");
        updates.remove("tenantId");

        if (updates.containsKey("lat")) {
            double latitude = toDouble(updates.get("lat"), "lat");
            if (latitude < -90 || latitude > 90)
                throw new IllegalArgumentException("Latitudine non valida.");
            updates.put("lat", latitude);
        }

        if (updates.containsKey("lng")) {
            double longitude = toDouble(updates.get("lng"), "lng");
            if (longitude < -180 || longitude > 180)
                throw new IllegalArgumentException("Longitudine non valida.");
            updates.put("lng", longitude);
        }

        if (updates.containsKey("name")) {

            String name = String.valueOf(updates.get("name")).trim();

            if (name.isBlank()) {
                throw new IllegalArgumentException(
                        "Il nome dell'antenna è obbligatorio.");
            }

            updates.put("name", name);
        }

        if (updates.containsKey("status")) {

            String status = String.valueOf(updates.get("status"))
                    .trim()
                    .toUpperCase();

            List<String> allowedStatuses = List.of(
                    "ATTIVA",
                    "MANUTENZIONE",
                    "OFFLINE",
                    "CRITICA");

            if (!allowedStatuses.contains(status)) {
                throw new IllegalArgumentException(
                        "Stato antenna non valido: " + status);
            }

            updates.put("status", status);
        }

        if (updates.containsKey("assetType")) {
            String assetType = String.valueOf(updates.get("assetType")).trim().toUpperCase();
            if (!java.util.Set.of("SITE", "TOWER", "SECTOR", "ANTENNA", "RRU", "BBU", "ROUTER", "SWITCH",
                    "UPS", "BATTERY", "GENERATOR", "FIBER", "MICROWAVE").contains(assetType)) {
                throw new IllegalArgumentException("Tipo asset non valido: " + assetType);
            }
            updates.put("assetType", assetType);
        }

        if (updates.containsKey("parentAssetId")) {
            Object rawParentId = updates.get("parentAssetId");
            if (rawParentId == null || String.valueOf(rawParentId).isBlank()) {
                updates.put("parentAssetId", null);
            } else {
                String parentId = String.valueOf(rawParentId).trim();
                if (parentId.equals(id)) {
                    throw new IllegalArgumentException("Un asset non può essere padre di se stesso.");
                }
                DocumentSnapshot parent = db().collection("antennas").document(parentId).get().get();
                if (!parent.exists() || !currentTenant().equals(parent.getString("tenantId"))) {
                    throw new IllegalArgumentException("Asset padre non trovato nel tenant autenticato.");
                }
                updates.put("parentAssetId", parentId);
            }
        }

        updates.put(
                "updatedAt",
                Instant.now().toString());

        db()
                .collection("antennas")
                .document(id)
                .update(updates)
                .get();

        return getDocument(
                "antennas",
                id);
    }

    public void deleteAntenna(String id) throws Exception {

        DocumentSnapshot document = db()
                .collection("antennas")
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            throw new IllegalArgumentException(
                    "Antenna non trovata: " + id);
        }
        requireDocumentTenant(document);

        db()
                .collection("antennas")
                .document(id)
                .delete()
                .get();
    }

    public Map<String, Object> updateTaskStatus(
            String id,
            String status) throws Exception {

        DocumentSnapshot document = db()
                .collection("tasks")
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            throw new IllegalArgumentException(
                    "Task non trovato: " + id);
        }
        requireDocumentTenant(document);

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Lo stato del task è obbligatorio.");
        }

        String normalizedStatus = status.trim().toUpperCase();

        List<String> allowedStatuses = List.of(
                "ASSIGNED",
                "IN_PROGRESS",
                "COMPLETED",
                "CANCELLED");

        if (!allowedStatuses.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Stato task non valido: " + status);
        }

        Map<String, Object> updates = new HashMap<>();

        updates.put(
                "status",
                normalizedStatus);

        updates.put(
                "updatedAt",
                Instant.now().toString());

        if ("COMPLETED".equals(normalizedStatus)) {

            updates.put(
                    "completedAt",
                    Instant.now().toString());

        } else {

            updates.put(
                    "completedAt",
                    null);
        }

        db()
                .collection("tasks")
                .document(id)
                .update(updates)
                .get();

        return getDocument(
                "tasks",
                id);
    }

    private Map<String, Object> getDocument(
            String collection,
            String id) throws Exception {

        DocumentSnapshot document = db().collection(collection).document(id).get().get();

        if (!document.exists()) {
            throw new IllegalArgumentException(
                    "Documento non trovato: " + id);
        }
        requireDocumentTenant(document);

        Map<String, Object> result = new HashMap<>(document.getData());

        result.put(
                "id",
                document.getId());

        return result;
    }

    private com.google.cloud.firestore.Query tenantQuery(String collectionName) {
        return db().collection(collectionName).whereEqualTo("tenantId", currentTenant());
    }

    private String currentTenant() {
        return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }

    private void requireDocumentTenant(DocumentSnapshot document) {
        if (!TenantAccessPolicy.canAccessTenant(currentTenant(), document.getString("tenantId"))) {
            throw new IllegalArgumentException("Documento non trovato nel tenant autenticato.");
        }
    }

    private double toDouble(
            Object value,
            String field) {

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(
                    String.valueOf(value));
        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Il campo '" + field +
                            "' deve essere numerico.");
        }
    }
}
