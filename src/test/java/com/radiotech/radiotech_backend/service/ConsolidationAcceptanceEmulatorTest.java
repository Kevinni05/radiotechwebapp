package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class ConsolidationAcceptanceEmulatorTest {
 private Firestore database(){return FirestoreOptions.newBuilder().setProjectId("demo-radiotech").setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator",new Date(Long.MAX_VALUE)))).build().getService();}
 @Test void resourceDeleteAndAuditAreAtomicAndRejectAnotherTenant()throws Exception{
  String tenant="delete-qa-"+UUID.randomUUID();
  try(var db=database()){
   var ref=db.collection("antennas").document(tenant);ref.set(Map.of("tenantId",tenant,"name","Synthetic")).get();
   assertThrows(Exception.class,()->AuditService.deleteResource(db,"antennas",tenant,"other","admin"));
   assertTrue(ref.get().get().exists());assertEquals(0,db.collection("auditLogs").whereEqualTo("tenantId",tenant).get().get().size());
   AuditService.deleteResource(db,"antennas",tenant,tenant,"admin");
   assertFalse(ref.get().get().exists());assertEquals(1,db.collection("auditLogs").whereEqualTo("tenantId",tenant).get().get().size());
  }
 }
 @Test void reportReferenceBindingIsTenantScopedAndCannotRaceCleanup()throws Exception{
  String tenant="file-qa-"+UUID.randomUUID(),id="a".repeat(64);String reference="radiotech-file:"+id;
  try(var db=database();var client=mockStatic(FirestoreClient.class)){
   client.when(FirestoreClient::getFirestore).thenReturn(db);
   var env=new MockEnvironment().withProperty("radiotech.attachments.backend","FIRESTORE");env.setActiveProfiles("production");var service=new LocalAttachmentService(env);
   var ref=db.collection("reportFiles").document(id);ref.set(Map.of("tenantId",tenant,"uid","tech","size",10L)).get();
   try {
    db.runTransaction(tx->{service.bindToReport(db,tx,List.of(reference),tenant,"tech","report");return null;}).get();
    assertEquals(List.of("report"),ref.get().get().get("referencedBy"));
    assertThrows(Exception.class,()->db.runTransaction(tx->{service.bindToReport(db,tx,List.of(reference),"foreign","tech","other-report");return null;}).get());
    ref.update("cleanupState","DELETING").get();
    assertThrows(Exception.class,()->db.runTransaction(tx->{service.bindToReport(db,tx,List.of(reference),tenant,"tech","late-report");return null;}).get());
    assertEquals(List.of("report"),ref.get().get().get("referencedBy"));
   }finally{ref.delete().get();}
  }
 }
 @Test void projectedSummaryExcludesHistoricalTasksFromSlaAndRequiresReadyTenant()throws Exception{
  String tenant="summary-qa-"+UUID.randomUUID();
  try(var db=database()){
   var ref=db.collection("dashboardSummaries").document(tenant);
   ref.set(Map.of("tenantId",tenant,"schemaVersion",1L,"counts",Map.of("antennas",1000L,"activeAntennas",950L,"tasks",10001L,"completed",10000L,"activeTasks",1L,"pendingReports",2L))).get();
   assertNull(DashboardProjectionReader.read(db,tenant,30));
   ref.update("ready",true).get();ref.collection("activeTasks").document("active").set(Map.of("dueAt","2000-01-01T00:00:00Z")).get();
   var summary=DashboardProjectionReader.read(db,tenant,30);
   assertEquals(1000,summary.get("antennas"));assertEquals(95.0,summary.get("availability"));assertEquals(2L,summary.get("pendingReports"));assertEquals(1,((Map<?,?>)summary.get("taskStats")).get("overdue"));
   ref.update("tenantId","foreign").get();assertThrows(IllegalStateException.class,()->DashboardProjectionReader.read(db,tenant,30));
   ref.collection("activeTasks").document("active").delete().get();ref.delete().get();
  }
 }
 @Test void deletedAuthIdentityRecoveryFinishesOnlyTheMatchingTenantProfile()throws Exception{
  String tenant="identity-delete-qa-"+UUID.randomUUID();
  try(var db=database()){
   var operator=db.collection("operators").document(tenant);operator.set(Map.of("tenantId",tenant,"firebaseUid","deleted-user")).get();
   var event=db.collection("auditLogs").document();event.set(Map.of("tenantId",tenant,"resourceId","deleted-user","operatorId",tenant,"action","OPERATOR_DELETE","result","PENDING","externalSystem","FIREBASE_AUTH","timestamp",java.time.Instant.now().minusSeconds(120).toString())).get();
   var auth=mock(com.google.firebase.auth.FirebaseAuth.class);
   var missing=mock(com.google.firebase.auth.FirebaseAuthException.class);when(missing.getAuthErrorCode()).thenReturn(com.google.firebase.auth.AuthErrorCode.USER_NOT_FOUND);
   when(auth.getUser("deleted-user")).thenThrow(missing);
   assertTrue(IdentityAuditRecovery.recover(db,auth,event,java.time.Instant.now()));
   assertFalse(operator.get().get().exists());assertEquals("SUCCESS",event.get().get().getString("result"));
   assertFalse(IdentityAuditRecovery.recover(db,auth,event,java.time.Instant.now()));
  }
 }
 private void identity(String tenant,String role){var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("qa",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role)));auth.setDetails(new com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails("qa",null,"QA",tenant));org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);}
 @Test void searchWorksBeforeProjectionDeploymentAndCursorIsTenantScoped()throws Exception{
  String tenant="fallback-qa-"+UUID.randomUUID();identity(tenant,"ADMIN");
  try(var db=database();var client=mockStatic(FirestoreClient.class)){
   client.when(FirestoreClient::getFirestore).thenReturn(db);
   for(int index=0;index<24;index++)db.collection("antennas").document(tenant+String.format("-%02d",index)).set(Map.of("tenantId",tenant,"name","Città Ponte "+index,"status",index%2==0?"ATTIVA":"OFFLINE")).get();
   var controller=new com.radiotech.radiotech_backend.controller.ResourceSearchController();
   var first=controller.search("citta");assertEquals(20,((List<?>)first.get("items")).size());String cursor=first.get("nextCursor").toString();assertTrue(cursor.startsWith("legacy:"));
   var second=controller.search("citta",cursor);assertEquals(4,((List<?>)second.get("items")).size());assertNull(second.get("nextCursor"));
   var service=new DashboardService();var filtered=service.resourcePage("antennas",null,10,"citta","","","","OFFLINE");assertEquals(10,((List<?>)filtered.get("items")).size());
   assertTrue(((List<?>)filtered.get("items")).stream().allMatch(item->"OFFLINE".equals(((Map<?,?>)item).get("status"))));
   identity("other-tenant","ADMIN");assertThrows(IllegalArgumentException.class,()->controller.search("citta",cursor));
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
 @Test void indexedSearchVerifiesSourceTenantAndRoleAndPagesStripBadgeSecrets()throws Exception{
  String tenant="search-qa-"+UUID.randomUUID();identity(tenant,"ADMIN");
  try(var db=database();var client=mockStatic(FirestoreClient.class)){
   client.when(FirestoreClient::getFirestore).thenReturn(db);
   db.collection("dashboardSummaries").document(tenant).set(Map.of("tenantId",tenant,"ready",true)).get();
   db.collection("antennas").document(tenant+"-own").set(Map.of("tenantId",tenant,"name","Ponte QA","status","ATTIVA")).get();
   db.collection("antennas").document(tenant+"-foreign").set(Map.of("tenantId","foreign","name","Foreign private name")).get();
   for(String key:List.of("own","foreign"))db.collection("resourceSearch").document(tenant+"-"+key).set(Map.of("tenantId",tenant,"collection","antennas","resourceId",tenant+"-"+key,"prefixes",List.of("po"))).get();
   var controller=new com.radiotech.radiotech_backend.controller.ResourceSearchController();
   var rows=(List<?>)controller.search("PO").get("items");assertEquals(1,rows.size());assertEquals("Ponte QA",((Map<?,?>)rows.getFirst()).get("label"));
   identity(tenant,"OPERATOR");assertThrows(SecurityException.class,()->controller.search("po"));identity(tenant,"ADMIN");
   for(String suffix:List.of("a","b"))db.collection("operators").document(tenant+suffix).set(Map.of("tenantId",tenant,"full_name","Legacy QA","status","ACTIVE","qrCodeToken","private-badge","fcmTokens",List.of("private-token"))).get();
   var service=new DashboardService();var first=service.resourcePage("operators",null,1);assertEquals(1,((List<?>)first.get("items")).size());
   var row=(Map<?,?>)((List<?>)first.get("items")).getFirst();assertFalse(row.containsKey("qrCodeToken"));assertFalse(row.containsKey("fcmTokens"));assertEquals("Legacy QA",row.get("fullName"));assertEquals("ATTIVO",row.get("status"));
   var second=service.resourcePage("operators",first.get("nextCursor").toString(),1);assertNull(second.get("nextCursor"));assertNotEquals(row.get("id"),((Map<?,?>)((List<?>)second.get("items")).getFirst()).get("id"));
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
}
