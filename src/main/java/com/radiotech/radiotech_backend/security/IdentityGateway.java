package com.radiotech.radiotech_backend.security;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/** The runtime can request low privilege operations; only the external authority grants chiefs. */
@Component
public class IdentityGateway {
    private static volatile IdentityGateway active;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Value("${radiotech.identity.required:true}") private boolean required = true;
    @Value("${radiotech.identity.origin:}") private String origin = "";
    @Value("${radiotech.identity.app-secret:}") private String secret = "";
    @Value("${app.firebase.project-id:demo-radiotech}") private String project = "demo-radiotech";

    @PostConstruct
    void initialize() {
        if (!required && !(project.startsWith("demo-") && System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null))
            throw new IllegalStateException("Identity isolation may only be disabled for demo Auth emulators.");
        if (!origin.isBlank()) {
            URI uri = URI.create(origin);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty())
                throw new IllegalStateException("Identity authority requires an HTTPS origin without a path.");
        }
        active = this;
    }

    public static boolean isolated() { return active != null && active.required && !FirebaseApp.getApps().isEmpty(); }
    public static String origin() { return active == null ? "" : active.origin; }

    private static boolean emulatorOrUnitFixture() {
        // Directly constructed unit fixtures have no initialized Firebase app. A real app never takes this path.
        if (FirebaseApp.getApps().isEmpty()) return true;
        if (active != null) return !active.required;
        return FirebaseApp.getInstance().getOptions().getProjectId().startsWith("demo-")
                && System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null;
    }

    public static Map<String, Object> call(String operation, Map<String, ?> input) {
        if (active == null || !active.required || active.origin.isBlank() || active.secret.length() < 32)
            throw new IdentityUnavailableException("Il servizio accessi protetti deve essere configurato.");
        try {
            String path = "/internal/" + operation;
            String body = JSON.writeValueAsString(input);
            String timestamp = Long.toString(System.currentTimeMillis());
            byte[] nonceBytes = new byte[32]; RANDOM.nextBytes(nonceBytes);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(active.secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal(("POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + digest).getBytes(StandardCharsets.UTF_8)));
            var request = HttpRequest.newBuilder(URI.create(active.origin + path)).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json").header("X-Identity-Time", timestamp)
                    .header("X-Identity-Nonce", nonce).header("X-Identity-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = active.http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403 || response.statusCode() == 401)
                throw new SecurityException("Operazione non autorizzata dal servizio accessi protetti.");
            if (response.statusCode() == 400 || response.statusCode() == 409 || response.statusCode() == 429)
                throw new IllegalArgumentException("Richiesta non accettata. Verifica i dati o accedi nuovamente con MFA.");
            if (response.statusCode() != 200) throw new IdentityUnavailableException("Servizio accessi protetti temporaneamente non disponibile.");
            @SuppressWarnings("unchecked") Map<String, Object> result = JSON.readValue(response.body(), Map.class);
            return result;
        } catch (SecurityException | IllegalArgumentException | IdentityUnavailableException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IdentityUnavailableException("Verifica dell'identità interrotta."); }
        catch (Exception e) { throw new IdentityUnavailableException("Servizio accessi protetti temporaneamente non disponibile."); }
    }

    public static void validate(FirebaseToken token, String rawToken) {
        if (emulatorOrUnitFixture() || !isolated() || !high(token.getClaims())) return;
        var status = call("status", Map.of("idToken", rawToken));
        if (!token.getUid().equals(status.get("uid")) || !Objects.equals(token.getClaims().get("tenantId"), status.get("tenantId"))
                || Role.parse(String.valueOf(status.get("role"))) != Role.fromClaims(token.getClaims()))
            throw new SecurityException("Identità privilegiata non autorizzata.");
    }

    public static boolean high(Map<String, Object> claims) {
        return Boolean.TRUE.equals(claims.get("admin")) || Set.of(Role.ADMIN, Role.SUPER_ADMIN, Role.CHIEF_EXECUTIVE,
                Role.NETWORK_MANAGER, Role.ENGINEER).contains(Role.fromClaims(claims));
    }

    public static UserRecord create(UserRecord.CreateRequest fixtureRequest, String email, String password, String displayName) throws Exception {
        if (emulatorOrUnitFixture()) return FirebaseAuth.getInstance().createUser(fixtureRequest);
        Map<String, Object> input = new LinkedHashMap<>(); input.put("email", email);
        if (password != null) input.put("password", password);
        if (displayName != null) input.put("displayName", displayName);
        return FirebaseAuth.getInstance().getUser(String.valueOf(call("operator/create", input).get("uid")));
    }
    public static UserRecord update(UserRecord.UpdateRequest fixtureRequest, String uid, String password, String displayName) throws Exception {
        if (emulatorOrUnitFixture()) return FirebaseAuth.getInstance().updateUser(fixtureRequest);
        return FirebaseAuth.getInstance().getUser(String.valueOf(call("operator/update", Map.of("uid", uid, "password", password, "displayName", displayName)).get("uid")));
    }
    public static void claims(String uid, Map<String, Object> claims) throws Exception {
        if (emulatorOrUnitFixture()) { FirebaseAuth.getInstance().setCustomUserClaims(uid, claims); return; }
        if (high(claims)) throw new SecurityException("I privilegi elevati richiedono approvazione esterna.");
        if (claims.get("role") == null) call("operator/clear", Map.of("uid", uid));
        else call("operator/claims", Map.of("uid", uid, "claims", claims));
    }
    public static void delete(String uid) throws Exception {
        if (emulatorOrUnitFixture()) FirebaseAuth.getInstance().deleteUser(uid);
        else call("operator/delete", Map.of("uid", uid));
    }
    public static void revoke(String uid) throws Exception {
        if (emulatorOrUnitFixture()) FirebaseAuth.getInstance().revokeRefreshTokens(uid);
        else call("operator/revoke", Map.of("uid", uid));
    }
    public static String customToken(String uid, Map<String, Object> claims) throws Exception {
        if (emulatorOrUnitFixture()) return FirebaseAuth.getInstance().createCustomToken(uid, claims);
        return String.valueOf(call("operator/token", Map.of("uid", uid)).get("customToken"));
    }
    public static class IdentityUnavailableException extends RuntimeException {
        public IdentityUnavailableException(String message) { super(message); }
    }
}
