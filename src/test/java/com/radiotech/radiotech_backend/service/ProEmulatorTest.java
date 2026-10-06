package com.radiotech.radiotech_backend.service;
import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class ProEmulatorTest {
 @AfterEach void clean(){SecurityContextHolder.clearContext();}
 @Test void tenantIsolationIdempotencyAndVersionConflict() throws Exception {
   String tenant="pro-"+UUID.randomUUID(); identity("admin","ADMIN",tenant);
   try(var db=database();var firebase=mockStatic(FirestoreClient.class)){
    firebase.when(FirestoreClient::getFirestore).thenReturn(db);var service=new ProService();
    var change=new ProService.Change(0,UUID.randomUUID().toString(),Map.of("name","Client"));
    var record=service.save("clients",null,change); assertEquals(record,service.save("clients",null,change));
    String id=record.get("id").toString();
    assertThrows(ResponseStatusException.class,()->service.save("clients",id,new ProService.Change(0,UUID.randomUUID().toString(),Map.of("name","Changed"))));
    identity("admin-b","ADMIN",tenant+"-other");assertTrue(((List<?>)service.list("clients").get("records")).isEmpty());
    assertThrows(SecurityException.class,()->service.action("clients",id,new ProService.Action(1,UUID.randomUUID().toString(),"ARCHIVE",0,"")));
    identity("operator","OPERATOR",tenant);assertThrows(SecurityException.class,()->service.list("clients"));
   }
 }
 @Test void purchasesRequireIndependentApprovalAndReceiptIsExactlyOnce() throws Exception {
   String tenant="stock-"+UUID.randomUUID();identity("buyer","ADMIN",tenant);
   try(var db=database();var firebase=mockStatic(FirestoreClient.class)){
    firebase.when(FirestoreClient::getFirestore).thenReturn(db);var service=new ProService();
    String item=tenant+"-item";db.collection("inventory").document(item).set(Map.of("tenantId",tenant,"quantity",10,"active",true)).get();
    var supplier=service.save("suppliers",null,new ProService.Change(0,UUID.randomUUID().toString(),Map.of("name","Supplier")));
    var purchase=service.save("purchases",null,new ProService.Change(0,UUID.randomUUID().toString(),Map.of("name","Order","supplierId",supplier.get("id"),"inventoryId",item,"quantity",3,"unitPrice",10)));
    String id=purchase.get("id").toString();service.action("purchases",id,new ProService.Action(1,UUID.randomUUID().toString(),"SUBMIT",0,""));
    assertThrows(IllegalArgumentException.class,()->service.action("purchases",id,new ProService.Action(2,UUID.randomUUID().toString(),"APPROVE",0,"")));
    identity("approver","ADMIN",tenant);service.action("purchases",id,new ProService.Action(2,UUID.randomUUID().toString(),"APPROVE",0,""));
    var receive=new ProService.Action(3,UUID.randomUUID().toString(),"RECEIVE",0,"");service.action("purchases",id,receive);service.action("purchases",id,receive);
    assertEquals(13,((Number)db.collection("inventory").document(item).get().get().get("quantity")).intValue());
   }
 }
 @Test void customerScopeRejectsAnotherClientsSitesAndInternalDocuments()throws Exception{
  String tenant="portal-"+UUID.randomUUID();identity("admin","ADMIN",tenant);
  try(var db=database();var firebase=mockStatic(FirestoreClient.class)){
   firebase.when(FirestoreClient::getFirestore).thenReturn(db);var service=new ProService();
   for(String c:List.of("a","b")){db.collection("pro_clients").document(tenant+c).set(Map.of("tenantId",tenant,"name",c)).get();db.collection("pro_sites").document(tenant+"site"+c).set(Map.of("tenantId",tenant,"clientId",tenant+c,"name","Site "+c)).get();}
   db.collection("pro_documents").document(tenant+"private").set(Map.of("tenantId",tenant,"clientId",tenant+"a","status","PUBLISHED","audience","INTERNAL","name","secret")).get();
   db.collection("pro_documents").document(tenant+"public").set(Map.of("tenantId",tenant,"clientId",tenant+"a","status","PUBLISHED","audience","CUSTOMER","name","manual")).get();
   var auth=new UsernamePasswordAuthenticationToken("customer-a",null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));auth.setDetails(new FirebaseAuthenticationDetails("customer-a","a@example.test","Customer",tenant,tenant+"a"));SecurityContextHolder.getContext().setAuthentication(auth);
   assertEquals(1,((List<?>)service.list("sites").get("records")).size());assertEquals(1,((List<?>)service.list("documents").get("records")).size());assertThrows(SecurityException.class,()->service.list("costs"));
   assertThrows(SecurityException.class,()->service.save("requests",null,new ProService.Change(0,UUID.randomUUID().toString(),Map.of("name","Help","clientId",tenant+"a","siteId",tenant+"siteb","priority","HIGH","description","fault"))));
   assertThrows(SecurityException.class,()->service.taskPack("any-task"));
  }
 }
 @Test void dispatchDetectsOverlapAndArchiveReleasesTaskReservation()throws Exception{
  String tenant="dispatch-"+UUID.randomUUID();identity("admin","ADMIN",tenant);
  try(var db=database();var firebase=mockStatic(FirestoreClient.class)){
   firebase.when(FirestoreClient::getFirestore).thenReturn(db);var service=new ProService();String operator=tenant+"op";
   db.collection("operators").document(operator).set(Map.of("tenantId",tenant,"firebaseUid","tech","name","Tech")).get();
   for(String t:List.of("a","b"))db.collection("tasks").document(tenant+t).set(Map.of("tenantId",tenant,"title","Task "+t,"status","ASSIGNED","operatorId",operator)).get();
   var fields=new LinkedHashMap<String,Object>(Map.of("name","Booking","taskId",tenant+"a","operatorId",operator,"startAt","2026-10-06T10:00:00Z","endAt","2026-10-06T11:00:00Z","availabilityOverride","NO"));
   var first=service.save("dispatch",null,new ProService.Change(0,UUID.randomUUID().toString(),fields));String firstId=first.get("id").toString();service.action("dispatch",firstId,new ProService.Action(1,UUID.randomUUID().toString(),"ASSIGN",0,""));
   fields.put("taskId",tenant+"b");var second=service.save("dispatch",null,new ProService.Change(0,UUID.randomUUID().toString(),fields));String secondId=second.get("id").toString();
   assertThrows(IllegalArgumentException.class,()->service.action("dispatch",secondId,new ProService.Action(1,UUID.randomUUID().toString(),"ASSIGN",0,"")));
   service.action("dispatch",firstId,new ProService.Action(2,UUID.randomUUID().toString(),"ARCHIVE",0,""));assertEquals("ASSIGNED",service.action("dispatch",secondId,new ProService.Action(1,UUID.randomUUID().toString(),"ASSIGN",0,"")).get("status"));
  }
 }
 @Test void encryptedBackupCanRestoreNestedDocumentsInDemoEmulator()throws Exception{
  try(var db=database()){
   String id="restore-"+UUID.randomUUID();db.collection("backupDrill").document(id).collection("nested").document("child").set(Map.of("tenantId","drill","count",Long.MAX_VALUE,"point",new GeoPoint(45,9))).get();
   var backup=com.radiotech.radiotech_backend.ops.BackupTool.snapshot(db);var records=(List<Map<String,Object>>)backup.get("documents");records.removeIf(r->!r.get("path").toString().startsWith("backupDrill/"+id+"/"));
   byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var restored=mapper.readValue(com.radiotech.radiotech_backend.ops.BackupTool.decrypt(com.radiotech.radiotech_backend.ops.BackupTool.encrypt(mapper.writeValueAsBytes(backup),key),key),Map.class);
   db.collection("backupDrill").document(id).collection("nested").document("child").delete().get();com.radiotech.radiotech_backend.ops.BackupTool.restoreDocuments(restored,db);
   assertEquals(Long.MAX_VALUE,db.collection("backupDrill").document(id).collection("nested").document("child").get().get().getLong("count"));
  }
 }
 @Test void managerAllocatedVanStockIsVisibleOnlyToItsTechnicianAndMovesAtomically()throws Exception{
  String tenant="van-"+UUID.randomUUID();identity("manager","ADMIN",tenant);
  try(var db=database();var firebase=mockStatic(FirestoreClient.class)){
   firebase.when(FirestoreClient::getFirestore).thenReturn(db);var service=new ProService();String operator=tenant+"op",item=tenant+"item";
   db.collection("operators").document(operator).set(Map.of("tenantId",tenant,"firebaseUid","technician","name","Tech")).get();
   db.collection("inventory").document(item).set(Map.of("tenantId",tenant,"quantity",10,"active",true)).get();
   var van=service.save("vanstock",null,new ProService.Change(0,UUID.randomUUID().toString(),Map.of("name","Kit","operatorId",operator,"inventoryId",item)));String id=van.get("id").toString();
   service.action("vanstock",id,new ProService.Action(1,UUID.randomUUID().toString(),"LOAD",4,""));
   identity("other-tech","OPERATOR",tenant);assertTrue(((List<?>)service.list("vanstock").get("records")).isEmpty());
   identity("technician","OPERATOR",tenant);var stockRecords=(List<Map<String,Object>>)service.list("vanstock").get("records");assertEquals(1,stockRecords.size());assertEquals(4.0,((Map<?,?>)stockRecords.getFirst().get("summary")).get("Disponibile"));
   service.action("vanstock",id,new ProService.Action(2,UUID.randomUUID().toString(),"RESERVE",2,""));
   var consume=new ProService.Action(3,UUID.randomUUID().toString(),"CONSUME",2,"");service.action("vanstock",id,consume);service.action("vanstock",id,consume);
   service.action("vanstock",id,new ProService.Action(4,UUID.randomUUID().toString(),"RETURN",2,""));
   assertEquals(8,((Number)db.collection("inventory").document(item).get().get().get("quantity")).intValue());
   assertEquals(0,((Number)db.collection("pro_vanstock").document(id).get().get().get("quantity")).intValue());
  }
 }
 private Firestore database(){return FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator-only",new Date(Long.MAX_VALUE)))).build().getService();}
 private void identity(String uid,String role,String tenant){var auth=new UsernamePasswordAuthenticationToken(uid,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));auth.setDetails(new FirebaseAuthenticationDetails(uid,"test@example.test","Test",tenant));SecurityContextHolder.getContext().setAuthentication(auth);}
}
