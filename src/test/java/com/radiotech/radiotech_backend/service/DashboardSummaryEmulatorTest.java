package com.radiotech.radiotech_backend.service;

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
import java.util.Map;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class DashboardSummaryEmulatorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void projectedSummariesPreserveLegacySemanticsAndRefreshWithoutCrossTenantLeakage() throws Exception {
        String a = "summary-a-" + UUID.randomUUID();
        String b = "summary-b-" + UUID.randomUUID();
        try (Firestore db = FirestoreOptions.getDefaultInstance().toBuilder().setProjectId("demo-radiotech")
                .setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST")).build().getService();
                var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("antennas").document().set(Map.of("tenantId", a, "status", "attiva", "name", "Private name")).get();
            db.collection("operators").document().set(Map.of("tenantId", a, "status", "ACTIVE")).get();
            var task = db.collection("tasks").document();
            task.set(Map.of("tenantId", a, "status", " in corso ", "dueAt", "2000-01-01T00:00:00Z")).get();
            db.collection("interventions").document().set(Map.of("tenantId", a, "description", "Large private body")).get();
            db.collection("inventory").document().set(Map.of("tenantId", a, "quantity", 1, "minimumThreshold", 2)).get();
            db.collection("maintenanceReports").document().set(Map.of("tenantId", a, "status", "SUBMITTED")).get();
            db.collection("maintenanceReports").document().set(Map.of("tenantId", a, "status", "SUBMITTED", "removedAt", "2026-01-01")).get();
            TenantDashboardCache cache = new TenantDashboardCache();
            DashboardService dashboard = new DashboardService(cache);
            authenticate(a);
            var first = dashboard.getDashboardStats();
            assertEquals(1, first.get("activeAntennas"));
            assertEquals(1, first.get("activeOperators"));
            assertEquals(1, first.get("interventions"));
            assertEquals(1, first.get("lowStock"));
            assertEquals(1L, first.get("pendingReports"));
            assertEquals(1, ((Map<?, ?>) first.get("taskStats")).get("inProgress"));
            assertEquals(1, ((Map<?, ?>) first.get("taskStats")).get("slaBreached"));
            assertNotNull(first.get("asOf"));
            task.update("status", "COMPLETATO").get();
            assertSame(first, dashboard.getDashboardStats());
            authenticate(b);
            assertEquals(0, dashboard.getDashboardStats().get("tasks"));
            cache.invalidate(a);
            authenticate(a);
            var updated = dashboard.getDashboardStats();
            assertEquals(0, updated.get("activeTasks"));
            assertEquals(1, ((Map<?, ?>) updated.get("taskStats")).get("completed"));
        }
    }

    private void authenticate(String tenant) {
        var auth = new UsernamePasswordAuthenticationToken("manager", null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
        auth.setDetails(new FirebaseAuthenticationDetails("manager", "manager@example.test", "Manager", tenant));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
