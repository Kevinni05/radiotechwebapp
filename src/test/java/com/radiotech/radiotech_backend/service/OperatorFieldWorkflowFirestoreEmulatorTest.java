package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.controller.MaintenanceReportController;
import com.radiotech.radiotech_backend.controller.OperatorMobileController;
import com.radiotech.radiotech_backend.dto.MaintenanceReportDto;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.model.MaintenanceReport;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.security.TenantInvitationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class OperatorFieldWorkflowFirestoreEmulatorTest {
    private static final String OPERATOR_UID = "workflow-operator";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedTaskRunsThroughFieldReportApprovalAndClosure() throws Exception {
        String tenant = "workflow-" + UUID.randomUUID();
        String antennaId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String stockId = UUID.randomUUID().toString();

        try (Firestore firestore = emulatorFirestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("antennas").document(antennaId).set(Map.of(
                    "tenantId", tenant, "lat", 41.1171, "lng", 16.8719)).get();
            Operator operator = new Operator();
            operator.setTenantId(tenant); operator.setFirebaseUid(OPERATOR_UID);
            operator.setFullName("Workflow operator"); operator.setStatus("ATTIVO");
            firestore.collection("operators").document("operator-doc").set(operator).get();
            Task task = new Task();
            task.setId(taskId); task.setTenantId(tenant); task.setOperatorId("operator-doc");
            task.setOperatorFirebaseUid(OPERATOR_UID); task.setAntennaId(antennaId); task.setStatus("ASSIGNED");
            firestore.collection("tasks").document(taskId).set(task).get();
            firestore.collection("inventory").document(stockId).set(Map.of(
                    "tenantId", tenant, "sku", "RF-CABLE-01", "name", "RF cable", "quantity", 3)).get();

            authenticate(tenant, OPERATOR_UID, "ROLE_OPERATOR");
            AuditService audit = new AuditService();
            OperatorService operators = new OperatorService();
            TaskService tasks = new TaskService(operators, audit, mock(NotificationService.class));
            ReflectionTestUtils.setField(tasks, "geofenceRadiusMeters", 250.0);
            RicambioService inventory = new RicambioService(audit);
            MaintenanceReportService reports = new MaintenanceReportService(tasks, audit, inventory, operators);
            ReflectionTestUtils.setField(reports, "verificationSecret", "emulator-workflow-secret-32-bytes-minimum");
            ReflectionTestUtils.setField(reports, "publicBaseUrl", "https://reports.example.test");
            ReflectionTestUtils.setField(reports, "storageBucket", "demo-radiotech.appspot.com");
            OperatorMobileController mobile = new OperatorMobileController(tasks, reports, operators,
                    new TenantInvitationService("emulator-invitation-secret-32-bytes", java.time.Clock.systemUTC()));

            assertEquals(200, mobile.accept(OPERATOR_UID, taskId).getStatusCode().value());
            assertEquals(200, mobile.enRoute(OPERATOR_UID, taskId).getStatusCode().value());
            assertEquals(200, mobile.checkIn(OPERATOR_UID, taskId, Map.of(
                    "latitude", 41.1171, "longitude", 16.8719, "accuracy", 5.0)).getStatusCode().value());
            assertEquals(200, mobile.start(OPERATOR_UID, taskId).getStatusCode().value());

            MaintenanceReportDto payload = new MaintenanceReportDto();
            payload.setTaskId(taskId); payload.setAntennaId(antennaId);
            payload.setDescription("Field service complete"); payload.setWorkPerformed("Connector replaced");
            payload.setChecklist(Map.of("power", true, "signal", true));
            payload.setMeasurements(Map.of("vswr", 1.2));
            payload.setMaterialsUsed(List.of(Map.of("inventoryId", stockId, "sku", "RF-CABLE-01", "quantity", 1)));
            payload.setAttachments(List.of("https://firebasestorage.googleapis.com/v0/b/demo-radiotech.appspot.com/o/"
                    + "tenants%2F" + tenant + "%2Fmaintenance-reports%2F" + OPERATOR_UID
                    + "%2Fphoto.jpg?alt=media&token=emulator-download-token"));
            payload.setDigitalSignature("operator-signature-payload");
            payload.setLatitude(41.1171); payload.setLongitude(16.8719);
            payload.setCompletedAt(java.time.Instant.now().toString());
            var submitted = mobile.report(OPERATOR_UID, taskId, payload, "workflow-report-1");
            assertEquals(201, submitted.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> submission = (Map<String, Object>) submitted.getBody();
            assertNotNull(submission.get("verificationUrl"));
            MaintenanceReport report = (MaintenanceReport) submission.get("report");
            String reportId = report.getId();
            assertEquals("REPORT_SUBMITTED", firestore.collection("tasks").document(taskId).get().get().getString("status"));

            authenticate(tenant, "manager-uid", "ROLE_ADMIN");
            MaintenanceReportController manager = new MaintenanceReportController(reports);
            assertEquals(200, manager.approve(reportId, "manager-uid", Map.of("note", "verified"), tenant)
                    .getStatusCode().value());
            assertEquals(taskId, tasks.updateStatus(taskId, "CLOSED").getId());
            assertEquals("CLOSED", firestore.collection("tasks").document(taskId).get().get().getString("status"));
            assertEquals(2L, firestore.collection("inventory").document(stockId).get().get().getLong("quantity"));
            assertEquals(1, firestore.collection("inventoryMovements").whereEqualTo("tenantId", tenant)
                    .whereEqualTo("operationId", reportId).get().get().size());
            org.junit.jupiter.api.Assertions.assertTrue(firestore.collection("auditLogs")
                    .whereEqualTo("tenantId", tenant).whereEqualTo("resourceId", taskId).get().get().size() >= 1);
        }
    }

    private Firestore emulatorFirestore() {
        return FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }

    private void authenticate(String tenant, String uid, String role) {
        var auth = new UsernamePasswordAuthenticationToken(uid, null, List.of(new SimpleGrantedAuthority(role)));
        auth.setDetails(new FirebaseAuthenticationDetails(uid, "workflow@example.test", "Workflow", tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
