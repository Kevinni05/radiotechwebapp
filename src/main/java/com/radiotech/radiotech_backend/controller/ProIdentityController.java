package com.radiotech.radiotech_backend.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import com.radiotech.radiotech_backend.service.AuditService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/pro/security")
public class ProIdentityController {
  private final AuditService audit;

  public ProIdentityController(AuditService audit) {
    this.audit = audit;
  }

  private String tenant() {
    String tenant = TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    if (!Set.of(Role.ADMIN, Role.SUPER_ADMIN, Role.CHIEF_EXECUTIVE).contains(SecurityContextAccessor.currentRole()))
      throw new SecurityException("Amministratore richiesto.");
    return tenant;
  }

  @GetMapping
  public Map<String, Object> status() {
    tenant();
    return Map.of("revocationChecked", true, "providers", List.of("google.com", "microsoft.com"), "providerActivation",
        "Abilitare il provider nella console Firebase e configurare l'identità aziendale.", "mfaActivation",
        "MFA gestita richiede configurazione Firebase Identity Platform; non viene attivata con costi durante il test gratuito.",
        "portalPath", "/portal");
  }

  public record Binding(String role, String customerId, boolean mfaRequired, List<String> proPermissions) {
  }

  @PutMapping("/accounts/{uid}")
  public Map<String, Object> bind(@PathVariable String uid, @RequestBody Binding request) throws Exception {
    String tenant = tenant();
    if (uid == null || uid.length() > 128 || uid.contains("/"))
      throw new IllegalArgumentException("UID non valido.");
    Role role = Role.parse(request.role);
    if (!Set.of(Role.CUSTOMER, Role.OPERATOR, Role.VIEWER, Role.NETWORK_MANAGER, Role.ENGINEER).contains(role))
      throw new IllegalArgumentException("Ruolo non assegnabile da questa sezione.");
    var auth = FirebaseAuth.getInstance();
    var user = auth.getUser(uid);
    var before = new LinkedHashMap<>(user.getCustomClaims());
    if (before.get("tenantId") != null && !tenant.equals(before.get("tenantId")))
      throw new SecurityException("Account appartenente ad un'altra azienda.");
    if (Set.of(Role.ADMIN,Role.SUPER_ADMIN,Role.CHIEF_EXECUTIVE).contains(Role.fromClaims(before)))
      throw new SecurityException("Usare la gestione amministratori per questo account.");
    if (role == Role.CUSTOMER) {
      if (request.customerId == null || !request.customerId.matches("[A-Za-z0-9_-]{1,128}"))
        throw new IllegalArgumentException("Cliente obbligatorio.");
      var client = FirestoreClient.getFirestore().collection("pro_clients").document(request.customerId).get().get();
      if (!client.exists() || !tenant.equals(client.getString("tenantId")))
        throw new SecurityException("Cliente non autorizzato.");
    }
    var after = new LinkedHashMap<>(before);
    after.put("tenantId", tenant);
    after.put("role", role.name());
    after.put("mfaRequired", request.mfaRequired);
    if(request.proPermissions!=null){if(request.proPermissions.size()>40)throw new IllegalArgumentException("Troppi permessi: massimo 40.");for(String p:request.proPermissions){String[] parts=p.split(":");if(parts.length!=2)throw new IllegalArgumentException("Permesso non valido.");var module=com.radiotech.radiotech_backend.service.ProCatalog.module(parts[0]);if(!Set.of("READ","WRITE").contains(parts[1])&&!module.actions().contains(parts[1]))throw new IllegalArgumentException("Permesso non valido.");}after.put("proPermissions",request.proPermissions);}else after.remove("proPermissions");
    if (role == Role.CUSTOMER)
      after.put("customerId", request.customerId);
    else
      after.remove("customerId");
    auth.setCustomUserClaims(uid, after);
    auth.revokeRefreshTokens(uid);
    audit.record("IDENTITY_BIND", tenant, SecurityContextAccessor.currentUid(), "users", uid, "SUCCESS", before, after);
    return Map.of("uid", uid, "role", role.name(), "reauthenticationRequired", true);
  }

  @PostMapping("/accounts/{uid}/revoke")
  public Map<String, Object> revoke(@PathVariable String uid) throws Exception {
    String tenant = tenant();
    var auth = FirebaseAuth.getInstance();
    var user = auth.getUser(uid);
    if (!tenant.equals(user.getCustomClaims().get("tenantId")))
      throw new SecurityException("Account non autorizzato.");
    auth.revokeRefreshTokens(uid);
    audit.record("SESSION_REVOKE", tenant, SecurityContextAccessor.currentUid(), "users", uid, "SUCCESS", null, null);
    return Map.of("revoked", true);
  }
}
