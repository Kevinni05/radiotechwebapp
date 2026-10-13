package com.radiotech.radiotech_backend.controller;

import com.google.firebase.auth.FirebaseToken;
import com.radiotech.radiotech_backend.security.IdentityGateway;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/chief-invitations")
public class ChiefInvitationController {
    private void requireChief() {
        if (!Set.of(Role.CHIEF_EXECUTIVE, Role.ADMIN, Role.SUPER_ADMIN).contains(SecurityContextAccessor.currentRole()))
            throw new SecurityException("Account Capo richiesto.");
    }
    private String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) throw new SecurityException("Accesso richiesto.");
        return header.substring(7);
    }
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("Authorization") String authorization) {
        return run(() -> { requireChief(); return IdentityGateway.call("invitations", Map.of("idToken", bearer(authorization))); });
    }
    @PostMapping
    public ResponseEntity<?> invite(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, String> request) {
        return run(() -> {
            requireChief();
            String address = request.get("email"), name = request.get("name");
            if (address == null || name == null) throw new IllegalArgumentException("Inserisci nome ed email.");
            return IdentityGateway.call("invite", Map.of("idToken", bearer(authorization), "email", address, "name", name,
                    "role", request.getOrDefault("role", "CHIEF_EXECUTIVE")));
        });
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancel(@RequestHeader("Authorization") String authorization, @PathVariable String id) {
        return run(() -> { requireChief(); return IdentityGateway.call("cancel", Map.of("idToken", bearer(authorization), "id", id)); });
    }
    private ResponseEntity<?> run(java.util.function.Supplier<Object> work) {
        try { return ResponseEntity.ok(work.get()); }
        catch (SecurityException e) { return ResponseEntity.status(403).body(Map.of("message", "Operazione non autorizzata.")); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
        catch (Exception e) { return ResponseEntity.status(503).body(Map.of("message", "Gli inviti richiedono il servizio accessi protetti configurato.")); }
    }
}
