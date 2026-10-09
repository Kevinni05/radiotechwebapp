package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.model.MaintenanceReport;
import com.radiotech.radiotech_backend.dto.MaintenanceReportDto;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import jakarta.validation.Valid;
import com.radiotech.radiotech_backend.service.MaintenanceReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({ "/api/reports", "/api/v1/reports" })
public class MaintenanceReportController {
    private final MaintenanceReportService service;

    public MaintenanceReportController(MaintenanceReportService service) {
        this.service = service;
    }

    @GetMapping("/verify/{token}")
    public ResponseEntity<?> verify(@PathVariable String token) {
        try {
            return ResponseEntity.ok(service.verifyPublicToken(token));
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("valid", false, "message", "Verifica report non valida."));
        } catch (Exception failure) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("valid", false, "message", "Verifica temporaneamente non disponibile."));
        }
    }

    @GetMapping
    public ResponseEntity<?> all(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requirePermission(Permission.REPORT_READ, tenantId);
        return ok(service::getAll);
    }

    @GetMapping("/page")
    public Object page(@RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) throws Exception {
        requirePermission(Permission.REPORT_READ, tenantId);
        return service.getPage(limit, cursor, null, null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> one(@PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requirePermission(Permission.REPORT_READ, tenantId);
        return ok(() -> service.getById(id));
    }

    @GetMapping("/operator/{operatorId}")
    public ResponseEntity<?> byOperator(@PathVariable String operatorId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requirePermission(Permission.REPORT_READ, tenantId);
        return ok(() -> service.getByOperator(operatorId));
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<?> byTask(@PathVariable String taskId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        requirePermission(Permission.REPORT_READ, tenantId);
        return ok(() -> service.getByTask(taskId));
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestAttribute("firebaseUid") String uid,
            @Valid @RequestBody MaintenanceReportDto request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            requirePermission(Permission.REPORT_CREATE, tenantId);
            service.requireVerificationConfiguration();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(submission(service.submit(request.toModel(), uid, idempotencyKey)));
        } catch (MaintenanceReportService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false,
                    "message", e.getMessage() == null ? "Operazione non consentita." : e.getMessage()));
        } catch (Exception e) {
            return server();
        }
    }

    private Map<String, Object> submission(MaintenanceReport report) throws Exception {
        return Map.of("report", report, "verificationUrl", service.verificationUrl(report));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable String id, @RequestAttribute("firebaseUid") String uid,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.REPORT_APPROVE, tenantId);
            return review(id, true, body, uid);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false,
                    "message", e.getMessage() == null ? "Operazione non consentita." : e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id, @RequestAttribute("firebaseUid") String uid,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.REPORT_APPROVE, tenantId);
            return review(id, false, body, uid);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false,
                    "message", e.getMessage() == null ? "Operazione non consentita." : e.getMessage()));
        }
    }

    private ResponseEntity<?> review(String id, boolean approve, Map<String, String> body, String reviewerUid) {
        try {
            return ResponseEntity.ok(service.review(id, approve, body == null ? null : body.get("note"), reviewerUid));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false,
                    "message", e.getMessage() == null ? "Operazione non consentita." : e.getMessage()));
        } catch (Exception e) {
            return server();
        }
    }

    private void requirePermission(Permission permission, String tenantId) {
        Role role = SecurityContextAccessor.currentRole();
        if (!TenantAccessPolicy.canAccess(role, permission)) {
            throw new SecurityException("Permessi insufficienti per l'operazione richiesta.");
        }

        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), tenantId);
    }

    private ResponseEntity<?> ok(ThrowingSupplier<?> supplier) {
        try {
            return ResponseEntity.ok(supplier.get());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false,
                    "message", e.getMessage() == null ? "Operazione non consentita." : e.getMessage()));
        } catch (Exception e) {
            return server();
        }
    }

    private ResponseEntity<?> bad(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<?> server() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Errore interno del servizio.");
        return ResponseEntity.status(500).body(body);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
