package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.Firestore;
import java.time.*;
import java.util.*;

/** One durable counter document plus active SLA tasks; no historical archive scan. */
final class DashboardProjectionReader {
    static Map<String,Object> read(Firestore db, String tenant, long warningMinutes) throws Exception {
        var ref = db.collection("dashboardSummaries").document(tenant);
        var summary = ref.get().get();
        if (!summary.exists() || !Boolean.TRUE.equals(summary.getBoolean("ready"))) return null;
        if (!tenant.equals(summary.getString("tenantId")) || !Long.valueOf(1).equals(summary.getLong("schemaVersion")))
            throw new IllegalStateException("Proiezione dashboard non valida.");
        var raw = summary.get("counts") instanceof Map<?,?> counts ? counts : Map.of();
        var result = new LinkedHashMap<String,Object>();
        for (String field : List.of("antennas","operators","activeOperators","interventions","tasks","activeTasks",
                "inventoryItems","lowStock","activeAntennas","maintenanceAntennas","offlineAntennas")) result.put(field, count(raw, field));
        result.put("pendingReports", (long) count(raw,"pendingReports"));
        int antennas = count(raw,"antennas"), active = count(raw,"activeAntennas");
        result.put("inactiveAntennas", antennas-active);
        result.put("availability", antennas==0 ? 0.0 : Math.round(10000.0*active/antennas)/100.0);
        int overdue=0, atRisk=0; Instant now=Instant.now();
        com.google.cloud.firestore.DocumentSnapshot cursor=null;
        while(true) {
            var query=ref.collection("activeTasks").select("dueAt");
            var page=(cursor==null?query:query.startAfter(cursor)).limit(500).get().get();
            for(var doc:page.getDocuments()) {
                var sla=SlaPolicy.evaluate(doc.getString("dueAt"),now,Duration.ofMinutes(Math.max(0,warningMinutes)));
                if(sla==SlaPolicy.Status.BREACHED)overdue++;else if(sla==SlaPolicy.Status.AT_RISK)atRisk++;
            }
            if(page.size()<500)break;cursor=page.getDocuments().getLast();
        }
        result.put("taskStats",Map.of("assigned",count(raw,"assigned"),"inProgress",count(raw,"inProgress"),
                "completed",count(raw,"completed"),"cancelled",count(raw,"cancelled"),"overdue",overdue,"slaBreached",overdue,"slaAtRisk",atRisk));
        result.put("asOf",now.toString());result.put("cacheMaxAgeSeconds",15);
        result.put("summarySource","PROJECTION");result.put("projectionUpdatedAt",Objects.toString(summary.getString("updatedAt"),""));
        return result;
    }
    private static int count(Map<?,?> values,String field) {
        Object value=values.get(field);if(value==null)return 0;
        if(!(value instanceof Number number)||number.longValue()<0||number.doubleValue()!=number.longValue())throw new IllegalStateException("Contatore dashboard non valido.");
        return Math.toIntExact(number.longValue());
    }
}
