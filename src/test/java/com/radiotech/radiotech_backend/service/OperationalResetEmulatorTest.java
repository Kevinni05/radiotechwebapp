package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.*;
import com.google.cloud.firestore.*;
import com.radiotech.radiotech_backend.ops.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FIRESTORE_EMULATOR_HOST", matches=".+")
class OperationalResetEmulatorTest {
    private Firestore database() {
        return FirestoreOptions.newBuilder().setProjectId("demo-reset-"+UUID.randomUUID())
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only",new Date(Long.MAX_VALUE))))
                .build().getService();
    }
    @Test void resetDeletesOwnedReportsAndChunksAtomicallyWhilePreservingInfrastructureAndOtherTenants() throws Exception {
        try(var db=database()) {
            var operator=db.collection("operators").document("operator");
            operator.set(Map.of("tenantId","own","full_name","Tecnico","qrCodeToken","unchanged-secret")).get();
            operator.collection("skills").document("skill").set(Map.of("name","RF")).get();
            db.collection("antennas").document("antenna").set(Map.of("tenantId","own","name","Sito")).get();
            db.collection("users").document("manager").set(Map.of("tenantId","own","role","MANAGER")).get();
            db.collection("pro_devices").document("device").set(Map.of("tenantId","own","active",true)).get();
            db.collection("tasks").document("task").set(Map.of("tenantId","own","status","REPORT_SUBMITTED")).get();
            db.collection("tasks").document("foreign").set(Map.of("tenantId","foreign","status","IN_PROGRESS")).get();
            db.collection("maintenanceReports").document("report").set(Map.of("tenantId","own","status","APPROVAL_PENDING")).get();
            var file=db.collection("reportFiles").document("file");
            file.set(Map.of("tenantId","own","chunkCount",1)).get();
            file.collection("chunks").document("0").set(Map.of("data","photo")).get();
            var backup=BackupTool.snapshot(db);backup.put("projectId",db.getOptions().getProjectId());
            var plan=OperationalResetTool.prepare(db,backup,"own");
            assertEquals(4,plan.documents().size());OperationalResetTool.apply(db,plan);
            assertEquals("unchanged-secret",operator.get().get().getString("qrCodeToken"));
            assertTrue(operator.collection("skills").document("skill").get().get().exists());
            assertTrue(db.collection("antennas").document("antenna").get().get().exists());
            assertTrue(db.collection("users").document("manager").get().get().exists());
            assertTrue(db.collection("pro_devices").document("device").get().get().exists());
            assertTrue(db.collection("tasks").document("foreign").get().get().exists());
            assertFalse(file.collection("chunks").document("0").get().get().exists());
            assertEquals(0,db.collection("maintenanceReports").get().get().size());
        }
    }
    @Test void staleBackupAndConcurrentChangesPreventDeletingAnyOperationalData() throws Exception {
        try(var db=database()) {
            var task=db.collection("tasks").document("task");task.set(Map.of("tenantId","own","status","ASSIGNED")).get();
            var backup=BackupTool.snapshot(db);backup.put("projectId",db.getOptions().getProjectId());
            task.update("status","IN_PROGRESS").get();
            final var staleBackup=backup;
            assertThrows(IllegalStateException.class,()->OperationalResetTool.prepare(db,staleBackup,"own"));
            var freshBackup=BackupTool.snapshot(db);freshBackup.put("projectId",db.getOptions().getProjectId());
            var plan=OperationalResetTool.prepare(db,freshBackup,"own");
            task.update("status","COMPLETED").get();
            assertThrows(Exception.class,()->OperationalResetTool.apply(db,plan));
            assertTrue(task.get().get().exists());
        }
    }
}
