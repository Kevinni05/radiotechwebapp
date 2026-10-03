package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.dto.IncidentCreateRequest;
import com.radiotech.radiotech_backend.dto.IncidentTransitionRequest;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({ "/api/incidents", "/api/v1/incidents" })
public class IncidentController {
    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> all(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireAccess(Permission.INCIDENT_READ, tenantId);
        try {
            return ResponseEntity.ok(service.getAll());
        } catch (Exception exception) {
            return server();
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody(required = false) IncidentCreateRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireAccess(Permission.INCIDENT_CREATE, tenantId);
        try {
            if (request == null) {
                throw new IllegalArgumentException("Payload incident obbligatorio.");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                    request.getTitle(),
                    request.getDescription(),
                    request.getSeverity(),
                    request.getSiteId(),
                    request.getAssetId(),
                    request.getTaskId(),
                    request.getAssignedOperatorId(),
                    SecurityContextAccessor.currentUid()));
        } catch (IllegalArgumentException exception) {
            return bad(exception.getMessage());
        } catch (SecurityException exception) {
            return forbidden(exception.getMessage());
        } catch (Exception exception) {
            return server();
        }
    }

    @PostMapping("/{id}/transitions")
    public ResponseEntity<?> transition(@PathVariable String id,
            @Valid @RequestBody(required = false) IncidentTransitionRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requireAccess(Permission.INCIDENT_UPDATE, tenantId);
        try {
            if (request == null) {
                throw new IllegalArgumentException("Payload transition incident obbligatorio.");
            }
            return ResponseEntity.ok(service.transition(id, request.getStatus(), SecurityContextAccessor.currentUid(),
                    request.getRootCause(), request.getResolution()));
        } catch (IllegalArgumentException exception) {
            return bad(exception.getMessage());
        } catch (SecurityException exception) {
            return forbidden(exception.getMessage());
        } catch (Exception exception) {
            return server();
        }
    }

    private void requireAccess(Permission permission, String tenantId) {
        Role role = SecurityContextAccessor.currentRole();
        if (!TenantAccessPolicy.canAccess(role, permission)) {
            throw new SecurityException("Permessi insufficienti per l'incident.");
        }
        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), tenantId);
    }

    private ResponseEntity<?> bad(String message) {
        return buildResponse(HttpStatus.BAD_REQUEST, message == null ? "Richiesta non valida." : message);
    }

    private ResponseEntity<?> forbidden(String message) {
        return buildResponse(HttpStatus.FORBIDDEN, message == null ? "Operazione non consentita." : message);
    }

    private ResponseEntity<?> server() {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno del servizio.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
