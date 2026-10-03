package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.RicambioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({ "/api/inventory", "/api/v1/inventory" })
public class InventoryController {
    private final RicambioService inventory;

    public InventoryController(RicambioService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            require(Permission.INVENTORY_READ);
            return ResponseEntity.ok(inventory.getAll());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nel caricamento del magazzino.");
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> request) {
        try {
            require(Permission.INVENTORY_WRITE);
            if (request == null) throw new IllegalArgumentException("Dati articolo obbligatori.");
            return ResponseEntity.status(HttpStatus.CREATED).body(inventory.createItem(request));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nella creazione dell'articolo.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        try {
            require(Permission.INVENTORY_WRITE);
            if (request == null) throw new IllegalArgumentException("Dati articolo obbligatori.");
            return ResponseEntity.ok(inventory.updateItem(id, request));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nell'aggiornamento dell'articolo.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> archive(@PathVariable String id) {
        try {
            require(Permission.INVENTORY_WRITE);
            return ResponseEntity.ok(inventory.archive(id));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nell'archiviazione dell'articolo.");
        }
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restore(@PathVariable String id) {
        try {
            require(Permission.INVENTORY_WRITE);
            return ResponseEntity.ok(inventory.restore(id));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nel ripristino dell'articolo.");
        }
    }

    @GetMapping("/movements")
    public ResponseEntity<?> movements(@RequestParam(defaultValue = "200") int limit) {
        try {
            require(Permission.INVENTORY_READ);
            return ResponseEntity.ok(inventory.getMovements(limit));
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nel caricamento dei movimenti.");
        }
    }

    @PostMapping("/{id}/movements")
    public ResponseEntity<?> movement(@PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        try {
            require(Permission.INVENTORY_WRITE);
            if (request == null) throw new IllegalArgumentException("Dati movimento obbligatori.");
            return ResponseEntity.ok(inventory.recordMovement(id, request));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nella registrazione del movimento.");
        }
    }

    private void require(Permission permission) {
        Role role = SecurityContextAccessor.currentRole();
        if (!TenantAccessPolicy.canAccess(role, permission)) {
            throw new SecurityException("Permessi insufficienti per il magazzino.");
        }
        TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("success", false,
                "message", message == null ? "Richiesta non valida." : message));
    }
}
