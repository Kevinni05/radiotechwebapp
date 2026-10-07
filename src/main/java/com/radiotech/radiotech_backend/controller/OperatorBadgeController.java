package com.radiotech.radiotech_backend.controller;



import com.radiotech.radiotech_backend.model.Operator;

import com.radiotech.radiotech_backend.security.*;

import com.radiotech.radiotech_backend.service.*;

import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.*;

import org.springframework.web.client.RestClient;

import java.util.*;



/** Badge operations always resolve the tenant and owner from the verified session. */

@RestController

public class OperatorBadgeController {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(OperatorBadgeController.class);

    private final OperatorService operators;

    private final QrCodeService qr;

    private final RestClient client;

    @Value("${radiotech.reports.public-base-url:}") private String publicUrl;

    @Value("${radiotech.firebase.web-api-key:}") private String apiKey;

    public OperatorBadgeController(OperatorService operators, QrCodeService qr, RestClient client) {

        this.operators = operators; this.qr = qr; this.client = client;

    }

    private Operator own() throws Exception {

        Operator o = operators.getByFirebaseUid(SecurityContextAccessor.currentUid());

        if (o == null || !"ATTIVO".equalsIgnoreCase(o.getStatus())) throw new SecurityException("Profilo operatore non attivo.");

        return o;

    }

    private Map<String,Object> badge(Operator o, boolean renew) throws Exception {

        if (renew) o = operators.personalBadge(o.getId());
        String payload = o.getQrCodeToken();

        if (publicUrl != null && publicUrl.startsWith("https://"))

            payload = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("type", "radiotech-operator", "version", 2, "token", payload, "serverUrl", publicUrl.replaceAll("/+$", "")));

        Map<String,Object> result = new LinkedHashMap<>();

        result.put("payload", payload); result.put("imageDataUrl", qr.toDataUri(payload));

        result.put("expiresAt", o.getQrExpiresAt()); result.put("validityMode", Objects.requireNonNullElse(o.getQrValidityMode(), "DEFAULT"));

        result.put("revision", o.getUpdatedAt()); return result;

    }

    @GetMapping({"/api/operator/me/badge", "/api/v1/operator/me/badge"})

    public ResponseEntity<?> mine() { return run(() -> badge(own(), true)); }

    @PutMapping({"/api/operators/{id}/qr-validity", "/api/v1/operators/{id}/qr-validity"})

    public ResponseEntity<?> validity(@PathVariable String id, @RequestBody Map<String,String> request) {

        return run(() -> { if (!TenantAccessPolicy.canAccess(SecurityContextAccessor.currentRole(), Permission.USER_UPDATE)) throw new SecurityException("Permesso insufficiente.");

            return badge(operators.configureQrValidity(id, request.get("mode"), request.get("expiresAt")), false); });

    }

    @PostMapping({"/api/operator/me/reset-password", "/api/v1/operator/me/reset-password"})

    public ResponseEntity<?> resetOwn() { return run(() -> reset(own())); }

    @PostMapping({"/api/operators/{id}/reset-password", "/api/v1/operators/{id}/reset-password"})

    public ResponseEntity<?> resetOther(@PathVariable String id) {

        return run(() -> { if (!TenantAccessPolicy.canAccess(SecurityContextAccessor.currentRole(), Permission.USER_UPDATE)) throw new SecurityException("Permesso insufficiente."); return reset(operators.getById(id)); });

    }

    private Map<String,Object> reset(Operator o) throws Exception {

        if (o.getEmail() == null || o.getEmail().isBlank()) throw new IllegalArgumentException("Email operatore non disponibile.");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Servizio email di reimpostazione non configurato.");

        // Generate/validate the next badge BEFORE sending mail (fixed expiry may have elapsed).

        if ("FIXED".equals(o.getQrValidityMode()) && OperatorService.badgeExpired(o.getQrFixedExpiresAt(), "FIXED"))

            throw new IllegalArgumentException("Aggiorna prima la scadenza fissa del badge.");

        client.post().uri("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key={key}", apiKey)

            .contentType(MediaType.APPLICATION_JSON).body(Map.of("requestType", "PASSWORD_RESET", "email", o.getEmail())).retrieve().toBodilessEntity();

        operators.regenerateQrToken(o.getId());

        return Map.of("message", "Email di reimpostazione inviata e badge rigenerato.");

    }

    private interface Operation { Object call() throws Exception; }

    private ResponseEntity<?> run(Operation op) {

        try { return ResponseEntity.ok(op.call()); }

        catch (SecurityException e) { return ResponseEntity.status(403).body(Map.of("message", e.getMessage())); }

        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }

        catch (Exception e) { log.warn("Operazione badge non completata",e); String message=com.radiotech.radiotech_backend.exception.CloudQuota.exhausted(e)?com.radiotech.radiotech_backend.exception.CloudQuota.MESSAGE:"Operazione non completata. Riprova tra poco."; return ResponseEntity.status(503).body(Map.of("message",message)); }

    }

}

