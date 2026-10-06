package com.radiotech.radiotech_backend.service;
import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.dto.WorkforceRequests;
import com.radiotech.radiotech_backend.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class WorkforceEmulatorTest {
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }
    @Test void shiftRetryDoesNotRegressStateAndConcurrentVersionsConflict() throws Exception {
        String tenant = "people-" + UUID.randomUUID(); identity("operator-a", "OPERATOR", tenant);
        try (var db = database(); var firebase = mockStatic(FirestoreClient.class)) {
            firebase.when(FirestoreClient::getFirestore).thenReturn(db);
            var service = new WorkforceService(mock(AuditService.class));
            var start = new WorkforceRequests.Shift("START", "READY", 0L, UUID.randomUUID().toString());
            assertEquals("ACTIVE", service.changeShift(start).get("status"));
            assertEquals("BREAK", service.changeShift(new WorkforceRequests.Shift("BREAK", "NEEDS_BREAK", 1L, UUID.randomUUID().toString())).get("status"));
            assertEquals("BREAK", service.changeShift(start).get("status"));
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.changeShift(new WorkforceRequests.Shift("END", "READY", 1L, UUID.randomUUID().toString()))).getStatusCode().value());
            assertThrows(SecurityException.class, service::shifts);
            identity("operator-b", "OPERATOR", tenant); assertEquals("OFF_DUTY", service.myShift().get("status"));
            identity("manager", "ADMIN", "other-" + tenant); assertTrue(service.shifts().isEmpty());
        }
    }
    @Test void signalsArePersonalAndOnlySameTenantManagersCanResolveThem() throws Exception {
        String tenant = "safety-" + UUID.randomUUID(); identity("operator-a", "OPERATOR", tenant);
        try (var db = database(); var firebase = mockStatic(FirestoreClient.class)) {
            firebase.when(FirestoreClient::getFirestore).thenReturn(db);
            var service = new WorkforceService(mock(AuditService.class));
            var request = new WorkforceRequests.Signal("HAZARD", "HIGH", "Cavo esposto", null, UUID.randomUUID().toString());
            var created = service.submitSignal(request); String id = created.get("id").toString();
            assertEquals(id, service.submitSignal(request).get("id")); assertEquals(1, service.signals().size()); assertFalse(service.signals().getFirst().containsKey("fingerprint"));
            assertThrows(SecurityException.class, () -> service.review(id, new WorkforceRequests.Review("ACKNOWLEDGED", "", 0L)));
            identity("operator-b", "OPERATOR", tenant); assertTrue(service.signals().isEmpty());
            identity("manager-b", "ADMIN", tenant + "-other"); assertThrows(IllegalArgumentException.class, () -> service.review(id, new WorkforceRequests.Review("ACKNOWLEDGED", "", 0L)));
            identity("manager-a", "ADMIN", tenant); assertEquals("ACKNOWLEDGED", service.review(id, new WorkforceRequests.Review("ACKNOWLEDGED", "", 0L)).get("status"));
            assertThrows(IllegalArgumentException.class, () -> service.review(id, new WorkforceRequests.Review("RESOLVED", "", 1L)));
            assertEquals("RESOLVED", service.review(id, new WorkforceRequests.Review("RESOLVED", "Area messa in sicurezza", 1L)).get("status"));
        }
    }
    @Test void personalInsightsDoNotExposeAnotherOperatorsTasksOrAssets() throws Exception {
        String tenant = "insights-" + UUID.randomUUID(); identity("uid-a", "OPERATOR", tenant);
        try (var db = database(); var firebase = mockStatic(FirestoreClient.class)) {
            firebase.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("tasks").document(tenant + "-a").set(Map.of("tenantId", tenant, "operatorId", "op-a", "operatorFirebaseUid", "uid-a", "antennaId", tenant + "-asset-a", "status", "ASSIGNED")).get();
            db.collection("tasks").document(tenant + "-b").set(Map.of("tenantId", tenant, "operatorId", "op-b", "antennaId", tenant + "-asset-b", "status", "ASSIGNED")).get();
            for (String suffix : List.of("a", "b")) db.collection("antennas").document(tenant + "-asset-" + suffix).set(Map.of("tenantId", tenant, "name", "Asset " + suffix, "status", "OFFLINE")).get();
            var operators = mock(OperatorService.class); var operator = new com.radiotech.radiotech_backend.model.Operator(); operator.setId("op-a"); when(operators.getByFirebaseUid("uid-a")).thenReturn(operator);
            var result = new OperationalInsightsService(operators).insights();
            assertEquals("PERSONAL", result.get("scope")); assertEquals(1L, result.get("activeTasks")); assertEquals(1, result.get("assetsObserved")); assertFalse(result.toString().contains("Asset b"));
        }
    }
    private Firestore database() { return FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE)))).build().getService(); }
    private void identity(String uid, String role, String tenant) { var auth = new UsernamePasswordAuthenticationToken(uid, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))); auth.setDetails(new FirebaseAuthenticationDetails(uid, "test@example.test", "Test", tenant)); SecurityContextHolder.getContext().setAuthentication(auth); }
}
