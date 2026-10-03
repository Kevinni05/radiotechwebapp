package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.IncidentStatus;
import com.radiotech.radiotech_backend.model.TaskStatus;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates only tenant-scoped state already persisted by the operational services.
 * Alert documents are stable per rule/source, updated when rule state changes and
 * resolved when the source no longer meets its rule.
 */
@Service
public class AlertEngineService {
    private static final String ALERTS = "alerts";
    private static final Set<TaskStatus> SLA_ACTIVE_TASK_STATUSES = Set.of(
            TaskStatus.CREATED, TaskStatus.ASSIGNED, TaskStatus.ACCEPTED, TaskStatus.EN_ROUTE,
            TaskStatus.CHECKED_IN, TaskStatus.IN_PROGRESS, TaskStatus.WAITING);

    private final AuditService auditService;
    private final long warningMinutes;

    public AlertEngineService(
            AuditService auditService,
            @Value("${radiotech.sla.warning-minutes:60}") long warningMinutes) {
        this.auditService = auditService;
        this.warningMinutes = Math.max(0, warningMinutes);
    }

    public Map<String, Object> evaluate() throws Exception {
        String tenantId = TenantAccessPolicy.requireTenantAccess(
                SecurityContextAccessor.currentTenantId(), null);
        Firestore db = FirestoreClient.getFirestore();
        Instant now = Instant.now();
        String timestamp = now.toString();
        Map<String, Map<String, Object>> desired = new LinkedHashMap<>();

        for (QueryDocumentSnapshot task : db.collection("tasks")
                .whereEqualTo("tenantId", tenantId).get().get().getDocuments()) {
            TaskStatus status = TaskStatus.parse(task.getString("status"));
            if (status == null || !SLA_ACTIVE_TASK_STATUSES.contains(status)) {
                continue;
            }
            SlaPolicy.Status sla = SlaPolicy.evaluate(
                    task.getString("dueAt"), now, Duration.ofMinutes(warningMinutes));
            if (sla == SlaPolicy.Status.AT_RISK || sla == SlaPolicy.Status.BREACHED) {
                String state = sla == SlaPolicy.Status.BREACHED ? "BREACHED" : "AT_RISK";
                String title = text(task.getString("title"), task.getId());
                addDesired(desired, tenantId, "TASK_SLA", "TASK", task.getId(), state,
                        sla == SlaPolicy.Status.BREACHED ? "CRITICA" : "MEDIA",
                        "Task SLA " + state.toLowerCase() + ": " + title,
                        task.getString("antennaId"));
            }
        }

        for (QueryDocumentSnapshot item : db.collection("inventory")
                .whereEqualTo("tenantId", tenantId).get().get().getDocuments()) {
            Number quantity = item.get("quantity") instanceof Number number ? number : null;
            Number threshold = item.get("minimumThreshold") instanceof Number number ? number : null;
            if (quantity != null && threshold != null && quantity.doubleValue() <= threshold.doubleValue()) {
                addDesired(desired, tenantId, "LOW_INVENTORY", "INVENTORY", item.getId(), "LOW",
                        "MEDIA", "Scorte basse: " + text(item.getString("name"), item.getId()), null);
            }
        }

        for (QueryDocumentSnapshot incident : db.collection("incidents")
                .whereEqualTo("tenantId", tenantId).get().get().getDocuments()) {
            IncidentStatus status = IncidentStatus.parse(incident.getString("status"));
            if (status == null || status == IncidentStatus.RESOLVED || status == IncidentStatus.POST_MORTEM) {
                continue;
            }
            String severity = incident.getString("severity");
            String priority = switch (severity == null ? "" : severity.trim().toUpperCase()) {
                case "CRITICAL", "HIGH" -> "CRITICA";
                case "MEDIUM" -> "MEDIA";
                case "LOW" -> "BASSA";
                default -> null;
            };
            if (priority == null) {
                continue;
            }
            addDesired(desired, tenantId, "OPEN_INCIDENT", "INCIDENT", incident.getId(), status.name(),
                    priority, "Incident " + text(severity, "LOW") + ": "
                            + text(incident.getString("title"), incident.getId()),
                    incident.getString("assetId"));
        }

        int created = 0;
        int updated = 0;
        int resolved = 0;
        for (Map.Entry<String, Map<String, Object>> entry : desired.entrySet()) {
            Change change = upsert(db, entry.getKey(), entry.getValue(), timestamp);
            if (change != Change.NONE) {
                auditService.record(
                        change == Change.CREATED ? "ALERT_GENERATED" : "ALERT_UPDATED",
                        tenantId, "SYSTEM", "ALERT", entry.getKey(), "SUCCESS", null, entry.getValue());
                if (change == Change.CREATED) {
                    created++;
                } else {
                    updated++;
                }
            }
        }

        List<QueryDocumentSnapshot> generated = db.collection(ALERTS)
                .whereEqualTo("tenantId", tenantId)
                .whereEqualTo("generated", true).get().get().getDocuments();
        for (QueryDocumentSnapshot alert : generated) {
            if (!desired.containsKey(alert.getId()) && Boolean.TRUE.equals(alert.getBoolean("active"))) {
                DocumentReference reference = alert.getReference();
                boolean didResolve = db.runTransaction(transaction -> {
                    DocumentSnapshot current = transaction.get(reference).get();
                    if (!current.exists() || !Boolean.TRUE.equals(current.getBoolean("active"))) {
                        return false;
                    }
                    transaction.update(reference, Map.of(
                            "active", false,
                            "resolvedAt", timestamp,
                            "timestamp", timestamp));
                    return true;
                }).get();
                if (didResolve) {
                    resolved++;
                    auditService.record("ALERT_RESOLVED", tenantId, "SYSTEM", "ALERT",
                            alert.getId(), "SUCCESS", null, Map.of("active", false));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluatedAt", timestamp);
        result.put("active", desired.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("resolved", resolved);
        return result;
    }

    private void addDesired(
            Map<String, Map<String, Object>> desired,
            String tenantId,
            String rule,
            String sourceType,
            String sourceId,
            String state,
            String priority,
            String description,
            String antennaId) {
        String id = stableId(tenantId + "|" + rule + "|" + sourceId);
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", id);
        alert.put("tenantId", tenantId);
        alert.put("generated", true);
        alert.put("ruleId", rule);
        alert.put("ruleState", state);
        alert.put("sourceType", sourceType);
        alert.put("sourceId", sourceId);
        alert.put("operatore", "SYSTEM");
        if (antennaId != null && !antennaId.isBlank()) {
            alert.put("antennaId", antennaId);
        }
        alert.put("descrizione", description);
        alert.put("priorita", priority);
        alert.put("active", true);
        desired.put(id, alert);
    }

    private Change upsert(Firestore db, String id, Map<String, Object> alert, String timestamp) throws Exception {
        DocumentReference reference = db.collection(ALERTS).document(id);
        return db.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(reference).get();
            Map<String, Object> values = new LinkedHashMap<>(alert);
            values.put("timestamp", timestamp);
            values.put("letto", current.exists() && Boolean.TRUE.equals(current.getBoolean("letto"))
                    && Boolean.TRUE.equals(current.getBoolean("active"))
                    && alert.get("ruleState").equals(current.getString("ruleState")));
            values.put("active", true);
            if (!current.exists()) {
                values.put("createdAt", timestamp);
                transaction.set(reference, values);
                return Change.CREATED;
            }
            boolean unchanged = Boolean.TRUE.equals(current.getBoolean("active"))
                    && alert.get("ruleState").equals(current.getString("ruleState"))
                    && alert.get("priorita").equals(current.getString("priorita"))
                    && alert.get("descrizione").equals(current.getString("descrizione"));
            if (unchanged) {
                return Change.NONE;
            }
            values.put("resolvedAt", null);
            transaction.set(reference, values, com.google.cloud.firestore.SetOptions.merge());
            return Change.UPDATED;
        }).get();
    }

    private static String stableId(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Impossibile generare l'identificativo alert.", exception);
        }
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private enum Change {
        NONE, CREATED, UPDATED
    }
}
