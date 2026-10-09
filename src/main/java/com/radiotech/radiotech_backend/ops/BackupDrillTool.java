package com.radiotech.radiotech_backend.ops;

import com.google.auth.oauth2.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.service.LocalAttachmentService;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Restores and verifies a complete encrypted archive on isolated demo emulators only. */
public final class BackupDrillTool {
    public static void main(String[] args) throws Exception {
        String project = System.getenv("FIREBASE_PROJECT_ID");
        if (args.length != 2 || project == null || !project.startsWith("demo-") ||
                System.getenv("FIRESTORE_EMULATOR_HOST") == null || System.getenv("FIREBASE_AUTH_EMULATOR_HOST") == null)
            throw new SecurityException("A demo Firestore/Auth emulator is required for a restore drill.");
        var json = JsonMapper.builder().build();
        Path archive = Path.of(args[0]), keyPath = Path.of(args[1]);
        byte[] encrypted = Files.readAllBytes(archive);
        var backup = json.readValue(BackupTool.decrypt(encrypted, Files.readAllBytes(keyPath)), Map.class);
        if (!((List<?>)backup.get("files")).isEmpty()) throw new IllegalArgumentException("Object storage needs a separate isolated restore target; this drill covers Firestore attachments.");
        FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project)
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE)))).build());
        var db = FirestoreClient.getFirestore();
        try {
            BackupTool.restoreDocuments(backup, db);
            Path localRoot = Path.of(".dist/drill-local-files-" + UUID.randomUUID());
            var localFiles = (List<Map<String,Object>>)backup.getOrDefault("localFiles", List.of());
            BackupTool.restoreLocalFiles(localFiles, localRoot);
            for (var file : localFiles) if (!Arrays.equals(Files.readAllBytes(localRoot.resolve(file.get("name").toString())), Base64.getDecoder().decode(file.get("data").toString())))
                throw new IllegalStateException("Local restore mismatch");
            for (var record : (List<Map<String,Object>>)backup.get("documents")) {
                var restored = db.document(record.get("path").toString()).get().get();
                if (!restored.exists() || !Objects.equals(record.get("data"), BackupTool.encode(restored.getData()))) throw new IllegalStateException("Document restore mismatch");
            }
            for (var user : (List<Map<String,Object>>)backup.get("users")) {
                String uid = user.get("uid").toString();
                var request = new UserRecord.CreateRequest().setUid(uid).setDisabled(true);
                if (user.get("email") instanceof String email) request.setEmail(email);
                FirebaseAuth.getInstance().createUser(request);
                FirebaseAuth.getInstance().setCustomUserClaims(uid, (Map<String,Object>)user.get("claims"));
                var restored = FirebaseAuth.getInstance().getUser(uid);
                if (!restored.isDisabled() || !Objects.equals(user.get("claims"), restored.getCustomClaims())) throw new IllegalStateException("Identity restore mismatch");
            }
            var env = new StandardEnvironment();
            env.getPropertySources().addFirst(new MapPropertySource("drill", Map.of("radiotech.attachments.backend", "FIRESTORE")));
            var files = new LocalAttachmentService(env);
            int verifiedAttachments = 0;
            for (var record : db.collection("reportFiles").get().get().getDocuments()) {
                var file = files.read(record.getId(), record.getString("tenantId"), record.getString("uid"), false);
                byte[] bytes = Base64.getDecoder().decode(file.get("base64").toString());
                if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(record.getString("digest"))) throw new IllegalStateException("Restored attachment mismatch");
                verifiedAttachments++;
            }
            var result = Map.of("restored", true, "testedAt", java.time.Instant.now().toString(), "sourceProject", backup.get("projectId"),
                    "archiveSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encrypted)),
                    "documents", ((List<?>)backup.get("documents")).size(), "users", ((List<?>)backup.get("users")).size(), "attachments", verifiedAttachments);
            Files.createDirectories(Path.of(".dist"));
            Files.writeString(Path.of(".dist/backup-drill-result.json"), json.writeValueAsString(result));
            System.out.println(json.writeValueAsString(result));
        } finally { db.close(); FirebaseApp.getInstance().delete(); }
    }
}
