package com.radiotech.radiotech_backend.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProCatalogTest {
 @Test void deviceRevokeRespectsGranularPermissionsBeforeAnyDatabaseAccess() {
  var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
  auth.setDetails(new com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails("admin","test@example.test","Test","tenant",null,List.of("devices:READ")));
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
  try {
   assertTrue(new ProService().catalog().getFirst().actions().isEmpty());
   assertThrows(SecurityException.class,()->new com.radiotech.radiotech_backend.controller.ProDeviceController().revoke("a".repeat(64),new ProService.Action(1,"op","REVOKE",0,"")));
  } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
 }
 @Test void rejectsMassAssignmentAndNonFiniteCosts() {
   var module=ProCatalog.module("clients");
   assertThrows(IllegalArgumentException.class,()->ProCatalog.validate(module,Map.of("name","Test","tenantId","other")));
   assertThrows(IllegalArgumentException.class,()->ProCatalog.validate(ProCatalog.module("costs"),Map.of("name","Cost","taskId","task","kind","LABOUR","amount",Double.NaN)));
 }
 @Test void rejectsFractionalStockAndInvalidDates() {
   assertThrows(IllegalArgumentException.class,()->ProCatalog.validate(ProCatalog.module("purchases"),Map.of("name","Ordine","supplierId","supplier","inventoryId","item","quantity",1.5,"unitPrice",2)));
   assertThrows(IllegalArgumentException.class,()->ProCatalog.validate(ProCatalog.module("contracts"),Map.of("name","SLA","clientId","client","responseMinutes",30,"resolutionMinutes",60,"endAt","bad")));
 }
}
