package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.security.TenantInvitationService;
import com.radiotech.radiotech_backend.service.OperatorService;
import com.radiotech.radiotech_backend.service.QrCodeService;
import com.radiotech.radiotech_backend.dto.OperatorProfileUpdateDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({ "/api/operators", "/api/v1/operators" })
public class OperatorController {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OperatorController.class);

        private final OperatorService operatorService;
        private final QrCodeService qrCodeService;
        private final TenantInvitationService tenantInvitationService;

        public OperatorController(OperatorService operatorService, QrCodeService qrCodeService,
                        TenantInvitationService tenantInvitationService) {
                this.operatorService = operatorService;
                this.qrCodeService = qrCodeService;
                this.tenantInvitationService = tenantInvitationService;
        }

        @GetMapping
        public ResponseEntity<?> getAllOperators(
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_READ, tenantId);
                        List<Operator> operators = operatorService.getAllOperators();

                        return ResponseEntity.ok(
                                        success(
                                                        "Operatori recuperati correttamente.",
                                                        operators));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        return internalError(
                                        "Errore durante il recupero degli operatori.");
                }
        }

        @GetMapping("/{id}")
        public ResponseEntity<?> getOperatorById(
                        @PathVariable String id,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_READ, tenantId);
                        Operator operator = operatorService.getById(id);

                        return ResponseEntity.ok(
                                        success(
                                                        "Operatore recuperato correttamente.",
                                                        operator));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .status(HttpStatus.NOT_FOUND)
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante il recupero dell'operatore.");
                }
        }

        @PostMapping("/me/fcm-token")
        public ResponseEntity<?> addMyFcmToken(
                        @RequestBody Map<String, String> request,
                        HttpServletRequest httpRequest,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
                try {
                        requirePermission(Permission.USER_UPDATE, tenantId);
                        if (request == null || request.get("fcmToken") == null
                                        || request.get("fcmToken").isBlank()) {
                                return ResponseEntity.badRequest()
                                                .body(error("FCM token obbligatorio."));
                        }

                        String firebaseUid = (String) httpRequest.getAttribute("firebaseUid");
                        Operator operator = operatorService.getByFirebaseUid(firebaseUid);
                        if (operator == null) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                                .body(error("Operatore non associato all'utente Firebase."));
                        }

                        operatorService.addFcmToken(operator.getId(), request.get("fcmToken"));
                        return ResponseEntity.ok(success("FCM token aggiunto correttamente.", null));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (Exception e) {
                        return internalError("Errore durante l'aggiunta del token FCM.");
                }
        }

        @PostMapping
        public ResponseEntity<?> createOperator(
                        @RequestBody Operator operator,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_CREATE, tenantId);
                        String authenticatedTenant = SecurityContextAccessor.currentTenantId();
                        TenantAccessPolicy.requireTenantAccess(authenticatedTenant, operator.getTenantId());
                        operator.setTenantId(authenticatedTenant);
                        operator.setFirebaseUid(null);
                        operator.setRole("OPERATOR");
                        operator.setStatus("IN_ATTESA");
                        Operator created = operatorService.createOperator(operator);

                        return ResponseEntity
                                        .status(HttpStatus.CREATED)
                                        .body(
                                                        success(
                                                                        "Operatore creato correttamente.",
                                                                        created));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante la creazione dell'operatore.");
                }
        }

        @PostMapping("/registration-invites")
        public ResponseEntity<?> createRegistrationInvite(
                        @RequestParam(defaultValue = "86400") long ttlSeconds,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
                try {
                        requirePermission(Permission.USER_CREATE, tenantId);
                        String authenticatedTenant = SecurityContextAccessor.currentTenantId();
                        String invitation = tenantInvitationService.issue(authenticatedTenant, ttlSeconds);
                        return ResponseEntity.status(HttpStatus.CREATED)
                                        .body(success("Invito creato.", Map.of("invitationCode", invitation)));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        return internalError("Impossibile creare l'invito.");
                }
        }

        @PostMapping("/generate-credentials")
        public ResponseEntity<?> generateCredentials(@RequestBody Map<String, String> request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
                try {
                        requirePermission(Permission.USER_CREATE, tenantId);
                        if (request == null) {
                                return ResponseEntity.badRequest().body(error("Body della richiesta obbligatorio."));
                        }
                        String fullName = request.get("fullName");
                        String email = request.get("email");
                        String phone = request.get("phone");
                        String level = request.get("level");
                        String shift = request.get("shift");
                        String password = request.get("password");

                        Map<String, Object> result = operatorService.generateOperatorWithCredentials(
                                        SecurityContextAccessor.currentTenantId(), fullName, email, phone, level, shift,
                                        password);

                        return ResponseEntity.status(HttpStatus.CREATED)
                                        .body(success("Operatore e credenziali generate con successo.", result));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        log.error("Errore non gestito", e);
                        return internalError("Errore durante la generazione delle credenziali.");
                }
        }

        @PostMapping("/{id}/regenerate-qr")
        public ResponseEntity<?> regenerateQr(@PathVariable String id,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
                try {
                        requirePermission(Permission.USER_UPDATE, tenantId);
                        String newQrToken = operatorService.regenerateQrToken(id);
                        return ResponseEntity.ok(
                                        success("QR Token operatore rigenerato.", Map.of("qrCodeToken", newQrToken)));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        return internalError("Errore durante la rigenerazione del QR token.");
                }
        }

        @PostMapping("/{id}/approve")
        public ResponseEntity<?> approveOperator(@PathVariable String id,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
                try {
                        requirePermission(Permission.USER_UPDATE, tenantId);
                        Operator operator = operatorService.getById(id);
                        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),
                                        operator.getTenantId());
                        Operator approved = operatorService.approveOperator(id);
                        return ResponseEntity.ok(success("Operatore approvato e abilitato.", approved));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        return internalError("Errore durante l'approvazione dell'operatore.");
                }
        }

        @PostMapping("/{id}/qr-image")
        public ResponseEntity<?> qrImage(@PathVariable String id,
                        @RequestBody Map<String, String> request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
                try {
                        requirePermission(Permission.USER_READ, tenantId);
                        Operator operator = operatorService.getById(id);
                        String token = request == null ? null : request.get("qrCodeToken");
                        if (token == null || !token.equals(operator.getQrCodeToken())) {
                                return ResponseEntity.badRequest().body(error("QR token non valido."));
                        }
                        return ResponseEntity.ok(success("QR generato localmente.",
                                        Map.of("imageDataUrl", qrCodeService.toDataUri(token))));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        return internalError("Errore nella generazione del badge QR.");
                }
        }

        @PostMapping("/sync-firebase-user")
        public ResponseEntity<?> syncFirebaseUser(@RequestBody Map<String, String> request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
                try {
                        requirePermission(Permission.USER_CREATE, tenantId);
                        Operator operator = operatorService.syncFirebaseUser(
                                        request == null ? null : request.get("email"),
                                        request == null ? null : request.get("fullName"),
                                        SecurityContextAccessor.currentTenantId());
                        return ResponseEntity.ok(success(
                                        "Utente Firebase sincronizzato con Firestore.", operator));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(error("Utente Firebase non trovato o sincronizzazione fallita."));
                }
        }

        @PutMapping("/{id}")
        public ResponseEntity<?> updateOperator(
                        @PathVariable String id,
                        @Valid @RequestBody OperatorProfileUpdateDto request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_UPDATE, tenantId);
                        Operator operator = operatorService.getById(id);
                        if (request.getFullName() != null)
                                operator.setFullName(request.getFullName().trim());
                        if (request.getPhone() != null)
                                operator.setPhone(request.getPhone().trim());
                        if (request.getLevel() != null)
                                operator.setLevel(request.getLevel().trim().toUpperCase());
                        if (request.getShift() != null)
                                operator.setShift(request.getShift().trim());
                        Operator updated = operatorService.updateOperator(
                                        id,
                                        operator);

                        return ResponseEntity.ok(
                                        success(
                                                        "Operatore aggiornato correttamente.",
                                                        updated));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante l'aggiornamento dell'operatore.");
                }
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<?> deleteOperator(
                        @PathVariable String id,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_DELETE, tenantId);
                        operatorService.deleteOperator(id);

                        return ResponseEntity.ok(
                                        success(
                                                        "Operatore eliminato correttamente.",
                                                        null));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante l'eliminazione dell'operatore.");
                }
        }

        @PostMapping("/{id}/fcm-token")
        public ResponseEntity<?> addFcmToken(
                        @PathVariable String id,
                        @RequestBody Map<String, String> request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_UPDATE, tenantId);

                        if (request == null ||
                                        request.get("fcmToken") == null ||
                                        request.get("fcmToken").isBlank()) {

                                return ResponseEntity
                                                .badRequest()
                                                .body(
                                                                error(
                                                                                "FCM token obbligatorio."));
                        }

                        operatorService.addFcmToken(
                                        id,
                                        request.get("fcmToken"));

                        return ResponseEntity.ok(
                                        success(
                                                        "FCM token aggiunto correttamente.",
                                                        null));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante l'aggiunta del token FCM.");
                }
        }

        @DeleteMapping("/{id}/fcm-token")
        public ResponseEntity<?> removeFcmToken(
                        @PathVariable String id,
                        @RequestBody Map<String, String> request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_UPDATE, tenantId);

                        if (request == null ||
                                        request.get("fcmToken") == null ||
                                        request.get("fcmToken").isBlank()) {

                                return ResponseEntity
                                                .badRequest()
                                                .body(
                                                                error(
                                                                                "FCM token obbligatorio."));
                        }

                        operatorService.removeFcmToken(
                                        id,
                                        request.get("fcmToken"));

                        return ResponseEntity.ok(
                                        success(
                                                        "FCM token rimosso correttamente.",
                                                        null));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante la rimozione del token FCM.");
                }
        }

        @PostMapping("/{id}/last-seen")
        public ResponseEntity<?> updateLastSeen(
                        @PathVariable String id,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_UPDATE, tenantId);

                        operatorService.updateLastSeen(id);

                        return ResponseEntity.ok(
                                        success(
                                                        "Ultimo accesso aggiornato.",
                                                        null));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante l'aggiornamento dell'ultimo accesso.");
                }
        }

        @GetMapping("/qr/{qrCodeToken}")
        public ResponseEntity<?> findByQrToken(
                        @PathVariable String qrCodeToken,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_READ, tenantId);

                        Operator operator = operatorService.findByQrToken(
                                        qrCodeToken);

                        return ResponseEntity.ok(
                                        success(
                                                        "Operatore trovato.",
                                                        operator));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .status(HttpStatus.NOT_FOUND)
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante la ricerca tramite QR.");
                }
        }

        @GetMapping("/firebase/{firebaseUid}")
        public ResponseEntity<?> getByFirebaseUid(
                        @PathVariable String firebaseUid,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_READ, tenantId);

                        Operator operator = operatorService.getByFirebaseUid(
                                        firebaseUid);

                        if (operator == null) {

                                return ResponseEntity
                                                .status(HttpStatus.NOT_FOUND)
                                                .body(
                                                                error(
                                                                                "Nessun operatore associato al Firebase UID."));
                        }

                        return ResponseEntity.ok(
                                        success(
                                                        "Operatore trovato.",
                                                        operator));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));

                } catch (Exception e) {

                        return internalError(
                                        "Errore durante la ricerca tramite Firebase UID.");
                }
        }

        @GetMapping("/count")
        public ResponseEntity<?> countOperators(
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.USER_READ, tenantId);

                        long count = operatorService.countOperators();

                        return ResponseEntity.ok(
                                        success(
                                                        "Conteggio operatori recuperato.",
                                                        count));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {

                        return internalError(
                                        "Errore durante il conteggio degli operatori.");
                }
        }

        private void requirePermission(Permission permission, String tenantId) {
                Role role = SecurityContextAccessor.currentRole();
                if (!TenantAccessPolicy.canAccess(role, permission)) {
                        throw new SecurityException("Permessi insufficienti per l'operazione richiesta.");
                }

                String currentTenant = SecurityContextAccessor.currentTenantId();
                TenantAccessPolicy.requireTenantAccess(currentTenant, tenantId);
        }

        private Map<String, Object> success(
                        String message,
                        Object data) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put("success", true);
                response.put("message", message);
                response.put("data", data);

                return response;
        }

        private Map<String, Object> error(
                        String message) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put("success", false);
                response.put(
                                "message",
                                message == null || message.isBlank()
                                                ? "Richiesta non valida."
                                                : message);

                return response;
        }

        private ResponseEntity<Map<String, Object>> internalError(
                        String message) {

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(error(message));
        }
}