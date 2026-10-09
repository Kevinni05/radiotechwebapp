package com.radiotech.radiotech_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.MaintenanceReport;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.model.TaskStatus;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.GeoFencePolicy;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

@Service
public class MaintenanceReportService {
    private static final String COLLECTION = "maintenanceReports";
    private static final String SUBMITTED = "SUBMITTED";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String APPROVAL_PENDING = "APPROVAL_PENDING";
    private static final String INVENTORY_PENDING = "PENDING";
    private static final String INVENTORY_COMPLETE = "COMPLETE";
    private static final Duration REVIEW_LEASE_DURATION = Duration.ofMinutes(2);
    private static final String IDEMPOTENCY_COLLECTION = "apiIdempotencyKeys";
    private static final Gson REQUEST_HASH_GSON = new GsonBuilder().serializeNulls()
            .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE).create();

    private record SubmissionResult(String reportId, boolean created, String requestHash) {
    }

    private record ReviewClaim(MaintenanceReport report, String attemptId, boolean alreadyFinalized) {
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException() {
            super("Idempotency-Key già utilizzata per un report diverso.");
        }
    }

    private final TaskService taskService;
    private final AuditService auditService;
    private final RicambioService ricambioService;
    private final OperatorService operatorService;

    @Value("${radiotech.reports.verification-secret:}")
    private String verificationSecret;

    @Value("${radiotech.reports.public-base-url:}")
    private String publicBaseUrl;

    @Value("${app.firebase.storage-bucket:}")
    private String storageBucket;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    @Autowired(required = false)
    private LocalAttachmentService localFiles;

    public MaintenanceReportService(TaskService taskService, AuditService auditService,
            RicambioService ricambioService, OperatorService operatorService) {
        this.taskService = taskService;
        this.auditService = auditService;
        this.ricambioService = ricambioService;
        this.operatorService = operatorService;
    }

    public List<MaintenanceReport> getAll() throws Exception {
        QuerySnapshot snapshot = FirestoreClient.getFirestore().collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant())
                .get().get();
        return map(snapshot);
    }

    /**
     * Reports belonging to an operator. Accepts either the operator Firestore
     * document id or the Firebase UID so both the Control Room and the mobile
     * application can use the same endpoint.
     */
    public List<MaintenanceReport> getByOperator(String operatorRef) throws Exception {
        if (blank(operatorRef)) {
            throw new IllegalArgumentException("operatorId obbligatorio.");
        }

        var profile = FirestoreClient.getFirestore().collection("operators")
                .document(operatorRef.trim()).get().get();
        String uid = profile.exists() && currentTenant().equals(profile.getString("tenantId"))
                ? profile.getString("firebaseUid") : null;
        return getByOperator(operatorRef, uid);
    }

    public List<MaintenanceReport> getByOperator(String operatorRef, String firebaseUid) throws Exception {
        if (blank(operatorRef)) throw new IllegalArgumentException("operatorId obbligatorio.");
        return map(ArchiveQueries.legacyOperator(FirestoreClient.getFirestore().collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant()), operatorRef, firebaseUid).get().get());
    }

    public List<MaintenanceReport> getByTask(String taskId) throws Exception {
        if (blank(taskId)) throw new IllegalArgumentException("taskId obbligatorio.");
        return map(FirestoreClient.getFirestore().collection(COLLECTION).whereEqualTo("tenantId", currentTenant())
                .where(com.google.cloud.firestore.Filter.or(com.google.cloud.firestore.Filter.equalTo("taskId", taskId.trim()),
                        com.google.cloud.firestore.Filter.equalTo("task_id", taskId.trim()))).get().get());
    }

    public ArchiveQueries.Page<MaintenanceReport> getPage(int limit, String cursor, String operatorId, String firebaseUid) throws Exception {
        return getPage(limit, cursor, operatorId, firebaseUid, null);
    }

    public ArchiveQueries.Page<MaintenanceReport> getPage(int limit, String cursor, String operatorId, String firebaseUid, String status) throws Exception {
        Query query = FirestoreClient.getFirestore().collection(COLLECTION).whereEqualTo("tenantId", currentTenant());
        if (operatorId != null) query = ArchiveQueries.operator(query, ArchiveQueries.identities(operatorId, firebaseUid));
        return ArchiveQueries.page(filterStatus(query, status), limit, cursor, this::mapDocument);
    }

    public long count(String status) throws Exception {
        return filterStatus(FirestoreClient.getFirestore().collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant()), status).count().get().get().getCount();
    }

    private Query filterStatus(Query query, String status) {
        if (status == null || status.isBlank()) return query;
        if (SUBMITTED.equals(status)) return query.whereIn("status", List.of(SUBMITTED, APPROVAL_PENDING));
        if (!List.of(APPROVED, REJECTED, APPROVAL_PENDING).contains(status))
            throw new IllegalArgumentException("Stato revisione non valido.");
        return query.whereEqualTo("status", status);
    }

    public long countByOperator(String operatorId, String firebaseUid) throws Exception {
        return ArchiveQueries.operator(FirestoreClient.getFirestore().collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant()), ArchiveQueries.identities(operatorId, firebaseUid))
                .count().get().get().getCount();
    }

    public MaintenanceReport getById(String id) throws Exception {
        if (blank(id))
            throw new IllegalArgumentException("Report ID obbligatorio.");
        DocumentSnapshot doc = FirestoreClient.getFirestore().collection(COLLECTION).document(id).get().get();
        if (!doc.exists())
            throw new IllegalArgumentException("Report non trovato: " + id);
        requireDocumentTenant(doc);
        return mapDocument(doc);
    }

    public MaintenanceReport submit(MaintenanceReport report, String firebaseUid) throws Exception {
        return submit(report, firebaseUid, null);
    }

    public MaintenanceReport submit(MaintenanceReport report, String firebaseUid, String idempotencyKey)
            throws Exception {
        if (report == null)
            throw new IllegalArgumentException("Report obbligatorio.");
        String tenantId = TenantAccessPolicy.requireTenantAccess(currentTenant(), report.getTenantId());
        report.setTenantId(tenantId);
        if (blank(firebaseUid))
            throw new IllegalArgumentException("Operatore non autenticato.");
        if (blank(report.getTaskId()) && blank(report.getAntennaId())) {
            throw new IllegalArgumentException("taskId o antennaId obbligatorio.");
        }
        if (!blank(report.getTaskId())) {
            requireCheckOutLocation(report);
        }

        Firestore db = FirestoreClient.getFirestore();

        String authenticatedUid = firebaseUid.trim();
        Operator operator = operatorService.getByFirebaseUid(authenticatedUid);

        Task linkedTask = null;
        if (!blank(report.getTaskId())) {
            linkedTask = taskService.getById(report.getTaskId());
            boolean assigned = authenticatedUid.equals(linkedTask.getOperatorFirebaseUid())
                    || authenticatedUid.equals(linkedTask.getOperatorId())
                    || operator != null && operator.getId().equals(linkedTask.getOperatorId());
            if (!assigned) {
                throw new SecurityException("Il task non è assegnato all'operatore autenticato.");
            }
            TaskStatus taskStatus = TaskStatus.parse(linkedTask.getStatus());
            if (taskStatus != TaskStatus.IN_PROGRESS && taskStatus != TaskStatus.WAITING
                    && taskStatus != TaskStatus.COMPLETED && taskStatus != TaskStatus.REPORT_SUBMITTED
                    && taskStatus != TaskStatus.APPROVED && taskStatus != TaskStatus.CLOSED) {
                throw new IllegalArgumentException("Il task non è nello stato previsto per la submission del report.");
            }
            boolean checkoutRequired = taskStatus == TaskStatus.IN_PROGRESS || taskStatus == TaskStatus.WAITING
                    || taskStatus == TaskStatus.COMPLETED
                            && !authenticatedUid.equals(linkedTask.getCheckedOutBy());
            if (checkoutRequired) {
                taskService.validateCheckOutLocation(linkedTask, authenticatedUid,
                        operator != null ? operator.getId() : linkedTask.getOperatorId(),
                        report.getLatitude(), report.getLongitude());
            }
            if (blank(report.getAntennaId())) {
                report.setAntennaId(linkedTask.getAntennaId());
            }
        }

        if (!blank(report.getAntennaId())) {
            DocumentSnapshot antenna = db.collection("antennas").document(report.getAntennaId()).get().get();
            if (!antenna.exists() || !tenantId.equals(antenna.getString("tenantId"))) {
                throw new IllegalArgumentException("Antenna del report non trovata: " + report.getAntennaId());
            }
            report.setAntennaName(antenna.getString("name"));
        } else {
            report.setAntennaName(null);
        }

        report.setTaskTitle(linkedTask == null ? null : linkedTask.getTitle());
        String requestHash = reportRequestHash(report);

        validateAttachmentReferences(report.getAttachments(), tenantId, authenticatedUid);

        report.setOperatorFirebaseUid(authenticatedUid);
        report.setOperatorId(operator != null ? operator.getId() : authenticatedUid);
        report.setOperatorName(operator != null ? operator.getFullName() : null);
        report.setStatus(SUBMITTED);
        report.setSubmittedAt(Instant.now().toString());
        report.setArchiveAt(ArchiveQueries.timestamp(report.getSubmittedAt()));
        report.setOperatorRefs(ArchiveQueries.identities(report.getOperatorId(), authenticatedUid));
        report.setReviewedAt(null);
        report.setReviewedBy(null);
        report.setReviewNote(null);

        DocumentReference ref = db.collection(COLLECTION).document();
        report.setId(ref.getId());
        report.setIntegrityHash(reportIntegrityHash(report));
        String keyDocumentId = idempotencyDocumentId(tenantId, authenticatedUid, idempotencyKey);
        boolean created = true;
        MaintenanceReport resultReport = report;
        if (keyDocumentId == null) {
            ref.set(report).get();
        } else {
            DocumentReference keyReference = db.collection(IDEMPOTENCY_COLLECTION).document(keyDocumentId);
            SubmissionResult result;
            try {
                result = db.runTransaction(transaction -> {
                    DocumentSnapshot existingKey = transaction.get(keyReference).get();
                    if (existingKey.exists()) {
                        if (!tenantId.equals(existingKey.getString("tenantId"))
                                || !authenticatedUid.equals(existingKey.getString("actorUid"))) {
                            throw new SecurityException("Chiave idempotenza non valida per questa identita'.");
                        }
                        String existingHash = existingKey.getString("requestHash");
                        if (existingHash != null && !requestHash.equals(existingHash)) {
                            throw new IdempotencyConflictException();
                        }
                        String existingResourceId = existingKey.getString("resourceId");
                        if (blank(existingResourceId)) {
                            throw new IdempotencyConflictException();
                        }
                        return new SubmissionResult(existingResourceId, false, existingHash);
                    }

                    transaction.set(ref, report);
                    Map<String, Object> keyEntry = new LinkedHashMap<>();
                    keyEntry.put("tenantId", tenantId);
                    keyEntry.put("actorUid", authenticatedUid);
                    keyEntry.put("resourceId", report.getId());
                    keyEntry.put("requestHash", requestHash);
                    keyEntry.put("createdAt", Instant.now().toString());
                    transaction.set(keyReference, keyEntry);
                    return new SubmissionResult(report.getId(), true, requestHash);
                }).get();
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw exception;
            }
            created = result.created();
            if (!created) {
                DocumentSnapshot previousReport = db.collection(COLLECTION)
                        .document(result.reportId()).get().get();
                if (!previousReport.exists()
                        || !tenantId.equals(previousReport.getString("tenantId"))
                        || !authenticatedUid.equals(previousReport.getString("operatorFirebaseUid"))) {
                    throw new SecurityException("Risorsa idempotente non accessibile.");
                }
                MaintenanceReport previous = previousReport.toObject(MaintenanceReport.class);
                previous.setId(previousReport.getId());
                String previousRequestHash = reportRequestHash(previous);
                if (!requestHash.equals(previousRequestHash)
                        || result.requestHash() != null && !requestHash.equals(result.requestHash())) {
                    throw new IdempotencyConflictException();
                }
                if (result.requestHash() == null) {
                    keyReference.update("requestHash", requestHash).get();
                }
                resultReport = previous;
            }
        }

        if (created) {
            incrementMetric("radiotech.report.submissions", "result", "created");
            auditService.record("REPORT_SUBMITTED", tenantId, authenticatedUid, "MAINTENANCE_REPORT", report.getId(),
                    "SUCCESS", null, null);
        }

        if (created && operator != null) {
            operatorService.updateLastSeen(operator.getId());
        }

        if (!blank(resultReport.getTaskId())) {
            Task currentTask = taskService.getById(resultReport.getTaskId());
            TaskStatus currentStatus = TaskStatus.parse(currentTask.getStatus());
            boolean checkoutRequired = currentStatus == TaskStatus.IN_PROGRESS || currentStatus == TaskStatus.WAITING
                    || currentStatus == TaskStatus.COMPLETED
                            && !authenticatedUid.equals(currentTask.getCheckedOutBy());
            if (checkoutRequired) {
                taskService.completeTaskWithLocation(resultReport.getTaskId(), authenticatedUid,
                        resultReport.getOperatorId(), resultReport.getLatitude(), resultReport.getLongitude());
                currentStatus = TaskStatus.COMPLETED;
            }
            if (currentStatus == TaskStatus.COMPLETED) {
                taskService.updateStatus(resultReport.getTaskId(), TaskStatus.REPORT_SUBMITTED.name());
            } else if (currentStatus != TaskStatus.REPORT_SUBMITTED && currentStatus != TaskStatus.APPROVED
                    && currentStatus != TaskStatus.CLOSED) {
                throw new IllegalArgumentException("Transizione task/report non consentita.");
            }
        }

        return resultReport;
    }

    public String verificationUrl(MaintenanceReport report) throws Exception {
        requireVerificationConfiguration();
        if (report == null || blank(report.getId()) || blank(report.getIntegrityHash())) {
            throw new IllegalArgumentException("Report non verificabile.");
        }
        String id = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(report.getId().getBytes(StandardCharsets.UTF_8));
        String payload = id + "." + report.getIntegrityHash();
        String token = payload + "." + sign(payload);
        String base = publicBaseUrl.trim().replaceAll("/+$", "");
        return base + "/api/v1/reports/verify/" + token;
    }

    public void requireVerificationConfiguration() throws Exception {
        sign("report-verification-configuration-check");
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (!(base.startsWith("https://") || base.startsWith("http://localhost")
                || base.startsWith("http://127.0.0.1"))) {
            throw new IllegalStateException("Report verification public base URL is not configured safely.");
        }
    }

    public Map<String, Object> verifyPublicToken(String token) throws Exception {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException();
            }
            String payload = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            String id = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (blank(id) || id.contains("/")) throw new IllegalArgumentException();
            DocumentSnapshot doc = FirestoreClient.getFirestore().collection(COLLECTION).document(id).get().get();
            if (!doc.exists()) throw new IllegalArgumentException();
            MaintenanceReport stored = doc.toObject(MaintenanceReport.class);
            stored.setId(doc.getId());
            String actual = reportIntegrityHash(stored);
            String storedHash = doc.getString("integrityHash");
            if (storedHash == null || !MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))
                    || !MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    storedHash.getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            incrementMetric("radiotech.report.verifications", "result", "valid");
            return Map.of("valid", true, "reportId", id,
                    "submittedAt", stored.getSubmittedAt() == null ? "" : stored.getSubmittedAt(),
                    "integrity", "VALID");
        } catch (IllegalArgumentException invalid) {
            incrementMetric("radiotech.report.verifications", "result", "invalid");
            throw new IllegalArgumentException("Verifica non valida.");
        }
    }

    private void incrementMetric(String name, String tag, String value) {
        if (meterRegistry != null) meterRegistry.counter(name, tag, value).increment();
    }

    private void validateAttachmentReferences(List<String> attachments, String tenantId, String operatorUid) {
        if (attachments == null) return;
        if (attachments.size() > 50) throw new IllegalArgumentException("Troppi allegati nel report.");
        for (String attachment : attachments) {
            if(attachment!=null&&attachment.startsWith("radiotech-file:")){
                if(localFiles==null)throw new IllegalArgumentException("Storage locale non disponibile.");
                localFiles.validateOwned(attachment,tenantId,operatorUid);
                continue;
            }
            validateAttachmentReference(attachment, tenantId, operatorUid, storageBucket);
        }
    }

    static void validateAttachmentReference(String rawUrl, String tenantId, String operatorUid, String expectedBucket) {
        try {
            if (blank(expectedBucket) || blank(rawUrl)) throw new IllegalArgumentException();
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            boolean firebaseHost = "firebasestorage.googleapis.com".equalsIgnoreCase(host);
            String emulator = System.getenv("FIREBASE_STORAGE_EMULATOR_HOST");
            boolean emulatorHost = false;
            if (!blank(emulator)) {
                URI emulatorUri = URI.create(emulator.contains("://") ? emulator : "http://" + emulator);
                emulatorHost = emulatorUri.getHost() != null && emulatorUri.getHost().equalsIgnoreCase(host)
                        && (emulatorUri.getPort() == -1 || emulatorUri.getPort() == uri.getPort());
            }
            if (!(firebaseHost && "https".equalsIgnoreCase(uri.getScheme())
                    || emulatorHost && "http".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            if (firebaseHost && uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException();
            String[] segments = uri.getRawPath().split("/", -1);
            if (segments.length < 6 || !"v0".equals(segments[1]) || !"b".equals(segments[2])
                    || !expectedBucket.equals(segments[3]) || !"o".equals(segments[4])) {
                throw new IllegalArgumentException();
            }
            String objectPath = URLDecoder.decode(segments[5], StandardCharsets.UTF_8);
            String requiredPrefix = "tenants/" + tenantId + "/maintenance-reports/" + operatorUid + "/";
            if (!objectPath.startsWith(requiredPrefix) || objectPath.length() == requiredPrefix.length()) {
                throw new IllegalArgumentException();
            }
            boolean downloadToken = uri.getRawQuery() != null && java.util.Arrays.stream(uri.getRawQuery().split("&"))
                    .anyMatch(value -> value.startsWith("token=") && value.length() > 6);
            if (!downloadToken) throw new IllegalArgumentException();
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Riferimento allegato non valido per tenant e operatore.");
        }
    }

    private String sign(String payload) throws Exception {
        if (verificationSecret == null || verificationSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Report verification secret is not configured.");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(verificationSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String reportIntegrityHash(MaintenanceReport report) throws Exception {
        Map<String, Object> immutable = new LinkedHashMap<>();
        immutable.put("id", report.getId()); immutable.put("tenantId", report.getTenantId());
        immutable.put("taskId", report.getTaskId()); immutable.put("operatorId", report.getOperatorId());
        immutable.put("operatorFirebaseUid", report.getOperatorFirebaseUid());
        immutable.put("antennaId", report.getAntennaId()); immutable.put("submittedAt", report.getSubmittedAt());
        immutable.put("startedAt", report.getStartedAt()); immutable.put("completedAt", report.getCompletedAt());
        immutable.put("description", report.getDescription()); immutable.put("workPerformed", report.getWorkPerformed());
        immutable.put("findings", report.getFindings()); immutable.put("measurements", report.getMeasurements());
        immutable.put("materialsUsed", report.getMaterialsUsed()); immutable.put("attachments", report.getAttachments());
        immutable.put("latitude", report.getLatitude()); immutable.put("longitude", report.getLongitude());
        immutable.put("operatorNotes", report.getOperatorNotes()); immutable.put("checklist", report.getChecklist());
        immutable.put("digitalSignature", report.getDigitalSignature());
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                REQUEST_HASH_GSON.toJson(sortJsonMaps(immutable)).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }

    static void requireCheckOutLocation(MaintenanceReport report) {
        if (report.getLatitude() == null || report.getLongitude() == null) {
            throw new IllegalArgumentException("La posizione GPS è obbligatoria per il check-out del task.");
        }
        GeoFencePolicy.distanceMeters(report.getLatitude(), report.getLongitude(),
                report.getLatitude(), report.getLongitude());
    }

    static String reportRequestHash(MaintenanceReport report) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskId", report.getTaskId());
        request.put("antennaId", report.getAntennaId());
        request.put("startedAt", report.getStartedAt());
        request.put("completedAt", report.getCompletedAt());
        request.put("description", report.getDescription());
        request.put("workPerformed", report.getWorkPerformed());
        request.put("findings", report.getFindings());
        request.put("measurements", report.getMeasurements());
        request.put("materialsUsed", report.getMaterialsUsed());
        request.put("attachments", report.getAttachments());
        request.put("latitude", report.getLatitude());
        request.put("longitude", report.getLongitude());
        request.put("operatorNotes", report.getOperatorNotes());
        request.put("checklist", report.getChecklist());
        request.put("digitalSignature", report.getDigitalSignature());

        String canonicalRequest = REQUEST_HASH_GSON.toJson(sortJsonMaps(request));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static Object sortJsonMaps(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nestedValue) -> sorted.put(String.valueOf(key), sortJsonMaps(nestedValue)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(MaintenanceReportService::sortJsonMaps).toList();
        }
        return value;
    }

    static String idempotencyDocumentId(String tenantId, String actorUid, String rawKey) throws Exception {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String key = rawKey.trim();
        if (!key.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Idempotency-Key non valido.");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((tenantId + "|" + actorUid + "|" + key).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    public MaintenanceReport review(String id, boolean approve, String note, String reviewerUid) throws Exception {
        String tenantId = currentTenant();
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference reportReference = db.collection(COLLECTION).document(id);
        String reviewedBy = reviewerUid == null ? null : reviewerUid.trim();
        String reviewNote = note == null ? "" : note.trim();
        if (!approve) {
            awaitTransaction(db.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(reportReference).get();
                if (!snapshot.exists()
                        || !TenantAccessPolicy.canAccessTenant(tenantId, snapshot.getString("tenantId"))) {
                    throw new IllegalArgumentException("Report non trovato.");
                }
                if (!SUBMITTED.equals(snapshot.getString("status"))) {
                    throw new IllegalArgumentException("Il report non è in stato SUBMITTED.");
                }
                String taskId = snapshot.getString("taskId");
                DocumentSnapshot task = blank(taskId) ? null
                        : transaction.get(db.collection("tasks").document(taskId)).get();
                String now = Instant.now().toString();
                transaction.update(reportReference,
                        "status", REJECTED,
                        "reviewedAt", now,
                        "reviewedBy", reviewedBy,
                        "reviewNote", reviewNote);
                if (task != null && task.exists() && tenantId.equals(task.getString("tenantId"))
                        && TaskStatus.parse(task.getString("status")) == TaskStatus.REPORT_SUBMITTED) {
                    transaction.update(task.getReference(), "status", TaskStatus.COMPLETED.name(), "updatedAt", now);
                }
                return null;
            }));
            auditService.record("REPORT_REJECTED", tenantId, reviewerUid,
                    "MAINTENANCE_REPORT", id, REJECTED, null, null);
            return getById(id);
        }

        ReviewClaim claim = claimApproval(reportReference, tenantId, reviewedBy, reviewNote);
        if (claim.alreadyFinalized()) {
            return getById(id);
        }

        MaintenanceReport reviewed = claim.report();
        try {
            ricambioService.consume(reviewed.getMaterialsUsed(), id);
            if (!blank(reviewed.getTaskId())) {
                taskService.updateStatus(reviewed.getTaskId(), TaskStatus.APPROVED.name(),
                        "report-approval-" + id);
            }
            finalizeApproval(reportReference, tenantId, claim.attemptId());
        } catch (Exception exception) {
            try {
                releaseApprovalLease(reportReference, tenantId, claim.attemptId());
            } catch (Exception releaseException) {
                exception.addSuppressed(releaseException);
            }
            throw exception;
        }
        auditService.record("REPORT_APPROVED", tenantId, reviewerUid,
                "MAINTENANCE_REPORT", id, APPROVED, null, null);
        return getById(id);
    }

    private ReviewClaim claimApproval(DocumentReference reportReference, String tenantId,
            String reviewedBy, String reviewNote) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        return awaitTransaction(db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reportReference).get();
            if (!snapshot.exists()
                    || !TenantAccessPolicy.canAccessTenant(tenantId, snapshot.getString("tenantId"))) {
                throw new IllegalArgumentException("Report non trovato.");
            }

            String status = snapshot.getString("status");
            if (APPROVED.equals(status)) {
                if (INVENTORY_COMPLETE.equals(snapshot.getString("inventoryConsumptionStatus"))) {
                    return new ReviewClaim(null, null, true);
                }
                Object materials = snapshot.get("materialsUsed");
                if (snapshot.get("inventoryConsumptionStatus") == null
                        && (!(materials instanceof List<?> materialList) || materialList.isEmpty())) {
                    return new ReviewClaim(null, null, true);
                }
                throw new IllegalArgumentException(
                        "Approvazione precedente senza esito inventario: riconciliazione necessaria.");
            }
            if (APPROVAL_PENDING.equals(status)) {
                if (!INVENTORY_PENDING.equals(snapshot.getString("inventoryConsumptionStatus"))) {
                    throw new IllegalArgumentException("Stato inventario approvazione non valido.");
                }
                String leaseUntil = snapshot.getString("approvalLeaseUntil");
                if (!blank(leaseUntil) && Instant.parse(leaseUntil).isAfter(Instant.now())) {
                    throw new IllegalArgumentException("Il report è già in fase di approvazione.");
                }
            } else if (!SUBMITTED.equals(status)) {
                throw new IllegalArgumentException("Il report non è in stato SUBMITTED.");
            }

            Instant now = Instant.now();
            String attemptId = java.util.UUID.randomUUID().toString();
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("status", APPROVAL_PENDING);
            updates.put("inventoryConsumptionStatus", INVENTORY_PENDING);
            updates.put("approvalAttemptId", attemptId);
            updates.put("approvalLeaseUntil", now.plus(REVIEW_LEASE_DURATION).toString());
            if (SUBMITTED.equals(status)) {
                updates.put("reviewedAt", now.toString());
                updates.put("reviewedBy", reviewedBy);
                updates.put("reviewNote", reviewNote);
            }
            transaction.update(reportReference, updates);
            MaintenanceReport report = snapshot.toObject(MaintenanceReport.class);
            report.setId(snapshot.getId());
            return new ReviewClaim(report, attemptId, false);
        }));
    }

    private void finalizeApproval(DocumentReference reportReference, String tenantId, String attemptId)
            throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        awaitTransaction(db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reportReference).get();
            if (!snapshot.exists()
                    || !TenantAccessPolicy.canAccessTenant(tenantId, snapshot.getString("tenantId"))
                    || !APPROVAL_PENDING.equals(snapshot.getString("status"))
                    || !attemptId.equals(snapshot.getString("approvalAttemptId"))) {
                throw new IllegalArgumentException("Approvazione report non più valida.");
            }
            transaction.update(reportReference,
                    "status", APPROVED,
                    "inventoryConsumptionStatus", INVENTORY_COMPLETE,
                    "approvalAttemptId", FieldValue.delete(),
                    "approvalLeaseUntil", FieldValue.delete());
            return null;
        }));
    }

    private void releaseApprovalLease(DocumentReference reportReference, String tenantId, String attemptId)
            throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        awaitTransaction(db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reportReference).get();
            if (snapshot.exists()
                    && TenantAccessPolicy.canAccessTenant(tenantId, snapshot.getString("tenantId"))
                    && APPROVAL_PENDING.equals(snapshot.getString("status"))
                    && attemptId.equals(snapshot.getString("approvalAttemptId"))) {
                transaction.update(reportReference,
                        "approvalAttemptId", FieldValue.delete(),
                        "approvalLeaseUntil", FieldValue.delete());
            }
            return null;
        }));
    }

    private <T> T awaitTransaction(ApiFuture<T> transaction) throws Exception {
        try {
            return transaction.get();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private List<MaintenanceReport> query(String field, String value) throws Exception {
        if (blank(value))
            throw new IllegalArgumentException(field + " obbligatorio.");
        ApiFuture<QuerySnapshot> future = FirestoreClient.getFirestore().collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant())
                .whereEqualTo(field, value.trim()).get();
        return map(future.get());
    }

    private String currentTenant() {
        return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }

    private void requireDocumentTenant(DocumentSnapshot document) {
        if (!TenantAccessPolicy.canAccessTenant(currentTenant(), document.getString("tenantId"))) {
            throw new IllegalArgumentException("Report non trovato.");
        }
    }

    private List<MaintenanceReport> map(QuerySnapshot snapshot) {
        List<MaintenanceReport> result = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
            result.add(mapDocument(doc));
        }
        result.sort(java.util.Comparator.comparing(
                (MaintenanceReport r) -> nullSafe(r.getSubmittedAt())).reversed()
                .thenComparing(MaintenanceReport::getId));
        return result;
    }

    private MaintenanceReport mapDocument(DocumentSnapshot doc) {
            Map<String, Object> data = new LinkedHashMap<>(doc.getData());
            for (String field : List.of("startedAt", "completedAt", "submittedAt", "reviewedAt", "approvalLeaseUntil")) {
                Object value = data.get(field);
                if (value instanceof com.google.cloud.Timestamp timestamp) {
                    data.put(field, Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toString());
                }
            }
            MaintenanceReport report = REQUEST_HASH_GSON.fromJson(
                    REQUEST_HASH_GSON.toJson(data), MaintenanceReport.class);
            if (blank(report.getAntennaId())) report.setAntennaId(doc.getString("antenna_id"));
            if (blank(report.getOperatorNotes())) report.setOperatorNotes(doc.getString("notes"));
            if (blank(report.getOperatorFirebaseUid())) report.setOperatorFirebaseUid(doc.getString("operator_uid"));
            if (blank(report.getSubmittedAt())) {
                Object legacy = doc.get("created_at");
                if (legacy instanceof com.google.cloud.Timestamp timestamp) report.setSubmittedAt(java.time.Instant.ofEpochSecond(timestamp.getSeconds(),timestamp.getNanos()).toString());
                else if (legacy instanceof String text) report.setSubmittedAt(text);
            }
            if (report.getAttachments()==null)report.setAttachments(new ArrayList<>());
            for (String field : List.of("pdfUrl","pdf_url")) {
                Object ref=doc.get(field); if(ref instanceof String text && !text.isBlank() && !report.getAttachments().contains(text))report.getAttachments().add(text);
            }
            report.setId(doc.getId());
            if (report.getOperatorFirebaseUid() == null) {
                report.setOperatorFirebaseUid(readString(doc, "operatorFirebaseUid", doc.getString("operator_uid")));
            }
            if (blank(report.getTaskId())) report.setTaskId(readString(doc, "task_id", null));
            if (blank(report.getOperatorId())) report.setOperatorId(readString(doc, "operator_id", null));
            return report;
    }

    private String readString(DocumentSnapshot snapshot, String field, String fallback) {
        Map<String, Object> data = snapshot.getData();
        if (data == null) {
            return fallback;
        }
        Object value = data.get(field);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
