package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class IdentityRecoveryEmulatorTest {
    @Test void confirmsLostCompletionWithoutReplayingClaimsAndLeavesChangedAccountsForReview() throws Exception {
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator", new Date(Long.MAX_VALUE))))
                .build().getService()) {
            var auth = mock(FirebaseAuth.class); var user = mock(UserRecord.class);
            String tenant = UUID.randomUUID().toString();
            when(user.getUid()).thenReturn("target");
            when(user.getCustomClaims()).thenReturn(Map.of("tenantId", tenant, "role", "ENGINEER"));
            when(auth.getUser("target")).thenReturn(user);
            Instant now = Instant.now();
            var event = db.collection("auditLogs").document();
            event.set(Map.of("action", "IDENTITY_BIND", "tenantId", tenant, "resourceId", "target",
                    "result", "PENDING", "timestamp", now.minusSeconds(120).toString(),
                    "externalSystem", "FIREBASE_AUTH", "after", user.getCustomClaims())).get();
            assertTrue(IdentityAuditRecovery.recover(db, auth, event, now));
            assertEquals("SUCCESS", event.get().get().getString("result"));
            assertNotNull(event.get().get().getString("reconciledAt"));
            assertFalse(IdentityAuditRecovery.recover(db, auth, event, now));
            verify(auth, times(1)).revokeRefreshTokens("target");
            verify(auth, never()).setCustomUserClaims(anyString(), anyMap());

            var changed = db.collection("auditLogs").document();
            changed.set(Map.of("action", "IDENTITY_BIND", "tenantId", tenant, "resourceId", "target",
                    "result", "PENDING", "timestamp", now.minusSeconds(120).toString(),
                    "externalSystem", "FIREBASE_AUTH", "after", Map.of("tenantId", tenant, "role", "OPERATOR"))).get();
            assertFalse(IdentityAuditRecovery.recover(db, auth, changed, now));
            assertEquals("PENDING", changed.get().get().getString("result"));
            assertEquals("CLAIMS_CHANGED", changed.get().get().getString("recoveryReason"));
            assertFalse(IdentityAuditRecovery.recover(db, auth, changed, now));
            when(user.getCustomClaims()).thenReturn(Map.of("tenantId", "other", "role", "OPERATOR"));
            assertFalse(IdentityAuditRecovery.recover(db, auth, changed, now.plusSeconds(4000)));
            assertEquals("TENANT_CHANGED", changed.get().get().getString("recoveryReason"));
            verify(auth, times(1)).revokeRefreshTokens("target");
        }
    }
}
