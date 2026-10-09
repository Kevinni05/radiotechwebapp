package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class MaintenanceReportRemovalEmulatorTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private void authenticate(String tenant, String role) {
        var auth = new UsernamePasswordAuthenticationToken("manager", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new FirebaseAuthenticationDetails("manager", "manager@example.test", "Manager", tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Firestore firestore() {
        return FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }

    private MaintenanceReportService service() {
        return new MaintenanceReportService(mock(TaskService.class), mock(AuditService.class),
                mock(RicambioService.class), mock(OperatorService.class));
    }

    @Test void removalRetainsEvidenceInEveryStateAndCommitsOneAuditForRetries() throws Exception {
        String tenant = UUID.randomUUID().toString();
        authenticate(tenant, "ADMIN");
        try (var db = firestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            var reports = service();
            for (String state : List.of("DRAFT", "SUBMITTED", "APPROVAL_PENDING", "IN_REVIEW", "APPROVED", "REJECTED")) {
                String id = UUID.randomUUID().toString();
                var reference = db.collection("maintenanceReports").document(id);
                reference.set(Map.of("tenantId", tenant, "status", state, "digitalSignature", "signed-evidence",
                        "attachments", List.of("retained.pdf"), "pdfUrl", "retained.pdf", "integrityHash", "original-hash",
                        "archiveAt", 123L)).get();
                reports.remove(id, "manager");
                String removedAt = reference.get().get().getString("removedAt");
                reports.remove(id, "manager");
                var preserved = reference.get().get();
                assertEquals(state, preserved.getString("status"));
                assertEquals("signed-evidence", preserved.getString("digitalSignature"));
                assertEquals("original-hash", preserved.getString("integrityHash"));
                assertEquals(List.of("retained.pdf"), preserved.get("attachments"));
                assertEquals("retained.pdf", preserved.getString("pdfUrl"));
                assertEquals(removedAt, preserved.getString("removedAt"));
                var audit = db.collection("auditLogs").whereEqualTo("resourceId", id).get().get();
                assertEquals(1, audit.size());
                assertEquals(tenant, audit.getDocuments().getFirst().getString("tenantId"));
                assertEquals("REPORT_REMOVED", audit.getDocuments().getFirst().getString("action"));
                assertEquals("manager", audit.getDocuments().getFirst().getString("actor"));
                assertEquals(state, reports.getById(id).getStatus());
                assertThrows(IllegalArgumentException.class, () -> reports.review(id, false, "", "manager"));
            }
            assertTrue(reports.getAll().isEmpty());
            assertEquals(0, reports.count(null));
        }
    }

    @Test void unauthorizedActorAndOtherTenantCannotRemoveOrCreateAudit() throws Exception {
        String tenant = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        try (var db = firestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            var reference = db.collection("maintenanceReports").document(id);
            reference.set(Map.of("tenantId", tenant, "status", "APPROVED")).get();
            var reports = service();
            for (String role : List.of("VIEWER", "OPERATOR", "CUSTOMER", "NONE")) {
                authenticate(tenant, role);
                assertThrows(SecurityException.class, () -> reports.remove(id, "manager"));
            }
            authenticate(tenant, "ADMIN");
            assertThrows(SecurityException.class, () -> reports.remove(id, "forged-actor"));
            authenticate("other-tenant", "SUPER_ADMIN");
            assertThrows(IllegalArgumentException.class, () -> reports.remove(id, "manager"));
            assertNull(reference.get().get().get("removedAt"));
            assertEquals(0, db.collection("auditLogs").whereEqualTo("resourceId", id).get().get().size());
        }
    }

    @Test void removalDoesNotStrandPreviouslyStartedApprovalOrReverseInventory() throws Exception {
        String tenant = UUID.randomUUID().toString();
        authenticate(tenant, "ADMIN");
        try (var db = firestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            String id = UUID.randomUUID().toString();
            var reference = db.collection("maintenanceReports").document(id);
            reference.set(Map.of("tenantId", tenant, "status", "APPROVAL_PENDING",
                    "inventoryConsumptionStatus", "PENDING", "materialsUsed", List.of(),
                    "digitalSignature", "retained-signature")).get();
            var reports = service();
            reports.remove(id, "manager");
            assertEquals("APPROVED", reports.review(id, true, "", "manager").getStatus());
            var snapshot = reference.get().get();
            assertNotNull(snapshot.getString("removedAt"));
            assertEquals("COMPLETE", snapshot.getString("inventoryConsumptionStatus"));
            assertEquals("retained-signature", snapshot.getString("digitalSignature"));
            assertTrue(reports.getAll().isEmpty());
        }
    }

    @Test void removedOnlyBatchesKeepACursorAndBoundEachPageScan() throws Exception {
        String tenant = UUID.randomUUID().toString();
        authenticate(tenant, "NETWORK_MANAGER");
        try (var db = firestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            var reports = service();
            for (int i = 0; i < 13; i++) {
                var record = new java.util.HashMap<String, Object>();
                record.put("tenantId", tenant); record.put("status", "SUBMITTED");
                record.put("archiveAt", (long) i);
                if (i > 0) record.put("removedAt", "2026-10-09T00:00:00Z");
                db.collection("maintenanceReports").document(tenant + "-" + i).set(record).get();
            }
            var first = reports.getPage(1, null, null, null);
            assertTrue(first.items().isEmpty());
            assertTrue(first.hasMore());
            assertNotNull(first.nextCursor());
            var next = reports.getPage(1, first.nextCursor(), null, null);
            assertEquals(List.of(tenant + "-0"), next.items().stream().map(r -> r.getId()).toList());
            assertFalse(next.hasMore());
            assertEquals(1, reports.count(null));
        }
    }

    @Test void pagesSkipRemovedRecordsWithoutSkippingLegacyVisibleRows() throws Exception {
        String tenant = UUID.randomUUID().toString();
        authenticate(tenant, "NETWORK_MANAGER");
        try (var db = firestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            var reports = service();
            for (int i = 0; i < 7; i++) {
                String id = tenant + "-" + i;
                db.collection("maintenanceReports").document(id).set(Map.of("tenantId", tenant,
                        "status", "SUBMITTED", "archiveAt", (long)i, "operatorRefs", List.of("op"))).get();
                if (i % 2 == 0) reports.remove(id, "manager");
            }
            var first = reports.getPage(2, null, null, null);
            assertEquals(List.of(tenant + "-5", tenant + "-3"), first.items().stream().map(r -> r.getId()).toList());
            var second = reports.getPage(2, first.nextCursor(), null, null);
            assertEquals(List.of(tenant + "-1"), second.items().stream().map(r -> r.getId()).toList());
            assertFalse(second.hasMore());
            assertEquals(3, reports.count("SUBMITTED"));
            assertEquals(3, reports.countByOperator("op", null));
        }
    }
}
