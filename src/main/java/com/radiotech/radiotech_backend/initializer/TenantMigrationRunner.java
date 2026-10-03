package com.radiotech.radiotech_backend.initializer;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TenantMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final List<String> COLLECTIONS = List.of(
            "operators", "tasks", "antennas", "interventions", "inventory",
            "maintenanceReports", "manutenzioni", "alerts", "incidents", "notificationHistory", "auditLogs", "users");

    @Value("${radiotech.tenant.migration.enabled:false}")
    private boolean enabled;

    @Value("${radiotech.tenant.migration.tenant-id:}")
    private String tenantId;

    @Value("${radiotech.tenant.migration.single-tenant-confirmed:false}")
    private boolean singleTenantConfirmed;

    @Value("${radiotech.tenant.migration.replace-default:false}")
    private boolean replaceDefault;

    @Value("${radiotech.tenant.migration.apply:false}")
    private boolean apply;

    @Override
    public void run(String... args) throws Exception {
        if (!enabled) {
            return;
        }
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId.trim()).matches()) {
            throw new IllegalStateException("RADIOTECH_LEGACY_TENANT_ID non configurato o non valido.");
        }
        if (!singleTenantConfirmed) {
            throw new IllegalStateException("Confermare il mapping single-tenant dei dati legacy prima di procedere.");
        }
        if (apply && !replaceDefault) {
            throw new IllegalStateException(
                    "RADIOTECH_TENANT_MIGRATION_REPLACE_DEFAULT=true obbligatorio in apply mode.");
        }

        String targetTenant = tenantId.trim();
        Firestore firestore = FirestoreClient.getFirestore();
        List<DocumentReference> documentsToUpdate = new ArrayList<>();
        Map<String, Map<String, Object>> claimsToUpdate = new HashMap<>();
        List<String> conflicts = new ArrayList<>();

        for (String collection : COLLECTIONS) {
            for (DocumentSnapshot document : firestore.collection(collection).get().get().getDocuments()) {
                String existingTenant = document.getString("tenantId");
                if (existingTenant == null || "default".equals(existingTenant) && (!apply || replaceDefault)) {
                    documentsToUpdate.add(document.getReference());
                } else if (!targetTenant.equals(existingTenant)) {
                    conflicts.add(collection + "/" + document.getId() + " has tenantId=" + existingTenant);
                }

                Map<String, Object> data = document.getData();
                if ("operators".equals(collection) && data != null && data.get("firebaseUid") instanceof String uid) {
                    Map<String, Object> claims = new HashMap<>();
                    claims.put("tenantId", targetTenant);
                    claims.put("operatorId", document.getId());
                    if ("ATTIVO".equalsIgnoreCase(document.getString("status"))) {
                        claims.put("role", data.getOrDefault("role", "OPERATOR"));
                    }
                    mergeClaims(claimsToUpdate, uid, claims, conflicts);
                } else if ("users".equals(collection)) {
                    String uid = data != null && data.get("uid") instanceof String value ? value : document.getId();
                    Map<String, Object> claims = new HashMap<>();
                    claims.put("tenantId", targetTenant);
                    if (data != null && data.get("role") != null) {
                        claims.put("role", data.get("role"));
                    }
                    mergeClaims(claimsToUpdate, uid, claims, conflicts);
                }
            }
        }

        if (!conflicts.isEmpty()) {
            conflicts.forEach(conflict -> log.error("Tenant migration conflict: {}", conflict));
            throw new IllegalStateException("Conflitti tenant trovati; nessuna modifica applicata.");
        }

        log.info("Tenant migration {}: {} documenti Firestore, {} account Firebase, tenant={}",
                apply ? "APPLY" : "DRY RUN", documentsToUpdate.size(), claimsToUpdate.size(), targetTenant);
        if (!apply) {
            log.info("Dry run only. Non sono state apportate modifiche.");
            return;
        }

        Map<String, Map<String, Object>> resolvedClaims = resolveClaims(claimsToUpdate, targetTenant);
        for (int start = 0; start < documentsToUpdate.size(); start += 400) {
            var batch = firestore.batch();
            for (DocumentReference document : documentsToUpdate.subList(
                    start, Math.min(start + 400, documentsToUpdate.size()))) {
                batch.update(document, "tenantId", targetTenant);
            }
            batch.commit().get();
        }

        for (Map.Entry<String, Map<String, Object>> entry : resolvedClaims.entrySet()) {
            FirebaseAuth.getInstance().setCustomUserClaims(entry.getKey(), entry.getValue());
        }
        log.info("Tenant migration applied. Gli utenti devono aggiornare i token Firebase.");
    }

    private void mergeClaims(Map<String, Map<String, Object>> allClaims, String uid,
            Map<String, Object> additions, List<String> conflicts) {
        Map<String, Object> current = allClaims.computeIfAbsent(uid, ignored -> new HashMap<>());
        Object previousTenant = current.get("tenantId");
        if (previousTenant != null && !tenantId.trim().equals(previousTenant)) {
            conflicts.add("Firebase UID " + uid + " maps to more than one tenant.");
        }
        current.putAll(additions);
    }

    private Map<String, Map<String, Object>> resolveClaims(Map<String, Map<String, Object>> plannedClaims,
            String targetTenant) throws Exception {
        Map<String, Map<String, Object>> resolved = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : plannedClaims.entrySet()) {
            UserRecord user = FirebaseAuth.getInstance().getUser(entry.getKey());
            Map<String, Object> claims = new HashMap<>();
            if (user.getCustomClaims() != null) {
                claims.putAll(user.getCustomClaims());
            }
            Object existingTenant = claims.get("tenantId");
            if (existingTenant != null && !targetTenant.equals(existingTenant)
                    && !("default".equals(existingTenant) && replaceDefault)) {
                throw new IllegalStateException("Claim tenant in conflitto per Firebase UID " + entry.getKey());
            }
            claims.putAll(entry.getValue());
            resolved.put(entry.getKey(), claims);
        }
        return resolved;
    }
}