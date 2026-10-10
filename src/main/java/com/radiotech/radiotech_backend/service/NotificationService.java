package com.radiotech.radiotech_backend.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;

import java.time.Instant;
import java.util.Map;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationService.class);

        private final OperatorService operatorService;

        public NotificationService(
                        OperatorService operatorService) {

                this.operatorService = operatorService;
        }

        /**
         * Invia una notifica ad un singolo operatore.
         */
        public int notifyOperator(
                        String operatorId,
                        String title,
                        String body) throws Exception {
                return notifyOperator(operatorId, title, body, Map.of());
        }

        public int notifyOperator(
                        String operatorId,
                        String title,
                        String body,
                        Map<String, String> data) throws Exception {

                if (operatorId == null || operatorId.isBlank()) {
                        throw new IllegalArgumentException(
                                        "ID operatore obbligatorio.");
                }

                if (title == null || title.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Titolo notifica obbligatorio.");
                }

                if (body == null || body.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Corpo notifica obbligatorio.");
                }

                Operator operator = operatorService.getById(
                                operatorId.trim());

                if (operator == null) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato.");
                }

                List<String> tokens = operator.getFcmTokens();

                if (tokens == null || tokens.isEmpty()) {

                        log.info(
                                        "Nessun token FCM per l'operatore: "
                                                        + operatorId);

                        record(title, body, 0, operatorId, data);
                        return 0;
                }

                int delivered = sendNotification(tokens, title, body, data);
                record(title, body, delivered, operatorId, data);

                log.info(
                                "Notifica FCM inviata all'operatore: "
                                                + operatorId);
                return delivered;
        }

        /**
         * Invia una notifica a tutti gli operatori.
         */
        public int notifyAllOperators(
                        String title,
                        String body) throws Exception {

                if (title == null || title.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Titolo notifica obbligatorio.");
                }

                if (body == null || body.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Corpo notifica obbligatorio.");
                }

                List<Operator> operators = operatorService.getAllOperators();

                List<String> tokens = new ArrayList<>();

                for (Operator operator : operators) {

                        if (operator == null) {
                                continue;
                        }

                        List<String> operatorTokens = operator.getFcmTokens();

                        if (operatorTokens == null) {
                                continue;
                        }

                        for (String token : operatorTokens) {

                                if (token == null || token.isBlank()) {
                                        continue;
                                }

                                String normalized = token.trim();

                                if (!tokens.contains(normalized)) {
                                        tokens.add(normalized);
                                }
                        }
                }

                if (tokens.isEmpty()) {

                        log.info(
                                        "Nessun token FCM disponibile.");

                        record(title, body, 0, "BROADCAST", Map.of());
                        return 0;
                }

                int delivered = sendNotification(tokens, title, body, Map.of());
                record(title, body, delivered, "BROADCAST", Map.of());
                return delivered;
        }

        public String recordWorkforceSignal(
                        String signalId,
                        String title,
                        String body,
                        String severity,
                        String assetId,
                        String operationId) throws Exception {
                if (signalId == null || signalId.isBlank()) {
                        throw new IllegalArgumentException("Identificativo segnalazione obbligatorio.");
                }
                if (title == null || title.isBlank() || body == null || body.isBlank()) {
                        throw new IllegalArgumentException("Titolo e contenuto della notifica sono obbligatori.");
                }

                String tenant = currentTenant();
                String notificationId = "workforce-signal-" + signalId;
                var ref = FirestoreClient.getFirestore().collection("notificationHistory").document(notificationId);
                var item = new java.util.LinkedHashMap<String, Object>();
                item.put("tenantId", tenant);
                item.put("title", title.trim());
                item.put("message", body);
                item.put("target", "BROADCAST");
                item.put("type", "WORKFORCE_SIGNAL");
                item.put("severity", severity);
                item.put("signalId", signalId);
                item.put("createdAt", Instant.now().toString());
                item.put("delivered", 0);
                item.put("deliveryStatus", "NOT_WEB_PUSHED");
                if (assetId != null && !assetId.isBlank()) item.put("assetId", assetId);
                if (operationId != null && !operationId.isBlank()) item.put("operationId", operationId);

                var existing = ref.get().get();
                if (existing.exists()) {
                        if (!tenant.equals(existing.getString("tenantId"))) {
                                throw new SecurityException("Notifica non appartenente al tenant autenticato.");
                        }
                        return notificationId;
                }
                ref.create(item).get();
                return notificationId;
        }

        public List<Map<String, Object>> getHistory() throws Exception {
                Firestore db = FirestoreClient.getFirestore();
                List<Map<String, Object>> result = new ArrayList<>();
                for (var doc : db.collection("notificationHistory")
                                .whereEqualTo("tenantId", currentTenant())
                                .orderBy("createdAt", com.google.cloud.firestore.Query.Direction.DESCENDING).limit(100)
                                .get().get().getDocuments()) {
                        if (java.util.Set.of("CALENDAR_REMINDER","BADGE_UPDATED").contains(java.util.Objects.toString(doc.getString("type"), "")) && !java.util.Objects.equals(SecurityContextAccessor.currentUid(),doc.getString("ownerUid"))) continue;
                        Map<String, Object> data = new java.util.LinkedHashMap<>(doc.getData());
                        data.put("id", doc.getId());
                        result.add(data);
                }
                return result;
        }

        public List<Map<String, Object>> getOperatorHistory(String uid) throws Exception {
                String tenantId = currentTenant();
                Operator operator = operatorService.getByFirebaseUid(uid);
                if (operator == null || !tenantId.equals(operator.getTenantId())
                                || !uid.equals(operator.getFirebaseUid())) {
                        throw new SecurityException("Operatore non appartenente alla sessione autenticata.");
                }
                List<Map<String, Object>> result = new ArrayList<>();
                // A single tenant equality index also supports existing installations.
                // Filter recipients before returning any records to the mobile client.
                var documents = FirestoreClient.getFirestore().collection("notificationHistory")
                                .whereEqualTo("tenantId", tenantId).get().get().getDocuments();
                var receipts = new java.util.HashMap<String, Map<String, Object>>();
                for (var receipt : FirestoreClient.getFirestore().collection("notificationReceipts")
                                .whereEqualTo("uid", uid).get().get().getDocuments()) {
                        if (tenantId.equals(receipt.getString("tenantId"))) receipts.put(receipt.getString("notificationId"), receipt.getData());
                }
                for (var document : documents) {
                        String target = document.getString("target");
                        if (!"BROADCAST".equals(target) && !operator.getId().equals(target)) continue;
                        Map<String, Object> item = new java.util.LinkedHashMap<>(document.getData());
                        item.put("id", document.getId());
                        var receipt = receipts.get(document.getId());
                        if (receipt != null) {
                                item.put("readAt", receipt.getOrDefault("readAt", ""));
                                item.put("acknowledgedAt", receipt.getOrDefault("acknowledgedAt", ""));
                        }
                        result.add(item);
                }
                result.sort(java.util.Comparator.comparing(
                                (Map<String, Object> item) -> String.valueOf(item.getOrDefault("createdAt", "")))
                                .reversed());
                return result.stream().limit(100).toList();
        }

        public Map<String, Object> receipt(String uid, String notificationId, boolean acknowledge) throws Exception {
                if (notificationId == null || !notificationId.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Notifica non valida.");
                String tenant = currentTenant(); Operator operator = operatorService.getByFirebaseUid(uid);
                if (operator == null || !tenant.equals(operator.getTenantId()) || !uid.equals(operator.getFirebaseUid())) throw new SecurityException("Operatore non autorizzato.");
                var db = FirestoreClient.getFirestore(); var ref = db.collection("notificationHistory").document(notificationId);
                String receiptId = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((tenant+":"+uid+":"+notificationId).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var own = db.collection("notificationReceipts").document(receiptId);
                return db.runTransaction(tx -> {
                        var message = tx.get(ref).get(); var previous = tx.get(own).get();
                        if (!message.exists() || !tenant.equals(message.getString("tenantId")) || (!"BROADCAST".equals(message.getString("target")) && !operator.getId().equals(message.getString("target")))) throw new SecurityException("Notifica non autorizzata.");
                        var data = previous.exists() ? new java.util.LinkedHashMap<>(previous.getData()) : new java.util.LinkedHashMap<String,Object>();
                        data.put("tenantId",tenant); data.put("uid",uid); data.put("notificationId",notificationId);
                        data.putIfAbsent("readAt",Instant.now().toString()); if (acknowledge) data.putIfAbsent("acknowledgedAt",Instant.now().toString());
                        tx.set(own,data); return data;
                }).get();
        }

        private void record(String title, String body, int delivered, String target, Map<String, String> payload) {
                try {
                        Map<String, Object> item = new java.util.LinkedHashMap<>(Map.of(
                                        "tenantId", currentTenant(),
                                        "title", title, "message", body, "target", target,
                                        "delivered", delivered, "createdAt", Instant.now().toString()));
                        if (payload != null && payload.get("taskId") != null) item.put("taskId", payload.get("taskId"));
                        if (payload != null && payload.get("type") != null) item.put("type", payload.get("type"));
                        FirestoreClient.getFirestore().collection("notificationHistory").add(item).get();
                } catch (Exception error) {
                        log.error("Salvataggio storico notifica non riuscito", error);
                }
        }

        private String currentTenant() {
                return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        }

        /** Deliver an existing inbox item without creating a second history entry. */
        public void deliverRecorded(String id) throws Exception {
                var db=FirestoreClient.getFirestore();var ref=db.collection("notificationHistory").document(id);String tenant=currentTenant();String lease=java.util.UUID.randomUUID().toString();
                boolean claimed=db.runTransaction(tx->{var doc=tx.get(ref).get();if(!doc.exists()||!tenant.equals(doc.getString("tenantId")))return false;
                        String status=doc.getString("deliveryStatus");if(!"PENDING".equals(status)&&!"SENDING".equals(status))return false;
                        if("SENDING".equals(status)&&doc.getString("leaseUntil")!=null&&Instant.parse(doc.getString("leaseUntil")).isAfter(Instant.now()))return false;
                        tx.update(ref,Map.of("deliveryStatus","SENDING","lease",lease,"leaseUntil",Instant.now().plusSeconds(120).toString()));return true;}).get();
                if(!claimed)return;var doc=ref.get().get();var tokens=new ArrayList<String>();String target=doc.getString("target");
                if("BROADCAST".equals(target)){for(var op:operatorService.getAllOperators())if(op.getFcmTokens()!=null)tokens.addAll(op.getFcmTokens());}
                else if(!target.startsWith("UID:")){var op=operatorService.getById(target);if(op!=null&&op.getFcmTokens()!=null)tokens.addAll(op.getFcmTokens());}
                var payload=new java.util.LinkedHashMap<String,String>();payload.put("notificationId",id);if(doc.getString("taskId")!=null)payload.put("taskId",doc.getString("taskId"));if(doc.getString("type")!=null)payload.put("type",doc.getString("type"));
                try{int delivered=sendNotification(tokens.stream().filter(t->t!=null&&!t.isBlank()).distinct().toList(),doc.getString("title"),doc.getString("message"),payload);
                        db.runTransaction(tx->{var current=tx.get(ref).get();if(lease.equals(current.getString("lease")))tx.update(ref,Map.of("deliveryStatus",tokens.isEmpty()?"NO_TOKENS":"SENT","delivered",delivered,"deliveryAttemptAt",Instant.now().toString()));return true;}).get();
                }catch(Exception error){ref.update(Map.of("deliveryStatus","PENDING","lastFailureAt",Instant.now().toString())).get();throw error;}
        }

        /**
         * Metodo comune per l'invio FCM.
         */
        private int sendNotification(
                        List<String> tokens,
                        String title,
                        String body,
                        Map<String, String> data) throws Exception {

                if (tokens == null || tokens.isEmpty()) {
                        return 0;
                }

                Notification notification = Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build();

                AndroidConfig androidConfig = AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                                .setChannelId("radiotech_alerts")
                                                .setSound("default")
                                                .build())
                                .build();

                int successCount = 0;

                /*
                 * Firebase Multicast supporta un numero limitato
                 * di token per singola richiesta.
                 *
                 * Dividiamo quindi i token in blocchi.
                 */
                int batchSize = 500;

                for (int start = 0; start < tokens.size(); start += batchSize) {

                        int end = Math.min(
                                        start + batchSize,
                                        tokens.size());

                        List<String> batch = tokens.subList(start, end);

                        var builder = MulticastMessage.builder().putAllData(data == null ? Map.of() : data).addAllTokens(batch);
                        if (data != null && "BADGE_UPDATED".equals(data.get("type"))) {
                            builder.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());
                            builder.setApnsConfig(com.google.firebase.messaging.ApnsConfig.builder().putHeader("apns-push-type","background").putHeader("apns-priority","5").setAps(com.google.firebase.messaging.Aps.builder().setContentAvailable(true).build()).build());
                        } else builder.setNotification(notification).setAndroidConfig(androidConfig);
                        MulticastMessage message = builder.build();

                        BatchResponse response = FirebaseMessaging
                                        .getInstance()
                                        .sendEachForMulticast(message);

                        successCount += response.getSuccessCount();

                        /*
                         * Rimuoviamo eventuali token non più validi.
                         */
                        removeInvalidTokens(
                                        batch,
                                        response);
                }

                return successCount;
        }

        /**
         * Rimuove automaticamente i token FCM non più validi.
         */
        private void removeInvalidTokens(
                        List<String> tokens,
                        BatchResponse response) {

                try {

                        List<SendResponse> responses = response.getResponses();

                        for (int i = 0; i < responses.size(); i++) {

                                SendResponse sendResponse = responses.get(i);

                                if (sendResponse.isSuccessful()) {
                                        continue;
                                }

                                String errorCode = sendResponse
                                                .getException()
                                                .getMessagingErrorCode()
                                                .name();

                                if ("UNREGISTERED".equals(errorCode)
                                                || "INVALID_ARGUMENT".equals(errorCode)) {

                                        String invalidToken = tokens.get(i);

                                        /*
                                         * Il token può appartenere a più operatori,
                                         * quindi non possiamo rimuoverlo direttamente
                                         * senza conoscere l'operatore.
                                         *
                                         * La pulizia viene gestita separatamente.
                                         */
                                        log.info(
                                                        "Token FCM non valido rilevato; pulizia richiesta.");
                                }
                        }

                } catch (Exception exception) {

                        /*
                         * La pulizia dei token non deve mai
                         * far fallire l'invio della notifica.
                         */
                        log.info(
                                        "Errore durante la verifica dei token FCM: "
                                                        + exception.getMessage());
                }
        }
}
