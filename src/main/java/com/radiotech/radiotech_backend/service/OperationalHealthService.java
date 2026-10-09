package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.AggregateField;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class OperationalHealthService {
    private final FirestoreUsageService usage;
    private final LocalAttachmentService files;
    private final Environment env;
    private final Map<String,Cached> cache = new HashMap<>();
    private record Cached(Instant expires, Map<String,Object> value) {}
    public OperationalHealthService(FirestoreUsageService usage, LocalAttachmentService files, Environment env) {
        this.usage = usage; this.files = files; this.env = env;
    }
    public synchronized Map<String,Object> snapshot() throws Exception {
        String tenant = TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        Instant now = Instant.now();
        var previous = cache.get(tenant);
        if (previous != null && now.isBefore(previous.expires())) return previous.value();
        var db = FirestoreClient.getFirestore();
        var count = AggregateField.count(); var size = AggregateField.sum("size"); var chunks = AggregateField.sum("chunkCount");
        var archive = db.collection("reportFiles").whereEqualTo("tenantId", tenant).aggregate(count, size, chunks).get().get();
        var pendingQuery = db.collection("maintenanceReports").whereEqualTo("tenantId", tenant).whereEqualTo("status", "SUBMITTED");
        long pending = pendingQuery.count().get().get().getCount();
        var oldest = pendingQuery.orderBy("archiveAt").limit(1).get().get().getDocuments();
        Long oldestAt = oldest.isEmpty() ? null : oldest.getFirst().getLong("archiveAt");
        var backupDoc = db.collection("systemOperations").document("backup").get().get();
        var backup = backupDoc.exists() ? new LinkedHashMap<>(backupDoc.getData()) : new LinkedHashMap<String,Object>();
        backup.put("state", backupState(backup, now));
        var metricSnapshot = usage.snapshot();
        var alarms = new ArrayList<Map<String,String>>();
        var quotaFailure = com.radiotech.radiotech_backend.exception.CloudQuota.lastExhaustedAt();
        if (quotaFailure != null && quotaFailure.isAfter(now.minus(Duration.ofHours(1))))
            alarms.add(Map.of("code", "CLOUD_QUOTA_EXHAUSTED", "severity", "CRITICAL", "message", "Il server ha rilevato un errore di quota cloud nell’ultima ora: " + quotaFailure));
        long storedBytes = ((Number) archive.get(size)).longValue();
        long transition = env.getProperty("radiotech.operations.object-storage-warning-bytes", Long.class, 67_108_864L);
        if (storedBytes >= transition && files.storageBackend().equals("FIRESTORE")) alarms.add(alarm("OBJECT_STORAGE", "Allegati oltre la soglia configurata: predisporre lo storage per oggetti."));
        if (pending > 0 && (oldestAt == null || oldestAt == 0 || now.toEpochMilli() - oldestAt >= Duration.ofHours(env.getProperty("radiotech.operations.pending-report-hours", Long.class, 24L)).toMillis()))
            alarms.add(alarm("PENDING_REPORTS", "Report in attesa di revisione oltre la soglia, oppure con data storica non disponibile."));
        if (!"OK".equals(backup.get("state"))) alarms.add(alarm("BACKUP", "Backup esterno o prova di ripristino mancanti o scaduti."));
        var thresholds = Map.of("reads", env.getProperty("radiotech.operations.daily-read-warning", Long.class, 40_000L),
                "writes", env.getProperty("radiotech.operations.daily-write-warning", Long.class, 16_000L),
                "deletes", env.getProperty("radiotech.operations.daily-delete-warning", Long.class, 16_000L),
                "storageBytes", env.getProperty("radiotech.operations.storage-warning-bytes", Long.class, 858_993_459L));
        for (var threshold : thresholds.entrySet()) if (metricSnapshot.get(threshold.getKey()) instanceof Number value && value.longValue() >= threshold.getValue())
            alarms.add(alarm("FIRESTORE_" + threshold.getKey().toUpperCase(Locale.ROOT), "Utilizzo Firestore oltre la soglia configurata: " + threshold.getKey()));
        var result = new LinkedHashMap<String,Object>();
        result.put("checkedAt", now.toString()); result.put("backend", files.storageBackend());
        result.put("attachments", Map.of("count", archive.get(count), "bytes", storedBytes, "chunks", ((Number)archive.get(chunks)).longValue()));
        result.put("pendingReports", pending); result.put("backup", backup); result.put("firestore", metricSnapshot);
        result.put("thresholds", thresholds); result.put("alarms", alarms);
        result.put("consoleUrl", "https://console.firebase.google.com/project/" + db.getOptions().getProjectId() + "/firestore/usage");
        cache.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expires()));
        cache.put(tenant, new Cached(now.plus(Duration.ofMinutes(2)), result));
        return result;
    }
    static String backupState(Map<String,Object> backup, Instant now) {
        if (!(backup.get("lastSuccessAt") instanceof String success)) return "NOT_CONFIGURED";
        try {
            if (Instant.parse(success).isBefore(now.minus(Duration.ofHours(36)))) return "STALE";
            if (!(backup.get("lastRestoreAt") instanceof String restore) || Instant.parse(restore).isBefore(now.minus(Duration.ofDays(35)))) return "RESTORE_REQUIRED";
            return "OK";
        } catch (java.time.format.DateTimeParseException invalid) { return "INVALID"; }
    }
    private static Map<String,String> alarm(String code, String message) { return Map.of("code", code, "message", message, "severity", "WARNING"); }
}
