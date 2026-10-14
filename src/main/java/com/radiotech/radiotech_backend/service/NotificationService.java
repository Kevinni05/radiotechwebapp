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
import java.util.Set;
import java.util.Objects;
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

                if (!currentTenant().equals(operator.getTenantId())) {
                        throw new SecurityException("Operatore non appartenente al tenant autenticato.");
                }
                return persistAndDeliver(operatorId.trim(), title, body, data);
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

                return persistAndDeliver("BROADCAST", title, body, Map.of());
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
                        if (!addressedTo(document,uid,operator.getId())) continue;
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
                        if (!message.exists() || !tenant.equals(message.getString("tenantId")) || !addressedTo(message,uid,operator.getId())) throw new SecurityException("Notifica non autorizzata.");
                        var data = previous.exists() ? new java.util.LinkedHashMap<>(previous.getData()) : new java.util.LinkedHashMap<String,Object>();
                        data.put("tenantId",tenant); data.put("uid",uid); data.put("notificationId",notificationId);
                        data.putIfAbsent("readAt",Instant.now().toString()); if (acknowledge) data.putIfAbsent("acknowledgedAt",Instant.now().toString());
                        tx.set(own,data); return data;
                }).get();
        }

        private static boolean addressedTo(com.google.cloud.firestore.DocumentSnapshot message,String uid,String operatorId) {
                String target=message.getString("target"),recipient=message.getString("recipientUid");
                return (recipient==null||recipient.equals(uid))&&("BROADCAST".equals(target)||operatorId.equals(target)||("UID:"+uid).equals(target));
        }

        static Map<String, Object> inboxEntry(String tenant, String target, String title, String body,
                        Map<String, String> payload) {
                Map<String, Object> item = new java.util.LinkedHashMap<>(Map.of(
                                "tenantId", tenant, "title", title, "message", body, "target", target,
                                "delivered", 0, "deliveryStatus", "PENDING", "createdAt", Instant.now().toString()));
                for (String field : List.of("taskId", "type")) {
                        if (payload != null && payload.get(field) != null) item.put(field, payload.get(field));
                }
                return item;
        }

        private int persistAndDeliver(String target, String title, String body, Map<String, String> payload) throws Exception {
                var reference = FirestoreClient.getFirestore().collection("notificationHistory").document();
                // Persistence failure reaches the caller before any external FCM request.
                reference.create(inboxEntry(currentTenant(), target, title, body, payload)).get();
                try {
                        deliverRecorded(reference.getId());
                        Long accepted = reference.get().get().getLong("delivered");
                        return Math.toIntExact(accepted == null ? 0 : accepted);
                } catch (Exception deliveryError) {
                        // The same durable item is retried by the scanner. Do not invite caller replay.
                        log.warn("Push delivery deferred for durable notification {}", reference.getId());
                        return 0;
                }
        }

        private String currentTenant() {
                return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        }

        /** Deliver a durable inbox item. Per-device receipts stay in a backend-only subcollection. */
        public void deliverRecorded(String id) throws Exception {
                var db = FirestoreClient.getFirestore();
                var ref = db.collection("notificationHistory").document(id);
                String tenant = currentTenant(), lease = java.util.UUID.randomUUID().toString();
                boolean claimed = db.runTransaction(tx -> {
                        var doc = tx.get(ref).get();
                        if (!doc.exists() || !tenant.equals(doc.getString("tenantId"))) return false;
                        String status = doc.getString("deliveryStatus");
                        if (!java.util.Set.of("PENDING", "SENDING", "PARTIAL").contains(java.util.Objects.toString(status, ""))) return false;
                        Instant now = Instant.now();
                        if (doc.getString("nextAttemptAt") != null && Instant.parse(doc.getString("nextAttemptAt")).isAfter(now)) return false;
                        if ("SENDING".equals(status) && doc.getString("leaseUntil") != null && Instant.parse(doc.getString("leaseUntil")).isAfter(now)) return false;
                        long attempts = doc.getLong("deliveryAttempts") == null ? 0 : doc.getLong("deliveryAttempts");
                        if (attempts >= 8) { tx.update(ref, "deliveryStatus", "DEAD_LETTER"); return false; }
                        tx.update(ref, Map.of("deliveryStatus", "SENDING", "lease", lease,
                                "leaseUntil", now.plusSeconds(120).toString(), "deliveryAttempts", attempts + 1));
                        return true;
                }).get();
                if (!claimed) return;
                try {
                        var doc = ref.get().get();
                        var tokens = new java.util.LinkedHashSet<String>();
                        var tokenOwners = new java.util.HashMap<String, String>();
                        String target = doc.getString("target");
                        if ("BROADCAST".equals(target)) {
                                for (var op : operatorService.getAllOperators()) {
                                        if (tenant.equals(op.getTenantId()) && op.getFcmTokens() != null) {
                                                tokens.addAll(op.getFcmTokens());
                                                for (String token : op.getFcmTokens()) tokenOwners.put(token,
                                                        java.util.Objects.toString(op.getFirebaseUid(), ""));
                                        }
                                }
                        } else if (target != null) {
                                var op = target.startsWith("UID:") ? operatorService.getByFirebaseUid(target.substring(4)) : operatorService.getById(target);
                                if (op != null && tenant.equals(op.getTenantId()) && op.getFcmTokens() != null
                                    && (doc.getString("recipientUid")==null||doc.getString("recipientUid").equals(op.getFirebaseUid()))) {
                                        tokens.addAll(op.getFcmTokens());
                                        for (String token : op.getFcmTokens()) tokenOwners.put(token,
                                                java.util.Objects.toString(op.getFirebaseUid(), ""));
                                }
                        }
                        tokens.removeIf(token -> token == null || token.isBlank());
                        var receipts = ref.collection("pushDeliveries");
                        var accepted = new java.util.HashSet<String>();
                        var invalid = new java.util.HashSet<String>();
                        for (var receipt : receipts.get().get().getDocuments()) {
                                if ("ACCEPTED".equals(receipt.getString("status"))) accepted.add(receipt.getId());
                                if ("INVALID".equals(receipt.getString("status"))) invalid.add(receipt.getId());
                        }
                        var pending = new ArrayList<String>();
                        for (String token : tokens) if (!accepted.contains(tokenId(token)) && !invalid.contains(tokenId(token))) pending.add(token);
                        var payload = new java.util.LinkedHashMap<String, String>();
                        payload.put("notificationId", id);
                        payload.put("tenantId", tenant);
                        for (String field : List.of("taskId", "type")) if (doc.getString(field) != null) payload.put(field, doc.getString(field));
                        // A multicast shares its data payload: group devices by account
                        // so a queued push cannot navigate another signed-in user's task.
                        var recipients = new java.util.LinkedHashMap<String, List<String>>();
                        for (String token : pending) recipients.computeIfAbsent(tokenOwners.getOrDefault(token, ""),
                                ignored -> new ArrayList<>()).add(token);
                        for (var recipient : recipients.entrySet()) {
                          var scopedPayload = new java.util.LinkedHashMap<>(payload);
                          if (!recipient.getKey().isBlank()) scopedPayload.put("recipientUid", recipient.getKey());
                          var recipientTokens = recipient.getValue();
                          for (int start = 0; start < recipientTokens.size(); start += 200) {
                                List<String> batch = recipientTokens.subList(start, Math.min(start + 200, recipientTokens.size()));
                                // Renew the owned lease before the external request. Never overwrite a successor's lease.
                                boolean owned = db.runTransaction(tx -> {
                                        var current = tx.get(ref).get();
                                        if (!lease.equals(current.getString("lease"))) return false;
                                        tx.update(ref, "leaseUntil", Instant.now().plusSeconds(120).toString());
                                        return true;
                                }).get();
                                if (!owned) return;
                                var response = FirebaseMessaging.getInstance().sendEachForMulticast(
                                        multicast(batch, doc.getString("title"), doc.getString("message"), scopedPayload));
                                if (response.getResponses().size() != batch.size()) throw new IllegalStateException("FCM recipient result count mismatch.");
                                boolean recorded = db.runTransaction(tx -> {
                                        var current = tx.get(ref).get();
                                        if (!lease.equals(current.getString("lease"))) return false;
                                        for (int i = 0; i < batch.size(); i++) {
                                                var outcome = response.getResponses().get(i);
                                                String code = outcome.isSuccessful() ? "" : messagingCode(outcome);
                                                String status = outcome.isSuccessful() ? "ACCEPTED" : "UNREGISTERED".equals(code) ? "INVALID" : "RETRY";
                                                tx.set(receipts.document(tokenId(batch.get(i))), Map.of("tenantId", tenant,
                                                        "status", status, "errorCode", code, "attemptedAt", Instant.now().toString()));
                                        }
                                        return true;
                                }).get();
                                if (!recorded) return;
                                removeInvalidTokens(batch, response);
                          }
                        }
                        var allResults = receipts.get().get().getDocuments();
                        // Delivery status describes this attempt's registrations. Keep
                        // retired-token receipts for audit without treating a recovered
                        // installation as a permanent failure on every later retry.
                        var currentTokenIds = tokens.stream().map(NotificationService::tokenId)
                                .collect(java.util.stream.Collectors.toSet());
                        var results = allResults.stream().filter(r -> currentTokenIds.contains(r.getId())).toList();
                        long previousTokenAccepted = allResults.stream().filter(r -> !currentTokenIds.contains(r.getId())
                                && "ACCEPTED".equals(r.getString("status"))).count();
                        long previousTokenFailures = allResults.stream().filter(r -> !currentTokenIds.contains(r.getId())
                                && !"ACCEPTED".equals(r.getString("status"))).count();
                        long delivered = results.stream().filter(r -> "ACCEPTED".equals(r.getString("status"))).count();
                        long failed = results.stream().filter(r -> !"ACCEPTED".equals(r.getString("status"))).count();
                        var errorCodes=new java.util.LinkedHashMap<String,Long>();
                        for(var result:results) {
                            String code=result.getString("errorCode");
                            if(code!=null&&!code.isBlank()) errorCodes.merge(code,1L,Long::sum);
                        }
                        boolean retryable = results.stream().anyMatch(r -> "RETRY".equals(r.getString("status")));
                        db.runTransaction(tx -> {
                                var current = tx.get(ref).get();
                                if (!lease.equals(current.getString("lease"))) return false;
                                long attempts = current.getLong("deliveryAttempts");
                                String status = results.isEmpty() ? "NO_TOKENS" : failed == 0 ? "SENT"
                                        : retryable && attempts < 8 ? "PARTIAL" : "DEAD_LETTER";
                                tx.update(ref, Map.of("deliveryStatus", status, "delivered", delivered, "failedRecipients", failed,
                                        "previousTokenAccepted", previousTokenAccepted, "previousTokenFailures", previousTokenFailures,
                                        "errorCodes",errorCodes,
                                        "deliveryAttemptAt", Instant.now().toString(), "nextAttemptAt", retryAt(attempts)));
                                return true;
                        }).get();
                } catch (Exception error) {
                        db.runTransaction(tx -> {
                                var current = tx.get(ref).get();
                                if (lease.equals(current.getString("lease"))) {
                                        long attempts = current.getLong("deliveryAttempts");
                                        tx.update(ref, Map.of("deliveryStatus", attempts >= 8 ? "DEAD_LETTER" : "PENDING",
                                                "lastFailureAt", Instant.now().toString(), "nextAttemptAt", retryAt(attempts)));
                                }
                                return true;
                        }).get();
                        throw error;
                }
        }

        private static String retryAt(long attempts) {
                long delay = Math.min(3600, 30L << Math.min(attempts, 6));
                return Instant.now().plusSeconds(delay + java.util.concurrent.ThreadLocalRandom.current().nextLong(15)).toString();
        }

        public Map<String,Object> retryRecorded(String id) throws Exception {
                if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Notifica non valida.");
                String tenant=currentTenant(),actor=SecurityContextAccessor.currentUid();
                var db=FirestoreClient.getFirestore();var ref=db.collection("notificationHistory").document(id);
                boolean retry;
                try { retry=db.runTransaction(tx->{
                    var item=tx.get(ref).get();
                    if(!item.exists()||!tenant.equals(item.getString("tenantId"))) throw new SecurityException("Notifica non autorizzata.");
                    if(Set.of("CALENDAR_REMINDER","BADGE_UPDATED").contains(Objects.toString(item.getString("type"),""))&&!Objects.equals(actor,item.getString("ownerUid"))) throw new SecurityException("Notifica privata non autorizzata.");
                    if("SENT".equals(item.getString("deliveryStatus"))) return false;
                    if("SENDING".equals(item.getString("deliveryStatus"))&&item.getString("leaseUntil")!=null&&Instant.parse(item.getString("leaseUntil")).isAfter(Instant.now())) return false;
                    tx.update(ref,Map.of("deliveryStatus","PENDING","deliveryAttempts",0L,"nextAttemptAt",Instant.now().toString(),"retryRequestedAt",Instant.now().toString()));
                    tx.create(db.collection("auditLogs").document(),Map.of("tenantId",tenant,"actor",actor,"action","NOTIFICATION_RETRY","resource","notificationHistory","resourceId",id,"result","SUCCESS","timestamp",Instant.now().toString()));
                    return true;
                }).get(); } catch(java.util.concurrent.ExecutionException error) {
                    for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof SecurityException denied) throw denied;
                    throw error;
                }
                if(retry) try { deliverRecorded(id); } catch(Exception ignored) { /* Recorded attempt remains retryable. */ }
                var result=new java.util.LinkedHashMap<String,Object>(ref.get().get().getData());result.put("id",id);return result;
        }

        private static String tokenId(String token) {
                try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
                catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        }

        private static String messagingCode(SendResponse response) {
                var error = response.getException();
                if (error == null) return "UNKNOWN";
                if (error.getMessagingErrorCode() != null) return error.getMessagingErrorCode().name();
                // IAM/transport failures can carry a general Firebase code only.
                return error.getErrorCode() == null ? "UNKNOWN" : error.getErrorCode().name();
        }

        private static MulticastMessage multicast(List<String> tokens, String title, String body, Map<String, String> data) {
                var builder = MulticastMessage.builder().putAllData(data).addAllTokens(tokens);
                if ("BADGE_UPDATED".equals(data.get("type"))) {
                        builder.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());
                        builder.setApnsConfig(com.google.firebase.messaging.ApnsConfig.builder().putHeader("apns-push-type", "background")
                                .putHeader("apns-priority", "5").setAps(com.google.firebase.messaging.Aps.builder().setContentAvailable(true).build()).build());
                } else {
                        // Lock-screen payloads must remain safe if the account is replaced
                        // while this provider request is already in flight. Full text stays
                        // in the tenant-authorized inbox, not in the OS notification.
                        builder.setNotification(Notification.builder().setTitle("RADIO TECH")
                                        .setBody(data.containsKey("taskId") ? "Nuovo aggiornamento incarico. Apri l'app per i dettagli."
                                                : "Nuova comunicazione operativa. Apri l'app per i dettagli.").build())
                                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                                        .setNotification(AndroidNotification.builder().setChannelId("radiotech_alerts").setSound("default")
                                                .setTag(data.get("notificationId")).build()).build());
                }
                return builder.build();
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

                                // INVALID_ARGUMENT can describe a bad payload; it is not proof that a token is invalid.
                                if ("UNREGISTERED".equals(messagingCode(sendResponse))) {
                                        String invalidToken = tokens.get(i);
                                        String tenant = currentTenant();
                                        for (Operator operator : operatorService.getAllOperators()) {
                                                if (tenant.equals(operator.getTenantId()) && operator.getFcmTokens() != null
                                                        && operator.getFcmTokens().contains(invalidToken)) {
                                                        var db = FirestoreClient.getFirestore();
                                                        var reference = db.collection("operators").document(operator.getId());
                                                        db.runTransaction(tx -> {
                                                                var current = tx.get(reference).get();
                                                                if (current.exists() && tenant.equals(current.getString("tenantId"))) {
                                                                        tx.update(reference, "fcmTokens", com.google.cloud.firestore.FieldValue.arrayRemove(invalidToken),
                                                                                "updatedAt", Instant.now().toString());
                                                                }
                                                                return true;
                                                        }).get();
                                                }
                                        }
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
