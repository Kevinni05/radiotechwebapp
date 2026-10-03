package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.MaintenanceReport;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class MaintenanceReportFirestoreEmulatorTest {

    private static final String OPERATOR_UID = "operator-emulator";
    private static final String VERIFICATION_SECRET = "emulator-test-secret-with-32-bytes-minimum";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void repeatedSubmissionCreatesOneReportAndChecksOutTaskOnce() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String antennaId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String idempotencyKey = "retry-" + UUID.randomUUID().toString().replace("-", "");
        authenticate(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            seedTask(firestore, tenantId, antennaId, taskId, "IN_PROGRESS");

            AuditService auditService = mock(AuditService.class);
            OperatorService operatorService = mock(OperatorService.class);
            TaskService taskService = taskService(operatorService, auditService);
            MaintenanceReportService reportService = new MaintenanceReportService(
                    taskService, auditService, mock(RicambioService.class), operatorService);
            MaintenanceReport report = report(taskId, antennaId, "Inspection complete");

            MaintenanceReport first = reportService.submit(report, OPERATOR_UID, idempotencyKey);
            MaintenanceReport retry = reportService.submit(
                    report(taskId, antennaId, "Inspection complete"), OPERATOR_UID, idempotencyKey);

            assertEquals(first.getId(), retry.getId());
            assertEquals(1, firestore.collection("maintenanceReports")
                    .whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("taskId", taskId)
                    .get().get().size());
            assertEquals(1, firestore.collection("apiIdempotencyKeys")
                    .whereEqualTo("tenantId", tenantId)
                    .get().get().size());

            var taskDocument = firestore.collection("tasks").document(taskId).get().get();
            assertEquals("REPORT_SUBMITTED", taskDocument.getString("status"));
            assertEquals(OPERATOR_UID, taskDocument.getString("checkedOutBy"));
            assertEquals(41.1171, taskDocument.getDouble("checkOutLatitude"), 0.000001);
            verify(auditService, times(1)).record(eq("REPORT_SUBMITTED"), eq(tenantId),
                    eq(OPERATOR_UID), eq("MAINTENANCE_REPORT"), anyString(), eq("SUCCESS"),
                    isNull(), isNull());
        }
    }

    @Test
    void publicVerificationAcceptsAuthenticReportAndRejectsTamperingWithoutLeakingReport() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String antennaId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        authenticate(tenantId);
        try (Firestore firestore = emulatorFirestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            seedTask(firestore, tenantId, antennaId, taskId, "IN_PROGRESS");
            OperatorService operators = mock(OperatorService.class);
            MaintenanceReportService reports = new MaintenanceReportService(
                    taskService(operators, mock(AuditService.class)), mock(AuditService.class),
                    mock(RicambioService.class), operators);
            ReflectionTestUtils.setField(reports, "verificationSecret", VERIFICATION_SECRET);
            ReflectionTestUtils.setField(reports, "publicBaseUrl", "https://reports.example.test");

            MaintenanceReport submitted = reports.submit(report(taskId, antennaId, "RF check"), OPERATOR_UID);
            String url = reports.verificationUrl(submitted);
            String token = url.substring(url.lastIndexOf('/') + 1);
            Map<String, Object> verified = reports.verifyPublicToken(token);
            assertEquals(true, verified.get("valid"));
            assertEquals(submitted.getId(), verified.get("reportId"));
            assertEquals("VALID", verified.get("integrity"));
            assertEquals(4, verified.size());
            String[] tokenParts = token.split("\\.");
            tokenParts[2] = (tokenParts[2].startsWith("A") ? "B" : "A") + tokenParts[2].substring(1);
            assertThrows(IllegalArgumentException.class,
                    () -> reports.verifyPublicToken(String.join(".", tokenParts)));

            firestore.collection("maintenanceReports").document(submitted.getId())
                    .update("description", "tampered").get();
            assertThrows(IllegalArgumentException.class, () -> reports.verifyPublicToken(token));
        }
    }

    @Test
    void outOfGeofenceSubmissionDoesNotCreateReportOrIdempotencyRecord() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String antennaId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String idempotencyKey = "outside-" + UUID.randomUUID().toString().replace("-", "");
        authenticate(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            seedTask(firestore, tenantId, antennaId, taskId, "IN_PROGRESS");

            OperatorService operatorService = mock(OperatorService.class);
            TaskService taskService = taskService(operatorService, mock(AuditService.class));
            MaintenanceReportService reportService = new MaintenanceReportService(
                    taskService, mock(AuditService.class), mock(RicambioService.class), operatorService);

            assertThrows(SecurityException.class, () -> reportService.submit(
                    report(taskId, antennaId, "Outside geofence", 41.1271, 16.8719),
                    OPERATOR_UID, idempotencyKey));

            assertEquals(0, firestore.collection("maintenanceReports")
                    .whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("taskId", taskId)
                    .get().get().size());
            String keyId = MaintenanceReportService.idempotencyDocumentId(
                    tenantId, OPERATOR_UID, idempotencyKey);
            assertTrue(!firestore.collection("apiIdempotencyKeys").document(keyId).get().get().exists());
        }
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String antennaId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String idempotencyKey = "conflict-" + UUID.randomUUID().toString().replace("-", "");
        authenticate(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            seedTask(firestore, tenantId, antennaId, taskId, "IN_PROGRESS");

            AuditService auditService = mock(AuditService.class);
            OperatorService operatorService = mock(OperatorService.class);
            TaskService taskService = taskService(operatorService, auditService);
            MaintenanceReportService reportService = new MaintenanceReportService(
                    taskService, auditService, mock(RicambioService.class), operatorService);

            reportService.submit(report(taskId, antennaId, "Initial report"), OPERATOR_UID, idempotencyKey);

            assertThrows(MaintenanceReportService.IdempotencyConflictException.class,
                    () -> reportService.submit(
                            report(taskId, antennaId, "Changed report"), OPERATOR_UID, idempotencyKey));
            assertEquals(1, firestore.collection("maintenanceReports")
                    .whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("taskId", taskId)
                    .get().get().size());
        }
    }

    @Test
    void concurrentApprovalsClaimAndConsumeAReportOnlyOnce() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String reportId = UUID.randomUUID().toString();
        CountDownLatch consumptionStarted = new CountDownLatch(1);
        CountDownLatch finishConsumption = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setProjectId("demo-radiotech")
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build());
        try (Firestore firestore = FirestoreClient.getFirestore()) {
            firestore.collection("maintenanceReports").document(reportId).set(Map.of(
                    "tenantId", tenantId,
                    "status", "SUBMITTED",
                    "materialsUsed", List.of(Map.of("inventoryId", "inventory-item", "quantity", 1))))
                    .get();

            RicambioService ricambioService = mock(RicambioService.class);
            doAnswer(invocation -> {
                consumptionStarted.countDown();
                if (!finishConsumption.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent approval test.");
                }
                return null;
            }).when(ricambioService).consume(anyList(), anyString());
            MaintenanceReportService reportService = new MaintenanceReportService(
                    mock(TaskService.class), mock(AuditService.class), ricambioService, mock(OperatorService.class));

            var firstApproval = executor.submit(() -> {
                authenticate(tenantId);
                try {
                    return reportService.review(reportId, true, "approved", "reviewer-one");
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            boolean firstClaimReachedInventory = consumptionStarted.await(5, TimeUnit.SECONDS);
            if (!firstClaimReachedInventory) {
                firstApproval.get(5, TimeUnit.SECONDS);
            }
            assertTrue(firstClaimReachedInventory,
                    "First approval should claim the report before consuming inventory.");

            var secondApproval = executor.submit(() -> {
                authenticate(tenantId);
                try {
                    return reportService.review(reportId, true, "duplicate", "reviewer-two");
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            ExecutionException duplicate = assertThrows(ExecutionException.class,
                    () -> secondApproval.get(5, TimeUnit.SECONDS));
            assertTrue(duplicate.getCause() instanceof IllegalArgumentException,
                    () -> "Unexpected duplicate approval failure: " + duplicate.getCause());
            assertEquals("Il report è già in fase di approvazione.", duplicate.getCause().getMessage());

            finishConsumption.countDown();
            assertEquals("APPROVED", firstApproval.get(5, TimeUnit.SECONDS).getStatus());
            assertEquals("APPROVED", firestore.collection("maintenanceReports")
                    .document(reportId).get().get().getString("status"));
            verify(ricambioService, times(1)).consume(anyList(), anyString());
        } finally {
            finishConsumption.countDown();
            executor.shutdownNow();
            firebaseApp.delete();
        }
    }

    @Test
    void failedInventoryConsumptionRestoresReportForRetry() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String reportId = UUID.randomUUID().toString();
        authenticate(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("maintenanceReports").document(reportId).set(Map.of(
                    "tenantId", tenantId,
                    "status", "SUBMITTED",
                    "materialsUsed", List.of(Map.of("inventoryId", "inventory-item", "quantity", 1))))
                    .get();

            RicambioService ricambioService = mock(RicambioService.class);
            doThrow(new IllegalArgumentException("Scorta insufficiente."))
                    .when(ricambioService).consume(anyList(), anyString());
            MaintenanceReportService reportService = new MaintenanceReportService(
                    mock(TaskService.class), mock(AuditService.class), ricambioService, mock(OperatorService.class));

            assertThrows(IllegalArgumentException.class,
                    () -> reportService.review(reportId, true, "approved", "reviewer"));
            DocumentSnapshot report = firestore.collection("maintenanceReports").document(reportId).get().get();
            assertEquals("APPROVAL_PENDING", report.getString("status"));
            assertEquals("PENDING", report.getString("inventoryConsumptionStatus"));
            assertTrue(report.getString("reviewedAt") != null);
            assertNull(report.getString("approvalLeaseUntil"));

            org.mockito.Mockito.doNothing().when(ricambioService).consume(anyList(), anyString());
            assertEquals("APPROVED", reportService.review(reportId, true, "approved", "reviewer").getStatus());
            verify(ricambioService, times(2)).consume(anyList(), anyString());
        }
    }

    @Test
    void retriesAfterInventoryCommitCompleteAnExpiredApprovalWithoutConsumingTwice() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String reportId = UUID.randomUUID().toString();
        List<Map<String, Object>> materials = List.of(Map.of("inventoryId", "cable", "quantity", 1));
        authenticate(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("inventory").document("cable").set(Map.of(
                    "tenantId", tenantId, "sku", "RF-001", "name", "Cavo RF", "quantity", 4)).get();

            RicambioService ricambioService = new RicambioService(mock(AuditService.class));
            // Simulate a process stop after the inventory transaction commits but before report finalization.
            ricambioService.consume(materials, reportId);
            firestore.collection("maintenanceReports").document(reportId).set(Map.of(
                    "tenantId", tenantId,
                    "status", "APPROVAL_PENDING",
                    "inventoryConsumptionStatus", "PENDING",
                    "approvalAttemptId", "expired-attempt",
                    "approvalLeaseUntil", Instant.now().minusSeconds(1).toString(),
                    "reviewedBy", "reviewer",
                    "materialsUsed", materials)).get();

            MaintenanceReportService reportService = new MaintenanceReportService(
                    mock(TaskService.class), mock(AuditService.class), ricambioService, mock(OperatorService.class));
            MaintenanceReport reviewed = reportService.review(reportId, true, "approved", "reviewer");

            assertEquals("APPROVED", reviewed.getStatus());
            assertEquals("COMPLETE", firestore.collection("maintenanceReports").document(reportId)
                    .get().get().getString("inventoryConsumptionStatus"));
            assertEquals(3L, firestore.collection("inventory").document("cable").get().get().getLong("quantity"));
            assertEquals(1, firestore.collection("inventoryMovements")
                    .whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("operationId", reportId)
                    .get().get().size());
        }
    }

    @Test
    void legacyApprovedReportWithMaterialsRequiresManualInventoryReconciliation() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String reportId = UUID.randomUUID().toString();
        authenticate(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("maintenanceReports").document(reportId).set(Map.of(
                    "tenantId", tenantId,
                    "status", "APPROVED",
                    "materialsUsed", List.of(Map.of("inventoryId", "cable", "quantity", 1))))
                    .get();

            RicambioService ricambioService = mock(RicambioService.class);
            MaintenanceReportService reportService = new MaintenanceReportService(
                    mock(TaskService.class), mock(AuditService.class), ricambioService, mock(OperatorService.class));
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> reportService.review(reportId, true, "retry", "reviewer"));

            assertTrue(exception.getMessage().contains("riconciliazione necessaria"));
            assertEquals("APPROVED", firestore.collection("maintenanceReports")
                    .document(reportId).get().get().getString("status"));
            verify(ricambioService, times(0)).consume(anyList(), anyString());
        }
    }

    private Firestore emulatorFirestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build()
                .getService();
    }

    private void authenticate(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                OPERATOR_UID, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                OPERATOR_UID, "operator@example.test", "Emulator Operator", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertEquals(tenantId, SecurityContextAccessor.currentTenantId());
    }

    private void seedTask(Firestore firestore, String tenantId, String antennaId,
            String taskId, String status) throws Exception {
        firestore.collection("antennas").document(antennaId).set(Map.of(
                "tenantId", tenantId,
                "lat", 41.1171,
                "lng", 16.8719)).get();
        Task task = new Task();
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setOperatorId(OPERATOR_UID);
        task.setOperatorFirebaseUid(OPERATOR_UID);
        task.setAntennaId(antennaId);
        task.setStatus(status);
        firestore.collection("tasks").document(taskId).set(task).get();
    }

    private TaskService taskService(OperatorService operatorService, AuditService auditService) {
        TaskService taskService = new TaskService(
                operatorService, auditService, mock(NotificationService.class));
        ReflectionTestUtils.setField(taskService, "geofenceRadiusMeters", 250.0);
        return taskService;
    }

    private MaintenanceReport report(String taskId, String antennaId, String description) {
        return report(taskId, antennaId, description, 41.1171, 16.8719);
    }

    private MaintenanceReport report(String taskId, String antennaId, String description,
            double latitude, double longitude) {
        MaintenanceReport report = new MaintenanceReport();
        report.setTaskId(taskId);
        report.setAntennaId(antennaId);
        report.setDescription(description);
        report.setWorkPerformed(description);
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        return report;
    }
}
