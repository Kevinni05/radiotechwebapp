package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.AlertEngineService;
import com.radiotech.radiotech_backend.service.ManutenzioneService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({ "/api/alerts", "/api/v1/alerts" })
public class AlertController {

    private final ManutenzioneService service;
    private final AlertEngineService alertEngine;

    public AlertController(ManutenzioneService service, AlertEngineService alertEngine) {
        this.service = service;
        this.alertEngine = alertEngine;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requireAccess(Permission.ALERT_EVALUATE, tenantId);
            return ResponseEntity.ok(alertEngine.evaluate());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            return server();
        }
    }

    @GetMapping
    public ResponseEntity<?> all(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireAccess(Permission.ALERT_READ, tenantId);
        try {
            return ResponseEntity.ok(service.getAllAlerts());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            return server();
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireAccess(Permission.ALERT_READ, tenantId);
        try {
            return ResponseEntity.ok(Map.of("count", service.countUnreadAlerts()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            return server();
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireAccess(Permission.ALERT_CREATE, tenantId);
        try {
            if (payload == null) {
                throw new IllegalArgumentException("Payload alert obbligatorio.");
            }
            String antennaId = value(payload, "antennaId");
            String description = value(payload, "descrizione");
            String priority = value(payload, "priorita");
            String operatorId = value(payload, "operatore");
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.createAlert(antennaId, description, priority, operatorId));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            return server();
        }
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireAccess(Permission.ALERT_ACK, tenantId);
        try {
            return ResponseEntity.ok(service.markAlertAsRead(id));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            return server();
        }
    }

    private void requireAccess(Permission permission, String tenantId) {
        Role role = SecurityContextAccessor.currentRole();
        if (!TenantAccessPolicy.canAccess(role, permission)) {
            throw new SecurityException("Permessi insufficienti per l'operazione alert.");
        }
        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), tenantId);
    }

    private ResponseEntity<?> bad(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message == null ? "Richiesta non valida." : message);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<?> forbidden(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message == null ? "Operazione non consentita." : message);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    private ResponseEntity<?> server() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Errore interno del servizio.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String value(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        return raw == null ? null : String.valueOf(raw);
    }
}
