package com.radiotech.radiotech_backend.service;

import com.google.firebase.cloud.FirestoreClient;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.common.util.concurrent.MoreExecutors;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.google.cloud.firestore.Query;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** Build an event for the caller's Firestore transaction or batch, with explicit request identity. */
    public static Map<String, Object> transactionEntry(String action, String tenantId, String actor,
            String resource, String resourceId, String result, String timestamp,
            Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("action", action);
        entry.put("tenantId", requireTenantId(tenantId));
        entry.put("actor", actor == null || actor.isBlank() ? "SYSTEM" : actor);
        entry.put("resource", resource);
        entry.put("resourceId", resourceId);
        entry.put("result", result);
        entry.put("timestamp", timestamp);
        entry.put("before", before == null ? Map.of() : new LinkedHashMap<>(before));
        entry.put("after", after == null ? Map.of() : new LinkedHashMap<>(after));
        return entry;
    }

    public void record(String action, String actor, String resource, String resourceId, String result) {
        record(action, null, actor, resource, resourceId, result, null, null);
    }

    public void record(String action, String tenantId, String actor, String resource, String resourceId,
            String result, Map<String, Object> before, Map<String, Object> after) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("action", action);
            String effectiveTenantId = requireTenantId(
                    tenantId == null ? SecurityContextAccessor.currentTenantId() : tenantId);
            entry.put("tenantId", effectiveTenantId);
            entry.put("actor", actor == null ? "SYSTEM" : actor);
            entry.put("resource", resource);
            entry.put("resourceId", resourceId);
            entry.put("result", result);
            entry.put("before", before == null ? new LinkedHashMap<>() : before);
            entry.put("after", after == null ? new LinkedHashMap<>() : after);
            entry.put("timestamp", Instant.now().toString());
            ApiFutures.addCallback(
                    FirestoreClient.getFirestore().collection("auditLogs").add(entry),
                    new ApiFutureCallback<DocumentReference>() {
                        @Override
                        public void onFailure(Throwable failure) {
                            log.warn("Audit event persistence failed: action={}, resource={}, resourceId={}",
                                    action, resource, resourceId, failure);
                        }

                        @Override
                        public void onSuccess(DocumentReference ignored) {
                        }
                    },
                    MoreExecutors.directExecutor());
        } catch (Exception exception) {
            log.warn("Audit event could not be submitted: action={}, resource={}, resourceId={}",
                    action, resource, resourceId, exception);
        }
    }

    static String requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant obbligatorio per registrare l'audit.");
        }
        return tenantId.trim();
    }

    public List<java.util.Map<String, Object>> recent(int limit, String tenantId) throws Exception {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant obbligatorio per consultare l'audit.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        var documents = FirestoreClient.getFirestore().collection("auditLogs")
                .whereEqualTo("tenantId", tenantId.trim())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(safeLimit).get().get().getDocuments();
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        for (var document : documents) {
            var entry = new java.util.LinkedHashMap<String, Object>(document.getData());
            entry.put("id", document.getId());
            result.add(entry);
        }
        return result;
    }
}
