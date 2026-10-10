package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class CriticalAuditEmulatorTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void reportApprovalAndInventoryConsumptionCommitDurableEventsOnceAcrossRetry() throws Exception {
        String tenant = "critical-audit-" + UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("reviewer", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(new FirebaseAuthenticationDetails("reviewer", null, "Reviewer", tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator", new Date(Long.MAX_VALUE))))
                .build().getService(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            String itemId = UUID.randomUUID().toString(), reportId = UUID.randomUUID().toString();
            var item = db.collection("inventory").document(itemId);
            item.set(Map.of("tenantId", tenant, "name", "Connettore", "quantity", 10)).get();
            var report = db.collection("maintenanceReports").document(reportId);
            var materials = List.of(Map.<String, Object>of("inventoryId", itemId, "quantity", 2));
            report.set(Map.of("tenantId", tenant, "status", "SUBMITTED", "materialsUsed", materials)).get();
            var auditService = mock(AuditService.class);
            var stock = new RicambioService(auditService);
            var reports = new MaintenanceReportService(mock(TaskService.class), auditService, stock, mock(OperatorService.class));
            reports.review(reportId, true, "Verificato", "reviewer");
            reports.review(reportId, true, "Retry", "reviewer");
            stock.consume(materials, reportId);
            assertEquals(8L, item.get().get().getLong("quantity"));
            assertEquals("APPROVED", report.get().get().getString("status"));
            var events = db.collection("auditLogs").whereEqualTo("tenantId", tenant).get().get().getDocuments();
            assertEquals(2, events.size());
            assertEquals(Set.of("REPORT_APPROVED", "INVENTORY_CONSUMED"),
                    new HashSet<>(events.stream().map(event -> event.getString("action")).toList()));
            assertTrue(events.stream().allMatch(event -> "reviewer".equals(event.getString("actor"))));
            // Critical events are persisted inside the actual mutations, independent of the async audit service.
            verifyNoInteractions(auditService);
            String failingOperation = UUID.randomUUID().toString();
            assertThrows(IllegalArgumentException.class, () -> stock.consume(
                    List.of(Map.of("inventoryId", itemId, "quantity", 20)), failingOperation));
            assertEquals(8L, item.get().get().getLong("quantity"));
            assertEquals(2, db.collection("auditLogs").whereEqualTo("tenantId", tenant).get().get().size());
        }
    }
}
