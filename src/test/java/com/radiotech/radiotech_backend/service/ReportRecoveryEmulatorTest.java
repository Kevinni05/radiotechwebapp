package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class ReportRecoveryEmulatorTest {
    @Test void resumesAfterStockCommitAndRemovalWithoutConsumingTwiceAndRestoresThreadIdentity() throws Exception {
        String tenant = UUID.randomUUID().toString();
        var actor = new UsernamePasswordAuthenticationToken("reviewer", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        actor.setDetails(new FirebaseAuthenticationDetails("reviewer", null, "Reviewer", tenant));
        var previous = SecurityContextHolder.createEmptyContext(); previous.setAuthentication(actor);
        SecurityContextHolder.setContext(previous);
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator", new Date(Long.MAX_VALUE))))
                .build().getService(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            String itemId = UUID.randomUUID().toString(), reportId = UUID.randomUUID().toString();
            var item = db.collection("inventory").document(itemId);
            item.set(Map.of("tenantId", tenant, "quantity", 10, "name", "Connettore")).get();
            var materials = List.of(Map.<String, Object>of("inventoryId", itemId, "quantity", 2));
            var stock = new RicambioService(mock(AuditService.class));
            stock.consume(materials, reportId); // crash after durable stock ledger
            var report = db.collection("maintenanceReports").document(reportId);
            report.set(Map.of("tenantId", tenant, "status", "APPROVAL_PENDING",
                    "inventoryConsumptionStatus", "PENDING", "reviewedBy", "reviewer",
                    "materialsUsed", materials, "inventoryConsumptionMaterials", materials,
                    "removedAt", Instant.now().toString())).get();
            var service = new MaintenanceReportService(mock(TaskService.class), mock(AuditService.class), stock, mock(OperatorService.class));
            var recovery = new ReportApprovalRecovery(service);
            assertTrue(recovery.recover(report.get().get(), Instant.now()));
            assertEquals("APPROVED", report.get().get().getString("status"));
            assertEquals(8L, item.get().get().getLong("quantity"));
            assertSame(previous, SecurityContextHolder.getContext());
            assertFalse(recovery.recover(report.get().get(), Instant.now()));
            var events = db.collection("auditLogs").whereEqualTo("resourceId", reportId).get().get().getDocuments();
            assertEquals(2, events.size());
            assertTrue(events.stream().allMatch(e -> "reviewer".equals(e.getString("actor"))));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void leavesLiveLeasesAndUnclaimedReportsUntouched() throws Exception {
        var report = mock(DocumentSnapshot.class); var service = mock(MaintenanceReportService.class);
        when(report.getString("status")).thenReturn("APPROVAL_PENDING");
        when(report.getString("tenantId")).thenReturn("tenant");
        when(report.getString("reviewedBy")).thenReturn("reviewer");
        when(report.getString("inventoryConsumptionStatus")).thenReturn("PENDING");
        when(report.getString("approvalLeaseUntil")).thenReturn(Instant.now().plusSeconds(120).toString());
        assertFalse(new ReportApprovalRecovery(service).recover(report, Instant.now()));
        when(report.getString("status")).thenReturn("SUBMITTED");
        assertFalse(new ReportApprovalRecovery(service).recover(report, Instant.now()));
        verifyNoInteractions(service);
    }
}
