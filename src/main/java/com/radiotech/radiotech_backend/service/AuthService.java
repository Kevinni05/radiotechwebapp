package com.radiotech.radiotech_backend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.radiotech.radiotech_backend.model.CapoProfile;
import com.radiotech.radiotech_backend.model.Operator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.MediaType;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

        private final OperatorService operatorService;
        private final CapoService capoService;
        private final RestClient restClient;

        @Value("${radiotech.firebase.web-api-key:}")
        private String firebaseWebApiKey;

        public AuthService(
                        OperatorService operatorService,
                        CapoService capoService,
                        RestClient restClient) {

                this.operatorService = operatorService;
                this.capoService = capoService;
                this.restClient = restClient;
        }

        /**
         * Verifica un Firebase ID Token.
         */
        public FirebaseToken verifyIdToken(String idToken) throws Exception {

                if (idToken == null || idToken.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Firebase ID token obbligatorio.");
                }

                return FirebaseAuth
                                .getInstance()
                                .verifyIdToken(idToken.trim(), true);
        }

        /**
         * LOGIN CON EMAIL E PASSWORD.
         *
         * Usa Firebase Identity Toolkit.
         */
        public Map<String, Object> loginWithEmailPassword(
                        String email,
                        String password) throws Exception {

                if (email == null || email.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Email obbligatoria.");
                }

                if (password == null || password.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Password obbligatoria.");
                }

                if (firebaseWebApiKey == null ||
                                firebaseWebApiKey.isBlank()) {

                        throw new IllegalStateException(
                                        "Firebase Web API Key non configurata.");
                }

                Map<String, Object> payload = new HashMap<>();

                payload.put("email", email.trim());
                payload.put("password", password);
                payload.put("returnSecureToken", true);

                try {

                        Map<?, ?> authResponse = restClient
                                        .post()
                                        .uri(
                                                        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key="
                                                                        + firebaseWebApiKey)
                                        .header(
                                                        "Content-Type",
                                                        "application/json")
                                        .body(payload)
                                        .retrieve()
                                        .body(Map.class);

                        if (authResponse == null) {

                                throw new IllegalArgumentException(
                                                "Firebase non ha restituito una risposta.");
                        }

                        Object idTokenObject = authResponse.get("idToken");

                        if (idTokenObject == null ||
                                        String.valueOf(idTokenObject).isBlank()) {

                                throw new IllegalArgumentException(
                                                "Firebase non ha restituito un ID token.");
                        }

                        String idToken = String.valueOf(idTokenObject);

                        Map<String, Object> result = loginWithFirebaseToken(idToken);

                        /*
                         * Quando il login promuove l'utente a CAPO, i custom
                         * claims vengono scritti su Firebase Auth dentro
                         * loginWithFirebaseToken. L'ID token ottenuto prima non
                         * li contiene ancora: il Control Room riceverebbe 403 su
                         * ogni chiamata /api/dashboard/**. Riautentichiamo per
                         * ottenere un token che includa il ruolo.
                         */
                        Object refreshToken = authResponse.get("refreshToken");
                        Object expiresIn = authResponse.get("expiresIn");

                        if ("CHIEF_EXECUTIVE".equals(result.get("role"))) {
                                Map<?, ?> refreshed = signInWithPassword(payload);
                                if (refreshed != null && refreshed.get("idToken") != null) {
                                        String refreshedToken = String.valueOf(refreshed.get("idToken"));
                                        if (!refreshedToken.isBlank()) {
                                                idToken = refreshedToken;
                                                if (refreshed.get("refreshToken") != null) {
                                                        refreshToken = refreshed.get("refreshToken");
                                                }
                                                if (refreshed.get("expiresIn") != null) {
                                                        expiresIn = refreshed.get("expiresIn");
                                                }
                                        }
                                }
                        }

                        /*
                         * Il frontend deve conservare questo token.
                         */
                        result.put(
                                        "token",
                                        idToken);

                        /*
                         * Utile al frontend per eventuale refresh.
                         */
                        if (refreshToken != null) {
                                result.put(
                                                "refreshToken",
                                                refreshToken);
                        }

                        if (expiresIn != null) {
                                result.put(
                                                "expiresIn",
                                                expiresIn);
                        }

                        return result;

                } catch (RestClientResponseException e) {

                        /*
                         * Questo è IMPORTANTISSIMO:
                         * non nascondiamo più l'errore reale Firebase.
                         */

                        String firebaseError = e.getResponseBodyAsString();

                        log.warn("Login Firebase rifiutato: {}", firebaseError);

                        throw new IllegalArgumentException(
                                        convertFirebaseLoginError(
                                                        firebaseError));
                }
        }

        private Map<?, ?> signInWithPassword(Map<String, Object> payload) {
                try {
                        return restClient
                                        .post()
                                        .uri("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key="
                                                        + firebaseWebApiKey)
                                        .header("Content-Type", "application/json")
                                        .body(payload)
                                        .retrieve()
                                        .body(Map.class);
                } catch (Exception e) {
                        return null;
                }
        }

        public Map<String, Object> refreshIdToken(String refreshToken) throws Exception {
                if (refreshToken == null || refreshToken.isBlank()) {
                        throw new IllegalArgumentException("Refresh token obbligatorio.");
                }
                if (firebaseWebApiKey == null || firebaseWebApiKey.isBlank()) {
                        throw new IllegalStateException("Firebase Web API Key non configurata.");
                }
                String form = "grant_type=refresh_token&refresh_token="
                                + URLEncoder.encode(refreshToken.trim(), StandardCharsets.UTF_8);
                Map<?, ?> response = restClient.post()
                                .uri("https://securetoken.googleapis.com/v1/token?key=" + firebaseWebApiKey)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .body(form)
                                .retrieve()
                                .body(Map.class);
                if (response == null || response.get("id_token") == null) {
                        throw new IllegalArgumentException("Refresh token Firebase non valido o scaduto.");
                }
                Map<String, Object> result = loginWithFirebaseToken(String.valueOf(response.get("id_token")));
                result.put("token", response.get("id_token"));
                if (response.get("refresh_token") != null)
                        result.put("refreshToken", response.get("refresh_token"));
                if (response.get("expires_in") != null)
                        result.put("expiresIn", response.get("expires_in"));
                return result;
        }

        /**
         * Login tramite Firebase ID Token.
         */
        public Map<String, Object> loginWithFirebaseToken(
                        String idToken) throws Exception {

                FirebaseToken decodedToken = verifyIdToken(idToken);

                String firebaseUid = decodedToken.getUid();

                Map<String, Object> response = new HashMap<>();

                response.put(
                                "success",
                                true);

                response.put(
                                "firebaseUid",
                                firebaseUid);

                if (com.radiotech.radiotech_backend.security.Role.fromClaims(decodedToken.getClaims()) == com.radiotech.radiotech_backend.security.Role.CUSTOMER) {
                        if (!(decodedToken.getClaims().get("customerId") instanceof String customer) || customer.isBlank()
                                || !(decodedToken.getClaims().get("tenantId") instanceof String tenant) || tenant.isBlank()) throw new SecurityException("Cliente non assegnato.");
                        response.put("role", "CUSTOMER");
                        response.put("user", Map.of("uid",firebaseUid,"email",decodedToken.getEmail()==null?"":decodedToken.getEmail(),"role","CUSTOMER"));
                        return response;
                }

                /*
                 * ==========================================================
                 * CAPO
                 * ==========================================================
                 */

                var signedRole=com.radiotech.radiotech_backend.security.Role.fromClaims(decodedToken.getClaims());
                if(java.util.Set.of(com.radiotech.radiotech_backend.security.Role.ADMIN,com.radiotech.radiotech_backend.security.Role.SUPER_ADMIN,com.radiotech.radiotech_backend.security.Role.NETWORK_MANAGER,com.radiotech.radiotech_backend.security.Role.VIEWER).contains(signedRole)){
                        response.put("role",signedRole.name());response.put("operatorFound",false);
                        response.put("user",Map.of("uid",firebaseUid,"email",decodedToken.getEmail()==null?"":decodedToken.getEmail(),"name",decodedToken.getName()==null?"":decodedToken.getName(),"role",signedRole.name()));return response;
                }
                if (signedRole==com.radiotech.radiotech_backend.security.Role.CHIEF_EXECUTIVE) {

                        CapoProfile profile = capoService.ensureCapoProfile(
                                        firebaseUid);

                        try {

                                capoService.updateLastSeen(
                                                firebaseUid);

                        } catch (Exception ignored) {
                        }

                        response.put(
                                        "role",
                                        "CHIEF_EXECUTIVE");

                        Map<String, Object> user = new HashMap<>();

                        user.put(
                                        "uid",
                                        firebaseUid);

                        user.put(
                                        "email",
                                        decodedToken.getEmail() == null
                                                        ? ""
                                                        : decodedToken.getEmail());

                        user.put(
                                        "name",
                                        decodedToken.getName() == null
                                                        ? ""
                                                        : decodedToken.getName());

                        user.put(
                                        "role",
                                        "CHIEF_EXECUTIVE");

                        response.put(
                                        "user",
                                        user);

                        response.put(
                                        "operatorFound",
                                        false);

                        response.put(
                                        "capo",
                                        profile);

                        response.put(
                                        "message",
                                        "Accesso CAPO completato.");

                        return response;
                }

                /*
                 * ==========================================================
                 * OPERATORE
                 * ==========================================================
                 */

                Operator operator = operatorService.getByFirebaseUid(
                                firebaseUid);

                if (operator == null) {

                        response.put(
                                        "operatorFound",
                                        false);

                        response.put(
                                        "role",
                                        "UNKNOWN");

                        response.put(
                                        "message",
                                        "Account Firebase valido, ma nessun profilo RadioTech è associato a questo account.");

                        return response;
                }

                try {

                        operatorService.updateLastSeen(
                                        operator.getId(), operator.getTenantId());

                } catch (Exception ignored) {
                }

                response.put(
                                "operatorFound",
                                true);

                response.put(
                                "role",
                                "OPERATOR");

                Map<String, Object> user = new HashMap<>();

                user.put(
                                "uid",
                                firebaseUid);

                user.put(
                                "email",
                                operator.getEmail() == null
                                                ? ""
                                                : operator.getEmail());

                user.put(
                                "name",
                                operator.getFullName() == null
                                                ? ""
                                                : operator.getFullName());

                user.put(
                                "role",
                                "OPERATOR");

                response.put(
                                "user",
                                user);

                response.put(
                                "operator",
                                operator);

                response.put(
                                "message",
                                "Autenticazione completata.");

                return response;
        }

        /**
         * Traduce gli errori Firebase in messaggi leggibili.
         */
        private String convertFirebaseLoginError(
                        String firebaseError) {

                if (firebaseError == null) {
                        return "Autenticazione Firebase fallita.";
                }

                String error = firebaseError.toUpperCase();

                if (error.contains("INVALID_LOGIN_CREDENTIALS")) {
                        return "Email o password non corrette.";
                }

                if (error.contains("INVALID_PASSWORD")) {
                        return "Password non corretta.";
                }

                if (error.contains("EMAIL_NOT_FOUND")) {
                        return "Account Firebase non trovato.";
                }

                if (error.contains("USER_DISABLED")) {
                        return "L'account Firebase è disabilitato.";
                }

                if (error.contains("INVALID_API_KEY")) {
                        return "Firebase Web API Key non valida.";
                }

                if (error.contains("API_KEY_SERVICE_BLOCKED")) {
                        return "La Firebase Web API Key non permette l'accesso al servizio Identity Toolkit.";
                }

                if (error.contains("TOO_MANY_ATTEMPTS")) {
                        return "Troppi tentativi di accesso. Riprova più tardi.";
                }

                return "Errore Firebase durante l'autenticazione.";
        }

        /**
         * Recupera utente Firebase.
         */
        public Map<String, Object> getFirebaseUser(
                        String firebaseUid) throws Exception {

                if (firebaseUid == null ||
                                firebaseUid.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Firebase UID obbligatorio.");
                }

                UserRecord user = FirebaseAuth
                                .getInstance()
                                .getUser(
                                                firebaseUid.trim());

                Map<String, Object> response = new HashMap<>();

                response.put(
                                "uid",
                                user.getUid());

                response.put(
                                "email",
                                user.getEmail());

                response.put(
                                "displayName",
                                user.getDisplayName());

                response.put(
                                "phoneNumber",
                                user.getPhoneNumber());

                response.put(
                                "photoUrl",
                                user.getPhotoUrl());

                response.put(
                                "emailVerified",
                                user.isEmailVerified());

                response.put(
                                "disabled",
                                user.isDisabled());

                response.put(
                                "customClaims",
                                user.getCustomClaims());

                return response;
        }

        /**
         * Crea utente Firebase.
         */
        public UserRecord createFirebaseUser(
                        String email,
                        String password,
                        String displayName)
                        throws Exception {

                if (email == null ||
                                email.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Email obbligatoria.");
                }

                if (password == null ||
                                password.length() < 8) {

                        throw new IllegalArgumentException(
                                        "La password deve contenere almeno 8 caratteri.");
                }

                UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                                .setEmail(email.trim())
                                .setPassword(password);

                if (displayName != null &&
                                !displayName.isBlank()) {

                        request.setDisplayName(
                                        displayName.trim());
                }

                return FirebaseAuth
                                .getInstance()
                                .createUser(request);
        }

        /**
         * Collega Firebase UID ad operatore.
         */
        public Operator linkFirebaseUserToOperator(
                        String operatorId,
                        String firebaseUid)
                        throws Exception {

                if (operatorId == null ||
                                operatorId.isBlank()) {

                        throw new IllegalArgumentException(
                                        "ID operatore obbligatorio.");
                }

                if (firebaseUid == null ||
                                firebaseUid.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Firebase UID obbligatorio.");
                }

                String uid = firebaseUid.trim();

                FirebaseAuth
                                .getInstance()
                                .getUser(uid);

                return operatorService.assignFirebaseUid(
                                operatorId.trim(),
                                uid);
        }

        /**
         * Elimina utente Firebase.
         */
        public void deleteFirebaseUser(
                        String firebaseUid)
                        throws Exception {

                if (firebaseUid == null ||
                                firebaseUid.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Firebase UID obbligatorio.");
                }

                FirebaseAuth
                                .getInstance()
                                .deleteUser(
                                                firebaseUid.trim());
        }

        /**
         * Promuove un utente a CAPO.
         */
        public CapoProfile promoteToCapo(
                        String firebaseUid)
                        throws Exception {

                if (firebaseUid == null ||
                                firebaseUid.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Firebase UID obbligatorio.");
                }

                String uid = firebaseUid.trim();

                FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

                UserRecord user = firebaseAuth.getUser(uid);

                capoService.setCapoRole(uid);

                return capoService.createOrUpdateProfile(
                                uid,
                                user.getDisplayName(),
                                user.getEmail(),
                                user.getPhoneNumber(),
                                "RadioTech",
                                user.getPhotoUrl());
        }

        public Map<String, Object> loginWithQrToken(String qrToken) throws Exception {
                if (qrToken == null || qrToken.isBlank()) {
                        throw new IllegalArgumentException("QR token obbligatorio.");
                }

                String cleanToken = qrToken.trim();
                if (cleanToken.contains("token=")) {
                        cleanToken = cleanToken.substring(cleanToken.indexOf("token=") + 6);
                        if (cleanToken.contains("&")) {
                                cleanToken = cleanToken.substring(0, cleanToken.indexOf("&"));
                        }
                }

                Operator operator = operatorService.validateQrToken(cleanToken);

                String firebaseUid = operator.getFirebaseUid();
                if (operator.getTenantId() == null || operator.getTenantId().isBlank()) {
                        throw new SecurityException("Tenant operatore mancante: completare la migrazione.");
                }
                if (firebaseUid == null || firebaseUid.isBlank()) {
                        String email = operator.getEmail();
                        if (email == null || email.isBlank()) {
                                email = "operatore-"
                                                + operator.getId().substring(0, Math.min(8, operator.getId().length()))
                                                + "@radiotech.local";
                        }
                        try {
                                UserRecord existingUser = FirebaseAuth.getInstance().getUserByEmail(email);
                                firebaseUid = existingUser.getUid();
                        } catch (com.google.firebase.auth.FirebaseAuthException notFound) {
                                if (notFound.getAuthErrorCode() != com.google.firebase.auth.AuthErrorCode.USER_NOT_FOUND) {
                                        throw notFound;
                                }
                                UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                                                .setEmail(email)
                                                .setDisplayName(operator.getFullName())
                                                .setEmailVerified(true);
                                UserRecord created = FirebaseAuth.getInstance().createUser(createReq);
                                firebaseUid = created.getUid();
                        }
                }

                UserRecord qrUser;
                try {
                        qrUser = FirebaseAuth.getInstance().getUser(firebaseUid);
                } catch (com.google.firebase.auth.FirebaseAuthException missing) {
                        if (missing.getAuthErrorCode() != com.google.firebase.auth.AuthErrorCode.USER_NOT_FOUND) throw missing;
                        throw new IllegalArgumentException("Account collegato non trovato. Risincronizza l'operatore e rigenera il badge.");
                }
                if (qrUser.isDisabled()) {
                        throw new IllegalArgumentException("Account disabilitato. Contatta l'amministratore.");
                }
                var accountRole = com.radiotech.radiotech_backend.security.Role.fromClaims(qrUser.getCustomClaims());
                if (accountRole != com.radiotech.radiotech_backend.security.Role.NONE
                                && accountRole != com.radiotech.radiotech_backend.security.Role.OPERATOR
                                && accountRole != com.radiotech.radiotech_backend.security.Role.VIEWER) {
                        throw new SecurityException("Il badge non può modificare il ruolo di questo account.");
                }
                Map<String, Object> claims = new HashMap<>();
                if (qrUser.getCustomClaims() != null) {
                        claims.putAll(qrUser.getCustomClaims());
                }
                if (Boolean.TRUE.equals(claims.get("mfaRequired"))) {
                        throw new IllegalArgumentException("Questo account richiede il secondo fattore. Accedi con le credenziali aziendali e MFA.");
                }
                Object existingTenant = claims.get("tenantId");
                if (existingTenant instanceof String value && !value.isBlank()
                                && !operator.getTenantId().equals(value.trim())) {
                        throw new SecurityException("Account Firebase già assegnato a un tenant diverso.");
                }
                if (operator.getFirebaseUid() == null || operator.getFirebaseUid().isBlank()) {
                        operator = operatorService.linkQrAccount(cleanToken, firebaseUid);
                }
                claims.remove("radioDeviceId");
                claims.remove("radioMfaVerified");
                String qrRole = "VIEWER".equalsIgnoreCase(operator.getRole())
                                || accountRole == com.radiotech.radiotech_backend.security.Role.VIEWER ? "VIEWER" : "OPERATOR";
                claims.put("role", qrRole);
                claims.put("operatorId", operator.getId());
                claims.put("tenantId", operator.getTenantId());
                FirebaseAuth.getInstance().setCustomUserClaims(firebaseUid, claims);

                String customToken = FirebaseAuth.getInstance().createCustomToken(firebaseUid, claims);

                // Do not deliver a token if the badge was revoked or consumed while
                // Firebase was preparing it. Preparation failures do not consume it.
                operator = operatorService.consumeQrToken(cleanToken);
                try {
                        operatorService.updateLastSeen(operator.getId(), operator.getTenantId());
                } catch (Exception ignored) {
                }

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Login tramite QR Code completato con successo.");
                response.put("customToken", customToken);
                response.put("operator", operator);
                response.put("role", qrRole);

                return response;
        }

        @PostConstruct
        public void checkFirebaseApiKey() {
                log.info(
                                "Firebase Web API Key configurata: "
                                                + (firebaseWebApiKey != null && !firebaseWebApiKey.isBlank()));
        }
}
