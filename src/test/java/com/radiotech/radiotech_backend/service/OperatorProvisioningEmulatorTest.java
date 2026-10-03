package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class OperatorProvisioningEmulatorTest {

    @Test
    void existingCrossTenantProfileIsRejectedBeforeFirebasePasswordUpdate() throws Exception {
        try (Firestore firestore = FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
                var firestoreClient = mockStatic(FirestoreClient.class);
                var firebaseAuthStatic = mockStatic(FirebaseAuth.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("operators").document("operator-b").set(Map.of(
                    "tenantId", "tenant-b",
                    "firebaseUid", "existing-uid",
                    "fullName", "Tenant B operator")).get();

            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            UserRecord existingUser = mock(UserRecord.class);
            firebaseAuthStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.getUserByEmail("same@example.test")).thenReturn(existingUser);
            when(existingUser.getUid()).thenReturn("existing-uid");
            when(existingUser.getCustomClaims()).thenReturn(Map.of("role", "OPERATOR"));

            OperatorService service = new OperatorService();

            assertThrows(SecurityException.class, () -> service.generateOperatorWithCredentials(
                    "tenant-a", "Same User", "same@example.test", null, null, null, "SafePass!234"));

            verify(firebaseAuth, never()).updateUser(any(UserRecord.UpdateRequest.class));
            var operator = firestore.collection("operators").document("operator-b").get().get();
            assertEquals("tenant-b", operator.getString("tenantId"));
        }
    }
}