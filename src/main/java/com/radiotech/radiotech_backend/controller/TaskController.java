package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.dto.TaskDto;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import jakarta.validation.Valid;
import com.radiotech.radiotech_backend.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({ "/api/tasks", "/api/v1/tasks" })
public class TaskController {

        private final TaskService taskService;

        public TaskController(
                        TaskService taskService) {

                this.taskService = taskService;
        }

        @GetMapping
        public ResponseEntity<?> getAllTasks(
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.TASK_READ, tenantId);

                        List<Task> tasks = taskService.getAllTasks();

                        return ResponseEntity.ok(
                                        success(
                                                        "Task recuperati correttamente.",
                                                        tasks));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {

                        return internalError(
                                        "Errore durante il recupero dei task.");
                }
        }

        @GetMapping("/{id}")
        public ResponseEntity<?> getTaskById(
                        @PathVariable String id,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        requirePermission(Permission.TASK_READ, tenantId);

                        Task task = taskService.getById(id);
                        if (!TenantAccessPolicy.canAccessTask(task, resolveTenant(tenantId))) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(error("Accesso negato a questo tenant."));
                        }

                        return ResponseEntity.ok(
                                        success(
                                                        "Task recuperato correttamente.",
                                                        task));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .status(HttpStatus.NOT_FOUND)
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {

                        return internalError(
                                        "Errore durante il recupero del task.");
                }
        }

        @PostMapping
        public ResponseEntity<?> createTask(
                        @Valid @RequestBody TaskDto request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

                try {
                        String effectiveTenant = resolveTenant(tenantId, request.getTenantId());
                        requirePermission(Permission.TASK_CREATE, effectiveTenant);

                        Task task = request.toModel();
                        task.setTenantId(effectiveTenant);
                        Task created = taskService.createTask(task);

                        return ResponseEntity
                                        .status(HttpStatus.CREATED)
                                        .body(
                                                        success(
                                                                        "Task creato correttamente.",
                                                                        created));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {

                        return internalError(
                                        "Errore durante la creazione del task.");
                }
        }

        @PatchMapping("/{id}/status")
        public ResponseEntity<?> updateStatus(
                        @PathVariable String id,
                        @RequestBody Map<String, String> request,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

                try {
                        if (request == null ||
                                        request.get("status") == null ||
                                        request.get("status").isBlank()) {

                                return ResponseEntity
                                                .badRequest()
                                                .body(
                                                                error(
                                                                                "Stato task obbligatorio."));
                        }

                        com.radiotech.radiotech_backend.model.TaskStatus targetStatus = com.radiotech.radiotech_backend.model.TaskStatus
                                        .parse(request.get("status"));
                        if (targetStatus == null) {
                                return ResponseEntity
                                                .badRequest()
                                                .body(error("Stato task non valido: " + request.get("status")));
                        }

                        requirePermission(resolvePermissionFor(targetStatus), tenantId);

                        Task updated = taskService.updateStatus(
                                        id,
                                        request.get("status"),
                                        idempotencyKey);

                        return ResponseEntity.ok(
                                        success(
                                                        "Stato del task aggiornato.",
                                                        updated));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {

                        return internalError(
                                        "Errore durante l'aggiornamento dello stato.");
                }
        }

        @PatchMapping("/{id}/close")
        public ResponseEntity<?> closeTask(
                        @PathVariable String id,
                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
                try {
                        requirePermission(Permission.TASK_APPROVE, tenantId);
                        return ResponseEntity.ok(taskService.updateStatus(id, "CLOSED", idempotencyKey));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(error(e.getMessage()));
                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {
                        return internalError("Errore durante la chiusura del task.");
                }
        }

        @GetMapping("/operator/{operatorId}")
        public ResponseEntity<?> getTasksByOperator(
                        @PathVariable String operatorId) {

                try {

                        requirePermission(Permission.TASK_READ, null);

                        List<Task> tasks = taskService.getByOperator(
                                        operatorId);

                        return ResponseEntity.ok(
                                        success(
                                                        "Task dell'operatore recuperati.",
                                                        tasks));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {

                        return internalError(
                                        "Errore durante il recupero dei task dell'operatore.");
                }
        }

        @GetMapping("/antenna/{antennaId}")
        public ResponseEntity<?> getTasksByAntenna(
                        @PathVariable String antennaId) {

                try {

                        requirePermission(Permission.TASK_READ, null);

                        List<Task> tasks = taskService.getByAntenna(
                                        antennaId);

                        return ResponseEntity.ok(
                                        success(
                                                        "Task dell'antenna recuperati.",
                                                        tasks));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(error(e.getMessage()));

                } catch (SecurityException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
                } catch (Exception e) {

                        return internalError(
                                        "Errore durante il recupero dei task dell'antenna.");
                }
        }

        private void requirePermission(Permission permission, String tenantId) {
                Role role = SecurityContextAccessor.currentRole();
                if (!TenantAccessPolicy.canAccess(role, permission)) {
                        throw new SecurityException("Permessi insufficienti per l'operazione richiesta.");
                }

                TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), tenantId);
        }

        private Permission resolvePermissionFor(com.radiotech.radiotech_backend.model.TaskStatus status) {
                if (status == null) {
                        return Permission.TASK_START;
                }
                if (status.requiresApprovalPermission()) {
                        return Permission.TASK_APPROVE;
                }
                if (status.requiresCompletionPermission()) {
                        return Permission.TASK_COMPLETE;
                }
                if (status.requiresExecutionPermission()) {
                        return Permission.TASK_START;
                }
                return Permission.TASK_READ;
        }

        private String resolveTenant(String headerTenantId, String bodyTenantId) {
                String authenticatedTenant = SecurityContextAccessor.currentTenantId();
                TenantAccessPolicy.requireTenantAccess(authenticatedTenant, headerTenantId);
                return TenantAccessPolicy.requireTenantAccess(authenticatedTenant, bodyTenantId);
        }

        private String resolveTenant(String tenantId) {
                return resolveTenant(tenantId, null);
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

        private ResponseEntity<Map<String, Object>> internalError(
                        String message) {

                return ResponseEntity
                                .status(
                                                HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(error(message));
        }
}