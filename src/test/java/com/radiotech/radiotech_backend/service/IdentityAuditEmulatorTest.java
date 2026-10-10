package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.auth.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.controller.ProIdentityController;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class IdentityAuditEmulatorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void identityMutationCannotRunBeforeItsDurableIntentAndAnInterruptedActionRemainsPending() throws Exception {
        String tenant = "identity-audit-" + UUID.randomUUID();
        var actor = new UsernamePasswordAuthenticationToken("administrator", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        actor.setDetails(new FirebaseAuthenticationDetails("administrator", null, "Admin", tenant));
        SecurityContextHolder.getContext().setAuthentication(actor);
        var firebase = mock(FirebaseAuth.class); var user = mock(UserRecord.class);
        when(user.getCustomClaims()).thenReturn(Map.of("tenantId", tenant, "role", "OPERATOR", "unrelated", "not-retained"));
        when(firebase.getUser("target")).thenReturn(user);
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator", new Date(Long.MAX_VALUE))))
                .build().getService(); var database = mockStatic(FirestoreClient.class); var auth = mockStatic(FirebaseAuth.class)) {
            database.when(FirestoreClient::getFirestore).thenReturn(db); auth.when(FirebaseAuth::getInstance).thenReturn(firebase);
            doAnswer(invocation -> {
                var events = db.collection("auditLogs").whereEqualTo("tenantId", tenant).get().get().getDocuments();
                assertEquals(1, events.size());
                assertEquals("PENDING", events.getFirst().getString("result"));
                assertTrue(events.getFirst().getBoolean("reconciliationRequired"));
                assertFalse(((Map<?, ?>) events.getFirst().get("before")).containsKey("unrelated"));
                throw new IllegalStateException("Simulated interruption after intent");
            }).when(firebase).revokeRefreshTokens("target");
            var controller = new ProIdentityController(new AuditService());
            assertThrows(IllegalStateException.class, () -> controller.revoke("target"));
            assertEquals("PENDING", db.collection("auditLogs").whereEqualTo("tenantId", tenant).get().get().getDocuments().getFirst().getString("result"));
            doNothing().when(firebase).revokeRefreshTokens("target");
            assertEquals(true, controller.revoke("target").get("revoked"));
            var events = db.collection("auditLogs").whereEqualTo("tenantId", tenant).get().get().getDocuments();
            assertEquals(2, events.size());
            assertEquals(1, events.stream().filter(event -> "SUCCESS".equals(event.getString("result"))).count());
            assertEquals(1, events.stream().filter(event -> "PENDING".equals(event.getString("result"))).count());
        }
    }
}
