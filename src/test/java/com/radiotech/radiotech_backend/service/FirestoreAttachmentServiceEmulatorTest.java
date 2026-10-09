package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreAttachmentServiceEmulatorTest {
    @TempDir Path archive;
    private Firestore database() {
        return FirestoreOptions.newBuilder().setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }
    private LocalAttachmentService persistent() {
        var env = new MockEnvironment().withProperty("radiotech.local-files.directory", archive.toString());
        env.setActiveProfiles("production");
        return new LocalAttachmentService(env);
    }

    @Test void persistentFilesSurviveAServiceRestartAndAreActorBoundIdempotentAndIntegrityChecked() throws Exception {
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            String operation = UUID.randomUUID().toString();
            byte[] bytes = new byte[700_000]; new Random(42).nextBytes(bytes);
            var service = persistent();
            var saved = service.save("tenant", "operator", operation, "report.pdf", bytes);
            String reference = saved.get("reference").toString(), id = reference.substring(15);
            assertEquals(saved, persistent().save("tenant", "operator", operation, "report.pdf", bytes));
            var result = persistent().read(id, "tenant", "operator", false);
            assertArrayEquals(bytes, Base64.getDecoder().decode(result.get("base64").toString()));
            assertEquals(3, db.collection("reportFiles").document(id).collection("chunks").get().get().size());
            assertThrows(SecurityException.class, () -> service.read(id, "foreign", "manager", true));
            assertThrows(SecurityException.class, () -> service.read(id, "tenant", "another-operator", false));
            assertDoesNotThrow(() -> service.read(id, "tenant", "manager", true));
            assertDoesNotThrow(() -> service.validateOwned(reference, "tenant", "operator"));
            assertThrows(IllegalArgumentException.class, () -> service.validateOwned(reference, "tenant", "other"));
            assertThrows(IllegalArgumentException.class, () -> service.save("tenant", "operator", operation, "report.pdf", new byte[]{1}));
            db.collection("reportFiles").document(id).collection("chunks").document("0")
                    .update("data", Blob.fromBytes(new byte[256 * 1024])).get();
            assertThrows(IllegalStateException.class, () -> service.read(id, "tenant", "operator", false));
        }
    }

    @Test void maximumSizedAttachmentCanRoundTripAndOversizedFilesAreRejectedBeforeWriting() throws Exception {
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            byte[] bytes = new byte[LocalAttachmentService.MAX_BYTES];
            new Random(1).nextBytes(bytes);
            var service = persistent();
            String operation = UUID.randomUUID().toString();
            var saved = service.save("tenant", "operator", operation, "large.pdf", bytes);
            var result = service.read(saved.get("reference").toString().substring(15), "tenant", "operator", false);
            assertArrayEquals(bytes, Base64.getDecoder().decode(result.get("base64").toString()));
            assertThrows(IllegalArgumentException.class, () -> service.save("tenant", "operator", operation,
                    "too-large.pdf", new byte[LocalAttachmentService.MAX_BYTES + 1]));
        }
    }

    @Test void switchingNewUploadsToObjectStorageKeepsFirestoreFilesReadableWithoutABucket() throws Exception {
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            byte[] bytes = "historical Firestore attachment".getBytes();
            String reference = persistent().save("tenant", "operator", UUID.randomUUID().toString(), "old.pdf", bytes).get("reference").toString();
            var env = new MockEnvironment().withProperty("radiotech.attachments.backend", "FIREBASE_STORAGE");
            env.setActiveProfiles("production");
            var switched = new LocalAttachmentService(env);
            assertArrayEquals(bytes, Base64.getDecoder().decode(switched.read(reference.substring(15), "tenant", "operator", false).get("base64").toString()));
            assertDoesNotThrow(() -> switched.validateOwned(reference, "tenant", "operator"));
            assertThrows(SecurityException.class, () -> switched.read(reference.substring(15), "foreign", "operator", true));
        }
    }

    @Test void recoveringLocalFilesKeepsOriginalReferencesAndCannotImportAnotherOperatorsFile() throws Exception {
        try (var db = database(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(db);
            var local = new LocalAttachmentService(new MockEnvironment()
                    .withProperty("radiotech.local-files.directory", archive.toString()));
            byte[] bytes = "%PDF-1.7 historical original".getBytes();
            var saved = local.save("tenant", "operator", UUID.randomUUID().toString(), "old.pdf", bytes);
            String reference = saved.get("reference").toString();
            var persistent = persistent();
            assertThrows(SecurityException.class, () -> persistent.restoreLocalArchive(reference, "tenant", "other"));
            assertEquals(saved, persistent.restoreLocalArchive(reference, "tenant", "operator"));
            assertEquals(saved, persistent.restoreLocalArchive(reference, "tenant", "operator"));
            var data = persistent.read(reference.substring(15), "tenant", "operator", false);
            assertArrayEquals(bytes, Base64.getDecoder().decode(data.get("base64").toString()));
        }
    }
}
