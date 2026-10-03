package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.dto.TechnicianSkillRequest;
import com.radiotech.radiotech_backend.model.TechnicianSkill;
import com.radiotech.radiotech_backend.model.TechnicianSkillType;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TechnicianSkillService {

    private static final String OPERATORS = "operators";
    private static final String SKILLS = "skills";

    private final AuditService auditService;

    public TechnicianSkillService(AuditService auditService) {
        this.auditService = auditService;
    }

    public List<TechnicianSkill> list(String operatorId) throws Exception {
        String safeOperatorId = requireOperatorId(operatorId);
        DocumentReference operator = requireTenantOperator(safeOperatorId);
        String tenantId = requireTenantId();
        List<TechnicianSkill> result = new ArrayList<>();
        for (QueryDocumentSnapshot document : operator.collection(SKILLS).get().get().getDocuments()) {
            TechnicianSkillType type = TechnicianSkillType.fromCode(document.getId());
            Map<String, Object> data = document.getData();
            requireSkillOwnership(data, tenantId, safeOperatorId, type);
            result.add(toSkill(safeOperatorId, type, data));
        }
        return result;
    }

    public List<TechnicianSkillType> supportedSkills(String operatorId) throws Exception {
        requireTenantOperator(requireOperatorId(operatorId));
        return List.of(TechnicianSkillType.values());
    }

    public TechnicianSkill upsert(String operatorId, TechnicianSkillType type, TechnicianSkillRequest request)
            throws Exception {
        if (type == null || request == null || request.getLevel() == null
                || request.getCertification() == null || request.getCertification().isBlank()
                || request.getExpiration() == null || request.getAuthorized() == null) {
            throw new IllegalArgumentException("Dati della competenza obbligatori.");
        }
        if (request.getLevel() < 1 || request.getLevel() > 5) {
            throw new IllegalArgumentException("Il livello deve essere compreso tra 1 e 5.");
        }
        if (Boolean.TRUE.equals(request.getAuthorized()) && request.getExpiration().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La certificazione scaduta non può essere autorizzata.");
        }
        if (Boolean.TRUE.equals(request.getAuthorized()) && request.getExpiration().isEqual(LocalDate.now())) {
            throw new IllegalArgumentException("La certificazione scaduta non può essere autorizzata.");
        }

        String safeOperatorId = requireOperatorId(operatorId);
        String tenantId = requireTenantId();
        DocumentReference skillRef = requireTenantOperator(safeOperatorId).collection(SKILLS).document(type.getCode());
        DocumentSnapshot existing = skillRef.get().get();
        if (existing.exists()) {
            requireSkillOwnership(existing.getData(), tenantId, safeOperatorId, type);
        }
        Map<String, Object> before = existing.exists() ? new LinkedHashMap<>(existing.getData()) : Map.of();
        String now = Instant.now().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        data.put("operatorId", safeOperatorId);
        data.put("skill", type.getCode());
        data.put("level", request.getLevel());
        data.put("certification", request.getCertification().trim());
        data.put("expiration", request.getExpiration().toString());
        data.put("authorized", request.getAuthorized());
        data.put("createdAt", existing.exists() ? existing.getString("createdAt") : now);
        data.put("updatedAt", now);
        skillRef.set(data).get();

        auditService.record(existing.exists() ? "TECHNICIAN_SKILL_UPDATED" : "TECHNICIAN_SKILL_CREATED",
                tenantId, SecurityContextAccessor.currentUid(), "technicianSkill",
                safeOperatorId + ":" + type.getCode(), "SUCCESS", before, data);
        return toSkill(safeOperatorId, type, data);
    }

    public void delete(String operatorId, TechnicianSkillType type) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("Competenza obbligatoria.");
        }
        String safeOperatorId = requireOperatorId(operatorId);
        String tenantId = requireTenantId();
        DocumentReference skillRef = requireTenantOperator(safeOperatorId).collection(SKILLS).document(type.getCode());
        DocumentSnapshot existing = skillRef.get().get();
        if (!existing.exists()) {
            throw new IllegalArgumentException("Competenza non trovata.");
        }
        requireSkillOwnership(existing.getData(), tenantId, safeOperatorId, type);
        Map<String, Object> before = new LinkedHashMap<>(existing.getData());
        skillRef.delete().get();
        auditService.record("TECHNICIAN_SKILL_DELETED", tenantId, SecurityContextAccessor.currentUid(),
                "technicianSkill", safeOperatorId + ":" + type.getCode(), "SUCCESS", before, Map.of());
    }

    private DocumentReference requireTenantOperator(String operatorId) throws Exception {
        String safeOperatorId = requireOperatorId(operatorId);
        String tenantId = requireTenantId();
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot operator = db.collection(OPERATORS).document(safeOperatorId).get().get();
        if (!operator.exists() || !tenantId.equals(operator.getString("tenantId"))) {
            throw new IllegalArgumentException("Tecnico non trovato.");
        }
        return operator.getReference();
    }

    private String requireOperatorId(String operatorId) {
        if (operatorId == null || operatorId.isBlank() || operatorId.contains("/")) {
            throw new IllegalArgumentException("Tecnico obbligatorio.");
        }
        return operatorId.trim();
    }

    private void requireSkillOwnership(Map<String, Object> data, String tenantId, String operatorId,
            TechnicianSkillType type) {
        if (data == null || !tenantId.equals(data.get("tenantId"))
                || !operatorId.equals(data.get("operatorId"))
                || !type.getCode().equals(data.get("skill"))) {
            throw new IllegalStateException("Dati della competenza non validi.");
        }
    }

    private String requireTenantId() {
        String tenantId = SecurityContextAccessor.currentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant non assegnato all'identita' autenticata.");
        }
        return tenantId;
    }

    private TechnicianSkill toSkill(String operatorId, TechnicianSkillType type, Map<String, Object> data) {
        Object level = data.get("level");
        Object certification = data.get("certification");
        Object expiration = data.get("expiration");
        Object authorized = data.get("authorized");
        Object updatedAt = data.get("updatedAt");
        if (!(level instanceof Number) || !(certification instanceof String)
                || !(expiration instanceof String) || !(authorized instanceof Boolean)) {
            throw new IllegalStateException("Dati della competenza non validi.");
        }
        return new TechnicianSkill(operatorId, type, ((Number) level).intValue(), (String) certification,
                LocalDate.parse((String) expiration), (Boolean) authorized,
                updatedAt instanceof String ? (String) updatedAt : null);
    }
}
