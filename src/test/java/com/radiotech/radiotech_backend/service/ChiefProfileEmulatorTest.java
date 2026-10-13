package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.cloud.FirestoreClient;
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
@EnabledIfEnvironmentVariable(named = "FIREBASE_AUTH_EMULATOR_HOST", matches = ".+")
class ChiefProfileEmulatorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void firstChiefLoginCreatesTheProfileFromAssignedClaimsWithoutBootstrapOrClaimWrites() throws Exception {
        verifyProfileCreation(false);
    }
    @Test void aDifferentAuthenticatedTenantCannotReassignTheNewChiefProfile() throws Exception {
        verifyProfileCreation(true);
    }
    private void verifyProfileCreation(boolean conflictingTenant) throws Exception {
        var credentials = GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE)));
        var app = FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId("demo-radiotech")
                .setCredentials(credentials).build(), "chief-profile-" + UUID.randomUUID());
        var auth = FirebaseAuth.getInstance(app);
        String tenant = "tenant-" + UUID.randomUUID();
        UserRecord user = auth.createUser(new UserRecord.CreateRequest().setEmail("chief-" + UUID.randomUUID() + "@example.test")
                .setPassword("FixturePassword123!").setEmailVerified(true));
        var claims = Map.<String,Object>of("role", "CHIEF_EXECUTIVE", "tenantId", tenant, "mfaRequired", true);
        auth.setCustomUserClaims(user.getUid(), claims);
        if (conflictingTenant) {
            var actor = new UsernamePasswordAuthenticationToken("other-chief", null, List.of(new SimpleGrantedAuthority("ROLE_CHIEF_EXECUTIVE")));
            actor.setDetails(new FirebaseAuthenticationDetails("other-chief", null, "Other", "other-tenant"));
            SecurityContextHolder.getContext().setAuthentication(actor);
        }
        try (var db = FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(credentials).build().getService();
                var database = mockStatic(FirestoreClient.class); var authentication = mockStatic(FirebaseAuth.class)) {
            database.when(FirestoreClient::getFirestore).thenReturn(db);
            authentication.when(FirebaseAuth::getInstance).thenReturn(auth);
            var service = new CapoService();
            if (conflictingTenant) {
                assertThrows(SecurityException.class, () -> service.ensureCapoProfile(user.getUid()));
                assertFalse(db.collection("users").document(user.getUid()).get().get().exists());
            } else {
                var profile = service.ensureCapoProfile(user.getUid());
                assertEquals(tenant, profile.getTenantId());
                assertEquals(user.getUid(), profile.getUid());
                assertEquals(user.getEmail(), profile.getEmail());
                assertEquals(claims, auth.getUser(user.getUid()).getCustomClaims());
            }
        } finally {
            auth.deleteUser(user.getUid());
            app.delete();
        }
    }
}
