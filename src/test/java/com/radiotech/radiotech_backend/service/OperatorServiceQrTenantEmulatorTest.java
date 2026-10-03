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

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class OperatorServiceQrTenantEmulatorTest {

        @AfterEach
        void clearSecurityContext() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void qrLookupIsTenantScoped() throws Exception {
                var authentication = new UsernamePasswordAuthenticationToken(
                                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
                authentication.setDetails(new FirebaseAuthenticationDetails(
                                "manager-a", "viewer@example.test", "Viewer", "tenant-a"));
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
                                        "qrCodeToken", "known-qr-token",
                                        "fullName", "Tenant B operator")).get();
                        firestore.collection("operators").document("operator-a").set(Map.of(
                                        "tenantId", "tenant-a",
                                        "qrCodeToken", "tenant-a-qr",
                                        "fullName", "Tenant A operator")).get();

                        OperatorService operatorService = new OperatorService();

                        assertThrows(IllegalArgumentException.class,
                                        () -> operatorService.findByQrToken("known-qr-token"));
                        Operator sameTenant = operatorService.findByQrToken("tenant-a-qr");
                        assertEquals("tenant-a", sameTenant.getTenantId());
                        assertEquals("Tenant A operator", sameTenant.getFullName());
                }
        }

        @Test
        void qrTokenIsSingleUse() throws Exception {
                var authentication = new UsernamePasswordAuthenticationToken(
                                "operator-a", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
                authentication.setDetails(new FirebaseAuthenticationDetails(
                                "operator-a", "operator@example.test", "Operator", "tenant-a"));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                try (Firestore firestore = FirestoreOptions.newBuilder()
                                .setProjectId("demo-radiotech")
                                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                                .setCredentials(GoogleCredentials.create(
                                                new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                                .build().getService();
                                var firestoreClient = mockStatic(FirestoreClient.class)) {
                        firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
                        firestore.collection("operators").document("operator-single-use").set(Map.of(
                                        "tenantId", "tenant-a",
                                        "status", "ATTIVO",
                                        "qrCodeToken", "single-use-qr",
                                        "qrExpiresAt", "2099-12-31T00:00:00Z",
                                        "fullName", "Single Use Operator")).get();

                        OperatorService operatorService = new OperatorService();

                        Operator firstUse = operatorService.consumeQrToken("single-use-qr");
                        assertEquals("operator-single-use", firstUse.getId());
                        assertThrows(IllegalArgumentException.class,
                                        () -> operatorService.consumeQrToken("single-use-qr"));
                }
        }

        @Test
        void qrLoginCanResolveTenantFromTheValidatedCredential() throws Exception {
                SecurityContextHolder.clearContext();

                try (Firestore firestore = FirestoreOptions.newBuilder()
                                .setProjectId("demo-radiotech")
                                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                                .setCredentials(GoogleCredentials.create(
                                                new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                                .build().getService();
                                var firestoreClient = mockStatic(FirestoreClient.class)) {
                        firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
                        firestore.collection("operators").document("operator-anonymous").set(Map.of(
                                        "tenantId", "tenant-a",
                                        "status", "ATTIVO",
                                        "qrCodeToken", "anonymous-login-qr",
                                        "qrExpiresAt", "2099-12-31T00:00:00Z",
                                        "fullName", "Anonymous Login Operator")).get();

                        Operator operator = new OperatorService().consumeQrToken("anonymous-login-qr");

                        assertEquals("operator-anonymous", operator.getId());
                        assertEquals("tenant-a", operator.getTenantId());
                        assertThrows(IllegalArgumentException.class,
                                        () -> new OperatorService().consumeQrToken("anonymous-login-qr"));
                }
        }

        @Test
        void expiredAndInactiveQrCredentialsAreRejected() throws Exception {
                var authentication = new UsernamePasswordAuthenticationToken(
                                "operator-a", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
                authentication.setDetails(new FirebaseAuthenticationDetails(
                                "operator-a", "operator@example.test", "Operator", "tenant-a"));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                try (Firestore firestore = FirestoreOptions.newBuilder()
                                .setProjectId("demo-radiotech")
                                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                                .setCredentials(GoogleCredentials.create(
                                                new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                                .build().getService();
                                var firestoreClient = mockStatic(FirestoreClient.class)) {
                        firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
                        firestore.collection("operators").document("operator-expired").set(Map.of(
                                        "tenantId", "tenant-a",
                                        "status", "ATTIVO",
                                        "qrCodeToken", "expired-qr",
                                        "qrExpiresAt", "2000-01-01T00:00:00Z",
                                        "fullName", "Expired Operator")).get();
                        firestore.collection("operators").document("operator-inactive").set(Map.of(
                                        "tenantId", "tenant-a",
                                        "status", "INATTIVO",
                                        "qrCodeToken", "inactive-qr",
                                        "qrExpiresAt", "2099-12-31T00:00:00Z",
                                        "fullName", "Inactive Operator")).get();

                        OperatorService operatorService = new OperatorService();
                        assertThrows(IllegalArgumentException.class,
                                        () -> operatorService.consumeQrToken("expired-qr"));
                        assertThrows(IllegalArgumentException.class,
                                        () -> operatorService.consumeQrToken("inactive-qr"));
                }
        }
}
