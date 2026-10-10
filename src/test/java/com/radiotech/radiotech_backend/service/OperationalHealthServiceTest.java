package com.radiotech.radiotech_backend.service;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OperationalHealthServiceTest {
    @Test void failedPushesAndPendingIdentityActionsHaveDistinctAlarms() {
        var alarms = new java.util.ArrayList<Map<String,String>>();
        OperationalHealthService.addDeliveryAlarms(alarms, 3, 1);
        assertEquals(java.util.List.of("PUSH_DEAD_LETTER", "IDENTITY_AUDIT_PENDING"),
                alarms.stream().map(a -> a.get("code")).toList());
        assertEquals("CRITICAL", alarms.get(1).get("severity"));
        alarms.clear();
        OperationalHealthService.addDeliveryAlarms(alarms, 0, 0);
        assertTrue(alarms.isEmpty());
    }
    @Test void missingMetricsAreUnknownAndCounterAndGaugeAggregationDiffer() {
        assertNull(FirestoreUsageService.metricValue(JsonParser.parseString("{}").getAsJsonObject(),false));
        var payload=JsonParser.parseString("{\"timeSeries\":[{\"points\":[{\"value\":{\"int64Value\":\"11\"}},{\"value\":{\"int64Value\":\"7\"}}]}]}").getAsJsonObject();
        assertEquals(18L,FirestoreUsageService.metricValue(payload,false));assertEquals(11L,FirestoreUsageService.metricValue(payload,true));
    }
    @Test void freshBackupWithoutRecentRestoreDoesNotClaimProtection() {
        Instant now=Instant.parse("2026-10-09T00:00:00Z");
        assertEquals("NOT_CONFIGURED",OperationalHealthService.backupState(Map.of(),now));
        assertEquals("RESTORE_REQUIRED",OperationalHealthService.backupState(Map.of("lastSuccessAt",now.toString()),now));
        assertEquals("STALE",OperationalHealthService.backupState(Map.of("lastSuccessAt",now.minusSeconds(150000).toString(),"lastRestoreAt",now.toString()),now));
        assertEquals("OK",OperationalHealthService.backupState(Map.of("lastSuccessAt",now.toString(),"lastRestoreAt",now.toString()),now));
        assertEquals("INVALID",OperationalHealthService.backupState(Map.of("lastSuccessAt","not-a-date"),now));
    }
}
