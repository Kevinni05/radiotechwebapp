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

                        return 0;
                }

                int delivered = sendNotification(tokens, title, body, data);
                record(title, body, delivered, operatorId);

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

                        return 0;
                }

                int delivered = sendNotification(tokens, title, body, Map.of());
                record(title, body, delivered, "BROADCAST");
                return delivered;
        }

        public List<Map<String, Object>> getHistory() throws Exception {
                Firestore db = FirestoreClient.getFirestore();
                List<Map<String, Object>> result = new ArrayList<>();
                for (var doc : db.collection("notificationHistory")
                                .whereEqualTo("tenantId", currentTenant())
                                .orderBy("createdAt", com.google.cloud.firestore.Query.Direction.DESCENDING).limit(100)
                                .get().get().getDocuments()) {
                        Map<String, Object> data = new java.util.LinkedHashMap<>(doc.getData());
                        data.put("id", doc.getId());
                        result.add(data);
                }
                return result;
        }

        private void record(String title, String body, int delivered, String target) {
                try {
                        FirestoreClient.getFirestore().collection("notificationHistory").add(Map.of(
                                        "tenantId", currentTenant(),
                                        "title", title, "message", body, "target", target,
                                        "delivered", delivered, "createdAt", Instant.now().toString())).get();
                } catch (Exception ignored) {
                }
        }

        private String currentTenant() {
                return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
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

                        MulticastMessage message = MulticastMessage.builder()
                                        .setNotification(notification)
                                        .setAndroidConfig(androidConfig)
                                        .putAllData(data == null ? Map.of() : data)
                                        .addAllTokens(batch)
                                        .build();

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
                                                        "Token FCM non valido: "
                                                                        + invalidToken);
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