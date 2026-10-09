package com.radiotech.radiotech_backend.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class RicambioServiceAtomicityEmulatorTest {

    @Test
    void invalidQuantitiesCannotBeTruncatedOrPartiallyConsumeStock() throws Exception {
        authenticateTenantA();
        String id = "whole-units-" + java.util.UUID.randomUUID();
        try (Firestore firestore = emulatorFirestore(); var client = mockStatic(FirestoreClient.class)) {
            client.when(FirestoreClient::getFirestore).thenReturn(firestore);
            var item = firestore.collection("inventory").document(id);
            item.set(Map.of("tenantId", "tenant-a", "name", "Cavo RF", "quantity", 5)).get();
            var inventory = new RicambioService(mock(AuditService.class));
            for (Object quantity : List.of(1.5, Double.NaN, Double.POSITIVE_INFINITY,
                    (long) Integer.MAX_VALUE + 1, 4294967297L, "1")) {
                String operation = "invalid-units-" + java.util.UUID.randomUUID();
                assertThrows(IllegalArgumentException.class, () -> inventory.consume(List.of(
                        Map.of("inventoryId", id, "quantity", 1),
                        Map.of("inventoryId", id, "quantity", quantity)), operation));
                assertEquals(5L, item.get().get().getLong("quantity"));
                assertEquals(0, firestore.collection("inventoryMovements")
                        .whereEqualTo("operationId", operation).get().get().size());
                assertEquals(0, firestore.collection("inventoryConsumptionOperations")
                        .whereEqualTo("operationId", operation).get().get().size());
            }
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void failedMultiMaterialConsumptionDoesNotPartiallyUpdateStock() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_CAPO")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try (Firestore firestore = FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);

            firestore.collection("inventory").document("cable").set(Map.of(
                    "tenantId", "tenant-a", "name", "Cavo RF", "quantity", 5)).get();
            firestore.collection("inventory").document("connector").set(Map.of(
                    "tenantId", "tenant-a", "name", "Connettore", "quantity", 1)).get();

            RicambioService service = new RicambioService(mock(AuditService.class));
            long movementCountBefore = firestore.collection("inventoryMovements")
                    .whereEqualTo("tenantId", "tenant-a").get().get().size();

            assertThrows(IllegalArgumentException.class, () -> service.consume(List.of(
                    Map.of("inventoryId", "cable", "quantity", 2),
                    Map.of("inventoryId", "connector", "quantity", 2))));

            assertEquals(5L, firestore.collection("inventory").document("cable").get().get().getLong("quantity"));
            assertEquals(1L, firestore.collection("inventory").document("connector").get().get().getLong("quantity"));
            assertEquals(movementCountBefore, firestore.collection("inventoryMovements")
                    .whereEqualTo("tenantId", "tenant-a").get().get().size());
        }
    }

    @Test
    void consumesByStableInventoryIdAndSku() throws Exception {
        authenticateTenantA();

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("inventory").document("cable").set(Map.of(
                    "tenantId", "tenant-a", "sku", "RF-001", "name", "Cavo RF", "quantity", 3)).get();
            firestore.collection("inventory").document("connector").set(Map.of(
                    "tenantId", "tenant-a", "sku", "CON-001", "name", "Connettore", "quantity", 2)).get();

            new RicambioService(mock(AuditService.class)).consume(List.of(
                    Map.of("inventoryId", "cable", "quantity", 1),
                    Map.of("sku", "CON-001", "quantity", 1)));

            assertEquals(2L, firestore.collection("inventory").document("cable").get().get().getLong("quantity"));
            assertEquals(1L, firestore.collection("inventory").document("connector").get().get().getLong("quantity"));
        }
    }

    @Test
    void retryWithSameOperationIdDoesNotDecrementStockOrDuplicateMovement() throws Exception {
        authenticateTenantA();
        String cableId = "cable-" + java.util.UUID.randomUUID();
        String connectorId = "connector-" + java.util.UUID.randomUUID();
        String operationId = "report-" + java.util.UUID.randomUUID();

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("inventory").document(cableId).set(Map.of(
                    "tenantId", "tenant-a", "sku", "RF-001", "name", "Cavo RF", "quantity", 3)).get();
            firestore.collection("inventory").document(connectorId).set(Map.of(
                    "tenantId", "tenant-a", "sku", "CON-001", "name", "Connettore", "quantity", 2)).get();

            RicambioService service = new RicambioService(mock(AuditService.class));
            List<Map<String, Object>> request = List.of(Map.of("inventoryId", cableId, "quantity", 1));
            service.consume(request, operationId);
            service.consume(request, operationId);

            assertEquals(2L, firestore.collection("inventory").document(cableId)
                    .get().get().getLong("quantity"));
            assertThrows(IllegalStateException.class,
                    () -> service.consume(List.of(Map.of("inventoryId", connectorId, "quantity", 1)), operationId));
            assertEquals(2L, firestore.collection("inventory").document(connectorId)
                    .get().get().getLong("quantity"));
            assertEquals(1, firestore.collection("inventoryMovements")
                    .whereEqualTo("tenantId", "tenant-a")
                    .whereEqualTo("operationId", operationId)
                    .get().get().size());
        }
    }

    @Test
    void stableInventoryIdCannotCrossTenantBoundary() throws Exception {
        authenticateTenantA();

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("inventory").document("tenant-b-item").set(Map.of(
                    "tenantId", "tenant-b", "sku", "B-001", "name", "Materiale B", "quantity", 4)).get();

            assertThrows(SecurityException.class, () -> new RicambioService(mock(AuditService.class)).consume(List.of(
                    Map.of("inventoryId", "tenant-b-item", "quantity", 1))));
            assertEquals(4L, firestore.collection("inventory").document("tenant-b-item").get().get()
                    .getLong("quantity"));
        }
    }

    @Test
    void materialCannotBeResolvedByNameAlone() throws Exception {
        authenticateTenantA();
        assertThrows(IllegalArgumentException.class,
                () -> new RicambioService(mock(AuditService.class)).consume(
                        List.of(Map.of("name", "Ambiguous cable", "quantity", 1))));
    }

    @Test
    void absoluteQuantityAdjustmentWritesMovementAndAuditAtomically() throws Exception {
        authenticateTenantA();
        String itemId = "adjustment-" + java.util.UUID.randomUUID();
        AuditService auditService = mock(AuditService.class);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);
            firestore.collection("inventory").document(itemId).set(Map.of(
                    "tenantId", "tenant-a", "sku", "RF-ADJ", "name", "Cavo RF", "quantity", 7)).get();

            Map<String, Object> result = new RicambioService(auditService).setQuantity(itemId, 4);

            assertEquals(4, result.get("quantity"));
            var movements = firestore.collection("inventoryMovements")
                    .whereEqualTo("tenantId", "tenant-a")
                    .whereEqualTo("inventoryId", itemId).get().get().getDocuments();
            assertEquals(1, movements.size());
            assertEquals("ADJUSTMENT", movements.get(0).getString("type"));
            assertEquals(-3L, movements.get(0).getLong("quantity"));
            assertEquals(7L, movements.get(0).getLong("previousQuantity"));
            assertEquals(4L, movements.get(0).getLong("resultingQuantity"));
            verify(auditService).record(eq("INVENTORY_ADJUSTED"), eq("tenant-a"), eq("manager-a"),
                    eq("INVENTORY"), eq(itemId), eq("SUCCESS"), anyMap(), anyMap());
        }
    }

    @Test
    void creatingStockWithInitialQuantityWritesReceiptMovement() throws Exception {
        authenticateTenantA();
        String sku = "RF-NEW-" + java.util.UUID.randomUUID();
        AuditService auditService = mock(AuditService.class);

        try (Firestore firestore = emulatorFirestore();
                var firestoreClient = mockStatic(FirestoreClient.class)) {
            firestoreClient.when(FirestoreClient::getFirestore).thenReturn(firestore);

            new RicambioService(auditService).add(sku, "Nuovo cavo", 6, 2, "RF");

            var item = firestore.collection("inventory").whereEqualTo("tenantId", "tenant-a")
                    .whereEqualTo("sku", sku).get().get().getDocuments().get(0);
            var movements = firestore.collection("inventoryMovements")
                    .whereEqualTo("tenantId", "tenant-a")
                    .whereEqualTo("inventoryId", item.getId()).get().get().getDocuments();
            assertEquals(1, movements.size());
            assertEquals("RECEIPT", movements.get(0).getString("type"));
            assertEquals(6L, movements.get(0).getLong("quantity"));
            verify(auditService).record(eq("INVENTORY_CREATED"), eq("tenant-a"), eq("manager-a"),
                    eq("INVENTORY"), eq(item.getId()), eq("SUCCESS"), isNull(), anyMap());
        }
    }

    private void authenticateTenantA() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_CAPO")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Firestore emulatorFirestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("demo-radiotech")
                .setHost(System.getenv("FIRESTORE_EMULATOR_HOST"))
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-only", new Date(Long.MAX_VALUE))))
                .build().getService();
    }
}
