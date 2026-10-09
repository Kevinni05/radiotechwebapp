// src/main/java/com/radiotech/radiotech_backend/service/TaskService.java

package com.radiotech.radiotech_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.GeoFencePolicy;
import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.model.TaskStatus;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskService.class);

        private static final String COLLECTION = "tasks";
        private static final String IDEMPOTENCY_COLLECTION = "taskStatusIdempotencyKeys";

        public static class IdempotencyConflictException extends RuntimeException {
                public IdempotencyConflictException() {
                        super("Idempotency-Key già utilizzata per uno stato task diverso.");
                }
        }

        private final OperatorService operatorService;
        private final AuditService auditService;
        private final NotificationService notificationService;

        @Value("${radiotech.geo.geofence-radius-meters:250}")
        private double geofenceRadiusMeters;

        public TaskService(OperatorService operatorService, AuditService auditService,
                        NotificationService notificationService) {
                this.operatorService = operatorService;
                this.auditService = auditService;
                this.notificationService = notificationService;
        }

        // ============================================================
        // GET ALL
        // ============================================================

        public List<Task> getAllTasks() throws Exception {

                Firestore db = FirestoreClient.getFirestore();

                ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                                .whereEqualTo("tenantId", requireCurrentTenant())
                                .get();

                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                var profiles = new java.util.HashMap<String, QueryDocumentSnapshot>();
                for (var profile : db.collection("operators").whereEqualTo("tenantId", requireCurrentTenant())
                                .get().get().getDocuments()) {
                        profiles.put(profile.getId(), profile);
                        if (!isBlank(profile.getString("firebaseUid"))) profiles.put(profile.getString("firebaseUid"), profile);
                }

                List<Task> tasks = new ArrayList<>();

                for (QueryDocumentSnapshot document : documents) {

                        Task task = mapDocument(document);

                        if (task == null) {
                                continue;
                        }

                        task.setId(document.getId());
                        normalizeLegacyOperator(task);
                        var profile = profiles.get(task.getOperatorId());
                        if (profile == null) profile = profiles.get(task.getOperatorFirebaseUid());
                        if (profile != null) {
                                task.setOperatorId(profile.getId());
                                task.setOperatorFirebaseUid(profile.getString("firebaseUid"));
                                if (isBlank(task.getOperatorName())) task.setOperatorName(profile.getString("fullName"));
                        }

                        tasks.add(task);
                }

                tasks.sort(java.util.Comparator.comparing(
                                (Task task) -> java.util.Objects.toString(task.getCreatedAt(), "")).reversed()
                                .thenComparing(Task::getId));
                return tasks;
        }

        // ============================================================
        // GET BY ID
        // ============================================================

        public Task getById(String id) throws Exception {

                validateId(id);

                Firestore db = FirestoreClient.getFirestore();

                DocumentSnapshot document = db.collection(COLLECTION)
                                .document(id)
                                .get()
                                .get();

                if (!document.exists()) {

                        throw new IllegalArgumentException(
                                        "Task non trovato: " + id);
                }

                if (!requireCurrentTenant().equals(document.getString("tenantId"))) {
                        throw new IllegalArgumentException("Task non trovato.");
                }
                Task task = mapDocument(document);

                if (task == null) {

                        throw new IllegalStateException(
                                        "Impossibile convertire il task.");
                }

                task.setId(document.getId());
                if (!TenantAccessPolicy.canAccessTask(task, requireCurrentTenant())) {
                        throw new IllegalArgumentException("Task non trovato.");
                }
                normalizeLegacyOperator(task);

                return task;
        }

        // ============================================================
        // CREATE
        // ============================================================

        public Task createTask(Task task)
                        throws Exception {

                String tenantId = TenantAccessPolicy.requireTenantAccess(
                                requireCurrentTenant(), task == null ? null : task.getTenantId());
                validateTask(task);
                task.setTenantId(tenantId);

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document();

                String now = Instant.now().toString();

                task.setId(document.getId());

                normalizeLegacyOperator(task);
                if (!isBlank(task.getOperatorId())) {
                        Operator operator = operatorService.getById(task.getOperatorId());
                        if (operator == null)
                                throw new IllegalArgumentException("Operatore non trovato.");
                        if (!TenantAccessPolicy.canAccessTenant(tenantId, operator.getTenantId())) {
                                throw new IllegalArgumentException("Operatore non appartenente al tenant autenticato.");
                        }
                        task.setOperatorName(operator.getFullName());
                        task.setOperatorFirebaseUid(operator.getFirebaseUid());
                }

                if (!isBlank(task.getAntennaId())) {
                        DocumentSnapshot antenna = db.collection("antennas").document(task.getAntennaId()).get().get();
                        if (!antenna.exists() || !tenantId.equals(antenna.getString("tenantId"))) {
                                throw new IllegalArgumentException("Antenna non trovata: " + task.getAntennaId());
                        }
                }
                validateDueAt(task.getDueAt());

                if (isBlank(task.getStatus())) {

                        task.setStatus("ASSIGNED");

                } else {

                        task.setStatus(
                                        normalizzaStato(
                                                        task.getStatus()));
                }

                if (isBlank(task.getPriority())) {

                        task.setPriority("MEDIUM");

                } else {

                        task.setPriority(
                                        normalizzaPriorita(
                                                        task.getPriority()));
                }

                if (isBlank(task.getCreatedBy())) {
                        task.setCreatedBy(task.getOperatorId());
                }
                if (isBlank(task.getUpdatedBy())) {
                        task.setUpdatedBy(task.getCreatedBy());
                }
                if (task.getMetadata() == null) {
                        task.setMetadata(new java.util.LinkedHashMap<>());
                }

                task.setCreatedAt(now);
                task.setArchiveAt(ArchiveQueries.timestamp(now));
                task.setOperatorRefs(ArchiveQueries.identities(task.getOperatorId(), task.getOperatorFirebaseUid()));
                task.setUpdatedAt(now);
                task.setCompletedAt(null);

                document.set(task).get();
                auditService.record("TASK_CREATED", tenantId, task.getCreatedBy(), "TASK", task.getId(), "SUCCESS",
                                null, null);

                if (!isBlank(task.getOperatorId())) {
                        try {
                                String message = isBlank(task.getDescription())
                                                ? "Ti e stato assegnato un nuovo task operativo."
                                                : task.getDescription();
                                notificationService.notifyOperator(
                                                task.getOperatorId(),
                                                "Nuovo task: " + task.getTitle(),
                                                message,
                                                java.util.Map.of(
                                                                "taskId", task.getId(),
                                                                "type", "TASK_ASSIGNED"));
                        } catch (Exception notificationError) {
                                log.warn("Notifica task non inviata: "
                                                + notificationError.getMessage());
                        }
                }

                return task;
        }

        // ============================================================
        // UPDATE STATUS
        // ============================================================

        public Task checkInTask(String id, String firebaseUid, String operatorId,
                        double latitude, double longitude) throws Exception {
                validateId(id);
                if (isBlank(firebaseUid)) {
                        throw new SecurityException("Operatore non autenticato.");
                }
                GeoFencePolicy.distanceMeters(latitude, longitude, latitude, longitude);

                Task task = getById(id);
                if (!operatorId.equals(task.getOperatorId())
                                && !firebaseUid.equals(task.getOperatorId())
                                && !firebaseUid.equals(task.getOperatorFirebaseUid())) {
                        throw new SecurityException("Il task non è assegnato all'operatore autenticato.");
                }
                if (isBlank(task.getAntennaId())) {
                        throw new IllegalArgumentException("Il task non ha un'antenna per il controllo geofence.");
                }

                String tenantId = requireCurrentTenant();
                Firestore db = FirestoreClient.getFirestore();
                requireWithinGeofence(task, latitude, longitude, "Check-in");

                TaskStatus current = TaskStatus.parse(task.getStatus());
                if (current == TaskStatus.CHECKED_IN && firebaseUid.equals(task.getCheckedInBy())) {
                        return task;
                }
                if (current != TaskStatus.EN_ROUTE || !current.canTransitionTo(TaskStatus.CHECKED_IN)) {
                        throw new IllegalArgumentException("Il check-in è consentito solo da EN_ROUTE.");
                }

                DocumentReference taskDocument = db.collection(COLLECTION).document(id);
                String now = Instant.now().toString();
                db.runTransaction(transaction -> {
                        DocumentSnapshot currentDocument = transaction.get(taskDocument).get();
                        if (!currentDocument.exists()
                                        || !tenantId.equals(currentDocument.getString("tenantId"))) {
                                throw new IllegalArgumentException("Task non trovato.");
                        }
                        if (TaskStatus.parse(currentDocument.getString("status")) != TaskStatus.EN_ROUTE) {
                                throw new IllegalArgumentException("Il task è cambiato durante il check-in.");
                        }
                        transaction.update(taskDocument,
                                        "status", TaskStatus.CHECKED_IN.name(),
                                        "checkInLatitude", latitude,
                                        "checkInLongitude", longitude,
                                        "checkedInAt", now,
                                        "checkedInBy", firebaseUid,
                                        "updatedAt", now);
                        return null;
                }).get();

                auditService.record("TASK_CHECKED_IN", requireCurrentTenant(), firebaseUid,
                                "TASK", id, "CHECKED_IN", null, null);
                return getById(id);
        }

        public Task completeTaskWithLocation(String id, String firebaseUid, String operatorId,
                        double latitude, double longitude) throws Exception {
                validateId(id);
                if (isBlank(firebaseUid)) {
                        throw new SecurityException("Operatore non autenticato.");
                }
                Task task = getById(id);
                requireCheckoutAssignment(task, firebaseUid, operatorId);
                if (isCheckoutAlreadyCompleted(task, firebaseUid)) {
                        return task;
                }
                TaskStatus current = TaskStatus.parse(task.getStatus());
                if (current != TaskStatus.IN_PROGRESS && current != TaskStatus.WAITING
                                && current != TaskStatus.COMPLETED) {
                        throw new IllegalArgumentException("Il check-out GPS non è consentito nello stato corrente.");
                }

                validateCheckOutLocation(task, firebaseUid, operatorId, latitude, longitude);
                String tenantId = requireCurrentTenant();
                Firestore db = FirestoreClient.getFirestore();
                DocumentReference taskDocument = db.collection(COLLECTION).document(id);
                String now = Instant.now().toString();
                db.runTransaction(transaction -> {
                        DocumentSnapshot currentDocument = transaction.get(taskDocument).get();
                        if (!currentDocument.exists()
                                        || !tenantId.equals(currentDocument.getString("tenantId"))) {
                                throw new IllegalArgumentException("Task non trovato.");
                        }
                        TaskStatus transactionStatus = TaskStatus.parse(currentDocument.getString("status"));
                        if (transactionStatus == TaskStatus.COMPLETED
                                        && firebaseUid.equals(currentDocument.getString("checkedOutBy"))) {
                                return null;
                        }
                        if (transactionStatus != TaskStatus.IN_PROGRESS
                                        && transactionStatus != TaskStatus.WAITING
                                        && transactionStatus != TaskStatus.COMPLETED) {
                                throw new IllegalArgumentException("Il task è cambiato durante il check-out.");
                        }
                        transaction.update(taskDocument,
                                        "status", TaskStatus.COMPLETED.name(),
                                        "completedAt", now,
                                        "checkOutLatitude", latitude,
                                        "checkOutLongitude", longitude,
                                        "checkedOutAt", now,
                                        "checkedOutBy", firebaseUid,
                                        "updatedAt", now);
                        return null;
                }).get();

                auditService.record("TASK_CHECKED_OUT", requireCurrentTenant(), firebaseUid,
                                "TASK", id, "COMPLETED", null, null);
                return getById(id);
        }

        public void validateCheckOutLocation(Task task, String firebaseUid, String operatorId,
                        double latitude, double longitude) throws Exception {
                requireCheckoutAssignment(task, firebaseUid, operatorId);
                if (isBlank(task.getAntennaId())) {
                        throw new IllegalArgumentException("Il task non ha un'antenna per il controllo geofence.");
                }
                requireWithinGeofence(task, latitude, longitude, "Check-out");
        }

        private void requireCheckoutAssignment(Task task, String firebaseUid, String operatorId) {
                if (task == null || isBlank(firebaseUid)) {
                        throw new SecurityException("Operatore o task non autenticato.");
                }
                if (!java.util.Objects.equals(operatorId, task.getOperatorId())
                                && !firebaseUid.equals(task.getOperatorId())
                                && !firebaseUid.equals(task.getOperatorFirebaseUid())) {
                        throw new SecurityException("Il task non è assegnato all'operatore autenticato.");
                }
        }

        static boolean isCheckoutAlreadyCompleted(Task task, String firebaseUid) {
                TaskStatus status = TaskStatus.parse(task.getStatus());
                return (status == TaskStatus.COMPLETED || status == TaskStatus.REPORT_SUBMITTED
                                || status == TaskStatus.APPROVED || status == TaskStatus.CLOSED)
                                && firebaseUid != null && firebaseUid.equals(task.getCheckedOutBy());
        }

        private void requireWithinGeofence(Task task, double latitude, double longitude, String action)
                        throws Exception {
                GeoFencePolicy.distanceMeters(latitude, longitude, latitude, longitude);
                DocumentSnapshot antenna = FirestoreClient.getFirestore()
                                .collection("antennas").document(task.getAntennaId()).get().get();
                if (!antenna.exists() || !requireCurrentTenant().equals(antenna.getString("tenantId"))) {
                        throw new IllegalArgumentException("Antenna non trovata nel tenant autenticato.");
                }
                Number antennaLatitude = (Number) antenna.get("lat");
                Number antennaLongitude = (Number) antenna.get("lng");
                if (antennaLatitude == null || antennaLongitude == null) {
                        throw new IllegalStateException("Coordinate antenna non configurate.");
                }
                if (!GeoFencePolicy.isWithinRadius(latitude, longitude,
                                antennaLatitude.doubleValue(), antennaLongitude.doubleValue(), geofenceRadiusMeters)) {
                        throw new SecurityException(action + " fuori dal geofence dell'antenna.");
                }
        }

        public Task updateStatus(
                        String id,
                        String status)
                        throws Exception {
                return updateStatus(id, status, null);
        }

        public Task updateStatus(
                        String id,
                        String status,
                        String idempotencyKey)
                        throws Exception {

                validateId(id);

                String normalizedStatus = normalizzaStato(status);
                String tenantId = requireCurrentTenant();

                Firestore db = FirestoreClient.getFirestore();
                DocumentReference document = db.collection(COLLECTION).document(id);
                String now = Instant.now().toString();
                TaskStatus nextStatus = TaskStatus.parse(normalizedStatus);
                if (nextStatus == TaskStatus.CHECKED_IN) {
                        throw new IllegalArgumentException("Usare il check-in GPS con geofence.");
                }
                String actorUid = resolveActorUid();
                boolean hasKey = idempotencyKey != null && !idempotencyKey.isBlank();
                if (hasKey && idempotencyKey.length() > 200) {
                        throw new IllegalArgumentException("Idempotency-Key troppo lunga.");
                }
                String requestHash = idempotencyHash(tenantId, actorUid, normalizedStatus);
                DocumentReference keyReference = hasKey ? db.collection(IDEMPOTENCY_COLLECTION)
                                .document(idempotencyDocumentId(tenantId, actorUid, idempotencyKey)) : null;
                Boolean changed;
                try {
                        changed = db.runTransaction(transaction -> {
                                DocumentSnapshot existing = transaction.get(document).get();
                                if (!existing.exists() || !tenantId.equals(existing.getString("tenantId"))) {
                                        throw new IllegalArgumentException("Task non trovato.");
                                }
                                if (keyReference != null) {
                                        DocumentSnapshot keyDocument = transaction.get(keyReference).get();
                                        if (keyDocument.exists()) {
                                                if (!tenantId.equals(keyDocument.getString("tenantId"))
                                                                || !actorUid.equals(keyDocument.getString("actorUid"))) {
                                                        throw new SecurityException("Idempotency-Key non valida per l'identità autenticata.");
                                                }
                                                if (!requestHash.equals(keyDocument.getString("requestHash"))
                                                                || !id.equals(keyDocument.getString("resourceId"))) {
                                                        throw new IdempotencyConflictException();
                                                }
                                                return false;
                                        }
                                }
                                TaskStatus currentStatus = TaskStatus.parse(existing.getString("status"));
                                if (currentStatus == null || nextStatus == null || !currentStatus.canTransitionTo(nextStatus)) {
                                        throw new IllegalArgumentException("Transizione di stato non consentita: " + currentStatus + " -> " + nextStatus);
                                }
                                if (keyReference != null) {
                                        Map<String, Object> keyEntry = new java.util.LinkedHashMap<>();
                                        keyEntry.put("tenantId", tenantId);
                                        keyEntry.put("actorUid", actorUid);
                                        keyEntry.put("resourceId", id);
                                        keyEntry.put("requestHash", requestHash);
                                        keyEntry.put("createdAt", now);
                                        transaction.set(keyReference, keyEntry);
                                }
                                applyStatusTransition(transaction, document, normalizedStatus, now);
                                return true;
                        }).get();
                } catch (java.util.concurrent.ExecutionException failure) {
                        Throwable cause = failure.getCause();
                        while (cause != null) {
                                if (cause instanceof IdempotencyConflictException conflict) throw conflict;
                                if (cause instanceof SecurityException denied) throw denied;
                                if (cause instanceof IllegalArgumentException invalid) throw invalid;
                                cause = cause.getCause();
                        }
                        throw failure;
                }
                if (Boolean.TRUE.equals(changed)) {
                        auditService.record("TASK_STATUS_CHANGED", tenantId, actorUid, "TASK", id,
                                        normalizedStatus, null, null);
                }
                return getById(id);
        }

        // ============================================================
        // DELETE
        // ============================================================

        public void deleteTask(String id)
                        throws Exception {

                validateId(id);

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document(id);

                DocumentSnapshot existing = document.get().get();

                if (!existing.exists()) {

                        throw new IllegalArgumentException(
                                        "Task non trovato: " + id);
                }

                if (!requireCurrentTenant().equals(existing.getString("tenantId"))) {
                        throw new IllegalArgumentException("Task non trovato.");
                }

                document.delete().get();
        }

        // ============================================================
        // BY OPERATOR
        // ============================================================

        public List<Task> getByOperator(
                        String operatorId)
                        throws Exception {
                validateId(operatorId);
                var profile = FirestoreClient.getFirestore().collection("operators")
                                .document(operatorId).get().get();
                String uid = profile.exists() && requireCurrentTenant().equals(profile.getString("tenantId"))
                                ? profile.getString("firebaseUid") : null;
                return getByOperator(operatorId, uid);
        }

        public List<Task> getByOperator(String operatorId, String firebaseUid) throws Exception {
                validateId(operatorId);
                var documents = ArchiveQueries.legacyOperator(FirestoreClient.getFirestore().collection(COLLECTION)
                                .whereEqualTo("tenantId", requireCurrentTenant()), operatorId, firebaseUid).get().get().getDocuments();
                var tasks = new ArrayList<Task>();
                var profile = FirestoreClient.getFirestore().collection("operators").document(operatorId).get().get();
                for (var document : documents) {
                        var task = mapDocument(document);
                        if (!isBlank(firebaseUid)) { task.setOperatorId(operatorId); task.setOperatorFirebaseUid(firebaseUid); }
                        if (isBlank(task.getOperatorName()) && profile.exists() && requireCurrentTenant().equals(profile.getString("tenantId"))) task.setOperatorName(profile.getString("fullName"));
                        tasks.add(task);
                }
                tasks.sort(java.util.Comparator.comparing((Task task) -> java.util.Objects.toString(task.getCreatedAt(), "")).reversed().thenComparing(Task::getId));
                return tasks;
        }

        // ============================================================
        // BY ANTENNA
        // ============================================================

        public ArchiveQueries.Page<Task> getPage(int limit, String cursor, String operatorId, String firebaseUid) throws Exception {
                Query query = FirestoreClient.getFirestore().collection(COLLECTION)
                                .whereEqualTo("tenantId", requireCurrentTenant());
                if (operatorId != null) query = ArchiveQueries.operator(query, ArchiveQueries.identities(operatorId, firebaseUid));
                return ArchiveQueries.page(query, limit, cursor, this::mapDocument);
        }

        public List<Task> getByAntenna(
                        String antennaId)
                        throws Exception {

                validateId(antennaId);

                Firestore db = FirestoreClient.getFirestore();

                ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                                .whereEqualTo("tenantId", requireCurrentTenant())
                                .whereEqualTo(
                                                "antennaId",
                                                antennaId)
                                .get();

                List<QueryDocumentSnapshot> documents = future.get().getDocuments();

                List<Task> tasks = new ArrayList<>();

                for (QueryDocumentSnapshot document : documents) {

                        Task task = mapDocument(document);

                        if (task == null) {
                                continue;
                        }

                        task.setId(document.getId());
                        normalizeLegacyOperator(task);

                        tasks.add(task);
                }

                return tasks;
        }

        // ============================================================
        // VALIDATION
        // ============================================================

        private void validateTask(Task task) {

                if (task == null) {

                        throw new IllegalArgumentException(
                                        "Task obbligatorio.");
                }

                if (isBlank(task.getTitle())) {

                        throw new IllegalArgumentException(
                                        "Titolo task obbligatorio.");
                }

                boolean hasOperator = !isBlank(task.getOperatorId()) || !isBlank(task.getOperator_uid());

                boolean hasAntenna = !isBlank(task.getAntennaId());

                if (!hasOperator && !hasAntenna) {

                        throw new IllegalArgumentException(
                                        "Il task deve essere assegnato almeno " +
                                                        "a un operatore o a un'antenna.");
                }
        }

        private void validateDueAt(String dueAt) {
                if (isBlank(dueAt))
                        return;
                try {
                        OffsetDateTime.parse(dueAt.trim());
                } catch (DateTimeParseException exception) {
                        throw new IllegalArgumentException("dueAt deve essere una data ISO-8601 valida.");
                }
        }

        // ============================================================
        // STATUS
        // ============================================================

        private String normalizzaStato(
                        String status) {

                if (isBlank(status)) {

                        throw new IllegalArgumentException(
                                        "Stato task obbligatorio.");
                }

                TaskStatus parsed = TaskStatus.parse(status);
                if (parsed == null) {
                        throw new IllegalArgumentException(
                                        "Stato task non valido: " + status);
                }

                return parsed.name();
        }

        // ============================================================
        // PRIORITY
        // ============================================================

        private String normalizzaPriorita(
                        String priority) {

                if (isBlank(priority)) {
                        return "MEDIUM";
                }

                String value = priority.trim().toUpperCase();

                return switch (value) {

                        case "LOW", "BASSA" -> "LOW";
                        case "MEDIUM", "MEDIA" -> "MEDIUM";
                        case "HIGH", "ALTA" -> "HIGH";
                        case "CRITICAL", "CRITICA" -> "CRITICAL";

                        default ->
                                throw new IllegalArgumentException(
                                                "Priorità task non valida: "
                                                                + priority);
                };
        }

        // ============================================================
        // ID
        // ============================================================

        private void normalizeLegacyOperator(Task task) {
                if (task.getOperatorId() == null || task.getOperatorId().isBlank()) {
                        task.setOperatorId(task.getOperator_uid());
                }
                if (isBlank(task.getOperatorFirebaseUid()) && !isBlank(task.getOperator_uid())) {
                        task.setOperatorFirebaseUid(task.getOperator_uid());
                }
                TaskStatus status = TaskStatus.parse(task.getStatus());
                if (status != null) task.setStatus(status.name());
        }

        private Task mapDocument(DocumentSnapshot document) {
                var data = new java.util.LinkedHashMap<String, Object>(document.getData());
                for (String field : List.of("createdAt", "updatedAt", "completedAt", "dueAt", "checkedInAt", "checkedOutAt")) {
                        Object value = data.get(field);
                        if (value instanceof com.google.cloud.Timestamp timestamp) {
                                data.put(field, Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toString());
                        }
                }
                var gson = new com.google.gson.Gson();
                Task task = gson.fromJson(gson.toJson(data), Task.class);
                task.setId(document.getId());
                normalizeLegacyOperator(task);
                if (document.getString("archiveOperatorId") != null) task.setOperatorId(document.getString("archiveOperatorId"));
                if (isBlank(task.getOperatorName())) task.setOperatorName(document.getString("archiveOperatorName"));
                return task;
        }

        private void validateId(String id) {

                if (isBlank(id)) {

                        throw new IllegalArgumentException(
                                        "ID non valido.");
                }
        }

        // ============================================================
        // BLANK
        // ============================================================

        public static String idempotencyDocumentId(String tenantId, String actorUid, String rawKey) {
                if (isBlank(tenantId) || isBlank(actorUid) || isBlank(rawKey)) {
                        return null;
                }

                String normalized = tenantId.trim() + "|" + actorUid.trim() + "|" + rawKey.trim();
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
                        return HexFormat.of().formatHex(hash);
                } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException("SHA-256 non disponibile.", e);
                }
        }

        private static boolean isBlank(String value) {

                return value == null ||
                                value.isBlank();
        }

        private String requireCurrentTenant() {
                return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        }

        private String resolveActorUid() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null) {
                        return "anonymous";
                }
                String name = authentication.getName();
                return name == null || name.isBlank() ? "anonymous" : name.trim();
        }

        private void applyStatusTransition(DocumentReference document, String normalizedStatus, String now,
                        String taskId, String tenantId) throws Exception {
                if ("COMPLETED".equals(normalizedStatus)) {
                        document.update("status", normalizedStatus, "completedAt", now, "updatedAt", now).get();
                } else {
                        document.update("status", normalizedStatus, "updatedAt", now).get();
                }
                auditService.record("TASK_STATUS_CHANGED", tenantId, null, "TASK", taskId,
                                normalizedStatus, null, null);
        }

        private void applyStatusTransition(com.google.cloud.firestore.Transaction transaction,
                        DocumentReference document, String normalizedStatus, String now) {
                if ("COMPLETED".equals(normalizedStatus)) {
                        transaction.update(document, "status", normalizedStatus, "completedAt", now, "updatedAt", now);
                } else {
                        transaction.update(document, "status", normalizedStatus, "updatedAt", now);
                }
        }

        private String idempotencyHash(String tenantId, String actorUid, String status) {
                return tenantId.trim() + "|" + actorUid.trim() + "|" + status.trim();
        }
}
