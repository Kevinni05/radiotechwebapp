package com.radiotech.radiotech_backend.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.firebase.cloud.StorageClient;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Attachment adapter.
 *
 * Development/test keeps the historical local filesystem implementation.
 * Production stores the same opaque radiotech-file references in Firebase
 * Storage and serves them only through the authenticated backend endpoint.
 */
@Service
public class LocalAttachmentService {
    public static final int MAX_BYTES = 10_000_000;
    private static final String CLOUD_PREFIX = "radiotech-report-files/";

    private final boolean localMode;
    private final Path root;
    private final JsonMapper json = JsonMapper.builder().build();

    public LocalAttachmentService(Environment env) {
        localMode = !env.acceptsProfiles(Profiles.of("production"));
        root = Path.of(env.getProperty("radiotech.local-files.directory", ".dist/private-files"))
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Kept for compatibility with existing health/config checks.
     * It means "local filesystem mode", not "attachment service available".
     */
    public boolean enabled() {
        return localMode;
    }

    public String storageBackend() {
        return localMode ? "LOCAL_FILESYSTEM" : "FIREBASE_STORAGE";
    }

    private Path path(String id, String suffix) {
        validateId(id);
        return root.resolve(id + suffix);
    }

    public synchronized Map<String, Object> save(
            String tenant,
            String uid,
            String operation,
            String name,
            byte[] data) throws Exception {
        requireIdentityAndPayload(tenant, uid, operation, name, data);
        return localMode
                ? saveLocal(tenant, uid, operation, name, data)
                : saveCloud(tenant, uid, operation, name, data);
    }

    public Map<String, Object> read(
            String id,
            String tenant,
            String uid,
            boolean manager) throws Exception {
        validateId(id);
        if (tenant == null || uid == null) {
            throw new SecurityException("Identità richiesta.");
        }
        return localMode
                ? readLocal(id, tenant, uid, manager)
                : readCloud(id, tenant, uid, manager);
    }

    public void validateOwned(String reference, String tenant, String uid) {
        try {
            if (reference == null || !reference.startsWith("radiotech-file:")) {
                throw new IllegalArgumentException("Riferimento allegato non valido.");
            }
            String id = reference.substring("radiotech-file:".length());
            validateId(id);
            Map<String, String> record = localMode ? localMetadata(id) : cloudMetadata(id);
            if (!Objects.equals(tenant, record.get("tenantId")) || !Objects.equals(uid, record.get("uid"))) {
                throw new SecurityException("Allegato non autorizzato.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Allegato non valido per tenant e operatore.");
        }
    }

    private Map<String, Object> saveLocal(
            String tenant,
            String uid,
            String operation,
            String name,
            byte[] data) throws Exception {
        String safeName = safeName(name);
        String id = hash((tenant + ":" + uid + ":" + operation).getBytes(StandardCharsets.UTF_8));
        String digest = hash(data);

        Files.createDirectories(root);
        Path metadata = path(id, ".json");
        Path binary = path(id, ".bin");

        if (Files.exists(metadata)) {
            var previous = json.readValue(Files.readAllBytes(metadata), Map.class);
            if (!digest.equals(previous.get("digest"))) {
                throw new IllegalArgumentException("Operazione allegato già usata per un altro file.");
            }
            return Map.of("reference", "radiotech-file:" + id, "name", previous.get("name"));
        }

        var record = Map.of(
                "tenantId", tenant,
                "uid", uid,
                "name", safeName,
                "digest", digest,
                "size", data.length);

        Path temp = Files.createTempFile(root, "upload-", ".partial");
        try {
            Files.write(temp, data);
            Files.move(temp, binary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }

        Path metaTemp = Files.createTempFile(root, "metadata-", ".partial");
        try {
            Files.write(metaTemp, json.writeValueAsBytes(record));
            Files.move(metaTemp, metadata, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(metaTemp);
        }

        return Map.of("reference", "radiotech-file:" + id, "name", safeName);
    }

    private Map<String, Object> saveCloud(
            String tenant,
            String uid,
            String operation,
            String name,
            byte[] data) throws Exception {
        String safeName = safeName(name);
        String id = hash((tenant + ":" + uid + ":" + operation).getBytes(StandardCharsets.UTF_8));
        String digest = hash(data);
        Storage storage = cloudStorage();
        BlobId blobId = cloudBlobId(id);
        Blob existing = storage.get(blobId);

        if (existing != null) {
            Map<String, String> metadata = existing.getMetadata();
            if (metadata == null || !digest.equals(metadata.get("digest"))) {
                throw new IllegalArgumentException("Operazione allegato già usata per un altro file.");
            }
            return Map.of(
                    "reference", "radiotech-file:" + id,
                    "name", metadata.getOrDefault("name", safeName));
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", tenant);
        metadata.put("uid", uid);
        metadata.put("name", safeName);
        metadata.put("digest", digest);
        metadata.put("size", Integer.toString(data.length));

        BlobInfo info = BlobInfo.newBuilder(blobId)
                .setContentType("application/octet-stream")
                .setMetadata(metadata)
                .build();

        try {
            storage.create(info, data, Storage.BlobTargetOption.doesNotExist());
        } catch (StorageException race) {
            if (race.getCode() != 409 && race.getCode() != 412) {
                throw race;
            }
            Blob concurrent = storage.get(blobId);
            Map<String, String> concurrentMetadata = concurrent == null ? null : concurrent.getMetadata();
            if (concurrentMetadata == null || !digest.equals(concurrentMetadata.get("digest"))) {
                throw new IllegalArgumentException("Operazione allegato già usata per un altro file.");
            }
        }

        return Map.of("reference", "radiotech-file:" + id, "name", safeName);
    }

    private Map<String, Object> readLocal(
            String id,
            String tenant,
            String uid,
            boolean manager) throws Exception {
        Map<String, String> record = localMetadata(id);
        requireAuthorized(record, tenant, uid, manager);
        byte[] data = Files.readAllBytes(path(id, ".bin"));
        verifyIntegrity(record, data);
        return Map.of("name", record.get("name"), "base64", Base64.getEncoder().encodeToString(data));
    }

    private Map<String, Object> readCloud(
            String id,
            String tenant,
            String uid,
            boolean manager) throws Exception {
        Storage storage = cloudStorage();
        Blob blob = storage.get(cloudBlobId(id));
        if (blob == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Allegato non trovato nello storage Firebase.");
        }

        Map<String, String> record = blob.getMetadata();
        if (record == null) {
            throw new IllegalStateException("Metadati allegato non disponibili.");
        }

        requireAuthorized(record, tenant, uid, manager);
        byte[] data = blob.getContent();
        if (data.length > MAX_BYTES) {
            throw new IllegalStateException("Allegato oltre il limite consentito.");
        }
        verifyIntegrity(record, data);

        return Map.of(
                "name", record.getOrDefault("name", "allegato"),
                "base64", Base64.getEncoder().encodeToString(data));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> localMetadata(String id) throws Exception {
        Path file = path(id, ".json");
        if (!Files.exists(file)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Allegato non trovato.");
        }
        Map<String, Object> raw = json.readValue(Files.readAllBytes(file), Map.class);
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(key, value == null ? null : String.valueOf(value)));
        return result;
    }

    private Map<String, String> cloudMetadata(String id) {
        Blob blob = cloudStorage().get(cloudBlobId(id));
        if (blob == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Allegato non trovato nello storage Firebase.");
        }
        Map<String, String> metadata = blob.getMetadata();
        if (metadata == null) {
            throw new IllegalStateException("Metadati allegato non disponibili.");
        }
        return metadata;
    }

    private void requireAuthorized(
            Map<String, String> record,
            String tenant,
            String uid,
            boolean manager) {
        if (!Objects.equals(tenant, record.get("tenantId"))
                || (!manager && !Objects.equals(uid, record.get("uid")))) {
            throw new SecurityException("Allegato non autorizzato.");
        }
    }

    private void verifyIntegrity(Map<String, String> record, byte[] data) throws Exception {
        String expected = record.get("digest");
        if (expected == null || data.length > MAX_BYTES || !hash(data).equals(expected)) {
            throw new IllegalStateException("Integrità allegato non valida.");
        }
    }

    private Storage cloudStorage() {
        Bucket bucket = StorageClient.getInstance().bucket();
        if (bucket == null || bucket.getName() == null || bucket.getName().isBlank()) {
            throw new IllegalStateException("Firebase Storage bucket non configurato.");
        }
        return bucket.getStorage();
    }

    private BlobId cloudBlobId(String id) {
        validateId(id);
        Bucket bucket = StorageClient.getInstance().bucket();
        if (bucket == null || bucket.getName() == null || bucket.getName().isBlank()) {
            throw new IllegalStateException("Firebase Storage bucket non configurato.");
        }
        return BlobId.of(bucket.getName(), CLOUD_PREFIX + id);
    }

    private void requireIdentityAndPayload(
            String tenant,
            String uid,
            String operation,
            String name,
            byte[] data) {
        if (tenant == null || uid == null) {
            throw new SecurityException("Identità richiesta.");
        }
        if (operation == null
                || !operation.matches("[A-Za-z0-9_-]{1,160}")
                || name == null
                || name.isBlank()
                || name.length() > 160
                || data == null
                || data.length == 0
                || data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Allegato non valido: limite 10 MB.");
        }
    }

    private String safeName(String name) {
        return name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
    }

    private void validateId(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Allegato non valido.");
        }
    }

    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(data));
    }
}
