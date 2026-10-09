package com.radiotech.radiotech_backend.ops;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.*;
import com.google.firebase.cloud.FirestoreClient;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;

/** Trusted CLI only. Requires an authenticated backup and preserves accounts and infrastructure. */
public final class OperationalResetTool {
    private static final Set<String> KEEP = Set.of("operators", "antennas", "users", "pro_devices", "systemOperations");
    private static final Set<String> RESET = Set.of("tasks", "maintenanceReports", "reportFiles", "interventions",
            "inventory", "inventoryMovements", "apiIdempotencyKeys", "taskStatusIdempotencyKeys", "auditLogs",
            "calendarNotes", "notificationHistory", "notificationReceipts", "test");

    public record Plan(List<DocumentSnapshot> documents, Map<String, Object> protectedRecords, Map<String, Integer> counts) {}

    @SuppressWarnings("unchecked")
    public static Plan prepare(Firestore db, Map<String, Object> backup, String tenant) throws Exception {
        if (tenant == null || tenant.isBlank() || !db.getOptions().getProjectId().equals(backup.get("projectId")))
            throw new IllegalArgumentException("Backup project and explicit tenant required.");
        var backedUp = new HashMap<String, Object>();
        for (var record : (List<Map<String, Object>>) backup.get("documents"))
            backedUp.put(record.get("path").toString(), record.get("data"));
        var current = BackupTool.snapshot(db);
        var selected = new ArrayList<DocumentSnapshot>();
        var protectedRecords = new TreeMap<String, Object>();
        var counts = new TreeMap<String, Integer>();
        boolean singleTenant = true;
        for (String collection : List.of("operators", "antennas"))
            for (var document : db.collection(collection).get().get().getDocuments())
                if (!tenant.equals(document.getString("tenantId"))) singleTenant = false;
        for (var record : (List<Map<String, Object>>) current.get("documents")) {
            String path = record.get("path").toString(), root = path.split("/")[0];
            if (!KEEP.contains(root) && !RESET.contains(root)) throw new IllegalStateException("Unclassified collection: " + root);
            if (KEEP.contains(root)) {
                if (root.equals("operators") || root.equals("antennas")) protectedRecords.put(path, record.get("data"));
                continue;
            }
            var document = db.document(path).get().get();
            String owner = document.getString("tenantId");
            if (owner == null && path.split("/").length > 2)
                owner = db.document(path.split("/")[0] + "/" + path.split("/")[1]).get().get().getString("tenantId");
            boolean globalTest = root.equals("test") && owner == null && singleTenant;
            if (!tenant.equals(owner) && !globalTest) continue;
            if (!Objects.equals(backedUp.get(path), BackupTool.encode(document.getData())))
                throw new IllegalStateException("Operational data changed after backup; export a fresh archive before resetting.");
            selected.add(document); counts.merge(root, 1, Integer::sum);
        }
        // One atomic batch prevents partially reset report/inventory state.
        if (selected.size() > 450) throw new IllegalStateException("Reset exceeds atomic batch limit; a maintenance procedure is required.");
        return new Plan(List.copyOf(selected), Map.copyOf(protectedRecords), Map.copyOf(counts));
    }

    @SuppressWarnings("unchecked")
    public static void apply(Firestore db, Plan plan) throws Exception {
        var batch = db.batch();
        for (var document : plan.documents())
            batch.delete(document.getReference(), Precondition.updatedAt(document.getUpdateTime()));
        batch.commit().get();
        var after = BackupTool.snapshot(db);
        var protectedAfter = new TreeMap<String, Object>();
        var remaining = new HashSet<String>();
        for (var record : (List<Map<String, Object>>) after.get("documents")) {
            String path = record.get("path").toString(), root = path.split("/")[0];
            if (root.equals("operators") || root.equals("antennas")) protectedAfter.put(path, record.get("data"));
            remaining.add(path);
        }
        if (!plan.protectedRecords().equals(protectedAfter)) throw new IllegalStateException("Operator or antenna records changed during reset.");
        if (plan.documents().stream().anyMatch(document -> remaining.contains(document.getReference().getPath())))
            throw new IllegalStateException("Operational records remain after reset.");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !Set.of("dry-run", "apply").contains(args[0]))
            throw new IllegalArgumentException("Usage: dry-run|apply archive key-file tenant-id");
        var mapper = JsonMapper.builder().build();
        var backup = mapper.readValue(BackupTool.decrypt(Files.readAllBytes(Path.of(args[1])), Files.readAllBytes(Path.of(args[2]))), Map.class);
        String project = System.getenv("FIREBASE_PROJECT_ID"), account = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH");
        if (project == null || account == null) throw new IllegalArgumentException("Project and service account required.");
        try (var input = Files.newInputStream(Path.of(account))) {
            FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project).setCredentials(GoogleCredentials.fromStream(input)).build());
        }
        var db = FirestoreClient.getFirestore();
        try {
            var plan = prepare(db, backup, args[3]);
            System.out.println("Reset plan: " + mapper.writeValueAsString(plan.counts()));
            if (args[0].equals("apply")) { apply(db, plan); System.out.println("Operational reset complete; operators and antennas unchanged."); }
        } finally { db.close(); FirebaseApp.getInstance().delete(); }
    }
}
