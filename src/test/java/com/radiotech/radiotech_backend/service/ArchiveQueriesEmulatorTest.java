package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.radiotech.radiotech_backend.ops.ArchiveMigrationTool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class ArchiveQueriesEmulatorTest {
    private Firestore database() { return FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
        .setHost(System.getenv("FIRESTORE_EMULATOR_HOST")).setCredentials(GoogleCredentials.create(new AccessToken("emulator-only",new Date(Long.MAX_VALUE)))).build().getService(); }
    @Test void legacyMigrationPreservesSignedContentAndPaginationIsStableAndTenantScoped() throws Exception {
        String tenant=UUID.randomUUID().toString(), profile=UUID.randomUUID().toString(), uid=UUID.randomUUID().toString();
        try(var db=database()) {
            db.collection("operators").document(profile).set(Map.of("tenantId",tenant,"firebaseUid",uid,"fullName","Operatore storico")).get();
            for(int index=0;index<63;index++) {
                var values=new HashMap<String,Object>(Map.of("tenantId",tenant,"operator_uid",uid,"integrityHash","unchanged","digitalSignature","original"));
                if(index!=0) values.put("submittedAt", com.google.cloud.Timestamp.ofTimeSecondsAndNanos(1_790_000_000,0));
                db.collection("maintenanceReports").document(profile+"-"+String.format("%03d",index)).set(values).get();
            }
            db.collection("maintenanceReports").document(profile+"-foreign").set(Map.of("tenantId","other","operator_uid",uid)).get();
            assertTrue(ArchiveMigrationTool.migrate(db,false)>=63);
            assertFalse(db.collection("maintenanceReports").document(profile+"-000").get().get().contains("archiveAt"));
            ArchiveMigrationTool.migrate(db,true);
            var own=ArchiveQueries.operator(db.collection("maintenanceReports").whereEqualTo("tenantId",tenant),List.of(profile,uid));
            var ids=new LinkedHashSet<String>();String cursor=null;int pages=0;
            do {
                var page=ArchiveQueries.page(own,20,cursor,DocumentSnapshot::getId);
                assertTrue(page.items().size()<=20);
                for(String id:page.items()) assertTrue(ids.add(id),"No duplicate cursor rows");
                cursor=page.nextCursor();pages++;
            }while(cursor!=null);
            assertEquals(4,pages);assertEquals(63,ids.size());assertEquals(profile+"-000",ids.stream().reduce((left,right)->right).orElseThrow());
            var undated=db.collection("maintenanceReports").document(profile+"-000").get().get();
            assertEquals("unchanged",undated.getString("integrityHash"));assertEquals("original",undated.getString("digitalSignature"));
            assertEquals(63,own.count().get().get().getCount());
            assertThrows(IllegalArgumentException.class,()->ArchiveQueries.page(own,101,null,DocumentSnapshot::getId));
            assertThrows(IllegalArgumentException.class,()->ArchiveQueries.page(own,20,"malformed-cursor",DocumentSnapshot::getId));
        }
    }
    @Test void metadataMigrationIsIdempotentAndKeepsLegacyTaskIdentityQueryable() throws Exception {
        String tenant=UUID.randomUUID().toString(), profile=UUID.randomUUID().toString();
        try(var db=database()) {
            db.collection("operators").document(profile).set(Map.of("tenantId",tenant,"firebaseUid","uid-"+profile,"fullName","Nome corretto")).get();
            db.collection("tasks").document(profile).set(Map.of("tenantId",tenant,"operator_uid","uid-"+profile,"status","IN LAVORAZIONE")).get();
            ArchiveMigrationTool.migrate(db,true);
            var task=db.collection("tasks").document(profile).get().get();
            assertEquals("Nome corretto",task.getString("archiveOperatorName"));assertEquals(profile,task.getString("archiveOperatorId"));
            assertEquals("IN LAVORAZIONE",task.getString("status"));assertEquals(0L,task.getLong("archiveAt"));
            var before=task.getUpdateTime();ArchiveMigrationTool.migrate(db,true);
            assertEquals(before,db.collection("tasks").document(profile).get().get().getUpdateTime());
        }
    }
}
