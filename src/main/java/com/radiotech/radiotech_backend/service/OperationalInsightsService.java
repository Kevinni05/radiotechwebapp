package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Transparent risk triage; scores are not calibrated failure probabilities. */
@Service
public class OperationalInsightsService {
    private static final int LIMIT = 500;
    private static final Set<String> FINISHED = Set.of("COMPLETED", "CLOSED", "CANCELLED", "APPROVED", "REPORT_SUBMITTED");
    private final OperatorService operators;
    public OperationalInsightsService(OperatorService operators) { this.operators = operators; }

    public static String requireIdentity() {
        if (SecurityContextAccessor.currentRole() == Role.NONE || SecurityContextAccessor.currentUid() == null)
            throw new SecurityException("Account abilitato richiesto.");
        return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }

    public Map<String, Object> insights() throws Exception {
        String tenant = requireIdentity();
        boolean personal = SecurityContextAccessor.currentRole() == Role.OPERATOR;
        var db = FirestoreClient.getFirestore();
        List<Map<String, Object>> tasks;
        List<Map<String, Object>> assets;
        List<Map<String, Object>> reports;
        if (personal) {
            var operator = operators.getByFirebaseUid(SecurityContextAccessor.currentUid());
            if (operator == null) throw new SecurityException("Operatore non associato all'account.");
            var own = new LinkedHashMap<String, Map<String, Object>>();
            for (var doc : db.collection("tasks").whereEqualTo("tenantId", tenant)
                    .whereEqualTo("operatorId", operator.getId()).limit(LIMIT).get().get().getDocuments())
                own.put(doc.getId(), row(doc));
            for (var doc : db.collection("tasks").whereEqualTo("tenantId", tenant)
                    .whereEqualTo("operatorFirebaseUid", SecurityContextAccessor.currentUid()).limit(LIMIT).get().get().getDocuments())
                own.put(doc.getId(), row(doc));
            tasks = own.values().stream().limit(LIMIT).toList();
            assets = new ArrayList<>();
            for (String id : tasks.stream().map(t -> text(t.get("antennaId"))).filter(s -> !s.isBlank()).distinct().limit(100).toList()) {
                var doc = db.collection("antennas").document(id).get().get();
                if (doc.exists() && tenant.equals(doc.getString("tenantId"))) assets.add(row(doc));
            }
            reports = db.collection("maintenanceReports").whereEqualTo("tenantId", tenant)
                    .whereEqualTo("operatorId", operator.getId()).limit(LIMIT).get().get().getDocuments().stream()
                    .map(OperationalInsightsService::row).toList();
        } else {
            tasks = collection("tasks", tenant); assets = collection("antennas", tenant);
            reports = collection("maintenanceReports", tenant);
        }
        var result = calculate(assets, tasks, reports, Instant.now());
        result.put("scope", personal ? "PERSONAL" : "TENANT");
        result.put("sampleLimit", LIMIT);
        result.put("partial", tasks.size() >= LIMIT || assets.size() >= (personal ? 100 : LIMIT) || reports.size() >= LIMIT);
        return result;
    }

    private List<Map<String, Object>> collection(String name, String tenant) throws Exception {
        return FirestoreClient.getFirestore().collection(name).whereEqualTo("tenantId", tenant)
                .limit(LIMIT).get().get().getDocuments().stream().map(OperationalInsightsService::row).toList();
    }
    private static Map<String, Object> row(DocumentSnapshot doc) {
        var result = new LinkedHashMap<String, Object>(doc.getData()); result.put("id", doc.getId()); return result;
    }
    public static Map<String, Object> calculate(List<Map<String, Object>> assets, List<Map<String, Object>> tasks,
            List<Map<String, Object>> reports, Instant now) {
        List<Map<String, Object>> risks = new ArrayList<>();
        long active = tasks.stream().filter(OperationalInsightsService::active).count();
        long overdue = tasks.stream().filter(t -> active(t) && before(t.get("dueAt"), now)).count();
        for (var asset : assets) {
            String id = text(asset.get("id")); List<String> reasons = new ArrayList<>(); int score = 0;
            String status = text(asset.get("status")).toUpperCase(Locale.ROOT);
            if (status.equals("OFFLINE")) { score += 50; reasons.add("Asset offline: verifica prioritaria"); }
            if (status.equals("CRITICA")) { score += 35; reasons.add("Stato asset critico"); }
            if (status.equals("MANUTENZIONE")) { score += 10; reasons.add("Manutenzione già in corso"); }
            Map<?, ?> specs = asset.get("specs") instanceof Map<?, ?> m ? m : Map.of();
            Double ros = measurement(specs, "ros", "ROS");
            Double temperature = measurement(specs, "temperatura", "Temperatura", "temperature");
            if (ros != null && ros >= 2) { score += 25; reasons.add("ROS >= 2: verificare adattamento e linea RF"); }
            if (temperature != null && temperature >= 60) { score += 20; reasons.add("Temperatura >= 60 °C: verificare raffreddamento"); }
            long late = tasks.stream().filter(t -> id.equals(text(t.get("antennaId"))) && active(t) && before(t.get("dueAt"), now)).count();
            if (late > 0) { score += 20; reasons.add(late + " incarichi oltre scadenza"); }
            var history = reports.stream().filter(r -> id.equals(text(r.get("antennaId"))) && "APPROVED".equals(text(r.get("status"))))
                    .map(r -> instant(r.get("completedAt") != null ? r.get("completedAt") : r.get("submittedAt"))).filter(Objects::nonNull).filter(d -> !d.isAfter(now)).distinct().sorted().toList();
            long recent = history.stream().filter(d -> d.isAfter(now.minus(Duration.ofDays(90)))).count();
            if (recent >= 3) { score += 15; reasons.add("Almeno tre interventi approvati negli ultimi 90 giorni"); }
            String estimated = null; long interval = 0;
            if (history.size() >= 3) {
                List<Long> gaps = new ArrayList<>();
                for (int i = 1; i < history.size(); i++) {
                    long days = Duration.between(history.get(i - 1), history.get(i)).toDays();
                    if (days > 0) gaps.add(days);
                }
                if (gaps.size() >= 2) {
                    Collections.sort(gaps);
                    int middle = gaps.size() / 2;
                    interval = gaps.size() % 2 == 0
                            ? Math.round((gaps.get(middle - 1) + gaps.get(middle)) / 2.0)
                            : gaps.get(middle);
                    estimated = history.getLast().plus(Duration.ofDays(interval)).toString();
                }
            }
            var risk = new LinkedHashMap<String, Object>();
            if (estimated != null && Instant.parse(estimated).isBefore(now)) { score += 10; reasons.add("Intervallo storico di manutenzione già superato"); }
            risk.put("assetId", id); risk.put("name", text(asset.get("name"))); risk.put("score", Math.min(100, score));
            risk.put("level", score >= 80 ? "CRITICAL" : score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW");
            risk.put("reasons", reasons); risk.put("dataQuality", ros == null && temperature == null && history.isEmpty() ? "LIMITED" : "OBSERVED");
            risk.put("estimatedMaintenanceAt", estimated); risk.put("medianIntervalDays", interval); risk.put("historyCount", history.size());
            risk.put("recommendation", score >= 60 ? "Valutare un intervento prioritario con il responsabile." : estimated != null ? "Confermare la pianificazione preventiva con il responsabile." : "Raccogliere misure aggiornate e applicare il piano del costruttore.");
            risks.add(risk);
        }
        risks.sort(Comparator.<Map<String, Object>>comparingInt(r -> ((Number)r.get("score")).intValue()).reversed().thenComparing(r -> text(r.get("assetId"))));
        var result = new LinkedHashMap<String, Object>(); result.put("generatedAt", now.toString()); result.put("method", "EXPLAINABLE_RULES_V1");
        result.put("notice", "Indicatori di priorità, non probabilità di guasto. La data stimata usa gli intervalli storici e richiede conferma umana.");
        result.put("activeTasks", active); result.put("overdueTasks", overdue); result.put("assetsObserved", assets.size());
        result.put("highRiskAssets", risks.stream().filter(r -> ((Number)r.get("score")).intValue() >= 60).count()); result.put("risks", risks);
        return result;
    }
    private static boolean active(Map<String, Object> task) { return !FINISHED.contains(text(task.get("status")).toUpperCase(Locale.ROOT)); }
    private static boolean before(Object date, Instant now) { var parsed = instant(date); return parsed != null && parsed.isBefore(now); }
    private static Instant instant(Object value) { try { return Instant.parse(text(value)); } catch (RuntimeException invalid) { return null; } }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static Double measurement(Map<?, ?> values, String... keys) {
        for (String key : keys) if (values.get(key) instanceof Number number && Double.isFinite(number.doubleValue())) return number.doubleValue();
        return null;
    }
}
