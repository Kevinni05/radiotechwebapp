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
                        var dashboardOperators = new DashboardService().getOperators();
                        for (var record : dashboardOperators) {
                                org.junit.jupiter.api.Assertions.assertFalse(record.containsKey("qrCodeToken"));
                                org.junit.jupiter.api.Assertions.assertFalse(record.containsKey("fcmTokens"));
                                assertEquals("tenant-a", record.get("tenantId"));
                        }
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
        void regenerationPersistsNewTokenAndClearsUsedState() throws Exception {
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
                        var reference = firestore.collection("operators").document("operator-regenerate");
                        reference.set(Map.of("tenantId", "tenant-a", "status", "ATTIVO",
                                        "qrCodeToken", "old-used-badge", "qrUsedAt", "2026-01-01T00:00:00Z",
                                        "qrLastUsedAt", "2026-01-01T00:00:00Z",
                                        "qrExpiresAt", "2026-01-01T00:00:00Z", "fullName", "Badge User")).get();
                        OperatorService service = new OperatorService();
                        var otherTenant = firestore.collection("operators").document("operator-regenerate-other-tenant");
                        otherTenant.set(Map.of("tenantId", "tenant-b", "qrCodeToken", "other-tenant-badge")).get();
                        assertThrows(SecurityException.class,
                                        () -> service.regenerateQrToken("operator-regenerate-other-tenant"));
                        assertEquals("other-tenant-badge", otherTenant.get().get().getString("qrCodeToken"));
                        String token = service.regenerateQrToken("operator-regenerate");
                        var saved = reference.get().get();
                        assertEquals(token, saved.getString("qrCodeToken"));
                        org.junit.jupiter.api.Assertions.assertNull(saved.getString("qrUsedAt"));
                        org.junit.jupiter.api.Assertions.assertNull(saved.getString("qrLastUsedAt"));
                        org.junit.jupiter.api.Assertions.assertTrue(java.time.Instant.parse(
                                        saved.getString("qrExpiresAt")).isAfter(java.time.Instant.now()));
                        assertThrows(IllegalArgumentException.class, () -> service.consumeQrToken("old-used-badge"));
                        service.validateQrToken(token);
                        org.junit.jupiter.api.Assertions.assertNull(reference.get().get().getString("qrUsedAt"));
                        assertEquals("operator-regenerate", service.consumeQrToken(token).getId());
                        assertThrows(IllegalArgumentException.class, () -> service.consumeQrToken(token));
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
