package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.dto.WorkforceRequests;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class WorkforceService {
    private final AuditService audit;
    private static final Set<Role> MANAGERS = Set.of(Role.SUPER_ADMIN, Role.ADMIN, Role.CHIEF_EXECUTIVE, Role.NETWORK_MANAGER);
    public WorkforceService(AuditService audit) { this.audit = audit; }
    public static boolean manager() { return MANAGERS.contains(SecurityContextAccessor.currentRole()); }
    public static void requireManager() { OperationalInsightsService.requireIdentity(); if (!manager()) throw new SecurityException("Sezione riservata ai responsabili."); }
    private static String key(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Impossibile generare l'identificativo."); }
    }
    private String uid() { String uid = SecurityContextAccessor.currentUid(); if (uid == null) throw new SecurityException("Autenticazione richiesta."); return uid; }
    private String name() {
        var details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return details instanceof FirebaseAuthenticationDetails d && d.getDisplayName() != null ? d.getDisplayName() : "Utente";
    }
    private DocumentReference ownReference(String tenant, String uid) { return FirestoreClient.getFirestore().collection("workforceShifts").document(key(tenant + ":" + uid)); }
    public Map<String, Object> myShift() throws Exception {
        String tenant = OperationalInsightsService.requireIdentity(); String uid = uid();
        var doc = ownReference(tenant, uid).get().get();
        return decorate(doc.exists() ? new LinkedHashMap<>(doc.getData()) : emptyShift(tenant, uid, name()), Instant.now());
    }
    private static Map<String, Object> emptyShift(String tenant, String uid, String name) {
        return new LinkedHashMap<>(Map.of("tenantId", tenant, "uid", uid, "name", name, "status", "OFF_DUTY",
                "readiness", "READY", "version", 0L, "activeMinutes", 0L, "breakMinutes", 0L));
    }
    public Map<String, Object> changeShift(WorkforceRequests.Shift request) throws Exception {
        String tenant = OperationalInsightsService.requireIdentity(), uid = uid(), name = name(); Instant now = Instant.now();
        var db = FirestoreClient.getFirestore(); var shift = ownReference(tenant, uid);
        var operation = db.collection("workforceOperations").document(key(tenant + ":" + uid + ":" + request.operationId()));
        String fingerprint = key(request.action() + ":" + request.readiness() + ":" + request.expectedVersion());
        boolean changed;
        try {
            changed = db.runTransaction(tx -> {
                var doc = tx.get(shift).get(); var previous = tx.get(operation).get();
                var state = doc.exists() ? new LinkedHashMap<>(doc.getData()) : emptyShift(tenant, uid, name);
                if (previous.exists()) {
                    if (!fingerprint.equals(previous.getString("fingerprint"))) throw conflict();
                    return false;
                }
                long version = number(state.get("version"));
                if (version != request.expectedVersion()) throw conflict();
                String old = String.valueOf(state.get("status"));
                String next = switch (request.action()) {
                    case "START" -> { if (!old.equals("OFF_DUTY")) throw invalid(); yield "ACTIVE"; }
                    case "BREAK" -> { if (!old.equals("ACTIVE")) throw invalid(); yield "BREAK"; }
                    case "RESUME" -> { if (!old.equals("BREAK")) throw invalid(); yield "ACTIVE"; }
                    case "END" -> { if (old.equals("OFF_DUTY")) throw invalid(); yield "OFF_DUTY"; }
                    default -> throw invalid();
                };
                var totals = decorate(state, now);
                if (request.action().equals("START")) { state.put("startedAt", now.toString()); state.put("activeMinutes", 0L); state.put("breakMinutes", 0L); state.remove("endedAt"); }
                else { state.put("activeMinutes", totals.get("workMinutes")); state.put("breakMinutes", totals.get("restMinutes")); }
                if (next.equals("OFF_DUTY")) state.put("endedAt", now.toString());
                state.put("name", name); state.put("status", next); state.put("readiness", request.readiness()); state.put("version", version + 1);
                state.put("lastTransitionAt", now.toString()); state.put("updatedAt", now.toString());
                if (request.action().equals("BREAK") || request.action().equals("RESUME") || request.action().equals("START")) state.put("lastRestAt", now.toString());
                tx.set(shift, state);
                tx.set(operation, Map.of("tenantId", tenant, "uid", uid, "fingerprint", fingerprint,
                        "expiresAt", com.google.cloud.Timestamp.ofTimeSecondsAndNanos(now.plus(Duration.ofDays(7)).getEpochSecond(), 0)));
                return true;
            }).get();
        } catch (ExecutionException e) { throwCause(e); throw e; }
        if (changed) audit.record("SHIFT_" + request.action(), tenant, uid, "WORKFORCE_SHIFT", shift.getId(), "SUCCESS", null, Map.of("action", request.action()));
        return myShift();
    }
    public List<Map<String, Object>> shifts() throws Exception {
        requireManager(); String tenant = OperationalInsightsService.requireIdentity();
        return FirestoreClient.getFirestore().collection("workforceShifts").whereEqualTo("tenantId", tenant).limit(250)
                .get().get().getDocuments().stream().map(d -> decorate(new LinkedHashMap<>(d.getData()), Instant.now())).toList();
    }
    public List<Map<String, Object>> signals() throws Exception {
        String tenant = OperationalInsightsService.requireIdentity();
        Query query = FirestoreClient.getFirestore().collection("workforceSignals").whereEqualTo("tenantId", tenant);
        if (!manager()) query = query.whereEqualTo("createdBy", uid());
        var rows = new ArrayList<Map<String, Object>>();
        for (var doc : query.orderBy("createdAt", Query.Direction.DESCENDING).limit(100).get().get().getDocuments()) {
            var row = new LinkedHashMap<String, Object>(doc.getData()); row.remove("fingerprint"); row.put("id", doc.getId()); rows.add(row);
        }
        return rows;
    }
    public Map<String, Object> submitSignal(WorkforceRequests.Signal request) throws Exception {
        String tenant = OperationalInsightsService.requireIdentity(), uid = uid();
        var db = FirestoreClient.getFirestore();
        if (request.assetId() != null && !request.assetId().isBlank()) {
            var asset = db.collection("antennas").document(request.assetId()).get().get();
            if (!asset.exists() || !tenant.equals(asset.getString("tenantId"))) throw new IllegalArgumentException("Asset non trovato nel tenant.");
        }
        var ref = db.collection("workforceSignals").document(key(tenant + ":" + uid + ":" + request.operationId()));
        String fingerprint = key(request.type() + ":" + request.severity() + ":" + request.description().trim() + ":" + Objects.toString(request.assetId(), ""));
        var record = new LinkedHashMap<String, Object>(Map.of("tenantId", tenant, "createdBy", uid, "name", name(), "type", request.type(),
                "severity", request.severity(), "description", request.description().trim(), "status", "OPEN", "version", 0L, "createdAt", Instant.now().toString()));
        record.put("assetId", request.assetId()); record.put("fingerprint", fingerprint);
        boolean created;
        try { created = db.runTransaction(tx -> {
            var existing = tx.get(ref).get();
            if (existing.exists()) { if (!fingerprint.equals(existing.getString("fingerprint"))) throw conflict(); return false; }
            tx.set(ref, record); return true;
        }).get(); } catch (ExecutionException e) { throwCause(e); throw e; }
        if (created) audit.record("WORKFORCE_SIGNAL_CREATED", tenant, uid, "WORKFORCE_SIGNAL", ref.getId(), "SUCCESS", null, Map.of("type", request.type()));
        var result = new LinkedHashMap<String, Object>(ref.get().get().getData()); result.remove("fingerprint"); result.put("id", ref.getId()); return result;
    }
    public Map<String, Object> review(String id, WorkforceRequests.Review request) throws Exception {
        requireManager(); String tenant = OperationalInsightsService.requireIdentity(), actor = uid();
        if (!id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Identificativo non valido.");
        if (request.status().equals("RESOLVED") && (request.resolution() == null || request.resolution().isBlank())) throw new IllegalArgumentException("Indicare come è stata risolta la segnalazione.");
        var db = FirestoreClient.getFirestore(); var ref = db.collection("workforceSignals").document(id);
        try { db.runTransaction(tx -> {
            var doc = tx.get(ref).get();
            if (!doc.exists() || !tenant.equals(doc.getString("tenantId"))) throw new IllegalArgumentException("Segnalazione non trovata.");
            if (number(doc.get("version")) != request.expectedVersion()) throw conflict();
            String status = doc.getString("status");
            if (!("OPEN".equals(status) && "ACKNOWLEDGED".equals(request.status()) || "ACKNOWLEDGED".equals(status) && "RESOLVED".equals(request.status()))) throw invalid();
            tx.update(ref, Map.of("status", request.status(), "resolution", Objects.toString(request.resolution(), "").trim(),
                    "reviewedBy", actor, "updatedAt", Instant.now().toString(), "version", request.expectedVersion() + 1)); return null;
        }).get(); } catch (ExecutionException e) { throwCause(e); throw e; }
        audit.record("WORKFORCE_SIGNAL_REVIEWED", tenant, actor, "WORKFORCE_SIGNAL", id, request.status(), null, null);
        var result = new LinkedHashMap<String, Object>(ref.get().get().getData()); result.remove("fingerprint"); result.put("id", id); return result;
    }
    public static Map<String, Object> decorate(Map<String, Object> data, Instant now) {
        var result = new LinkedHashMap<>(data); String status = String.valueOf(data.get("status"));
        long delta = minutes(data.get("lastTransitionAt"), now);
        result.put("workMinutes", number(data.get("activeMinutes")) + (status.equals("ACTIVE") ? delta : 0));
        result.put("restMinutes", number(data.get("breakMinutes")) + (status.equals("BREAK") ? delta : 0));
        result.put("pauseSuggested", status.equals("ACTIVE") && minutes(data.get("lastRestAt"), now) >= 90);
        result.put("stale", !status.equals("OFF_DUTY") && minutes(data.get("updatedAt"), now) >= 720);
        result.put("notice", "Registrazione volontaria; promemoria pausa dopo 90 minuti. Non certifica presenze o conformità normativa."); return result;
    }
    private static long minutes(Object value, Instant now) { try { return Math.max(0, Duration.between(Instant.parse(String.valueOf(value)), now).toMinutes()); } catch (RuntimeException e) { return 0; } }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Transizione non consentita. Aggiorna lo stato."); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "Stato cambiato o operazione già utilizzata. Aggiorna e riprova."); }
    private static void throwCause(ExecutionException e) { if (e.getCause() instanceof RuntimeException cause) throw cause; }
}
