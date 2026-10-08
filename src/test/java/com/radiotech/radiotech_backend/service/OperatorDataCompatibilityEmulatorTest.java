package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class OperatorDataCompatibilityEmulatorTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private Firestore database() {
        return FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }

    private void authenticate(String tenant) {
        var auth = new UsernamePasswordAuthenticationToken("manager", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(new FirebaseAuthenticationDetails("manager", null, null, tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test void taskListsIncludeUndatedAndLegacyAssignmentsWithoutCrossingTenant() throws Exception {
        String tenant = UUID.randomUUID().toString(), operator = UUID.randomUUID().toString();
        authenticate(tenant);
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("operators").document(operator).set(Map.of("tenantId", tenant, "firebaseUid", "legacy-uid", "fullName", "Operatore storico")).get();
            db.collection("tasks").document(operator + "-old").set(Map.of("tenantId", tenant, "operator_uid", "legacy-uid", "status", "IN LAVORAZIONE")).get();
            db.collection("tasks").document(operator + "-new").set(Map.of("tenantId", tenant, "operatorId", operator, "status", "ASSIGNED", "createdAt", com.google.cloud.Timestamp.now())).get();
            db.collection("tasks").document(operator + "-foreign").set(Map.of("tenantId", "foreign", "operatorId", operator, "status", "ASSIGNED")).get();
            var service = new TaskService(new OperatorService(), mock(AuditService.class), mock(NotificationService.class));
            var own = service.getByOperator(operator);
            assertEquals(2, own.size());
            assertTrue(own.stream().allMatch(task -> operator.equals(task.getOperatorId())));
            assertTrue(own.stream().allMatch(task -> "Operatore storico".equals(task.getOperatorName())));
            assertEquals(2, service.getAllTasks().size());
            var legacy = service.getById(operator + "-old");
            assertEquals("legacy-uid", legacy.getOperatorFirebaseUid());
            assertEquals("IN_PROGRESS", legacy.getStatus());
            assertThrows(IllegalArgumentException.class, () -> service.getById(operator + "-foreign"));
        }
    }

    @Test void reportListsIncludeLegacyDatesAndPdfAndReturnTheSameDocumentInDetail() throws Exception {
        String tenant = UUID.randomUUID().toString(), operator = UUID.randomUUID().toString();
        authenticate(tenant);
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("operators").document(operator).set(Map.of("tenantId", tenant, "firebaseUid", "report-uid")).get();
            db.collection("maintenanceReports").document(operator + "-old").set(Map.of("tenantId", tenant,
                    "operator_uid", "report-uid", "created_at", com.google.cloud.Timestamp.now(),
                    "antenna_id", "asset", "task_id", "old-task", "notes", "Legacy report",
                    "pdf_url", "https://firebasestorage.googleapis.com/v0/b/test/o/report.pdf")).get();
            db.collection("maintenanceReports").document(operator + "-timestamp").set(Map.of("tenantId", tenant,
                    "operator_id", operator, "submittedAt", com.google.cloud.Timestamp.now())).get();
            db.collection("maintenanceReports").document(operator + "-foreign").set(Map.of("tenantId", "foreign", "operator_uid", "report-uid")).get();
            var service = new MaintenanceReportService(mock(TaskService.class), mock(AuditService.class),
                    mock(RicambioService.class), new OperatorService());
            assertEquals(2, service.getByOperator(operator).size());
            assertEquals(2, service.getAll().size());
            var report = service.getById(operator + "-old");
            assertNotNull(report.getSubmittedAt());
            assertEquals("asset", report.getAntennaId());
            assertEquals("old-task", report.getTaskId());
            assertEquals("Legacy report", report.getOperatorNotes());
            assertEquals(1, report.getAttachments().size());
            assertThrows(IllegalArgumentException.class, () -> service.getById(operator + "-foreign"));
        }
    }

    @Test void aPendingReviewDoesNotBlockDeletionButAnActiveTaskDoes() throws Exception {
        String tenant = UUID.randomUUID().toString(), operator = UUID.randomUUID().toString();
        authenticate(tenant);
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("operators").document(operator).set(Map.of("tenantId", tenant, "role", "OPERATOR")).get();
            var task = db.collection("tasks").document(operator);
            task.set(Map.of("tenantId", tenant, "operator_uid", operator, "status", "IN LAVORAZIONE", "title", "Intervento storico")).get();
            var service = new OperatorService();
            var blocked = assertThrows(IllegalArgumentException.class, () -> service.deleteOperator(operator));
            assertTrue(blocked.getMessage().contains(operator));
            task.update("status", "REPORT_SUBMITTED").get();
            db.collection("maintenanceReports").document(operator).set(Map.of("tenantId", tenant, "operatorId", operator, "status", "SUBMITTED")).get();
            service.deleteOperator(operator);
            assertFalse(db.collection("operators").document(operator).get().get().exists());
            assertTrue(task.get().get().exists());
            assertTrue(db.collection("maintenanceReports").document(operator).get().get().exists());
        }
    }

    @Test void rejectionRestoresReportActionAtomicallyAndLeavesOtherTenantsUntouched() throws Exception {
        String tenant = UUID.randomUUID().toString(), id = UUID.randomUUID().toString();
        authenticate(tenant);
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("tasks").document(id).set(Map.of("tenantId", tenant, "status", "REPORT_SUBMITTED")).get();
            db.collection("maintenanceReports").document(id).set(Map.of("tenantId", tenant, "taskId", id, "status", "SUBMITTED")).get();
            var service = new MaintenanceReportService(mock(TaskService.class), mock(AuditService.class), mock(RicambioService.class), mock(OperatorService.class));
            service.review(id, false, "Correggi le misure", "manager");
            assertEquals("COMPLETED", db.collection("tasks").document(id).get().get().getString("status"));
            assertEquals("REJECTED", service.getById(id).getStatus());
            db.collection("tasks").document(id).update("tenantId", "foreign", "status", "REPORT_SUBMITTED").get();
            db.collection("maintenanceReports").document(id).update("status", "SUBMITTED").get();
            service.review(id, false, "Correggi", "manager");
            assertEquals("REPORT_SUBMITTED", db.collection("tasks").document(id).get().get().getString("status"));
        }
    }
}
