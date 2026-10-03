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
class ManutenzioneServiceAlertTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAlertStoresTenantScopedIncidentData() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
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

            firestore.collection("alerts").document("foreign-alert").set(Map.of(
                    "tenantId", "tenant-b",
                    "antennaId", "ANT-B",
                    "operatore", "Other",
                    "descrizione", "wrong tenant",
                    "priorita", "CRITICA",
                    "letto", false,
                    "timestamp", "2026-01-01T00:00:00Z")).get();

            ManutenzioneService service = new ManutenzioneService();
            Map<String, Object> created = service.createAlert(
                    "ANT-A",
                    "Sistema fuori range",
                    "CRITICA",
                    "operator-a");

            assertEquals("tenant-a", created.get("tenantId"));
            assertEquals("ANT-A", created.get("antennaId"));
            assertEquals("CRITICA", created.get("priorita"));

            assertEquals(1, service.getAllAlerts().size());
            assertEquals("Sistema fuori range", service.getAllAlerts().get(0).get("descrizione"));
            assertEquals(1L, service.countUnreadAlerts());

            Map<String, Object> readAlert = service.markAlertAsRead((String) created.get("id"));
            assertEquals(Boolean.TRUE, readAlert.get("letto"));
            assertEquals(0L, service.countUnreadAlerts());
        }
    }

    @Test
    void createAlertRejectsMissingCriticalFields() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ManutenzioneService service = new ManutenzioneService();

        assertThrows(IllegalArgumentException.class,
                () -> service.createAlert(null, "desc", "MEDIA", "operator-a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAlert("ANT-A", "", "MEDIA", "operator-a"));
    }
}
