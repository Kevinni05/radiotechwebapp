package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST", matches=".+")
class TaskAssignmentOutboxEmulatorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void assignmentInboxAndAuditSurviveImmediatePushFailure() throws Exception {
        String tenant="task-outbox-"+UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken("manager",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(new FirebaseAuthenticationDetails("manager",null,"Manager",tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
        var operator=new Operator();operator.setId("operator");operator.setTenantId(tenant);operator.setFirebaseUid("field-user");operator.setFullName("Field");
        var operators=mock(OperatorService.class);when(operators.getById("operator")).thenReturn(operator);
        var notifications=mock(NotificationService.class);doThrow(new IllegalStateException("FCM offline")).when(notifications).deliverRecorded(anyString());
        try(var db=FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator",new Date(Long.MAX_VALUE)))).build().getService();
                var database=mockStatic(FirestoreClient.class)) {
            database.when(FirestoreClient::getFirestore).thenReturn(db);
            var task=new Task();task.setTitle("Inspect site");task.setOperatorId("operator");task.setTenantId(tenant);
            var created=new TaskService(operators,mock(AuditService.class),notifications).createTask(task);
            assertTrue(db.collection("tasks").document(created.getId()).get().get().exists());
            var message=db.collection("notificationHistory").document("task_assignment_"+created.getId()).get().get();
            assertEquals("PENDING",message.getString("deliveryStatus"));assertEquals(created.getId(),message.getString("taskId"));
            assertEquals(tenant,message.getString("tenantId"));
            var audit=db.collection("auditLogs").document("task_created_"+created.getId()).get().get();
            assertEquals("manager",audit.getString("actor"));assertEquals("SUCCESS",audit.getString("result"));
            verify(notifications).deliverRecorded(message.getId());
        }
    }
}
