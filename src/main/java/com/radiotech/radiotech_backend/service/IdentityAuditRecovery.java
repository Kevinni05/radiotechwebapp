package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/** Recover the Auth/Firestore boundary without ever replaying old role assignments. */
@Component
@ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityAuditRecovery {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdentityAuditRecovery.class);
    private String cursor;

    @Scheduled(fixedDelayString = "${radiotech.identity-recovery.delay-ms:60000}", initialDelay = 90000)
    public void scan() {
        if (FirebaseApp.getApps().isEmpty()) return;
        try {
            var db = FirestoreClient.getFirestore();
            Query query = db.collection("auditLogs").whereEqualTo("result", "PENDING")
                    .orderBy(FieldPath.documentId()).limit(50);
            if (cursor != null) query = query.startAfter(cursor);
            var page = query.get().get().getDocuments();
            cursor = page.size() == 50 ? page.getLast().getId() : null;
            for (var event : page) if ("FIREBASE_AUTH".equals(event.getString("externalSystem"))) {
                try { recover(db, FirebaseAuth.getInstance(), event.getReference(), Instant.now()); }
                catch (Exception failure) {
                    log.warn("Identity audit recovery deferred: event={}, type={}", event.getId(), failure.getClass().getSimpleName());
                    if (com.radiotech.radiotech_backend.exception.CloudQuota.exhausted(failure)) return;
                }
            }
        } catch (Exception failure) {
            log.warn("Identity audit scan deferred: type={}", failure.getClass().getSimpleName());
        }
    }

    static boolean recover(Firestore db, FirebaseAuth auth, DocumentReference ref, Instant now) throws Exception {
        String lease = UUID.randomUUID().toString();
        Map<String, Object> intent = db.runTransaction(tx -> {
            var event = tx.get(ref).get();
            if (!event.exists() || !"PENDING".equals(event.getString("result"))
                    || !"FIREBASE_AUTH".equals(event.getString("externalSystem"))) return null;
            if (event.getString("timestamp") == null || Instant.parse(event.getString("timestamp")).plusSeconds(60).isAfter(now)) return null;
            for (String field : List.of("recoveryLeaseUntil", "recoveryNextAttemptAt")) {
                String value = event.getString(field);
                if (value != null && Instant.parse(value).isAfter(now)) return null;
            }
            tx.update(ref, "recoveryLease", lease, "recoveryLeaseUntil", now.plusSeconds(120).toString());
            return event.getData();
        }).get();
        if (intent == null) return false;
        boolean confirmed = false;
        String reason = "UNSUPPORTED_INTENT";
        try {
            String action = Objects.toString(intent.get("action"), "");
            if (Set.of("IDENTITY_BIND", "SESSION_REVOKE").contains(action)) {
                String tenant = Objects.toString(intent.get("tenantId"), "");
                var user = auth.getUser((String) intent.get("resourceId"));
                if (tenant.isBlank() || !tenant.equals(user.getCustomClaims().get("tenantId"))) reason = "TENANT_CHANGED";
                else if (!(intent.get("after") instanceof Map<?, ?> expected)
                        || !AuditService.identitySnapshot(user.getCustomClaims()).equals(expected)) reason = "CLAIMS_CHANGED";
                else {
                    // A lost completion may leave tokens unrevoked after the claims write.
                    // Revoke is safe to repeat; assigning old claims would not be.
                    auth.revokeRefreshTokens(user.getUid());
                    confirmed = true;
                    reason = "CURRENT_CLAIMS_CONFIRMED_AND_SESSIONS_REVOKED";
                }
            }
        } catch (Exception failure) {
            reason = "DEPENDENCY_UNAVAILABLE";
            throw failure;
        } finally {
            boolean success = confirmed;
            String outcome = reason;
            db.runTransaction(tx -> {
                var event = tx.get(ref).get();
                if (!event.exists() || !"PENDING".equals(event.getString("result"))
                        || !lease.equals(event.getString("recoveryLease"))) return false;
                long attempts = event.get("recoveryAttempts") instanceof Number n ? n.longValue() + 1 : 1;
                Map<String, Object> update = new LinkedHashMap<>();
                update.put("recoveryLease", FieldValue.delete());
                update.put("recoveryLeaseUntil", FieldValue.delete());
                update.put("recoveryAttempts", attempts);
                update.put("recoveryReason", outcome);
                update.put("lastRecoveryAt", now.toString());
                if (success) {
                    update.put("result", "SUCCESS");
                    update.put("reconciliationRequired", false);
                    update.put("reconciledAt", now.toString());
                    update.put("recoveryNextAttemptAt", FieldValue.delete());
                } else update.put("recoveryNextAttemptAt", now.plusSeconds(Math.min(3600, 60L << Math.min(attempts, 6))).toString());
                tx.update(ref, update);
                return true;
            }).get();
        }
        return confirmed;
    }
}
