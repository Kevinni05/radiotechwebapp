package com.radiotech.radiotech_backend.service;

import org.junit.jupiter.api.Test;

import com.radiotech.radiotech_backend.model.MaintenanceReport;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaintenanceReportIdempotencyTest {

    @Test
    void retriesMapToTheSameTenantAndActorScopedDocument() throws Exception {
        String first = MaintenanceReportService.idempotencyDocumentId("tenant-a", "operator-a", "request-123");
        String retry = MaintenanceReportService.idempotencyDocumentId("tenant-a", "operator-a", "request-123");

        assertEquals(first, retry);
        assertNotEquals(first,
                MaintenanceReportService.idempotencyDocumentId("tenant-b", "operator-a", "request-123"));
        assertNotEquals(first,
                MaintenanceReportService.idempotencyDocumentId("tenant-a", "operator-b", "request-123"));
    }

    @Test
    void malformedIdempotencyKeyIsRejected() {
        assertThrows(Exception.class,
                () -> MaintenanceReportService.idempotencyDocumentId("tenant-a", "operator-a", "../bad key"));
    }

    @Test
    void taskReportRequiresBothCheckOutCoordinates() {
        MaintenanceReport report = new MaintenanceReport();
        report.setLatitude(41.1171);

        assertThrows(IllegalArgumentException.class, () -> MaintenanceReportService.requireCheckOutLocation(report));

        report.setLongitude(16.8719);
        MaintenanceReportService.requireCheckOutLocation(report);

        report.setLatitude(91.0);
        assertThrows(IllegalArgumentException.class, () -> MaintenanceReportService.requireCheckOutLocation(report));
    }

    @Test
    void reportRequestHashIgnoresMapInsertionOrderButChangesWithContent() throws Exception {
        MaintenanceReport first = new MaintenanceReport();
        first.setDescription("antenna inspection");
        first.setMeasurements(new LinkedHashMap<>(Map.of("power", 12.5, "vswr", 1.2)));

        MaintenanceReport retry = new MaintenanceReport();
        retry.setDescription("antenna inspection");
        Map<String, Double> reorderedMeasurements = new LinkedHashMap<>();
        reorderedMeasurements.put("vswr", 1.2);
        reorderedMeasurements.put("power", 12.5);
        retry.setMeasurements(reorderedMeasurements);

        assertEquals(MaintenanceReportService.reportRequestHash(first),
                MaintenanceReportService.reportRequestHash(retry));

        retry.setDescription("different inspection");
        assertNotEquals(MaintenanceReportService.reportRequestHash(first),
                MaintenanceReportService.reportRequestHash(retry));
    }

    @Test
    void reportAttachmentMustBelongToAuthenticatedTenantAndOperatorBucketPath() {
        String url = "https://firebasestorage.googleapis.com/v0/b/radio.appspot.com/o/"
                + "tenants%2Ftenant-a%2Fmaintenance-reports%2Foperator-a%2Fphoto.jpg?alt=media&token=download-key";
        MaintenanceReportService.validateAttachmentReference(url, "tenant-a", "operator-a", "radio.appspot.com");
        assertThrows(IllegalArgumentException.class, () -> MaintenanceReportService.validateAttachmentReference(
                url, "tenant-b", "operator-a", "radio.appspot.com"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceReportService.validateAttachmentReference(
                url, "tenant-a", "operator-b", "radio.appspot.com"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceReportService.validateAttachmentReference(
                url.replace("firebasestorage.googleapis.com", "example.org"), "tenant-a", "operator-a",
                "radio.appspot.com"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceReportService.validateAttachmentReference(
                url.substring(0, url.indexOf('?')), "tenant-a", "operator-a", "radio.appspot.com"));
    }
}
