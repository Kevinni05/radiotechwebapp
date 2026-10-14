package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.NotificationService;
import com.radiotech.radiotech_backend.service.OperatorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({ "/api/notifications", "/api/v1/notifications" })
public class NotificationController {
    private final NotificationService notifications;
    private final OperatorService operators;
    private final com.radiotech.radiotech_backend.service.NotificationDispatchService dispatch;

    public NotificationController(NotificationService notifications, OperatorService operators,
            com.radiotech.radiotech_backend.service.NotificationDispatchService dispatch) {
        this.notifications = notifications;
        this.operators = operators;
        this.dispatch = dispatch;
    }

    @PostMapping("/operator/{operatorId}")
    public ResponseEntity<?> send(@PathVariable String operatorId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.USER_UPDATE, tenantId);
            if (body == null)
                return bad("Body della notifica obbligatorio.");
            int delivered = notifications.notifyOperator(operatorId, body.get("title"), body.get("message"));
            return ResponseEntity.ok(Map.of("success", true, "delivered", delivered));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Invio notifica fallito."));
        }
    }

    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(@RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.USER_UPDATE, tenantId);
            if (body == null)
                return bad("Body della notifica obbligatorio.");
            int count = notifications.notifyAllOperators(body.get("title"), body.get("message"));
            return ResponseEntity.ok(Map.of("success", true, "delivered", count));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Broadcast fallito."));
        }
    }

    @PostMapping
    public ResponseEntity<?> dashboardSend(@RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.USER_UPDATE, tenantId);
            if (body == null)
                return bad("Body della notifica obbligatorio.");
            return ResponseEntity.ok(dispatch.dispatch(body));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("success",false,"message",java.util.Objects.toString(e.getReason(),"Operazione non valida.")));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Invio notifica fallito."));
        }
    }

    private ResponseEntity<?> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable String id, @RequestHeader(value="X-Tenant-Id",required=false) String tenantId) throws Exception {
        requirePermission(Permission.USER_UPDATE,tenantId);
        return ResponseEntity.ok(notifications.retryRecorded(id));
    }

    @GetMapping("/recipients")
    public ResponseEntity<?> recipients(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.USER_READ, tenantId);
            return ResponseEntity.ok(operators.getAllOperators());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Impossibile recuperare i destinatari."));
        }
    }

    private void requirePermission(Permission permission, String tenantId) {
        Role role = SecurityContextAccessor.currentRole();
        if (!TenantAccessPolicy.canAccess(role, permission)) {
            throw new SecurityException("Permessi insufficienti per l'operazione richiesta.");
        }

        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), tenantId);
    }
}
