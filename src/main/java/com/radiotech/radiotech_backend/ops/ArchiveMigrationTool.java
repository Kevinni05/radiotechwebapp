package com.radiotech.radiotech_backend.ops;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.service.ArchiveQueries;
import java.nio.file.*;
import java.util.*;

/** Adds index metadata only; report content and integrity hashes are never rewritten. */
public final class ArchiveMigrationTool {
    public static int migrate(Firestore db, boolean apply) throws Exception {
        var profiles = new HashMap<String,DocumentSnapshot>();
        for (var profile : db.collection("operators").get().get().getDocuments()) {
            String tenant = profile.getString("tenantId");
            if (tenant == null) continue;
            profiles.put(tenant + ":" + profile.getId(), profile);
            if (profile.getString("firebaseUid") != null) profiles.put(tenant + ":" + profile.getString("firebaseUid"), profile);
        }
        int changed = 0;
        for (String collection : List.of("tasks", "maintenanceReports")) {
            String after = null;
            while (true) {
                Query query = db.collection(collection).orderBy(FieldPath.documentId()).limit(100);
                if (after != null) query = query.startAfter(after);
                var page = query.get().get().getDocuments();
                if (page.isEmpty()) break;
                for (var doc : page) {
                    String tenant = doc.getString("tenantId");
                    if (tenant == null || tenant.isBlank()) continue;
                    var patch = metadata(doc, profiles, collection);
                    if (patch.entrySet().stream().allMatch(entry -> Objects.equals(doc.get(entry.getKey()), entry.getValue()))) continue;
                    changed++;
                    if (apply) db.runTransaction(tx -> {
                        var current = tx.get(doc.getReference()).get();
                        if (current.exists() && tenant.equals(current.getString("tenantId")))
                            tx.update(doc.getReference(), metadata(current, profiles, collection));
                        return null;
                    }).get();
                }
                after = page.getLast().getId();
            }
        }
        return changed;
    }
    private static Map<String,Object> metadata(DocumentSnapshot doc, Map<String,DocumentSnapshot> profiles, String collection) {
        var refs = new LinkedHashSet<>(ArchiveQueries.identities(doc.getString("operatorId"), doc.getString("operatorFirebaseUid"), doc.getString("operator_uid"), doc.getString("operator_id")));
        DocumentSnapshot profile = null;
        for (String ref : refs) { profile = profiles.get(doc.getString("tenantId") + ":" + ref); if (profile != null) break; }
        var patch = new LinkedHashMap<String,Object>();
        if (profile != null) {
            refs.addAll(ArchiveQueries.identities(profile.getId(), profile.getString("firebaseUid")));
            if (collection.equals("tasks")) {
                patch.put("archiveOperatorId", profile.getId());
                if (profile.getString("fullName") != null) patch.put("archiveOperatorName", profile.getString("fullName"));
            }
        }
        Object date = doc.get(collection.equals("tasks") ? "createdAt" : "submittedAt");
        if (date == null) date = doc.get("created_at");
        patch.put("archiveAt", ArchiveQueries.timestamp(date));
        patch.put("operatorRefs", List.copyOf(refs));
        return patch;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Set.of("dry-run", "apply").contains(args[0])) throw new IllegalArgumentException("Usage: dry-run|apply");
        String project = System.getenv("FIREBASE_PROJECT_ID");
        if (project == null || project.isBlank()) throw new IllegalArgumentException("FIREBASE_PROJECT_ID required");
        String account = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH");
        GoogleCredentials credentials;
        if (account == null || account.isBlank()) credentials = GoogleCredentials.getApplicationDefault();
        else try (var input = Files.newInputStream(Path.of(account))) { credentials = GoogleCredentials.fromStream(input); }
        FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project).setCredentials(credentials).build());
        var db = FirestoreClient.getFirestore();
        try { System.out.println("Archive metadata " + args[0] + ": " + migrate(db, args[0].equals("apply")) + " documents."); }
        finally { db.close(); FirebaseApp.getInstance().delete(); }
    }
}
