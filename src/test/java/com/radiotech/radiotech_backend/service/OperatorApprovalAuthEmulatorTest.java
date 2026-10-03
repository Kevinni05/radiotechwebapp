package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Assumptions;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "FIREBASE_AUTH_EMULATOR_HOST", matches = ".+")
class OperatorApprovalAuthEmulatorTest {

    private static FirebaseApp firebaseApp;

    @AfterAll
    static void deleteFirebaseApp() {
        if (firebaseApp != null) {
            firebaseApp.delete();
            firebaseApp = null;
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvalActivatesOperatorAfterTenantClaimsAreAssigned() throws Exception {
        assumeFirestoreEmulator();
        initializeFirebaseApp();
        String tenantId = "tenant-" + UUID.randomUUID();
        UserRecord user = createUser();
        String operatorId = UUID.randomUUID().toString();
        authenticateAsManager(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            seedPendingOperator(firestore, tenantId, operatorId, user.getUid());

            OperatorService service = new OperatorService();
            service.approveOperator(operatorId);

            assertEquals("ATTIVO", firestore.collection("operators")
                    .document(operatorId).get().get().getString("status"));
            Map<String, Object> claims = FirebaseAuth.getInstance(firebaseApp)
                    .getUser(user.getUid()).getCustomClaims();
            assertEquals("OPERATOR", claims.get("role"));
            assertEquals(tenantId, claims.get("tenantId"));
            assertEquals(operatorId, claims.get("operatorId"));
        }
    }

    @Test
    void tenantClaimConflictLeavesOperatorPending() throws Exception {
        assumeFirestoreEmulator();
        initializeFirebaseApp();
        String tenantId = "tenant-" + UUID.randomUUID();
        UserRecord user = createUser();
        FirebaseAuth.getInstance(firebaseApp).setCustomUserClaims(user.getUid(), Map.of(
                "role", "OPERATOR", "tenantId", "tenant-other", "operatorId", "old-operator"));
        String operatorId = UUID.randomUUID().toString();
        authenticateAsManager(tenantId);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            seedPendingOperator(firestore, tenantId, operatorId, user.getUid());

            OperatorService service = new OperatorService();
            assertThrows(SecurityException.class, () -> service.approveOperator(operatorId));

            assertEquals("IN_ATTESA", firestore.collection("operators")
                    .document(operatorId).get().get().getString("status"));
            assertEquals("tenant-other", FirebaseAuth.getInstance(firebaseApp)
                    .getUser(user.getUid()).getCustomClaims().get("tenantId"));
        }
    }

    private void initializeFirebaseApp() {
        if (firebaseApp != null)
            return;
        firebaseApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setProjectId("demo-radiotech")
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build());
    }

    private void assumeFirestoreEmulator() {
        Assumptions.assumeTrue(System.getenv("FIRESTORE_EMULATOR_HOST") != null,
                "Firestore Emulator is required for operator approval integration tests.");
    }

    private UserRecord createUser() throws Exception {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        return FirebaseAuth.getInstance(firebaseApp).createUser(new UserRecord.CreateRequest()
                .setEmail("approval-" + nonce + "@example.test")
                .setPassword("SafePass!234"));
    }

    private Firestore emulatorFirestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }

    private void authenticateAsManager(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void seedPendingOperator(Firestore firestore, String tenantId,
            String operatorId, String firebaseUid) throws Exception {
        firestore.collection("operators").document(operatorId).set(Map.of(
                "tenantId", tenantId,
                "firebaseUid", firebaseUid,
                "fullName", "Pending Operator",
                "email", "pending@example.test",
                "role", "OPERATOR",
                "status", "IN_ATTESA")).get();
    }
}