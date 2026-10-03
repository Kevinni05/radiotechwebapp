
package com.radiotech.radiotech_backend.initializer;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Component
public class FirestoreDataInitializer implements CommandLineRunner {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FirestoreDataInitializer.class);

        @Value("${app.firestore.seed:false}")
        private boolean seedEnabled;

        @Value("${app.firestore.seed-tenant-id:}")
        private String seedTenantId;

        @Override
        public void run(String... args) throws Exception {

                if (!seedEnabled) {
                        log.info("Firestore seed disabilitato.");
                        return;
                }
                if (seedTenantId == null || seedTenantId.isBlank()) {
                        throw new IllegalStateException(
                                        "RADIOTECH_FIRESTORE_SEED_TENANT_ID obbligatorio quando il seed è attivo.");
                }

                Firestore db = FirestoreClient.getFirestore();

                log.info("========================================");
                log.info(" Avvio inizializzazione dati Firestore");
                log.info("========================================");

                seedAntennas(db);
                seedOperators(db);
                seedTasks(db);
                seedInterventions(db);
                seedInventory(db);

                log.info("========================================");
                log.info(" Firestore inizializzato correttamente");
                log.info("========================================");
        }

        private void seedAntennas(Firestore db) throws Exception {

                if (!db.collection("antennas")
                                .limit(1)
                                .get()
                                .get()
                                .isEmpty()) {

                        log.info("Collection antennas già popolata.");
                        return;
                }

                String now = Instant.now().toString();

                createAntenna(
                                db,
                                "ANT-BARI-001",
                                "ANT-BARI-001",
                                41.1171,
                                16.8719,
                                "ATTIVA",
                                98.5,
                                120.0,
                                1.2,
                                36.4,
                                now);

                createAntenna(
                                db,
                                "ANT-BARI-002",
                                "ANT-BARI-002",
                                41.1115,
                                16.8792,
                                "ATTIVA",
                                101.2,
                                110.0,
                                1.1,
                                35.8,
                                now);

                createAntenna(
                                db,
                                "ANT-MOLA-001",
                                "ANT-MOLA-001",
                                40.9997,
                                17.0900,
                                "MANUTENZIONE",
                                97.8,
                                95.0,
                                1.5,
                                39.2,
                                now);

                createAntenna(
                                db,
                                "ANT-TRANI-001",
                                "ANT-TRANI-001",
                                41.2771,
                                16.4107,
                                "OFFLINE",
                                102.4,
                                100.0,
                                1.8,
                                42.1,
                                now);

                createAntenna(
                                db,
                                "ANT-BRINDISI-001",
                                "ANT-BRINDISI-001",
                                40.6327,
                                17.9418,
                                "ATTIVA",
                                99.1,
                                125.0,
                                1.0,
                                34.7,
                                now);

                log.info("Antenne demo create.");
        }

        private void createAntenna(
                        Firestore db,
                        String documentId,
                        String name,
                        double lat,
                        double lng,
                        String status,
                        double frequenza,
                        double potenza,
                        double ros,
                        double temperatura,
                        String now) throws Exception {

                Map<String, Object> specs = new HashMap<>();

                specs.put("Frequenza", frequenza);
                specs.put("Potenza", potenza);
                specs.put("ROS", ros);
                specs.put("Temperatura", temperatura);

                Map<String, Object> antenna = new HashMap<>();

                antenna.put("tenantId", seedTenantId.trim());
                antenna.put("name", name);
                antenna.put("lat", lat);
                antenna.put("lng", lng);
                antenna.put("status", status);
                antenna.put("specs", specs);
                antenna.put("createdAt", now);
                antenna.put("updatedAt", now);

                db.collection("antennas")
                                .document(documentId)
                                .set(antenna)
                                .get();
        }

        private void seedOperators(Firestore db) throws Exception {

                if (!db.collection("operators")
                                .limit(1)
                                .get()
                                .get()
                                .isEmpty()) {

                        log.info("Collection operators già popolata.");
                        return;
                }

                String now = Instant.now().toString();

                createOperator(
                                db,
                                "operator-001",
                                "Mario Rossi",
                                "mario.rossi@radiotech.it",
                                "SENIOR_TECHNICIAN",
                                "MATTINA",
                                "ATTIVO",
                                now);

                createOperator(
                                db,
                                "operator-002",
                                "Luca Bianchi",
                                "luca.bianchi@radiotech.it",
                                "TECHNICIAN",
                                "POMERIGGIO",
                                "ATTIVO",
                                now);

                createOperator(
                                db,
                                "operator-003",
                                "Andrea Verdi",
                                "andrea.verdi@radiotech.it",
                                "TECHNICIAN",
                                "MATTINA",
                                "ATTIVO",
                                now);

                createOperator(
                                db,
                                "operator-004",
                                "Marco Esposito",
                                "marco.esposito@radiotech.it",
                                "JUNIOR_TECHNICIAN",
                                "NOTTE",
                                "ATTIVO",
                                now);

                createOperator(
                                db,
                                "operator-005",
                                "Giuseppe Romano",
                                "giuseppe.romano@radiotech.it",
                                "TECHNICIAN",
                                "POMERIGGIO",
                                "INATTIVO",
                                now);

                log.info("Operatori demo creati.");
        }

        private void createOperator(
                        Firestore db,
                        String documentId,
                        String fullName,
                        String email,
                        String level,
                        String shift,
                        String status,
                        String now) throws Exception {

                Map<String, Object> operator = new HashMap<>();

                operator.put("tenantId", seedTenantId.trim());
                operator.put("fullName", fullName);
                operator.put("email", email);
                operator.put("level", level);
                operator.put("shift", shift);
                operator.put("status", status);

                operator.put("firebaseUid", null);
                operator.put(
                                "qrCodeToken",
                                "AUTH_OP_SEED_" + documentId);

                operator.put(
                                "fcmTokens",
                                new ArrayList<String>());

                operator.put("lastSeen", null);
                operator.put("createdAt", now);
                operator.put("updatedAt", now);

                db.collection("operators")
                                .document(documentId)
                                .set(operator)
                                .get();
        }

        private void seedTasks(Firestore db) throws Exception {

                if (!db.collection("tasks")
                                .limit(1)
                                .get()
                                .get()
                                .isEmpty()) {

                        log.info("Collection tasks già popolata.");
                        return;
                }

                String now = Instant.now().toString();

                createTask(
                                db,
                                "task-001",
                                "Controllo antenna ANT-BARI-001",
                                "Verificare frequenza, potenza e ROS dell'antenna.",
                                "operator-001",
                                "Mario Rossi",
                                "ANT-BARI-001",
                                "ASSIGNED",
                                "HIGH",
                                now,
                                null);

                createTask(
                                db,
                                "task-002",
                                "Manutenzione ANT-MOLA-001",
                                "Effettuare controllo tecnico completo dell'apparato.",
                                "operator-002",
                                "Luca Bianchi",
                                "ANT-MOLA-001",
                                "IN_PROGRESS",
                                "CRITICA",
                                now,
                                null);

                createTask(
                                db,
                                "task-003",
                                "Verifica collegamento ANT-BARI-002",
                                "Controllare stato collegamento e temperatura apparato.",
                                "operator-003",
                                "Andrea Verdi",
                                "ANT-BARI-002",
                                "COMPLETED",
                                "MEDIUM",
                                now,
                                now);

                log.info("Task demo creati.");
        }

        private void createTask(
                        Firestore db,
                        String documentId,
                        String title,
                        String description,
                        String operatorId,
                        String operatorName,
                        String antennaId,
                        String status,
                        String priority,
                        String createdAt,
                        String completedAt) throws Exception {

                Map<String, Object> task = new HashMap<>();

                task.put("tenantId", seedTenantId.trim());
                task.put("title", title);
                task.put("description", description);
                task.put("operatorId", operatorId);
                task.put("operatorName", operatorName);
                task.put("antennaId", antennaId);
                task.put("status", status);
                task.put("priority", priority);
                task.put("createdAt", createdAt);
                task.put("updatedAt", createdAt);
                task.put("completedAt", completedAt);

                db.collection("tasks")
                                .document(documentId)
                                .set(task)
                                .get();
        }

        private void seedInterventions(Firestore db) throws Exception {

                if (!db.collection("interventions")
                                .limit(1)
                                .get()
                                .get()
                                .isEmpty()) {

                        log.info("Collection interventions già popolata.");
                        return;
                }

                String now = Instant.now().toString();

                createIntervention(
                                db,
                                "intervention-001",
                                "ANT-BARI-001",
                                "operator-001",
                                "Mario Rossi",
                                "CONTROLLO_RF",
                                "Controllo periodico dei parametri RF.",
                                "COMPLETED",
                                now);

                createIntervention(
                                db,
                                "intervention-002",
                                "ANT-BARI-002",
                                "operator-003",
                                "Andrea Verdi",
                                "MANUTENZIONE",
                                "Sostituzione connettore e verifica ROS.",
                                "COMPLETED",
                                now);

                log.info("Interventi demo creati.");
        }

        private void createIntervention(
                        Firestore db,
                        String documentId,
                        String antennaId,
                        String operatorId,
                        String operatorName,
                        String type,
                        String description,
                        String status,
                        String now) throws Exception {

                Map<String, Object> intervention = new HashMap<>();

                intervention.put("tenantId", seedTenantId.trim());
                intervention.put("antennaId", antennaId);
                intervention.put("operatorId", operatorId);
                intervention.put("operatorName", operatorName);
                intervention.put("type", type);
                intervention.put("description", description);
                intervention.put("status", status);
                intervention.put("createdAt", now);

                if ("COMPLETED".equals(status)) {
                        intervention.put("completedAt", now);
                } else {
                        intervention.put("completedAt", null);
                }

                db.collection("interventions")
                                .document(documentId)
                                .set(intervention)
                                .get();
        }

        private void seedInventory(Firestore db) throws Exception {

                if (!db.collection("inventory")
                                .limit(1)
                                .get()
                                .get()
                                .isEmpty()) {

                        log.info("Collection inventory già popolata.");
                        return;
                }

                String now = Instant.now().toString();

                createInventory(
                                db,
                                "inventory-001",
                                "RF-001",
                                "Connettore N",
                                "RF",
                                12,
                                5,
                                now);

                createInventory(
                                db,
                                "inventory-002",
                                "RF-002",
                                "Cavo coassiale",
                                "RF",
                                3,
                                10,
                                now);

                createInventory(
                                db,
                                "inventory-003",
                                "RF-003",
                                "Alimentatore 24V",
                                "POWER",
                                8,
                                3,
                                now);

                createInventory(
                                db,
                                "inventory-004",
                                "RF-004",
                                "Connettore SMA",
                                "RF",
                                2,
                                5,
                                now);

                createInventory(
                                db,
                                "inventory-005",
                                "TOOL-001",
                                "Kit terminazione",
                                "TOOLS",
                                15,
                                5,
                                now);

                log.info("Inventario demo creato.");
        }

        private void createInventory(
                        Firestore db,
                        String documentId,
                        String sku,
                        String name,
                        String category,
                        int quantity,
                        int minimumThreshold,
                        String now) throws Exception {

                Map<String, Object> item = new HashMap<>();

                item.put("tenantId", seedTenantId.trim());
                item.put("sku", sku);
                item.put("name", name);
                item.put("category", category);
                item.put("quantity", quantity);
                item.put("minimumThreshold", minimumThreshold);
                item.put("createdAt", now);
                item.put("updatedAt", now);

                db.collection("inventory")
                                .document(documentId)
                                .set(item)
                                .get();
        }
}
