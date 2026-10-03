package com.radiotech.radiotech_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Incident;
import com.radiotech.radiotech_backend.model.IncidentStatus;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Service
public class IncidentService {
    private static final String COLLECTION = "incidents";
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final AuditService auditService;

    public IncidentService(AuditService auditService) {
        this.auditService = auditService;
    }

    public List<Incident> getAll() throws Exception {
        String tenantId = currentTenant();
        ApiFuture<QuerySnapshot> future = FirestoreClient.getFirestore().collection(COLLECTION)
                .whereEqualTo("tenantId", tenantId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
        List<Incident> incidents = new ArrayList<>();
        for (QueryDocumentSnapshot document : future.get().getDocuments()) {
            Incident incident = document.toObject(Incident.class);
            if (incident != null) {
                incident.setId(document.getId());
                incidents.add(incident);
            }
        }
        return incidents;
    }

    public Incident getById(String id) throws Exception {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID incident obbligatorio.");
        }
        DocumentSnapshot document = FirestoreClient.getFirestore().collection(COLLECTION)
                .document(id.trim()).get().get();
        if (!document.exists()
                || !TenantAccessPolicy.canAccessTenant(currentTenant(), document.getString("tenantId"))) {
            throw new IllegalArgumentException("Incident non trovato.");
        }
        Incident incident = document.toObject(Incident.class);
        if (incident == null) {
            throw new IllegalStateException("Incident non valido.");
        }
        incident.setId(document.getId());
        return incident;
    }

    public Incident create(String title, String description, String severity, String siteId, String assetId,
            String taskId, String assignedOperatorId, String actorUid) throws Exception {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Titolo incident obbligatorio.");
        }
        String normalizedSeverity = severity == null ? "MEDIUM" : severity.trim().toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(normalizedSeverity)) {
            throw new IllegalArgumentException("Severità incident non valida.");
        }
        String tenantId = currentTenant();
        String now = Instant.now().toString();
        DocumentReference reference = FirestoreClient.getFirestore().collection(COLLECTION).document();
        Incident incident = new Incident();
        incident.setId(reference.getId()); incident.setTenantId(tenantId); incident.setTitle(title.trim());
        incident.setDescription(blankToNull(description)); incident.setSeverity(normalizedSeverity);
        incident.setStatus(IncidentStatus.DETECTED.name()); incident.setSiteId(blankToNull(siteId));
        incident.setAssetId(blankToNull(assetId)); incident.setTaskId(blankToNull(taskId));
        incident.setAssignedOperatorId(blankToNull(assignedOperatorId)); incident.setDetectedAt(now);
        incident.setCreatedAt(now); incident.setUpdatedAt(now);
        incident.setCreatedBy(blankToNull(actorUid)); incident.setUpdatedBy(blankToNull(actorUid));
        incident.setRootCause(null); incident.setResolution(null);
        incident.setTimeline(new ArrayList<>(List.of(Map.of("status", IncidentStatus.DETECTED.name(),
                "actorUid", actorUid == null ? "SYSTEM" : actorUid, "at", now))));
        reference.set(incident).get();
        auditService.record("INCIDENT_CREATED", tenantId, actorUid, "INCIDENT", incident.getId(), "SUCCESS", null,
                Map.of("severity", incident.getSeverity(), "status", incident.getStatus()));
        return incident;
    }

    public Incident transition(String id, String targetStatus, String actorUid) throws Exception {
        return transition(id, targetStatus, actorUid, null, null);
    }

    public Incident transition(String id, String targetStatus, String actorUid, String rootCause, String resolution)
            throws Exception {
        IncidentStatus target = IncidentStatus.parse(targetStatus);
        if (target == null) {
            throw new IllegalArgumentException("Stato incident non valido.");
        }
        String tenantId = currentTenant();
        DocumentReference reference = FirestoreClient.getFirestore().collection(COLLECTION).document(id);
        String now = Instant.now().toString();
        IncidentStatus previous;
        try {
            previous = FirestoreClient.getFirestore().runTransaction(transaction -> {
                DocumentSnapshot document = transaction.get(reference).get();
                if (!document.exists() || !tenantId.equals(document.getString("tenantId"))) {
                    throw new IllegalArgumentException("Incident non trovato.");
                }
                IncidentStatus current = IncidentStatus.parse(document.getString("status"));
                if (current == null) {
                    throw new IllegalArgumentException("Stato incident persistito non valido.");
                }
                if (current == target) {
                    return current;
                }
                if (!current.canTransitionTo(target)) {
                    throw new IllegalArgumentException(
                            "Transizione incident non consentita: " + current + " -> " + target);
                }
                Map<String, Object> updates = new java.util.LinkedHashMap<>();
                updates.put("status", target.name()); updates.put("updatedAt", now);
                updates.put("updatedBy", actorUid); updates.put(transitionTimestampField(target), now);
                String effectiveRootCause = rootCause == null ? document.getString("rootCause") : rootCause.trim();
                String effectiveResolution = resolution == null ? document.getString("resolution") : resolution.trim();
                if (target == IncidentStatus.RESOLVED
                        && (blankToNull(effectiveRootCause) == null || blankToNull(effectiveResolution) == null)) {
                    throw new IllegalArgumentException("Root cause e resolution sono obbligatorie per risolvere l'incident.");
                }
                if (rootCause != null) updates.put("rootCause", rootCause.trim());
                if (resolution != null) updates.put("resolution", resolution.trim());
                List<Map<String, Object>> timeline = new ArrayList<>();
                Object storedTimeline = document.get("timeline");
                if (storedTimeline instanceof List<?> entries) for (Object entry : entries) {
                    if (entry instanceof Map<?, ?> values) {
                        Map<String, Object> copy = new java.util.LinkedHashMap<>();
                        values.forEach((key, value) -> copy.put(String.valueOf(key), value)); timeline.add(copy);
                    }
                }
                Map<String, Object> event = new java.util.LinkedHashMap<>();
                event.put("status", target.name()); event.put("actorUid", actorUid == null ? "SYSTEM" : actorUid);
                event.put("at", now);
                if (rootCause != null) event.put("rootCause", rootCause.trim());
                if (resolution != null) event.put("resolution", resolution.trim());
                timeline.add(event); updates.put("timeline", timeline);
                transaction.update(reference, updates);
                return current;
            }).get();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
        if (previous != target) {
            auditService.record("INCIDENT_STATUS_CHANGED", tenantId, actorUid, "INCIDENT", id, target.name(),
                    Map.of("status", previous.name()), Map.of("status", target.name()));
        }
        return getById(id);
    }

    private String transitionTimestampField(IncidentStatus status) {
        return switch (status) {
            case ACKNOWLEDGED -> "acknowledgedAt";
            case ASSIGNED -> "assignedAt";
            case INVESTIGATING -> "investigatingAt";
            case MITIGATED -> "mitigatedAt";
            case RESOLVED -> "resolvedAt";
            case POST_MORTEM -> "postMortemAt";
            case CLOSED -> "closedAt";
            case DETECTED -> "detectedAt";
        };
    }

    private String currentTenant() {
        return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
