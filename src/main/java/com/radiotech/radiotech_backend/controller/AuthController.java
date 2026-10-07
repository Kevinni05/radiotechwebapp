package com.radiotech.radiotech_backend.controller;

import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.AuthService;
import com.radiotech.radiotech_backend.service.OperatorService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({ "/api/auth", "/api/v1/auth" })
public class AuthController {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

        private final AuthService authService;
        private final OperatorService operatorService;

        public AuthController(
                        AuthService authService,
                        OperatorService operatorService) {

                this.authService = authService;
                this.operatorService = operatorService;
        }

        @PostMapping("/verify")
        public ResponseEntity<?> verifyToken(
                        @RequestBody Map<String, String> request) {

                try {

                        String idToken = getValue(request, "idToken");

                        FirebaseToken token = authService.verifyIdToken(idToken);

                        Map<String, Object> data = new LinkedHashMap<>();

                        data.put(
                                        "uid",
                                        token.getUid());

                        data.put(
                                        "email",
                                        token.getEmail());

                        data.put(
                                        "name",
                                        token.getName());

                        data.put(
                                        "emailVerified",
                                        token.isEmailVerified());

                        data.put(
                                        "claims",
                                        token.getClaims());

                        return ResponseEntity.ok(
                                        success(
                                                        "Token Firebase valido.",
                                                        data));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(
                                                        error(
                                                                        e.getMessage()));

                } catch (Exception e) {

                        log.error("Errore non gestito", e);

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                        error(
                                                                        "Token Firebase non valido o scaduto."));
                }
        }

        @PostMapping("/login")
        public ResponseEntity<?> login(
                        @RequestBody Map<String, String> request) {

                try {

                        if (request == null) {

                                return ResponseEntity
                                                .badRequest()
                                                .body(
                                                                error(
                                                                                "Body della richiesta obbligatorio."));
                        }

                        String idToken = request.get("idToken");

                        Map<String, Object> response;

                        /*
                         * CASO 1:
                         * il frontend ci manda già un Firebase ID token.
                         */
                        if (idToken != null &&
                                        !idToken.isBlank()) {

                                response = authService.loginWithFirebaseToken(
                                                idToken);
                        }

                        /*
                         * CASO 2:
                         * il frontend manda email/password.
                         */
                        else {

                                response = authService.loginWithEmailPassword(
                                                request.get("email"),
                                                request.get("password"));
                        }

                        /*
                         * Account Firebase valido ma
                         * non associato a RadioTech.
                         */
                        if ("UNKNOWN".equals(
                                        response.get("role"))) {

                                return ResponseEntity
                                                .status(HttpStatus.FORBIDDEN)
                                                .body(response);
                        }

                        return ResponseEntity.ok(response);

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                        error(
                                                                        e.getMessage()));

                } catch (Exception e) {

                        log.error("Errore non gestito", e);

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                        error(
                                                                        e.getMessage() == null
                                                                                        ? "Autenticazione Firebase fallita."
                                                                                        : e.getMessage()));
                }
        }

        @PostMapping("/refresh")
        public ResponseEntity<?> refresh(@RequestBody Map<String, String> request) {
                try {
                        return ResponseEntity.ok(authService.refreshIdToken(
                                        request == null ? null : request.get("refreshToken")));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(e.getMessage()));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(error("Sessione Firebase non rinnovabile."));
                }
        }

        @PostMapping("/qr-login")
        public ResponseEntity<?> qrLogin(
                        @RequestBody Map<String, String> request) {

                try {
                        if (request == null) {
                                return ResponseEntity.badRequest().body(error("Body della richiesta obbligatorio."));
                        }

                        String qrToken = request.get("qrToken");
                        if (qrToken == null || qrToken.isBlank()) {
                                qrToken = request.get("token");
                        }

                        if (qrToken == null || qrToken.isBlank()) {
                                return ResponseEntity.badRequest().body(error("Token QR obbligatorio."));
                        }

                        Map<String, Object> result = authService.loginWithQrToken(qrToken);
                        return ResponseEntity.ok(result);

                } catch (IllegalArgumentException e) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(error("Operazione non autorizzata."));
                } catch (Exception e) {
                        if (com.radiotech.radiotech_backend.exception.CloudQuota.exhausted(e)) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(com.radiotech.radiotech_backend.exception.CloudQuota.MESSAGE));
                        String reference = java.util.UUID.randomUUID().toString();
                        log.error("QR login failure reference={}", reference, e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(error("Errore durante il login con QR. Rif. " + reference));
                }
        }

        @GetMapping("/me")
        public ResponseEntity<?> me(
                        @RequestAttribute("firebaseUid") String firebaseUid,

                        @RequestAttribute("firebaseToken") FirebaseToken token) {

                try {

                        Map<String, Object> data = new LinkedHashMap<>();

                        data.put(
                                        "uid",
                                        firebaseUid);

                        data.put(
                                        "email",
                                        token.getEmail());

                        data.put(
                                        "name",
                                        token.getName());

                        data.put(
                                        "picture",
                                        token.getPicture());

                        data.put(
                                        "emailVerified",
                                        token.isEmailVerified());

                        data.put(
                                        "claims",
                                        token.getClaims());

                        return ResponseEntity.ok(
                                        success(
                                                        "Utente autenticato.",
                                                        data));

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(
                                                        HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(
                                                        error(
                                                                        "Impossibile recuperare l'utente."));
                }
        }

        @GetMapping("/firebase-user/{firebaseUid}")
        public ResponseEntity<?> getFirebaseUser(
                        @PathVariable String firebaseUid) {

                try {

                        Operator operator = operatorService.getByFirebaseUid(firebaseUid);
                        if (operator == null) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                                .body(error("Utente Firebase non trovato."));
                        }

                        Map<String, Object> user = authService.getFirebaseUser(
                                        firebaseUid);

                        return ResponseEntity.ok(
                                        success(
                                                        "Utente Firebase recuperato.",
                                                        user));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(
                                                        error(
                                                                        e.getMessage()));

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(
                                                        HttpStatus.NOT_FOUND)
                                        .body(
                                                        error(
                                                                        "Utente Firebase non trovato."));
                }
        }

        @PostMapping("/firebase-user")
        public ResponseEntity<?> createFirebaseUser(
                        @RequestBody Map<String, String> request) {
                String createdFirebaseUid = null;
                String createdOperatorId = null;

                try {
                        if (request == null) {
                                return ResponseEntity.badRequest().body(error("Body della richiesta obbligatorio."));
                        }
                        String tenantId = TenantAccessPolicy.requireTenantAccess(
                                        SecurityContextAccessor.currentTenantId(), null);

                        String email = getValue(request, "email");

                        String password = getValue(request, "password");

                        String displayName = request.get("displayName");

                        UserRecord user = authService.createFirebaseUser(
                                        email,
                                        password,
                                        displayName);
                        createdFirebaseUid = user.getUid();
                        Operator operator = operatorService.syncFirebaseUser(
                                        user.getEmail(), displayName, tenantId);
                        createdOperatorId = operator.getId();

                        Map<String, Object> data = new LinkedHashMap<>();

                        data.put(
                                        "uid",
                                        user.getUid());

                        data.put(
                                        "email",
                                        user.getEmail());

                        data.put(
                                        "displayName",
                                        user.getDisplayName());

                        data.put("operator", operator);

                        return ResponseEntity
                                        .status(HttpStatus.CREATED)
                                        .body(
                                                        success(
                                                                        "Utente Firebase creato.",
                                                                        data));

                } catch (IllegalArgumentException e) {
                        rollbackCreatedOperator(createdFirebaseUid, createdOperatorId);
                        deleteCreatedUser(createdFirebaseUid);

                        return ResponseEntity
                                        .badRequest()
                                        .body(
                                                        error(
                                                                        e.getMessage()));

                } catch (SecurityException e) {
                        rollbackCreatedOperator(createdFirebaseUid, createdOperatorId);
                        deleteCreatedUser(createdFirebaseUid);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        rollbackCreatedOperator(createdFirebaseUid, createdOperatorId);
                        deleteCreatedUser(createdFirebaseUid);

                        log.error("Errore non gestito", e);

                        return ResponseEntity
                                        .status(HttpStatus.CONFLICT)
                                        .body(
                                                        error(
                                                                        e.getMessage()));
                }
        }

        private void deleteCreatedUser(String firebaseUid) {
                if (firebaseUid == null || firebaseUid.isBlank())
                        return;
                try {
                        authService.deleteFirebaseUser(firebaseUid);
                } catch (Exception ignored) {
                }
        }

        private void rollbackCreatedOperator(String firebaseUid, String operatorId) {
                if (firebaseUid == null || firebaseUid.isBlank())
                        return;
                try {
                        Operator operator = operatorId == null || operatorId.isBlank()
                                        ? operatorService.getByFirebaseUid(firebaseUid)
                                        : operatorService.getById(operatorId);
                        if (operator != null && operator.getId() != null) {
                                operatorService.deleteOperator(operator.getId());
                        }
                } catch (Exception ignored) {
                }
        }

        @PostMapping("/link")
        public ResponseEntity<?> linkFirebaseUser(
                        @RequestBody Map<String, String> request) {

                Role role = SecurityContextAccessor.currentRole();
                if (role != Role.SUPER_ADMIN && role != Role.ADMIN && role != Role.CHIEF_EXECUTIVE) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(error("Permessi insufficienti per collegare account Firebase."));
                }

                try {

                        String operatorId = getValue(
                                        request,
                                        "operatorId");

                        String firebaseUid = getValue(
                                        request,
                                        "firebaseUid");

                        Operator operator = authService.linkFirebaseUserToOperator(
                                        operatorId,
                                        firebaseUid);

                        return ResponseEntity.ok(
                                        success(
                                                        "Utente Firebase collegato all'operatore.",
                                                        operator));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(
                                                        error(
                                                                        e.getMessage()));

                } catch (Exception e) {

                        log.error("Errore non gestito", e);

                        return ResponseEntity
                                        .status(HttpStatus.BAD_REQUEST)
                                        .body(
                                                        error(
                                                                        e.getMessage()));
                }
        }

        @DeleteMapping("/firebase-user/{firebaseUid}")
        public ResponseEntity<?> deleteFirebaseUser(
                        @PathVariable String firebaseUid) {

                try {

                        Operator operator = operatorService.getByFirebaseUid(firebaseUid);
                        if (operator == null) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                                .body(error("Utente Firebase non trovato."));
                        }

                        authService.deleteFirebaseUser(
                                        firebaseUid);

                        return ResponseEntity.ok(
                                        success(
                                                        "Utente Firebase eliminato.",
                                                        null));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(
                                                        error(
                                                                        e.getMessage()));

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(
                                                        HttpStatus.NOT_FOUND)
                                        .body(
                                                        error(
                                                                        e.getMessage()));
                }
        }

        private String getValue(
                        Map<String, String> request,
                        String key) {

                if (request == null) {

                        throw new IllegalArgumentException(
                                        "Body della richiesta obbligatorio.");
                }

                String value = request.get(key);

                if (value == null ||
                                value.isBlank()) {

                        throw new IllegalArgumentException(
                                        key + " obbligatorio.");
                }

                return value.trim();
        }

        private Map<String, Object> success(
                        String message,
                        Object data) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "success",
                                true);

                response.put(
                                "message",
                                message);

                response.put(
                                "data",
                                data);

                return response;
        }

        private Map<String, Object> error(
                        String message) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "success",
                                false);

                response.put(
                                "message",
                                message == null ||
                                                message.isBlank()
                                                                ? "Richiesta non valida."
                                                                : message);

                return response;
        }
}
