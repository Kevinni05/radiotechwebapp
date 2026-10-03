package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.service.DashboardService;
import com.radiotech.radiotech_backend.service.NotificationService;
import com.radiotech.radiotech_backend.service.CapoService;
import com.radiotech.radiotech_backend.service.AntennaService;
import com.radiotech.radiotech_backend.service.AuditService;
import com.radiotech.radiotech_backend.service.TaskService;
import com.radiotech.radiotech_backend.service.RicambioService;
import com.radiotech.radiotech_backend.exception.EntityNotFoundException;
import com.radiotech.radiotech_backend.dto.AntennaDto;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({ "/api/dashboard", "/api/v1/dashboard" })
public class DashboardController {

    private final DashboardService dashboardService;
    private final NotificationService notificationService;
    private final CapoService capoService;
    private final AntennaService antennaService;
    private final AuditService auditService;
    private final TaskService taskService;
    private final RicambioService ricambioService;

    public DashboardController(
            DashboardService dashboardService,
            NotificationService notificationService,
            CapoService capoService,
            AntennaService antennaService,
            AuditService auditService,
            TaskService taskService,
            RicambioService ricambioService) {

        this.dashboardService = dashboardService;
        this.notificationService = notificationService;
        this.capoService = capoService;
        this.antennaService = antennaService;
        this.auditService = auditService;
        this.taskService = taskService;
        this.ricambioService = ricambioService;
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.ANALYTICS_READ, tenantId);
            return ResponseEntity.ok(notificationService.getHistory());
        } catch (Exception e) {
            return internalError("Errore nel caricamento delle notifiche", e);
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestAttribute("firebaseUid") String uid) {
        try {
            return ResponseEntity.ok(capoService.getProfile(uid));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse("Profilo non disponibile."));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        try {
            requirePermission(Permission.ANALYTICS_READ, tenantId);

            return ResponseEntity.ok(
                    dashboardService.getDashboardStats());

        } catch (Exception e) {

            return internalError(
                    "Errore nel caricamento delle statistiche",
                    e);
        }
    }

    @GetMapping("/audit")
    public ResponseEntity<?> getAudit(@RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.AUDIT_READ, tenantId);
            return ResponseEntity.ok(auditService.recent(limit, SecurityContextAccessor.currentTenantId()));
        } catch (Exception e) {
            return internalError("Errore nel caricamento dell'audit", e);
        }
    }

    @PostMapping("/antenne")
    public ResponseEntity<?> createAntenna(@Valid @RequestBody AntennaDto request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.ASSET_CREATE, tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(antennaService.createAntenna(request.toModel()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        } catch (Exception e) {
            return internalError("Errore nella creazione dell'antenna", e);
        }
    }

    @GetMapping("/antenne")
    public ResponseEntity<?> getAntennas() {

        try {
            requirePermission(Permission.ASSET_READ, null);

            return ResponseEntity.ok(
                    dashboardService.getAntennas());

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));
        } catch (Exception e) {

            return internalError(
                    "Errore nel caricamento delle antenne",
                    e);
        }
    }

    @GetMapping("/operatori")
    public ResponseEntity<?> getOperators() {

        try {
            requirePermission(Permission.USER_READ, null);

            return ResponseEntity.ok(
                    dashboardService.getOperators());

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));
        } catch (Exception e) {

            return internalError(
                    "Errore nel caricamento degli operatori",
                    e);
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> getTasks() {

        try {
            requirePermission(Permission.TASK_READ, null);

            return ResponseEntity.ok(
                    dashboardService.getTasks());

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));
        } catch (Exception e) {

            return internalError(
                    "Errore nel caricamento dei task",
                    e);
        }
    }

    @GetMapping("/interventi")
    public ResponseEntity<?> getInterventions() {

        try {
            requirePermission(Permission.ANALYTICS_READ, null);

            return ResponseEntity.ok(
                    dashboardService.getInterventions());

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));
        } catch (Exception e) {

            return internalError(
                    "Errore nel caricamento degli interventi",
                    e);
        }
    }

    @GetMapping("/inventario")
    public ResponseEntity<?> getInventory() {

        try {
            requirePermission(Permission.INVENTORY_READ, null);

            return ResponseEntity.ok(
                    dashboardService.getInventory());

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));
        } catch (Exception e) {

            return internalError(
                    "Errore nel caricamento dell'inventario",
                    e);
        }
    }

    @GetMapping("/inventario/scorte-basse")
    public ResponseEntity<?> getLowStockInventory() {

        try {
            requirePermission(Permission.INVENTORY_READ, null);

            return ResponseEntity.ok(
                    dashboardService
                            .getLowStockInventory());

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));
        } catch (Exception e) {

            return internalError(
                    "Errore nel caricamento delle scorte basse",
                    e);
        }
    }

    @PutMapping("/antenne/{id}")
    public ResponseEntity<?> updateAntenna(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {

        try {
            requirePermission(Permission.ASSET_UPDATE, null);

            return ResponseEntity.ok(
                    dashboardService.updateAntenna(
                            id,
                            updates));

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse(
                            e.getMessage()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));

        } catch (Exception e) {

            return internalError(
                    "Errore nella modifica dell'antenna",
                    e);
        }
    }

    @DeleteMapping("/antenne/{id}")
    public ResponseEntity<?> deleteAntenna(
            @PathVariable String id) {

        try {
            requirePermission(Permission.ASSET_DELETE, null);

            dashboardService.deleteAntenna(id);

            Map<String, Object> response = new HashMap<>();

            response.put(
                    "success",
                    true);

            response.put(
                    "message",
                    "Antenna eliminata correttamente");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(
                            e.getMessage()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));

        } catch (Exception e) {

            return internalError(
                    "Errore nell'eliminazione dell'antenna",
                    e);
        }
    }

    @PutMapping("/task/{id}/stato")
    public ResponseEntity<?> updateTaskStatus(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        try {
            if (body == null) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse(
                                "Il body della richiesta è obbligatorio."));
            }

            Object statusValue = body.get("status");

            if (statusValue == null) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse(
                                "Il campo 'status' è obbligatorio."));
            }

            com.radiotech.radiotech_backend.model.TaskStatus targetStatus = com.radiotech.radiotech_backend.model.TaskStatus
                    .parse(statusValue.toString());
            if (targetStatus == null) {
                return ResponseEntity.badRequest().body(errorResponse("Stato task non valido."));
            }
            requirePermission(resolvePermissionFor(targetStatus), null);
            return ResponseEntity.ok(taskService.updateStatus(id, targetStatus.name()));

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse(
                            e.getMessage()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));

        } catch (Exception e) {

            return internalError(
                    "Errore nella modifica dello stato del task",
                    e);
        }
    }

    @PutMapping("/inventario/{id}/quantita")
    public ResponseEntity<?> updateInventoryQuantity(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        try {
            requirePermission(Permission.INVENTORY_WRITE, null);

            if (body == null ||
                    body.get("quantity") == null) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse(
                                "Il campo 'quantity' è obbligatorio."));
            }

            int quantity;

            Object value = body.get("quantity");

            if (value instanceof Number number) {

                quantity = number.intValue();

            } else {

                quantity = Integer.parseInt(
                        value.toString());
            }

            return ResponseEntity.ok(ricambioService.setQuantity(id, quantity));

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .badRequest()
                    .body(errorResponse(
                            "La quantità deve essere un numero intero."));

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse(
                            e.getMessage()));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(e.getMessage()));

        } catch (Exception e) {

            return internalError(
                    "Errore nella modifica dell'inventario",
                    e);
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
        if (status.requiresApprovalPermission()) {
            return Permission.TASK_APPROVE;
        }
        if (status.requiresCompletionPermission()) {
            return Permission.TASK_COMPLETE;
        }
        return Permission.TASK_START;
    }

    private ResponseEntity<Map<String, Object>> internalError(
            String message,
            Exception exception) {

        if (exception instanceof SecurityException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorResponse("Permesso insufficiente."));
        }
        if (exception instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(errorResponse(exception.getMessage()));
        }
        if (exception instanceof EntityNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse(exception.getMessage()));
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse(message));
    }

    private Map<String, Object> errorResponse(
            String message) {

        Map<String, Object> error = new HashMap<>();

        error.put(
                "success",
                false);

        error.put(
                "message",
                message);

        return error;
    }
}
