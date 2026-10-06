package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Operator;
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

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST", matches=".+")
class NotificationHistoryEmulatorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void historyContainsOnlyOwnAndBroadcastMessagesFromTheAuthenticatedTenant() throws Exception {
        String tenant = "notification-test-" + UUID.randomUUID();
        authenticate(tenant);
        OperatorService operators = mock(OperatorService.class);
        when(operators.getByFirebaseUid("uid-a")).thenReturn(operator(tenant));
        try (Firestore db = firestore(); var staticDb = mockStatic(FirestoreClient.class)) {
            staticDb.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("notificationHistory").add(record(tenant, "operator-a", "Personal", "2026-01-01T00:00:00Z")).get();
            db.collection("notificationHistory").add(record(tenant, "BROADCAST", "General", "2026-01-02T00:00:00Z")).get();
            db.collection("notificationHistory").add(record(tenant, "operator-b", "Other operator", "2026-01-03T00:00:00Z")).get();
            db.collection("notificationHistory").add(record(tenant+"-other", "BROADCAST", "Other tenant", "2026-01-04T00:00:00Z")).get();
            var result = new NotificationService(operators).getOperatorHistory("uid-a");
            assertEquals(List.of("General", "Personal"), result.stream().map(item -> item.get("title")).toList());
            assertTrue(result.stream().allMatch(item -> item.get("id") != null));
            when(operators.getByFirebaseUid("uid-a")).thenReturn(operator("wrong-tenant"));
            assertThrows(SecurityException.class, () -> new NotificationService(operators).getOperatorHistory("uid-a"));
        }
    }

    @Test void missingPushTokensDoNotLoseTheInboxMessageOrTaskLink() throws Exception {
        String tenant = "notification-offline-" + UUID.randomUUID();
        authenticate(tenant);
        OperatorService operators = mock(OperatorService.class);
        Operator operator = operator(tenant);
        operator.setFcmTokens(List.of());
        when(operators.getById("operator-a")).thenReturn(operator);
        when(operators.getByFirebaseUid("uid-a")).thenReturn(operator);
        when(operators.getAllOperators()).thenReturn(List.of(operator));
        try (Firestore db = firestore(); var staticDb = mockStatic(FirestoreClient.class)) {
            staticDb.when(FirestoreClient::getFirestore).thenReturn(db);
            NotificationService service = new NotificationService(operators);
            assertEquals(0, service.notifyOperator("operator-a", "Assignment", "Inspect site",
                    Map.of("taskId", "task-a", "type", "TASK_ASSIGNED")));
            var history = service.getOperatorHistory("uid-a");
            assertEquals(1, history.size());
            assertEquals("task-a", history.get(0).get("taskId"));
            assertEquals("TASK_ASSIGNED", history.get(0).get("type"));
            assertEquals(0L, history.get(0).get("delivered"));
            assertEquals(0, service.notifyAllOperators("General", "Team message"));
            assertEquals(2, service.getOperatorHistory("uid-a").size());
        }
    }

    @Test void readAndAcknowledgementAreIdempotentAndJoinedIntoOwnInbox()throws Exception{
        String tenant="receipt-"+UUID.randomUUID();authenticate(tenant);var operators=mock(OperatorService.class);when(operators.getByFirebaseUid("uid-a")).thenReturn(operator(tenant));
        try(var db=firestore();var staticDb=mockStatic(FirestoreClient.class)){
            staticDb.when(FirestoreClient::getFirestore).thenReturn(db);String id=tenant+"-message";db.collection("notificationHistory").document(id).set(record(tenant,"operator-a","Personal","2026-10-06T00:00:00Z")).get();
            var service=new NotificationService(operators);var read=service.receipt("uid-a",id,false);var ack=service.receipt("uid-a",id,true);
            assertEquals(read.get("readAt"),ack.get("readAt"));assertEquals(ack,service.receipt("uid-a",id,true));assertNotNull(service.getOperatorHistory("uid-a").getFirst().get("acknowledgedAt"));
            db.collection("notificationHistory").document(id).update("target","another-operator").get();assertThrows(Exception.class,()->service.receipt("uid-a",id,true));
        }
    }
    private Map<String,Object> record(String tenant, String target, String title, String created) {
        return Map.of("tenantId",tenant,"target",target,"title",title,"message","Text","createdAt",created);
    }
    private Operator operator(String tenant) {
        Operator value = new Operator(); value.setId("operator-a"); value.setFirebaseUid("uid-a"); value.setTenantId(tenant); return value;
    }
    private Firestore firestore() {
        return FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator", new Date(Long.MAX_VALUE))))
                .build().getService();
    }
    private void authenticate(String tenant) {
        var auth = new UsernamePasswordAuthenticationToken("uid-a",null,List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        auth.setDetails(new FirebaseAuthenticationDetails("uid-a","operator@example.test","Operator",tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
