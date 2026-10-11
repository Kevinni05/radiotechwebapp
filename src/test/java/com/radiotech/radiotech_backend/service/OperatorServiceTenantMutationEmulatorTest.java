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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class OperatorServiceTenantMutationEmulatorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fcmAndLastSeenMutationsCannotCrossTenant() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try (Firestore firestore = FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("operators").document("operator-b").set(Map.of(
                    "tenantId", "tenant-b",
                    "fcmTokens", List.of("existing-token"))).get();

            OperatorService operatorService = new OperatorService();
            assertThrows(SecurityException.class,
                    () -> operatorService.addFcmToken("operator-b", "new-token"));
            assertThrows(SecurityException.class,
                    () -> operatorService.removeFcmToken("operator-b", "existing-token"));
            assertThrows(SecurityException.class,
                    () -> operatorService.updateFcmTokens("operator-b", List.of("spoofed-token")));
            assertThrows(SecurityException.class,
                    () -> operatorService.updateLastSeen("operator-b"));

            var operator = firestore.collection("operators").document("operator-b").get().get();
            assertEquals(List.of("existing-token"), operator.get("fcmTokens"));
            assertNull(operator.get("lastSeen"));
        }
    }

    @Test
    void logoutRemovesOneDeviceWithoutDiscardingOtherTokens() throws Exception {
        String tenant = "device-test-" + java.util.UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("user-a", null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        auth.setDetails(new FirebaseAuthenticationDetails("user-a", null, null, tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator", new Date(Long.MAX_VALUE))))
                .build().getService(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            var record = db.collection("operators").document("device-" + java.util.UUID.randomUUID());
            record.set(Map.of("tenantId", tenant, "fcmTokens", List.of("phone", "tablet"))).get();
            var service = new OperatorService();
            service.addFcmToken(record.getId(), "new-phone");
            service.removeFcmToken(record.getId(), "phone");
            service.removeFcmToken(record.getId(), "phone");
            assertEquals(List.of("tablet", "new-phone"), record.get().get().get("fcmTokens"));
        }
    }

    @Test
    void selfRegistrationIdentityCannotResolveAnotherUsersOperatorByUid() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                "attacker-uid", null, List.of(new SimpleGrantedAuthority("ROLE_NONE")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "attacker-uid", "attacker@example.test", "Attacker", null));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try (Firestore firestore = FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("operators").document("operator-b").set(Map.of(
                    "tenantId", "tenant-b",
                    "firebaseUid", "victim-uid",
                    "fullName", "Victim")).get();

            assertThrows(SecurityException.class,
                    () -> new OperatorService().getByFirebaseUid("victim-uid"));
            assertNull(new OperatorService().getByFirebaseUid("attacker-uid"));
        }
    }
}
