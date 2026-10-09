package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class ReportMaterialReviewEmulatorTest {
    private Firestore database() {
        var auth=new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(new FirebaseAuthenticationDetails("reviewer",null,null,"own"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return FirestoreOptions.newBuilder().setProjectId("demo-material-"+UUID.randomUUID())
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator",new Date(Long.MAX_VALUE)))).build().getService();
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private MaintenanceReportService service(TaskService tasks,RicambioService stock) {
        return new MaintenanceReportService(tasks,mock(AuditService.class),stock,new OperatorService());
    }
    @Test void legacyRowsAreMappedExplicitlyWithoutChangingTheSignedMaterialsOrChargingExternalParts() throws Exception {
        try(var db=database();var client=mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("inventory").document("connector").set(Map.of("tenantId","own","name","Connettore N","sku","RF-001","quantity",5,"unitCost",12)).get();
            db.collection("inventory").document("cable").set(Map.of("tenantId","own","name","Cavo coassiale","sku","RF-002","quantity",3)).get();
            db.collection("inventory").document("foreign").set(Map.of("tenantId","other","name","Connettore N","quantity",100)).get();
            var materials=List.of(Map.of("name","Connettore N","quantity",1),Map.of("name","Cavo RG-213","quantity",1),Map.of("name","Fusibile 5A","quantity",2));
            var ref=db.collection("maintenanceReports").document("report");
            ref.set(Map.of("tenantId","own","status","SUBMITTED","materialsUsed",materials,"integrityHash","original-hash","digitalSignature","original-signature")).get();
            var original=ref.get().get().get("materialsUsed");
            var stock=new RicambioService(mock(AuditService.class));var reports=service(mock(TaskService.class),stock);
            assertThrows(IllegalArgumentException.class,()->reports.review("report",true,"ok","reviewer"));
            assertEquals("SUBMITTED",ref.get().get().getString("status"));
            assertEquals(5L,db.collection("inventory").document("connector").get().get().getLong("quantity"));
            var mappings=Map.of("1","cable","2","@external");
            assertEquals("APPROVED",reports.review("report",true,"verified","reviewer",mappings).getStatus());
            reports.review("report",true,"retry","reviewer",mappings);
            assertEquals(4L,db.collection("inventory").document("connector").get().get().getLong("quantity"));
            assertEquals(2L,db.collection("inventory").document("cable").get().get().getLong("quantity"));
            assertEquals(100L,db.collection("inventory").document("foreign").get().get().getLong("quantity"));
            assertEquals(original,ref.get().get().get("materialsUsed"));
            assertEquals("original-hash",ref.get().get().getString("integrityHash"));
            assertEquals("original-signature",ref.get().get().getString("digitalSignature"));
            assertEquals("reviewer",ref.get().get().getString("inventoryMaterialsReviewedBy"));
            assertTrue(stock.getMaterialCatalog().stream().noneMatch(item->item.containsKey("unitCost")));
        }
    }
    @Test void mappingCannotUseOtherCompaniesOrInvalidQuantitiesOrUnknownRows() throws Exception {
        try(var db=database();var client=mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            db.collection("inventory").document("foreign").set(Map.of("tenantId","other","name","Part","quantity",10)).get();
            var stock=new RicambioService(mock(AuditService.class));var material=List.<Map<String,Object>>of(Map.of("name","Part","quantity",1));
            assertThrows(IllegalArgumentException.class,()->stock.materialsForReview(material,Map.of("0","foreign")));
            assertThrows(IllegalArgumentException.class,()->stock.materialsForReview(material,Map.of("5","@external")));
            assertThrows(IllegalArgumentException.class,()->stock.materialsForReview(List.of(Map.of("name","Part","quantity",1.5)),Map.of("0","@external")));
        }
    }
    @Test void failedTaskFinalizationKeepsTheSameConsumptionAndRejectsChangedMappings() throws Exception {
        try(var db=database();var client=mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            for(String id:List.of("first","second"))db.collection("inventory").document(id).set(Map.of("tenantId","own","name",id,"quantity",5)).get();
            db.collection("maintenanceReports").document("report").set(Map.of("tenantId","own","status","SUBMITTED","taskId","task","materialsUsed",List.of(Map.of("name","Old part","quantity",1)))).get();
            var tasks=mock(TaskService.class);var calls=new AtomicInteger();
            when(tasks.updateStatus(anyString(),anyString(),anyString())).thenAnswer(call->{if(calls.getAndIncrement()==0)throw new IllegalStateException("Task temporarily unavailable");return null;});
            var reports=service(tasks,new RicambioService(mock(AuditService.class)));
            assertThrows(IllegalStateException.class,()->reports.review("report",true,"ok","reviewer",Map.of("0","first")));
            assertThrows(IllegalArgumentException.class,()->reports.review("report",true,"changed","reviewer",Map.of("0","second")));
            assertEquals("APPROVED",reports.review("report",true,"retry","reviewer").getStatus());
            assertEquals(4L,db.collection("inventory").document("first").get().get().getLong("quantity"));
            assertEquals(5L,db.collection("inventory").document("second").get().get().getLong("quantity"));
        }
    }
}
