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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class AntennaHistoryEmulatorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void historyCombinesTenantScopedTasksAndReports() throws Exception {
        authenticate("tenant-a");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("antennas").document("asset-a").set(Map.of(
                    "tenantId", "tenant-a", "name", "A", "lat", 41.1, "lng", 16.8,
                    "status", "ATTIVA")).get();
            firestore.collection("tasks").document("task-a").set(Map.of(
                    "tenantId", "tenant-a", "antennaId", "asset-a", "status", "COMPLETED",
                    "createdAt", "2026-10-01T08:00:00Z", "updatedAt", "2026-10-01T09:00:00Z")).get();
            firestore.collection("maintenanceReports").document("report-a").set(Map.of(
                    "tenantId", "tenant-a", "antennaId", "asset-a", "status", "APPROVED",
                    "submittedAt", "2026-10-01T10:00:00Z", "materialsUsed", List.of())).get();

            var history = new AntennaService().getMaintenanceHistory("asset-a");

            assertEquals(2, history.size());
            assertEquals("REPORT", history.get(0).get("recordType"));
            assertEquals("TASK", history.get(1).get("recordType"));
        }
    }

    @Test
    void historyCannotReadAnotherTenantAsset() throws Exception {
        authenticate("tenant-a");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("antennas").document("asset-b").set(Map.of(
                    "tenantId", "tenant-b", "name", "B", "lat", 41.1, "lng", 16.8,
                    "status", "ATTIVA")).get();

            assertThrows(IllegalArgumentException.class, () -> new AntennaService().getMaintenanceHistory("asset-b"));
        }
    }

    @Test
    void directAssetLookupDoesNotReturnAnotherTenantDocument() throws Exception {
        authenticate("tenant-a");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("antennas").document("asset-b").set(Map.of(
                    "tenantId", "tenant-b", "name", "B", "code", "SITE-B",
                    "lat", 41.1, "lng", 16.8, "status", "ATTIVA")).get();

            AntennaService service = new AntennaService();
            assertThrows(IllegalArgumentException.class, () -> service.getById("asset-b"));
            assertThrows(IllegalArgumentException.class, () -> service.findByCodeOrId("asset-b"));
            assertThrows(IllegalArgumentException.class, () -> service.findByCodeOrId("SITE-B"));
        }
    }

    private void authenticate(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "operator", "operator@example.test", "Operator", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Firestore emulatorFirestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }
}
