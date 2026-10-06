package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.model.Antenna;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.AntennaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read/resolve surface for radio stations. The Control Room uses the full list,
 * the mobile application resolves a scanned QR payload through
 * {@code GET /api/antennas/resolve/{reference}}.
 */
@RestController
@RequestMapping({ "/api/antennas", "/api/v1/antennas" })
public class AntennaController {

    private final AntennaService antennaService;

    public AntennaController(AntennaService antennaService) {
        this.antennaService = antennaService;
    }

    @GetMapping
    public ResponseEntity<?> all(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.ASSET_READ, tenantId);
            List<Antenna> antennas = antennaService.getAllAntennas();
            return ResponseEntity.ok(antennas);
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile recuperare le antenne.");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> one(@PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.ASSET_READ, tenantId);
            return ResponseEntity.ok(antennaService.getById(id));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile recuperare l'antenna.");
        }
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> history(@PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.ASSET_READ, tenantId);
            return ResponseEntity.ok(antennaService.getMaintenanceHistory(id));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile recuperare la storia dell'asset.");
        }
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<?> qr(@PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) throws Exception {
        requirePermission(Permission.ASSET_READ, tenantId);
        return ResponseEntity.ok(antennaService.generateQrData(id));
    }

    /**
     * Resolves a scanned station QR payload to the full antenna document.
     */
    @GetMapping("/resolve/{reference}")
    public ResponseEntity<?> resolve(@PathVariable String reference,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        try {
            requirePermission(Permission.ASSET_READ, tenantId);
            String cleaned = clean(reference);
            if (cleaned == null) {
                return error(HttpStatus.BAD_REQUEST, "Codice antenna obbligatorio.");
            }
            return ResponseEntity.ok(antennaService.findByCodeOrId(cleaned));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile risolvere il codice antenna.");
        }
    }

    /**
     * Accepts a raw QR string, a JSON payload like {"code":"ANT-..."} or a URL
     * with a {@code code} / {@code antennaId} query parameter.
     */
    private String clean(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        String value = reference.trim();

        if (value.contains("code=")) {
            value = value.substring(value.indexOf("code=") + 5);
        } else if (value.contains("antennaId=")) {
            value = value.substring(value.indexOf("antennaId=") + 10);
        }

        if (value.contains("&")) {
            value = value.substring(0, value.indexOf('&'));
        }

        value = value.replace("\"", "").replace("{", "").replace("}", "").trim();

        if (value.contains(":")) {
            String[] parts = value.split(":", 2);
            if (parts.length == 2 && parts[1].trim().length() > 0) {
                value = parts[1].trim();
            }
        }

        return value.isBlank() ? null : value;
    }

    private void requirePermission(Permission permission, String tenantId) {
        Role role = SecurityContextAccessor.currentRole();
        if (!TenantAccessPolicy.canAccess(role, permission)) {
            throw new SecurityException("Permessi insufficienti per l'operazione richiesta.");
        }

        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), tenantId);
    }

    private ResponseEntity<?> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
