package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/** Resume only approvals already authorized and durably claimed by a reviewer. */
@Component
@ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true", matchIfMissing = true)
public class ReportApprovalRecovery {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportApprovalRecovery.class);
    private final MaintenanceReportService reports;
    private String cursor;
    public ReportApprovalRecovery(MaintenanceReportService reports) { this.reports = reports; }

    @Scheduled(fixedDelayString = "${radiotech.report-recovery.delay-ms:60000}", initialDelay = 90000)
    public void scan() {
        if (FirebaseApp.getApps().isEmpty()) return;
        try {
            Query query = FirestoreClient.getFirestore().collection("maintenanceReports")
                    .whereEqualTo("status", "APPROVAL_PENDING").orderBy(FieldPath.documentId()).limit(50);
            if (cursor != null) query = query.startAfter(cursor);
            var page = query.get().get().getDocuments();
            cursor = page.size() == 50 ? page.getLast().getId() : null;
            for (var report : page) {
                try { recover(report, Instant.now()); }
                catch (Exception failure) {
                    log.warn("Report approval recovery deferred: resource={}, type={}", report.getId(), failure.getClass().getSimpleName());
                    if (com.radiotech.radiotech_backend.exception.CloudQuota.exhausted(failure)) return;
                }
            }
        } catch (Exception failure) { log.warn("Report recovery scan deferred: type={}", failure.getClass().getSimpleName()); }
    }

    boolean recover(DocumentSnapshot report, Instant now) throws Exception {
        if (!"APPROVAL_PENDING".equals(report.getString("status"))) return false;
        String tenant = report.getString("tenantId"), reviewer = report.getString("reviewedBy");
        if (tenant == null || tenant.isBlank() || reviewer == null || reviewer.isBlank()
                || !"PENDING".equals(report.getString("inventoryConsumptionStatus"))) return false;
        for (String field : List.of("approvalLeaseUntil", "recoveryNextAttemptAt")) {
            String value = report.getString(field);
            if (value != null && !value.isBlank() && Instant.parse(value).isAfter(now)) return false;
        }
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var actor = new UsernamePasswordAuthenticationToken("SYSTEM_REPORT_RECOVERY", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        actor.setDetails(new FirebaseAuthenticationDetails("SYSTEM_REPORT_RECOVERY", null, "Recupero approvazione", tenant));
        context.setAuthentication(actor);
        SecurityContextHolder.setContext(context);
        try {
            reports.review(report.getId(), true, report.getString("reviewNote"), reviewer);
            return true;
        } catch (Exception failure) {
            // Preserve the pending claim and its inventory ledger. Never roll back
            // stock that may already have been consumed in another process.
            var db = FirestoreClient.getFirestore();
            db.runTransaction(tx -> {
                var current = tx.get(report.getReference()).get();
                if (current.exists() && tenant.equals(current.getString("tenantId"))
                        && "APPROVAL_PENDING".equals(current.getString("status"))) {
                    long attempts = current.get("recoveryAttempts") instanceof Number n ? n.longValue() + 1 : 1;
                    tx.update(report.getReference(), "recoveryAttempts", attempts,
                            "lastRecoveryAt", now.toString(),
                            "recoveryNextAttemptAt", now.plusSeconds(Math.min(3600, 60L << Math.min(attempts, 6))).toString());
                }
                return null;
            }).get();
            throw failure;
        } finally { SecurityContextHolder.setContext(previous); }
    }
}
