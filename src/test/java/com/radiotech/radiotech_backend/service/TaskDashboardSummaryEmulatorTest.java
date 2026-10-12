package com.radiotech.radiotech_backend.service;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST",matches=".+")
class TaskDashboardSummaryEmulatorTest {
 @Test void tenThousandClosedTasksTransferOnlyActiveTasksAndLegacyFallbackRemainsExact()throws Exception{
  String tenant="task-benchmark-qa-"+UUID.randomUUID();
  try(var db=FirestoreOptions.getDefaultInstance().toBuilder().setProjectId("demo-radiotech").setEmulatorHost(System.getenv("FIRESTORE_EMULATOR_HOST")).build().getService()){
   for(int offset=0;offset<10010;offset+=250){var batch=db.batch();for(int i=offset;i<Math.min(offset+250,10010);i++)batch.set(db.collection("tasks").document(tenant+"-"+i),Map.of("tenantId",tenant,"status",i<8000?"COMPLETED":i<10000?"CANCELLED":"ASSIGNED","dueAt","2000-01-01T00:00:00Z"));batch.commit().get();}
   long started=System.nanoTime();var summary=TaskDashboardSummary.read(db,tenant);long millis=(System.nanoTime()-started)/1000000;
   assertNotNull(summary);assertEquals(10010,summary.total());assertEquals(8000,summary.completed());assertEquals(2000,summary.cancelled());assertEquals(10,summary.active().size());
   assertTrue(1.0-summary.active().size()/(double)summary.total()>=0.70);
   System.out.println("TASK_ARCHIVE_BENCHMARK baselineDocuments=10010 transferredDocuments=10 elapsedMs="+millis+" environment=EMULATOR");
   db.collection("tasks").document(tenant+"-legacy").set(Map.of("tenantId",tenant,"status"," in corso ")).get();assertNull(TaskDashboardSummary.read(db,tenant));
  }
 }
}
