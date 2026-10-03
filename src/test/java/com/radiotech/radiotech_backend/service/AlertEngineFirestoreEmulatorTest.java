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

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class AlertEngineFirestoreEmulatorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void evaluatesPersistedTenantDataDeduplicatesAndResolvesAlerts() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String otherTenantId = "tenant-" + UUID.randomUUID();
        String taskId = UUID.randomUUID().toString();
        String atRiskTaskId = UUID.randomUUID().toString();
        String inventoryId = UUID.randomUUID().toString();
        String incidentId = UUID.randomUUID().toString();
        authenticate(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);

            firestore.collection("tasks").document(taskId).set(Map.of(
                    "tenantId", tenantId,
                    "title", "Replace radio",
                    "status", "IN_PROGRESS",
                    "dueAt", Instant.now().minusSeconds(60).toString())).get();
            firestore.collection("tasks").document(atRiskTaskId).set(Map.of(
                    "tenantId", tenantId,
                    "title", "Inspect antenna",
                    "status", "ASSIGNED",
                    "dueAt", Instant.now().plusSeconds(1800).toString())).get();
            firestore.collection("inventory").document(inventoryId).set(Map.of(
                    "tenantId", tenantId,
                    "name", "Power module",
                    "quantity", 1,
                    "minimumThreshold", 2)).get();
            firestore.collection("incidents").document(incidentId).set(Map.of(
                    "tenantId", tenantId,
                    "title", "Signal loss",
                    "severity", "HIGH",
                    "status", "DETECTED")).get();
            firestore.collection("incidents").document(UUID.randomUUID().toString()).set(Map.of(
                    "tenantId", otherTenantId,
                    "title", "Foreign incident",
                    "severity", "CRITICAL",
                    "status", "DETECTED")).get();

            AuditService audit = mock(AuditService.class);
            AlertEngineService engine = new AlertEngineService(audit, 60);
            Map<String, Object> first = engine.evaluate();
            Map<String, Object> second = engine.evaluate();

            assertEquals(4, first.get("created"));
            assertEquals(4, first.get("active"));
            assertEquals(0, second.get("created"));
            assertEquals(0, second.get("updated"));
            assertEquals(4, firestore.collection("alerts").whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("generated", true).get().get().size());
            var slaAlert = firestore.collection("alerts").whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("sourceId", taskId).get().get().getDocuments().get(0);
            assertEquals("CRITICA", slaAlert.getString("priorita"));
            assertTrue(Boolean.TRUE.equals(slaAlert.getBoolean("active")));
            var atRiskAlert = firestore.collection("alerts").whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("sourceId", atRiskTaskId).get().get().getDocuments().get(0);
            assertEquals("MEDIA", atRiskAlert.getString("priorita"));

            firestore.collection("tasks").document(atRiskTaskId).update(
                    "dueAt", Instant.now().minusSeconds(60).toString()).get();
            Map<String, Object> escalated = engine.evaluate();
            assertEquals(1, escalated.get("updated"));
            assertEquals("CRITICA", firestore.collection("alerts").document(atRiskAlert.getId())
                    .get().get().getString("priorita"));

            firestore.collection("tasks").document(taskId).update(
                    "dueAt", Instant.now().plusSeconds(7200).toString()).get();
            firestore.collection("tasks").document(atRiskTaskId).update(
                    "dueAt", Instant.now().plusSeconds(7200).toString()).get();
            firestore.collection("inventory").document(inventoryId).update("quantity", 5).get();
            firestore.collection("incidents").document(incidentId).update("status", "RESOLVED").get();
            Map<String, Object> resolved = engine.evaluate();

            assertEquals(0, resolved.get("active"));
            assertEquals(4, resolved.get("resolved"));
            var allAlerts = firestore.collection("alerts").whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("generated", true).get().get().getDocuments();
            assertTrue(allAlerts.stream().allMatch(alert -> Boolean.FALSE.equals(alert.getBoolean("active"))));
            assertFalse(firestore.collection("alerts").whereEqualTo("tenantId", otherTenantId)
                    .whereEqualTo("generated", true).get().get().size() > 0);
            verify(audit, times(4)).record(eq("ALERT_GENERATED"), eq(tenantId),
                    eq("SYSTEM"), eq("ALERT"), org.mockito.ArgumentMatchers.anyString(),
                    eq("SUCCESS"), eq(null), org.mockito.ArgumentMatchers.anyMap());
            verify(audit, times(1)).record(eq("ALERT_UPDATED"), eq(tenantId),
                    eq("SYSTEM"), eq("ALERT"), org.mockito.ArgumentMatchers.anyString(),
                    eq("SUCCESS"), eq(null), org.mockito.ArgumentMatchers.anyMap());
        }
    }

    private Firestore emulatorFirestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build()
                .getService();
    }

    private void authenticate(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_NETWORK_MANAGER")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-" + tenantId, "manager@example.test", "Manager", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
