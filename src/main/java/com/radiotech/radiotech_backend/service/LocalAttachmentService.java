package com.radiotech.radiotech_backend.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.firebase.cloud.StorageClient;
import com.google.firebase.cloud.FirestoreClient;
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
 * Production persists opaque references in Firestore chunks by default, or
 * Firebase Storage when explicitly configured. Files are served by the API.
 */
@Service
public class LocalAttachmentService {
    public static final int MAX_BYTES = 10_000_000;
    private static final String CLOUD_PREFIX = "radiotech-report-files/";
    private static final String FILE_COLLECTION = "reportFiles";
    private static final int CHUNK_BYTES = 256 * 1024;

    private final boolean localMode;
    private final String backend;
    private final Path root;
    private final JsonMapper json = JsonMapper.builder().build();

    public LocalAttachmentService(Environment env) {
        boolean production = env.acceptsProfiles(Profiles.of("production"));
        backend = env.getProperty("radiotech.attachments.backend", production ? "FIRESTORE" : "LOCAL_FILESYSTEM")
                .trim().toUpperCase(Locale.ROOT);
        if (!Set.of("LOCAL_FILESYSTEM", "FIRESTORE", "FIREBASE_STORAGE").contains(backend)
                || production && "LOCAL_FILESYSTEM".equals(backend)) {
            throw new IllegalArgumentException("Archivio allegati non valido per questo ambiente.");
        }
        localMode = "LOCAL_FILESYSTEM".equals(backend);
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
        return backend;
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
        if (localMode) return saveLocal(tenant, uid, operation, name, data);
        if ("FIREBASE_STORAGE".equals(backend)) return saveCloud(tenant, uid, operation, name, data);
        String id = hash((tenant + ":" + uid + ":" + operation).getBytes(StandardCharsets.UTF_8));
        return saveFirestore(id, tenant, uid, safeName(name), data);
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
        if (localMode) return readLocal(id, tenant, uid, manager);
        return "FIREBASE_STORAGE".equals(backend)
                ? readCloud(id, tenant, uid, manager) : readFirestore(id, tenant, uid, manager);
    }

    public void validateOwned(String reference, String tenant, String uid) {
        try {
            if (reference == null || !reference.startsWith("radiotech-file:")) {
                throw new IllegalArgumentException("Riferimento allegato non valido.");
            }
            String id = reference.substring("radiotech-file:".length());
            validateId(id);
            Map<String, String> record = localMode ? localMetadata(id)
                    : "FIREBASE_STORAGE".equals(backend) ? cloudMetadata(id) : firestoreMetadata(id);
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

    private Map<String, Object> saveFirestore(String id, String tenant, String uid, String name, byte[] data)
            throws Exception {
        validateId(id);
        String digest = hash(data);
        var db = FirestoreClient.getFirestore();
        var ref = db.collection(FILE_COLLECTION).document(id);
        int chunkCount = (data.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        try {
            db.runTransaction(tx -> {
                var previous = tx.get(ref).get();
                if (previous.exists()) {
                    if (!tenant.equals(previous.getString("tenantId")) || !uid.equals(previous.getString("uid"))
                            || !digest.equals(previous.getString("digest"))) {
                        throw new IllegalArgumentException("Operazione allegato già usata per un altro file.");
                    }
                    return null;
                }
                for (int index = 0; index < chunkCount; index++) {
                    byte[] chunk = Arrays.copyOfRange(data, index * CHUNK_BYTES,
                            Math.min(data.length, (index + 1) * CHUNK_BYTES));
                    tx.set(ref.collection("chunks").document(Integer.toString(index)),
                            Map.of("data", com.google.cloud.firestore.Blob.fromBytes(chunk)));
                }
                tx.set(ref, Map.of("tenantId", tenant, "uid", uid, "name", name,
                        "digest", digest, "size", data.length, "chunkCount", chunkCount,
                        "createdAt", java.time.Instant.now().toString()));
                return null;
            }).get();
        } catch (java.util.concurrent.ExecutionException failure) {
            if (failure.getCause() instanceof IllegalArgumentException invalid) throw invalid;
            throw failure;
        }
        return Map.of("reference", "radiotech-file:" + id, "name", name);
    }

    private Map<String, String> firestoreMetadata(String id) throws Exception {
        validateId(id);
        var document = FirestoreClient.getFirestore().collection(FILE_COLLECTION).document(id).get().get();
        if (!document.exists()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Allegato non trovato nell’archivio persistente.");
        Map<String, String> metadata = new LinkedHashMap<>();
        document.getData().forEach((key, value) -> metadata.put(key, String.valueOf(value)));
        return metadata;
    }

    private Map<String, Object> readFirestore(String id, String tenant, String uid, boolean manager) throws Exception {
        Map<String, String> metadata = firestoreMetadata(id);
        requireAuthorized(metadata, tenant, uid, manager);
        int size = Integer.parseInt(metadata.get("size")), count = Integer.parseInt(metadata.get("chunkCount"));
        if (size <= 0 || size > MAX_BYTES || count != (size + CHUNK_BYTES - 1) / CHUNK_BYTES)
            throw new IllegalStateException("Metadati allegato non validi.");
        var collection = FirestoreClient.getFirestore().collection(FILE_COLLECTION).document(id).collection("chunks");
        var refs = new com.google.cloud.firestore.DocumentReference[count];
        for (int index = 0; index < count; index++) refs[index] = collection.document(Integer.toString(index));
        var chunks = FirestoreClient.getFirestore().getAll(refs).get();
        byte[] data = new byte[size];
        for (int index = 0; index < count; index++) {
            var chunk = chunks.get(index).getBlob("data");
            byte[] bytes = chunk == null ? null : chunk.toBytes();
            int expected = Math.min(CHUNK_BYTES, size - index * CHUNK_BYTES);
            if (bytes == null || bytes.length != expected) throw new IllegalStateException("Allegato incompleto.");
            System.arraycopy(bytes, 0, data, index * CHUNK_BYTES, expected);
        }
        verifyIntegrity(metadata, data);
        return Map.of("name", metadata.get("name"), "base64", Base64.getEncoder().encodeToString(data));
    }

    /** Administrative recovery only; never exposed as an HTTP operation. */
    public Map<String, Object> restoreLocalArchive(String reference, String tenant, String uid) throws Exception {
        if (!"FIRESTORE".equals(backend) || reference == null || !reference.matches("radiotech-file:[a-f0-9]{64}"))
            throw new IllegalArgumentException("Ripristino disponibile solo nell’archivio Firestore.");
        String id = reference.substring("radiotech-file:".length());
        var metadata = localMetadata(id);
        requireAuthorized(metadata, tenant, uid, false);
        byte[] data = Files.readAllBytes(path(id, ".bin"));
        verifyIntegrity(metadata, data);
        return saveFirestore(id, tenant, uid, metadata.get("name"), data);
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
