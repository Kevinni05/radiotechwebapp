package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class DashboardPendingReportsEmulatorTest {
    @Test void pagesOnlyPendingReportsPreservingMissingNullBlankAndRemovedSemantics() throws Exception {
        String tenant = "pending-count-" + UUID.randomUUID();
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST")).build().getService()) {
            for (int offset = 0; offset < 1200; offset += 400) {
                var batch = db.batch();
                for (int i = offset; i < offset + 400; i++) {
                    batch.set(db.collection("maintenanceReports").document(),
                            Map.of("tenantId", tenant, "status", i < 510 ? "SUBMITTED" : "APPROVED"));
                }
                batch.commit().get();
            }
            var batch = db.batch();
            var legacy = new HashMap<String, Object>(Map.of("tenantId", tenant, "status", "APPROVAL_PENDING"));
            legacy.put("removedAt", null);
            batch.set(db.collection("maintenanceReports").document(), legacy);
            legacy.put("removedAt", " \t ");
            batch.set(db.collection("maintenanceReports").document(), legacy);
            batch.set(db.collection("maintenanceReports").document(),
                    Map.of("tenantId", tenant, "status", "SUBMITTED", "removedAt", "2026-10-12T10:00:00Z"));
            batch.set(db.collection("maintenanceReports").document(),
                    Map.of("tenantId", tenant + "-other", "status", "SUBMITTED"));
            batch.commit().get();
            assertEquals(512L, DashboardService.pendingReportCount(db, tenant));
            assertEquals(1L, DashboardService.pendingReportCount(db, tenant + "-other"));
            assertEquals(0L, DashboardService.pendingReportCount(db, tenant + "-missing"));
            assertNull(DashboardService.pendingReportSummary(db, tenant).oldestAt());
            String datedTenant = tenant + "-dated";
            var dated = db.batch();
            dated.set(db.collection("maintenanceReports").document(),
                    Map.of("tenantId", datedTenant, "status", "SUBMITTED", "archiveAt", 500L));
            dated.set(db.collection("maintenanceReports").document(),
                    Map.of("tenantId", datedTenant, "status", "APPROVAL_PENDING", "archiveAt", 200L));
            dated.set(db.collection("maintenanceReports").document(),
                    Map.of("tenantId", datedTenant, "status", "SUBMITTED", "archiveAt", 100L, "removedAt", "removed"));
            dated.commit().get();
            assertEquals(2L, DashboardService.pendingReportSummary(db, datedTenant).count());
            assertEquals(200L, DashboardService.pendingReportSummary(db, datedTenant).oldestAt());
        }
    }
}
