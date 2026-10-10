package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.*;
import com.radiotech.radiotech_backend.model.Operator;
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
class NotificationDeliveryEmulatorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private SendResponse success() { var result = mock(SendResponse.class); when(result.isSuccessful()).thenReturn(true); return result; }
    private SendResponse failure(MessagingErrorCode code) {
        var result = mock(SendResponse.class); var error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(code); when(result.getException()).thenReturn(error); return result;
    }
    private BatchResponse batch(SendResponse... responses) {
        var result = mock(BatchResponse.class); when(result.getResponses()).thenReturn(List.of(responses)); return result;
    }
    @Test void partialDeliveryRetriesOnlyFailedRecipientsAndRemovesOnlyUnregisteredTokens() throws Exception {
        String tenant = "push-test-" + UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("manager", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(new FirebaseAuthenticationDetails("manager", null, "Manager", tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
        Operator operator = new Operator(); operator.setId(UUID.randomUUID().toString()); operator.setTenantId(tenant);
        operator.setFcmTokens(List.of("accepted-token", "retry-token"));
        var operators = mock(OperatorService.class); when(operators.getById(operator.getId())).thenReturn(operator);
        when(operators.getAllOperators()).thenReturn(List.of(operator));
        var messaging = mock(FirebaseMessaging.class);
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator", new Date(Long.MAX_VALUE))))
                .build().getService(); var database = mockStatic(FirestoreClient.class); var fcm = mockStatic(FirebaseMessaging.class)) {
            database.when(FirestoreClient::getFirestore).thenReturn(db); fcm.when(FirebaseMessaging::getInstance).thenReturn(messaging);
            var ref = db.collection("notificationHistory").document(UUID.randomUUID().toString());
            ref.set(Map.of("tenantId", tenant, "target", operator.getId(), "title", "Test", "message", "Text", "deliveryStatus", "PENDING")).get();
            var partialBatch = batch(success(), failure(MessagingErrorCode.UNAVAILABLE));
            var successfulRetry = batch(success());
            when(messaging.sendEachForMulticast(any())).thenReturn(partialBatch, successfulRetry);
            var notifications = new NotificationService(operators);
            notifications.deliverRecorded(ref.getId());
            assertEquals("PARTIAL", ref.get().get().getString("deliveryStatus"));
            assertEquals(1L, ref.get().get().getLong("delivered"));
            ref.update("nextAttemptAt", "1970-01-01T00:00:00Z").get();
            notifications.deliverRecorded(ref.getId());
            assertEquals("SENT", ref.get().get().getString("deliveryStatus"));
            assertEquals(2L, ref.get().get().getLong("delivered"));
            notifications.deliverRecorded(ref.getId());
            verify(messaging, times(2)).sendEachForMulticast(any());
            assertEquals(2, ref.collection("pushDeliveries").get().get().size());

            operator.setFcmTokens(List.of("expired-token"));
            var stored = db.collection("operators").document(operator.getId());
            stored.set(Map.of("tenantId", tenant, "fcmTokens", List.of("expired-token", "fresh-token"))).get();
            var expired = db.collection("notificationHistory").document(UUID.randomUUID().toString());
            expired.set(Map.of("tenantId", tenant, "target", operator.getId(), "title", "Test", "message", "Text", "deliveryStatus", "PENDING")).get();
            var invalidBatch = batch(failure(MessagingErrorCode.UNREGISTERED));
            when(messaging.sendEachForMulticast(any())).thenReturn(invalidBatch);
            notifications.deliverRecorded(expired.getId());
            assertEquals("DEAD_LETTER", expired.get().get().getString("deliveryStatus"));
            assertEquals(List.of("fresh-token"), stored.get().get().get("fcmTokens"));
        }
    }
}
