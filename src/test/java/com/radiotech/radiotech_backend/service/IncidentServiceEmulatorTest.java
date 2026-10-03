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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class IncidentServiceEmulatorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lifecycleIsTenantScopedAndRejectsIllegalJumps() throws Exception {
        authenticate("tenant-a");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            IncidentService service = new IncidentService(mock(AuditService.class));
            var incident = service.create("Link down", "Core link unavailable", "HIGH", null, "asset-a", null,
                    null, "actor-a");
            String incidentId = incident.getId();

            assertEquals("DETECTED", incident.getStatus());
            assertThrows(IllegalArgumentException.class,
                    () -> service.transition(incidentId, "RESOLVED", "actor-a"));

            for (String status : List.of("ACKNOWLEDGED", "ASSIGNED", "INVESTIGATING", "MITIGATED")) {
                incident = service.transition(incident.getId(), status, "actor-a");
            }
            String resolvingId = incident.getId();
            assertThrows(IllegalArgumentException.class,
                    () -> service.transition(resolvingId, "RESOLVED", "actor-a"));
            incident = service.transition(incident.getId(), "RESOLVED", "actor-a", "Power module fault", "Module replaced");
            assertEquals("Power module fault", incident.getRootCause());
            assertEquals("Module replaced", incident.getResolution());
            assertEquals(6, incident.getTimeline().size());
            incident = service.transition(incident.getId(), "POST_MORTEM", "actor-a");
            incident = service.transition(incident.getId(), "CLOSED", "actor-a");
            assertEquals("CLOSED", incident.getStatus());
        }
    }

    @Test
    void crossTenantIncidentCannotTransition() throws Exception {
        authenticate("tenant-b");
        try (Firestore firestore = emulatorFirestore();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            String id = firestore.collection("incidents").document("incident-a").getId();
            firestore.collection("incidents").document(id).set(Map.of(
                    "id", id, "tenantId", "tenant-a", "title", "A", "status", "DETECTED",
                    "severity", "LOW", "createdAt", "2026-10-01T00:00:00Z")).get();

            IncidentService service = new IncidentService(mock(AuditService.class));
            assertThrows(IllegalArgumentException.class,
                    () -> service.transition(id, "ACKNOWLEDGED", "actor-b"));
        }
    }

    private void authenticate(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager", "manager@example.test", "Manager", tenantId));
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
