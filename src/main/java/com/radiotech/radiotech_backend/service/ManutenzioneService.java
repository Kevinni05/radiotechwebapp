package com.radiotech.radiotech_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManutenzioneService {

    private static final String MANUTENZIONI = "manutenzioni";

    private static final String ALERTS = "alerts";

    public List<Map<String, Object>> getAllManutenzioni() throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        ApiFuture<QuerySnapshot> future = tenantQuery(db, MANUTENZIONI).get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Map<String, Object>> lista = new ArrayList<>();

        for (QueryDocumentSnapshot doc : documents) {

            Map<String, Object> data = new HashMap<>(doc.getData());

            data.put("id", doc.getId());

            lista.add(data);
        }

        return lista;
    }

    public List<Map<String, Object>> getAllAlerts() throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        ApiFuture<QuerySnapshot> future = tenantQuery(db, ALERTS)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Map<String, Object>> lista = new ArrayList<>();

        for (QueryDocumentSnapshot doc : documents) {

            Map<String, Object> data = new HashMap<>(doc.getData());

            data.put("id", doc.getId());

            lista.add(data);
        }

        return lista;
    }

    public Map<String, Object> createAlert(String antennaId, String description, String priority, String operatorId)
            throws Exception {
        if (antennaId == null || antennaId.isBlank()) {
            throw new IllegalArgumentException("Antenna obbligatoria per l'alert.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrizione alert obbligatoria.");
        }
        if (priority == null || priority.isBlank()) {
            throw new IllegalArgumentException("Priorita' alert obbligatoria.");
        }

        String tenantId = TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        Firestore db = FirestoreClient.getFirestore();

        String cleanPriority = priority.trim().toUpperCase();
        if (!("BASSA".equals(cleanPriority) || "MEDIA".equals(cleanPriority) || "CRITICA".equals(cleanPriority))) {
            throw new IllegalArgumentException("Priorita' alert non valida: " + priority);
        }

        String alertId = java.util.UUID.randomUUID().toString();
        Map<String, Object> alert = new HashMap<>();
        alert.put("id", alertId);
        alert.put("tenantId", tenantId);
        alert.put("antennaId", antennaId.trim());
        alert.put("operatore", operatorId == null || operatorId.isBlank() ? "SYSTEM" : operatorId.trim());
        alert.put("descrizione", description.trim());
        alert.put("priorita", cleanPriority);
        alert.put("timestamp", java.time.Instant.now().toString());
        alert.put("letto", false);

        db.collection(ALERTS).document(alertId).set(alert).get();

        Map<String, Object> response = new HashMap<>(alert);
        response.put("id", alertId);
        return response;
    }

    public Map<String, Object> markAlertAsRead(String alertId) throws Exception {
        if (alertId == null || alertId.isBlank()) {
            throw new IllegalArgumentException("Alert ID obbligatorio.");
        }

        Firestore db = FirestoreClient.getFirestore();
        QuerySnapshot snapshot = tenantQuery(db, ALERTS)
                .whereEqualTo(FieldPath.documentId(), alertId)
                .get()
                .get();

        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("Alert non trovato: " + alertId);
        }

        DocumentReference ref = db.collection(ALERTS).document(alertId);
        Map<String, Object> data = new HashMap<>(snapshot.getDocuments().get(0).getData());
        data.put("letto", true);
        data.put("timestamp", data.getOrDefault("timestamp", java.time.Instant.now().toString()));
        ref.update("letto", true).get();

        return data;
    }

    public long countUnreadAlerts() throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        List<QueryDocumentSnapshot> docs = tenantQuery(db, ALERTS)
                .whereEqualTo("letto", false)
                .get()
                .get()
                .getDocuments();

        return docs.size();
    }

    public long countManutenzioni()
            throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        return tenantQuery(db, MANUTENZIONI)
                .get()
                .get()
                .size();
    }

    public long countAlertsCritici()
            throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        List<QueryDocumentSnapshot> docs = tenantQuery(db, ALERTS)
                .get()
                .get()
                .getDocuments();

        long count = 0;
        for (QueryDocumentSnapshot doc : docs) {

            String priorita = doc.getString("priorita");

            if ("CRITICA".equalsIgnoreCase(
                    priorita)) {

                count++;
            }
        }

        return count;
    }

    private Query tenantQuery(Firestore db, String collection) {
        String tenantId = TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        return db.collection(collection).whereEqualTo("tenantId", tenantId);
    }
}