package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.dto.OperatorProfileUpdateDto;
import com.radiotech.radiotech_backend.dto.MaintenanceReportDto;
import com.radiotech.radiotech_backend.model.MaintenanceReport;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.service.MaintenanceReportService;
import com.radiotech.radiotech_backend.service.OperatorService;
import com.radiotech.radiotech_backend.service.TaskService;
import com.radiotech.radiotech_backend.security.TenantInvitationService;
import com.radiotech.radiotech_backend.security.GeoFencePolicy;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface consumed by the operator mobile application. Everything here is
 * scoped to the authenticated operator resolved from the Firebase ID token.
 */
@RestController
@RequestMapping({ "/api/operator", "/api/v1/operator" })
public class OperatorMobileController {

    @Value("${radiotech.geo.max-accuracy-meters:100}")
    private double maxGpsAccuracyMeters = 100;

    private final TaskService taskService;
    private final MaintenanceReportService reportService;
    private final OperatorService operatorService;
    private final TenantInvitationService tenantInvitationService;

    public OperatorMobileController(TaskService taskService, MaintenanceReportService reportService,
            OperatorService operatorService, TenantInvitationService tenantInvitationService) {
        this.taskService = taskService;
        this.reportService = reportService;
        this.operatorService = operatorService;
        this.tenantInvitationService = tenantInvitationService;
    }

    /*
     * ==========================================================
     * SELF REGISTRATION
     * ==========================================================
     */

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestAttribute("firebaseUid") String uid,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            Operator existing = operatorService.getByFirebaseUid(uid);
            if (existing != null) {
                return ResponseEntity.ok(existing);
            }

            String tenantId = tenantInvitationService.verify(value(request, "invitationCode"));

            String email = value(request, "email");
            String fullName = value(request, "fullName");

            if (email == null) {
                email = "operatore-" + uid.substring(0, Math.min(8, uid.length())) + "@gestionale-radio.local";
            }
            if (fullName == null) {
                fullName = email.substring(0, email.indexOf('@'));
            }

            Operator operator = new Operator();
            operator.setFullName(fullName);
            operator.setEmail(email);
            operator.setFirebaseUid(uid);
            operator.setTenantId(tenantId);
            operator.setLevel("TECNICO");
            operator.setShift("GIORNALIERO");
            // Auto-registrazione: serve approvazione di un manager (POST
            // /api/operators/{id}/approve).
            operator.setStatus("IN_ATTESA");
            operator.setRole("OPERATOR");

            return ResponseEntity.status(HttpStatus.CREATED).body(operatorService.createOperator(operator));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (Exception e) {
            return server();
        }
    }

    /*
     * ==========================================================
     * PROFILE
     * ==========================================================
     */

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestAttribute("firebaseUid") String uid) {
        try {
            Operator operator = operatorService.getByFirebaseUid(uid);
            if (operator == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(message("Profilo operatore non trovato."));
            }
            return ResponseEntity.ok(operator);
        } catch (Exception e) {
            return server();
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            @RequestAttribute("firebaseUid") String uid,
            @Valid @RequestBody OperatorProfileUpdateDto request) {
        try {
            Operator operator = operatorService.getByFirebaseUid(uid);
            if (operator == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(message("Profilo operatore non trovato."));
            }

            if (request.getFullName() != null && !request.getFullName().isBlank()) {
                operator.setFullName(request.getFullName().trim());
            }
            if (request.getPhone() != null) {
                operator.setPhone(request.getPhone().trim());
            }
            if (request.getBirthDate() != null) {
                operator.setBirthDate(request.getBirthDate().trim());
            }
            if (request.getCompany() != null) {
                operator.setCompany(request.getCompany().trim());
            }
            if (request.getSpecialization() != null) {
                operator.setSpecialization(request.getSpecialization().trim());
            }
            if (request.getShift() != null && !request.getShift().isBlank()) {
                operator.setShift(request.getShift().trim().toUpperCase());
            }

            return ResponseEntity.ok(operatorService.updateOperator(operator.getId(), operator));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return server();
        }
    }

    @PostMapping("/me/fcm-token")
    public ResponseEntity<?> addMyFcmToken(
            @RequestAttribute("firebaseUid") String uid,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            String token = value(request, "fcmToken");
            if (token == null) {
                return bad("FCM token obbligatorio.");
            }

            Operator operator = operatorService.getByFirebaseUid(uid);
            if (operator == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(message("Operatore non sincronizzato."));
            }

            operatorService.addFcmToken(operator.getId(), token);
            return ResponseEntity.ok(message("Token FCM registrato."));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return server();
        }
    }

    /*
     * ==========================================================
     * TASKS
     * ==========================================================
     */

    @GetMapping("/me/tasks")
    public ResponseEntity<?> myTasks(@RequestAttribute("firebaseUid") String uid) {
        try {
            Operator operator = operatorService.getByFirebaseUid(uid);
            if (operator == null) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.ok(taskService.getByOperator(operator.getId(), uid));
        } catch (Exception e) {
            return server();
        }
    }

    @PostMapping("/tasks/{id}/accept")
    public ResponseEntity<?> accept(@RequestAttribute("firebaseUid") String uid, @PathVariable String id) {
        return changeStatus(uid, id, "ACCEPTED");
    }

    @PostMapping("/tasks/{id}/en-route")
    public ResponseEntity<?> enRoute(@RequestAttribute("firebaseUid") String uid, @PathVariable String id) {
        return changeStatus(uid, id, "EN_ROUTE");
    }

    @PostMapping("/tasks/{id}/check-in")
    public ResponseEntity<?> checkIn(
            @RequestAttribute("firebaseUid") String uid,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> request) {
        try {
            Object latitude = request == null ? null : request.get("latitude");
            Object longitude = request == null ? null : request.get("longitude");
            Object accuracy = request == null ? null : request.get("accuracy");
            if (!(latitude instanceof Number) || !(longitude instanceof Number)
                    || !(accuracy instanceof Number)) {
                return bad("Coordinate e accuratezza GPS obbligatorie per il check-in.");
            }
            GeoFencePolicy.validateAccuracy(((Number) accuracy).doubleValue(), maxGpsAccuracyMeters);

            Operator operator = operatorService.getByFirebaseUid(uid);
            Task task = taskService.getById(id);
            if (operator == null || !isAssigned(task, operator, uid)) {
                return forbidden(null);
            }
            return ResponseEntity.ok(taskService.checkInTask(
                    id, uid, operator.getId(), ((Number) latitude).doubleValue(),
                    ((Number) longitude).doubleValue()));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (Exception e) {
            return server();
        }
    }

    @PostMapping("/tasks/{id}/start")
    public ResponseEntity<?> start(@RequestAttribute("firebaseUid") String uid, @PathVariable String id) {
        return changeStatus(uid, id, "IN_PROGRESS");
    }

    @PostMapping("/tasks/{id}/complete")
    public ResponseEntity<?> complete(
            @RequestAttribute("firebaseUid") String uid,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> request) {
        try {
            Object latitude = request == null ? null : request.get("latitude");
            Object longitude = request == null ? null : request.get("longitude");
            Object accuracy = request == null ? null : request.get("accuracy");
            if (!(latitude instanceof Number) || !(longitude instanceof Number)
                    || !(accuracy instanceof Number)) {
                return bad("Coordinate e accuratezza GPS obbligatorie per il check-out.");
            }
            GeoFencePolicy.validateAccuracy(((Number) accuracy).doubleValue(), maxGpsAccuracyMeters);

            Operator operator = operatorService.getByFirebaseUid(uid);
            Task task = taskService.getById(id);
            if (operator == null || !isAssigned(task, operator, uid)) {
                return forbidden(null);
            }
            return ResponseEntity.ok(taskService.completeTaskWithLocation(
                    id, uid, operator.getId(), ((Number) latitude).doubleValue(),
                    ((Number) longitude).doubleValue()));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (Exception e) {
            return server();
        }
    }

    @PostMapping("/tasks/{id}/wait")
    public ResponseEntity<?> waitForDependency(@RequestAttribute("firebaseUid") String uid, @PathVariable String id) {
        return changeStatus(uid, id, "WAITING");
    }

    /*
     * ==========================================================
     * REPORTS
     * ==========================================================
     */

    @PostMapping("/tasks/{id}/report")
    public ResponseEntity<?> report(
            @RequestAttribute("firebaseUid") String uid,
            @PathVariable String id,
            @Valid @RequestBody MaintenanceReportDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            MaintenanceReport report = request.toModel();
            report.setTaskId(id);
            reportService.requireVerificationConfiguration();
            MaintenanceReport submitted = reportService.submit(report, uid, idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "report", submitted, "verificationUrl", reportService.verificationUrl(submitted)));
        } catch (MaintenanceReportService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", e.getMessage()));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (Exception e) {
            return server();
        }
    }

    @PostMapping("/reports")
    public ResponseEntity<?> standaloneReport(
            @RequestAttribute("firebaseUid") String uid,
            @Valid @RequestBody MaintenanceReportDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            MaintenanceReport report = request.toModel();
            report.setTaskId(null);
            reportService.requireVerificationConfiguration();
            MaintenanceReport submitted = reportService.submit(report, uid, idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "report", submitted, "verificationUrl", reportService.verificationUrl(submitted)));
        } catch (MaintenanceReportService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", e.getMessage()));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (Exception e) {
            return server();
        }
    }

    @GetMapping("/me/reports")
    public ResponseEntity<?> myReports(@RequestAttribute("firebaseUid") String uid) {
        try {
            Operator own = operatorService.getByFirebaseUid(uid);
            return ResponseEntity.ok(reportService.getByOperator(own == null ? uid : own.getId(), uid));
        } catch (Exception e) {
            return server();
        }
    }

    /*
     * ==========================================================
     * HELPERS
     * ==========================================================
     */

    private ResponseEntity<?> changeStatus(String uid, String taskId, String status) {
        try {
            Operator operator = operatorService.getByFirebaseUid(uid);
            Task task = taskService.getById(taskId);

            if (operator == null || !isAssigned(task, operator, uid)) {
                return forbidden(null);
            }

            var requestAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            String key = requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                    ? servlet.getRequest().getHeader("Idempotency-Key") : null;
            return ResponseEntity.ok(key == null ? taskService.updateStatus(taskId, status)
                    : taskService.updateStatus(taskId, status, key));
        } catch (TaskService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Conflitto di idempotenza."));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (Exception e) {
            return server();
        }
    }

    private boolean isAssigned(Task task, Operator operator, String uid) {
        if (task == null) {
            return false;
        }
        return operator.getId().equals(task.getOperatorId())
                || uid.equals(task.getOperatorFirebaseUid())
                || uid.equals(task.getOperatorId());
    }

    private String value(Map<String, String> request, String key) {
        if (request == null) {
            return null;
        }
        String raw = request.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private Map<String, Object> message(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", text);
        return body;
    }

    private ResponseEntity<?> bad(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", text == null || text.isBlank() ? "Richiesta non valida." : text);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<?> forbidden(Exception exception) {
        String text = exception == null || exception.getMessage() == null
                ? "Operazione non consentita per questo operatore."
                : exception.getMessage();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", text);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    private ResponseEntity<?> server() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Operazione non disponibile.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
