package com.radiotech.radiotech_backend.controller;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import com.radiotech.radiotech_backend.service.ProService;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
@RestController
public class ProDeviceController {
 public record Registration(String installationId,String platform){}
 @PostMapping("/api/v1/session/devices") public Map<String,Object> register(@RequestBody Registration request,@RequestAttribute("firebaseToken") com.google.firebase.auth.FirebaseToken token)throws Exception{
  String tenant=TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null),uid=SecurityContextAccessor.currentUid();if(uid==null)throw new SecurityException("Identità richiesta.");
  long authenticatedAt=token.getClaims().get("auth_time") instanceof Number n?n.longValue():0;
  if(authenticatedAt<Instant.now().minusSeconds(300).getEpochSecond()||authenticatedAt>Instant.now().plusSeconds(60).getEpochSecond())throw new SecurityException("Accedi nuovamente per registrare il dispositivo.");
  if(request.installationId==null||!request.installationId.matches("[A-Za-z0-9_-]{16,128}")||request.platform==null||request.platform.length()>40)throw new IllegalArgumentException("Dispositivo non valido.");
  String id=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((tenant+":"+uid+":"+request.installationId).getBytes(java.nio.charset.StandardCharsets.UTF_8)));var db=FirestoreClient.getFirestore();var ref=db.collection("pro_devices").document(id);var now=Instant.now();
  db.runTransaction(tx->{var previous=tx.get(ref).get();if(previous.exists()&&"REVOKED".equals(previous.getString("status"))){long authenticated=token.getClaims().get("auth_time") instanceof Number n?n.longValue():0;if(authenticated<=Instant.parse(previous.getString("revokedAt")).getEpochSecond())throw new SecurityException("Accedi nuovamente per riattivare il dispositivo.");}
   var data=new LinkedHashMap<String,Object>();data.put("tenantId",tenant);data.put("uid",uid);data.put("createdBy",uid);data.put("name",request.platform);data.put("platform",request.platform);data.put("status","ACTIVE");data.put("version",previous.exists()?((Number)previous.get("version")).longValue()+1:1L);data.put("createdAt",previous.exists()?previous.get("createdAt"):now.toString());data.put("updatedAt",now.toString());data.put("lastLoginAt",now.toString());tx.set(ref,data);return true;
  }).get();
  boolean mfa=token.getClaims().get("firebase") instanceof Map<?,?> firebase&&firebase.get("sign_in_second_factor")!=null;
  var deviceClaims=new LinkedHashMap<String,Object>(Map.of("radioDeviceId",id,"radioMfaVerified",mfa));if(token.getClaims().get("operatorId") instanceof String operatorId)deviceClaims.put("operatorId",operatorId);
  return Map.of("customToken",FirebaseAuth.getInstance().createCustomToken(uid,deviceClaims),"deviceId",id);
 }
 @PostMapping("/api/v1/pro/devices/{id}/actions") public Map<String,Object> revoke(@PathVariable String id,@RequestBody ProService.Action request)throws Exception{
  new ProService().requireModulePermission("devices","REVOKE");
  String tenant=TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(),null),uid=SecurityContextAccessor.currentUid();if(!id.matches("[a-f0-9]{64}")||!"REVOKE".equals(request.action()))throw new IllegalArgumentException("Azione non valida.");boolean manager=Set.of(Role.ADMIN,Role.SUPER_ADMIN,Role.CHIEF_EXECUTIVE).contains(SecurityContextAccessor.currentRole());var db=FirestoreClient.getFirestore();var ref=db.collection("pro_devices").document(id);
  return db.runTransaction(tx->{var doc=tx.get(ref).get();if(!doc.exists()||!tenant.equals(doc.get("tenantId"))||(!manager&&!uid.equals(doc.get("uid"))))throw new SecurityException("Dispositivo non autorizzato.");var data=new LinkedHashMap<>(doc.getData());if("REVOKED".equals(data.get("status")))return data;if(((Number)data.get("version")).longValue()!=request.expectedVersion())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Ricarica i dispositivi.");data.put("status","REVOKED");data.put("revokedAt",Instant.now().toString());data.put("updatedAt",Instant.now().toString());data.put("version",((Number)data.get("version")).longValue()+1);tx.set(ref,data);tx.set(db.collection("auditLogs").document(),Map.of("tenantId",tenant,"actor",uid,"action","DEVICE_REVOKE","resource","devices","resourceId",id,"result","SUCCESS","timestamp",Instant.now().toString()));return data;}).get();
 }
}
