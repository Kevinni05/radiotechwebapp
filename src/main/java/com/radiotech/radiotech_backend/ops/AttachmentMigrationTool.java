package com.radiotech.radiotech_backend.ops;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.FieldPath;
import com.radiotech.radiotech_backend.service.LocalAttachmentService;
import org.springframework.core.env.StandardEnvironment;
import java.nio.file.*;
import java.util.*;

/** Explicit tenant, bounded batch, checkpoint only after verified metadata commit. */
public final class AttachmentMigrationTool {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !Set.of("dry-run", "apply", "rollback").contains(args[0]))
            throw new IllegalArgumentException("Usage: dry-run|apply|rollback tenant [afterId] [limit<=100]");
        String tenant = args[1];
        if (tenant.isBlank() || tenant.contains("/") || tenant.length() > 1500) throw new IllegalArgumentException("Tenant non valido.");
        int limit = args.length > 3 ? Integer.parseInt(args[3]) : 25;
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Batch tra 1 e 100.");
        String project = Objects.requireNonNull(System.getenv("FIREBASE_PROJECT_ID"), "FIREBASE_PROJECT_ID required");
        String bucket = Objects.requireNonNull(System.getenv("FIREBASE_STORAGE_BUCKET"), "FIREBASE_STORAGE_BUCKET required");
        GoogleCredentials credentials;
        String account = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH");
        if (account == null) credentials = GoogleCredentials.getApplicationDefault();
        else try (var input = Files.newInputStream(Path.of(account))) { credentials = GoogleCredentials.fromStream(input); }
        FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project).setStorageBucket(bucket).setCredentials(credentials).build());
        var db = FirestoreClient.getFirestore();
        try {
            var env = new StandardEnvironment(); env.setActiveProfiles("production");
            var files = new LocalAttachmentService(env);
            var checkpoint = db.collection("systemOperations").document("attachment-migration").collection("tenants").document(tenant);
            var query = db.collection("reportFiles").whereEqualTo("tenantId", tenant).orderBy(FieldPath.documentId()).limit(limit);
            if (args.length > 2 && !args[2].isBlank()) query = query.startAfter(args[2]);
            var docs = query.get().get().getDocuments();
            String last = "";
            for (var doc : docs) {
                if ("rollback".equals(args[0]) && !"FIRESTORE".equals(doc.getString("legacyBackend"))) { last = doc.getId(); continue; }
                files.migrateToObjectStorage(doc.getId(), tenant, args[0]); last = doc.getId();
                if (!"dry-run".equals(args[0])) checkpoint.set(Map.of("afterId", last, "mode", args[0],
                        "checkedAt", java.time.Instant.now().toString(), "sourceRetained", true)).get();
            }
            System.out.println("Verified entries=" + docs.size() + "; nextAfterId=" + last + "; hasMore=" + (docs.size() == limit));
        } finally { db.close(); FirebaseApp.getInstance().delete(); }
    }
}
