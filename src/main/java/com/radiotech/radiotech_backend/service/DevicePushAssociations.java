package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.*;

/** Bind push to an authenticated installation, without trusting client device IDs. */
public final class DevicePushAssociations {
    private DevicePushAssociations() {}

    public static void change(Firestore db, String operatorId, String deviceId, String token,
                              String tenant, String uid, boolean add) throws Exception {
        if (deviceId == null || !deviceId.matches("[a-f0-9]{64}") || token == null || token.isBlank() || token.length() > 4096)
            throw new IllegalArgumentException("Associazione dispositivo non valida.");
        var operatorRef = db.collection("operators").document(operatorId);
        var deviceRef = db.collection("pro_devices").document(deviceId);
        try {
            db.runTransaction(tx -> {
                var operator = tx.get(operatorRef).get();
                var device = tx.get(deviceRef).get();
                if (!operator.exists() || !device.exists() || !tenant.equals(operator.getString("tenantId"))
                    || !uid.equals(operator.getString("firebaseUid")) || !tenant.equals(device.getString("tenantId"))
                    || !uid.equals(device.getString("uid")) || (add && !"ACTIVE".equals(device.getString("status"))))
                    throw new SecurityException("Dispositivo non autorizzato.");
                var bindings = new LinkedHashMap<String, String>();
                if (operator.get("fcmDevices") instanceof Map<?, ?> stored)
                    stored.forEach((key, value) -> { if (key instanceof String k && value instanceof String v) bindings.put(k, v); });
                String previous = add ? bindings.put(deviceId, token.trim()) : bindings.remove(deviceId);
                var tokens = new LinkedHashSet<String>();
                if (operator.get("fcmTokens") instanceof List<?> stored)
                    for (Object value : stored) if (value instanceof String v) tokens.add(v);
                if (previous != null && !bindings.containsValue(previous)) tokens.remove(previous);
                if (add) tokens.add(token.trim());
                else if (!bindings.containsValue(token.trim())) tokens.remove(token.trim());
                tx.update(operatorRef, Map.of("fcmDevices", bindings, "fcmTokens", List.copyOf(tokens), "updatedAt", Instant.now().toString()));
                tx.update(deviceRef, Map.of("operatorId", operatorId, "updatedAt", Instant.now().toString()));
                return null;
            }).get();
        } catch (java.util.concurrent.ExecutionException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause())
                if (cause instanceof SecurityException denied) throw denied;
            throw error;
        }
    }

    public static void removeRevokedDevice(com.google.cloud.firestore.Transaction tx, Firestore db,
                                           com.google.cloud.firestore.DocumentSnapshot device) throws Exception {
        String operatorId = device.getString("operatorId");
        if (operatorId == null) return; // Legacy installations rebind on their next successful login/sync.
        var ref = db.collection("operators").document(operatorId);
        var operator = tx.get(ref).get();
        if (!operator.exists() || !Objects.equals(device.getString("tenantId"), operator.getString("tenantId"))
            || !Objects.equals(device.getString("uid"), operator.getString("firebaseUid"))) return;
        var bindings = new LinkedHashMap<String, String>();
        if (operator.get("fcmDevices") instanceof Map<?, ?> stored)
            stored.forEach((key, value) -> { if (key instanceof String k && value instanceof String v) bindings.put(k, v); });
        String removed = bindings.remove(device.getId());
        var tokens = new ArrayList<String>();
        if (operator.get("fcmTokens") instanceof List<?> stored)
            for (Object value : stored) if (value instanceof String v && (!Objects.equals(v, removed) || bindings.containsValue(v))) tokens.add(v);
        tx.update(ref, Map.of("fcmDevices", bindings, "fcmTokens", tokens, "updatedAt", Instant.now().toString()));
    }
}
