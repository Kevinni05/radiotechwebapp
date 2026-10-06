package com.radiotech.radiotech_backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@EnabledIfEnvironmentVariable(named = "FIREBASE_AUTH_EMULATOR_HOST", matches = ".+")
class FirebaseAuthenticationAuthEmulatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static FirebaseApp firebaseApp;

    @AfterAll
    static void deleteFirebaseApp() {
        SecurityContextHolder.clearContext();
        if (firebaseApp != null) {
            firebaseApp.delete();
            firebaseApp = null;
        }
    }

    @Test
    void verifiedEmulatorTokenCarriesTenantClaimsAndUnassignedTokenFailsClosed() throws Exception {
        firebaseApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setProjectId("demo-radiotech")
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build());

        String email = "operator-" + System.nanoTime() + "@example.test";
        String password = "SafePass!234";
        JsonNode created = authRequest("accounts:signUp", Map.of(
                "email", email,
                "password", password,
                "returnSecureToken", true));
        String uid = created.get("localId").asText();
        String unassignedToken = created.get("idToken").asText();

        MockHttpServletRequest unassignedRequest = new MockHttpServletRequest("GET", "/api/operator/me");
        unassignedRequest.addHeader("Authorization", "Bearer " + unassignedToken);
        MockHttpServletResponse unassignedResponse = new MockHttpServletResponse();
        MockFilterChain unassignedChain = new MockFilterChain();
        new FirebaseAuthenticationFilter().doFilter(
                unassignedRequest, unassignedResponse, unassignedChain);

        assertEquals(403, unassignedResponse.getStatus());
        assertNull(unassignedChain.getRequest());

        FirebaseAuth.getInstance(firebaseApp).setCustomUserClaims(uid, Map.of(
                "tenantId", "tenant-a",
                "role", "OPERATOR",
                "operatorId", "operator-doc-a"));
        String assignedToken = authRequest("accounts:signInWithPassword", Map.of(
                "email", email,
                "password", password,
                "returnSecureToken", true)).get("idToken").asText();

        MockHttpServletRequest assignedRequest = new MockHttpServletRequest("GET", "/api/operator/me");
        assignedRequest.addHeader("Authorization", "Bearer " + assignedToken);
        assignedRequest.addHeader("X-Tenant-Id", "tenant-b");
        MockHttpServletResponse assignedResponse = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {
        };
        new FirebaseAuthenticationFilter().doFilter(assignedRequest, assignedResponse, chain);

        assertEquals("tenant-a", assignedRequest.getAttribute("tenantId"));
        assertEquals("OPERATOR", assignedRequest.getAttribute("firebaseRole"));
        assertEquals(uid, assignedRequest.getAttribute("firebaseUid"));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        String deviceId="a".repeat(64);
        FirebaseAuth.getInstance(firebaseApp).setCustomUserClaims(uid,Map.of("tenantId","tenant-a","role","OPERATOR","radioDeviceId",deviceId));
        String deviceToken=authRequest("accounts:signInWithPassword",Map.of("email",email,"password",password,"returnSecureToken",true)).get("idToken").asText();
        try(var db=com.google.cloud.firestore.FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator-only",new Date(Long.MAX_VALUE)))).build().getService();var staticDb=org.mockito.Mockito.mockStatic(com.google.firebase.cloud.FirestoreClient.class)){
            staticDb.when(com.google.firebase.cloud.FirestoreClient::getFirestore).thenReturn(db);
            for(String status:java.util.List.of("ACTIVE","REVOKED")){
                db.collection("pro_devices").document(deviceId).set(Map.of("tenantId","tenant-a","uid",uid,"status",status)).get();
                var deviceRequest=new MockHttpServletRequest("GET","/api/operator/me");deviceRequest.addHeader("Authorization","Bearer "+deviceToken);var deviceResponse=new MockHttpServletResponse();
                new FirebaseAuthenticationFilter().doFilter(deviceRequest,deviceResponse,new MockFilterChain());assertEquals(status.equals("ACTIVE")?200:401,deviceResponse.getStatus());
            }
        }
    }

    private JsonNode authRequest(String endpoint, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "http://" + System.getenv("FIREBASE_AUTH_EMULATOR_HOST")
                        + "/identitytoolkit.googleapis.com/v1/" + endpoint + "?key=demo-api-key"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }
}
