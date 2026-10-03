package com.radiotech.radiotech_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.model.Antenna;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Service
public class AntennaService {

    private static final String COLLECTION = "antennas";

    /**
     * Recupera tutte le antenne da Firestore.
     */
    public List<Antenna> getAllAntennas() throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant())
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Antenna> antenne = new ArrayList<>();

        for (QueryDocumentSnapshot doc : documents) {

            Antenna antenna = doc.toObject(Antenna.class);

            if (antenna == null) {
                continue;
            }

            antenna.setId(doc.getId());

            antenne.add(antenna);
        }

        return antenne;
    }

    /**
     * Recupera una singola antenna tramite ID Firestore.
     */
    public Antenna getById(String id) throws Exception {

        validateId(id);

        Firestore db = FirestoreClient.getFirestore();

        DocumentSnapshot doc = findTenantDocument(db, id);

        if (doc == null || !doc.exists()) {
            throw new IllegalArgumentException(
                    "Antenna non trovata: " + id);
        }
        requireDocumentTenant(doc);

        Antenna antenna = doc.toObject(Antenna.class);

        if (antenna == null) {
            throw new IllegalStateException(
                    "Impossibile convertire il documento antenna.");
        }

        antenna.setId(doc.getId());

        return antenna;
    }

    public List<Map<String, Object>> getMaintenanceHistory(String antennaId) throws Exception {
        validateId(antennaId);
        String tenantId = currentTenant();
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot antenna = db.collection(COLLECTION).document(antennaId.trim()).get().get();
        if (!antenna.exists() || !tenantId.equals(antenna.getString("tenantId"))) {
            throw new IllegalArgumentException("Antenna non trovata.");
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (QueryDocumentSnapshot document : db.collection("maintenanceReports")
                .whereEqualTo("tenantId", tenantId).get().get().getDocuments()) {
            if (!antennaId.trim().equals(document.getString("antennaId"))) {
                continue;
            }
            Map<String, Object> entry = new HashMap<>(document.getData());
            entry.put("id", document.getId());
            entry.put("recordType", "REPORT");
            entry.put("timestamp", firstNonBlank(document.getString("submittedAt"),
                    document.getString("reviewedAt"), document.getString("createdAt")));
            history.add(entry);
        }
        for (QueryDocumentSnapshot document : db.collection("tasks")
                .whereEqualTo("tenantId", tenantId).get().get().getDocuments()) {
            if (!antennaId.trim().equals(document.getString("antennaId"))) {
                continue;
            }
            Map<String, Object> entry = new HashMap<>(document.getData());
            entry.put("id", document.getId());
            entry.put("recordType", "TASK");
            entry.put("timestamp", firstNonBlank(document.getString("updatedAt"), document.getString("createdAt")));
            history.add(entry);
        }
        history.sort(Comparator.comparing(entry -> String.valueOf(entry.getOrDefault("timestamp", "")),
                Comparator.reverseOrder()));
        return history;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Resolves a station from a QR payload. Accepts the Firestore document id,
     * the short station code or the station name so the mobile application can
     * work with whatever the technician scans or types.
     */
    public Antenna findByCodeOrId(String reference) throws Exception {

        validateId(reference);

        String value = reference.trim();

        Firestore db = FirestoreClient.getFirestore();

        DocumentSnapshot direct = findTenantDocument(db, value);
        if (direct != null && direct.exists()) {
            Antenna antenna = direct.toObject(Antenna.class);
            if (antenna != null) {
                antenna.setId(direct.getId());
                return antenna;
            }
        }

        Antenna byCode = firstMatch("code", value);
        if (byCode != null) {
            return byCode;
        }

        Antenna byName = firstMatch("name", value);
        if (byName != null) {
            return byName;
        }

        throw new IllegalArgumentException("Nessuna antenna associata al codice: " + value);
    }

    private Antenna firstMatch(String field, String value) throws Exception {
        List<QueryDocumentSnapshot> documents = FirestoreClient.getFirestore()
                .collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant())
                .whereEqualTo(field, value)
                .limit(1)
                .get()
                .get()
                .getDocuments();

        if (documents.isEmpty()) {
            return null;
        }

        QueryDocumentSnapshot document = documents.get(0);
        Antenna antenna = document.toObject(Antenna.class);
        if (antenna == null) {
            return null;
        }
        antenna.setId(document.getId());
        return antenna;
    }

    /**
     * Crea una nuova antenna.
     */
    public Antenna createAntenna(Antenna antenna)
            throws ExecutionException, InterruptedException {

        validate(antenna);

        String tenantId = TenantAccessPolicy.requireTenantAccess(currentTenant(), antenna.getTenantId());
        antenna.setTenantId(tenantId);
        normalizeAssetMetadata(antenna, tenantId, null);

        Firestore db = FirestoreClient.getFirestore();

        DocumentReference docRef = db.collection(COLLECTION).document();

        String now = Instant.now().toString();

        antenna.setId(docRef.getId());
        antenna.setCode(resolveCode(antenna.getCode(), antenna.getName(), docRef.getId()));
        antenna.setCreatedAt(now);
        antenna.setUpdatedAt(now);

        docRef.set(antenna).get();

        return antenna;
    }

    private String resolveCode(String code, String name, String documentId) {
        if (code != null && !code.isBlank()) {
            return code.trim().toUpperCase();
        }

        String source = name == null ? "" : name.trim().toUpperCase();

        StringBuilder slug = new StringBuilder();
        for (char character : source.toCharArray()) {
            if (Character.isLetterOrDigit(character)) {
                slug.append(character);
            } else if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
            if (slug.length() >= 12) {
                break;
            }
        }

        String suffix = documentId.substring(0, Math.min(4, documentId.length())).toUpperCase();
        String cleaned = slug.toString().replaceAll("-+$", "");

        return cleaned.isEmpty() ? "ANT-" + suffix : "ANT-" + cleaned + "-" + suffix;
    }

    /**
     * Aggiorna completamente un'antenna esistente.
     */
    public Antenna updateAntenna(
            String id,
            Antenna antenna)
            throws ExecutionException, InterruptedException {

        validateId(id);
        validate(antenna);

        Firestore db = FirestoreClient.getFirestore();

        DocumentReference docRef = db.collection(COLLECTION).document(id);

        DocumentSnapshot existing = findTenantDocument(db, id);

        if (existing == null || !existing.exists()) {
            throw new IllegalArgumentException(
                    "Antenna non trovata: " + id);
        }
        requireDocumentTenant(existing);
        TenantAccessPolicy.requireTenantAccess(existing.getString("tenantId"), antenna.getTenantId());

        antenna.setId(id);
        antenna.setTenantId(existing.getString("tenantId"));
        antenna.setCode(resolveCode(antenna.getCode(), antenna.getName(), id));
        normalizeAssetMetadata(antenna, antenna.getTenantId(), id);

        String createdAt = existing.getString("createdAt");

        antenna.setCreatedAt(
                createdAt != null
                        ? createdAt
                        : Instant.now().toString());

        antenna.setUpdatedAt(
                Instant.now().toString());

        docRef.set(antenna).get();

        return antenna;
    }

    /**
     * Elimina un'antenna.
     */
    public void deleteAntenna(String id)
            throws ExecutionException, InterruptedException {

        validateId(id);

        Firestore db = FirestoreClient.getFirestore();

        DocumentReference docRef = db.collection(COLLECTION).document(id);

        DocumentSnapshot existing = findTenantDocument(db, id);

        if (existing == null || !existing.exists()) {
            throw new IllegalArgumentException(
                    "Antenna non trovata: " + id);
        }
        requireDocumentTenant(existing);

        docRef.delete().get();
    }

    /**
     * Valida l'ID dell'antenna.
     */
    private void validateId(String id) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "ID antenna non valido.");
        }
    }

    private String currentTenant() {
        return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
    }

    private DocumentSnapshot findTenantDocument(Firestore db, String id)
            throws ExecutionException, InterruptedException {
        List<QueryDocumentSnapshot> matches = db.collection(COLLECTION)
                .whereEqualTo("tenantId", currentTenant())
                .whereEqualTo(FieldPath.documentId(), id.trim())
                .limit(1)
                .get().get().getDocuments();
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void requireDocumentTenant(DocumentSnapshot document) {
        if (!TenantAccessPolicy.canAccessTenant(currentTenant(), document.getString("tenantId"))) {
            throw new IllegalArgumentException("Antenna non trovata.");
        }
    }

    /**
     * Valida i dati dell'antenna.
     *
     * Campi Firestore:
     *
     * name
     * lat
     * lng
     * status
     * specs
     */
    private void validate(Antenna antenna) {

        if (antenna == null) {
            throw new IllegalArgumentException(
                    "Antenna obbligatoria.");
        }

        /*
         * NAME
         */
        if (antenna.getName() == null ||
                antenna.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "Il nome dell'antenna è obbligatorio.");
        }

        /*
         * LATITUDE
         */
        if (antenna.getLat() == null ||
                antenna.getLat() < -90 ||
                antenna.getLat() > 90) {

            throw new IllegalArgumentException(
                    "Latitudine non valida. Deve essere compresa tra -90 e 90.");
        }

        /*
         * LONGITUDE
         */
        if (antenna.getLng() == null ||
                antenna.getLng() < -180 ||
                antenna.getLng() > 180) {

            throw new IllegalArgumentException(
                    "Longitudine non valida. Deve essere compresa tra -180 e 180.");
        }

        /* STATUS */
        if (antenna.getStatus() == null || antenna.getStatus().isBlank()) {
            throw new IllegalArgumentException("Lo stato dell'antenna è obbligatorio.");
        }
        String status = antenna.getStatus().trim().toUpperCase();
        if (!java.util.Set.of("ATTIVA", "OFFLINE", "MANUTENZIONE", "CRITICA").contains(status)) {
            throw new IllegalArgumentException("Stato antenna non valido: " + antenna.getStatus());
        }
        antenna.setStatus(status);

        if (antenna.getSpecs() != null) {
            if (antenna.getSpecs().getFrequenza() != null && antenna.getSpecs().getFrequenza() <= 0)
                throw new IllegalArgumentException("Frequenza non valida.");
            if (antenna.getSpecs().getPotenza() != null && antenna.getSpecs().getPotenza() < 0)
                throw new IllegalArgumentException("Potenza non valida.");
            if (antenna.getSpecs().getROS() != null && antenna.getSpecs().getROS() < 1)
                throw new IllegalArgumentException("ROS non valido: deve essere >= 1.");
        }
    }

    private void normalizeAssetMetadata(Antenna antenna, String tenantId, String currentId)
            throws ExecutionException, InterruptedException {
        Set<String> assetTypes = Set.of("SITE", "TOWER", "SECTOR", "ANTENNA", "RRU", "BBU", "ROUTER", "SWITCH",
                "UPS", "BATTERY", "GENERATOR", "FIBER", "MICROWAVE");
        String assetType = antenna.getAssetType();
        if (assetType == null || assetType.isBlank()) {
            antenna.setAssetType("ANTENNA");
        } else {
            assetType = assetType.trim().toUpperCase();
            if (!assetTypes.contains(assetType)) {
                throw new IllegalArgumentException("Tipo asset non valido: " + assetType);
            }
            antenna.setAssetType(assetType);
        }

        if (antenna.getParentAssetId() == null || antenna.getParentAssetId().isBlank()) {
            antenna.setParentAssetId(null);
            return;
        }
        String parentId = antenna.getParentAssetId().trim();
        if (parentId.equals(currentId)) {
            throw new IllegalArgumentException("Un asset non può essere padre di se stesso.");
        }
        DocumentSnapshot parent = findTenantDocument(FirestoreClient.getFirestore(), parentId);
        if (parent == null || !parent.exists() || !tenantId.equals(parent.getString("tenantId"))) {
            throw new IllegalArgumentException("Asset padre non trovato nel tenant autenticato.");
        }
        antenna.setParentAssetId(parentId);
    }

}
