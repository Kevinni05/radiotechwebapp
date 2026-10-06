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
class TaskIdempotencyEmulatorTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void retryOfEarlierActionDoesNotRegressTaskOrDuplicateAudit() throws Exception {
        String tenant = "idem-" + UUID.randomUUID();
        String taskId = UUID.randomUUID().toString();
        var authentication = new UsernamePasswordAuthenticationToken("operator-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        authentication.setDetails(new FirebaseAuthenticationDetails("operator-1", "field@example.test", "Field", tenant));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try (Firestore firestore = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("tasks").document(taskId).set(Map.of("tenantId", tenant, "status", "ASSIGNED", "operatorId", "operator-1")).get();
            AuditService audit = mock(AuditService.class);
            TaskService service = new TaskService(mock(OperatorService.class), audit, mock(NotificationService.class));
            assertEquals("ACCEPTED", service.updateStatus(taskId, "ACCEPTED", "accept-1").getStatus());
            assertEquals("EN_ROUTE", service.updateStatus(taskId, "EN_ROUTE", "travel-1").getStatus());
            assertEquals("EN_ROUTE", service.updateStatus(taskId, "ACCEPTED", "accept-1").getStatus());
            assertThrows(TaskService.IdempotencyConflictException.class, () -> service.updateStatus(taskId, "IN_PROGRESS", "accept-1"));
            assertThrows(IllegalArgumentException.class, () -> service.updateStatus(taskId, "CLOSED", "close-1"));
            assertEquals("EN_ROUTE", service.getById(taskId).getStatus());
            verify(audit, times(2)).record(eq("TASK_STATUS_CHANGED"), eq(tenant), eq("operator-1"), eq("TASK"), eq(taskId), anyString(), isNull(), isNull());
        }
    }
}
