package com.radiotech.radiotech_backend.service;

import com.google.firebase.FirebaseApp;
import com.google.gson.*;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import com.google.auth.oauth2.GoogleCredentials;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cloud Monitoring totals, cached independently of archive reads. Unknown is never reported as zero. */
@Service
public class FirestoreUsageService {
    private final Environment env;
    public FirestoreUsageService(Environment env) { this.env = env; }
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Map<String,Object> cached;
    private Instant expires = Instant.EPOCH;
    public synchronized Map<String,Object> snapshot() {
        Instant now = Instant.now();
        if (cached != null && now.isBefore(expires)) return cached;
        var result = new LinkedHashMap<String,Object>();
        result.put("checkedAt", now.toString());
        result.put("source", "GOOGLE_CLOUD_MONITORING");
        result.put("quotaDayTimezone", "America/Los_Angeles");
        try {
            var app = FirebaseApp.getInstance();
            String account = env.getProperty("app.firebase.service-account-path", "");
            GoogleCredentials source;
            if (account.isBlank()) source = GoogleCredentials.getApplicationDefault();
            else try (var input = Files.newInputStream(Path.of(account))) { source = GoogleCredentials.fromStream(input); }
            var credentials = source.createScoped("https://www.googleapis.com/auth/monitoring.read");
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();
            String project = app.getOptions().getProjectId();
            Instant start = now.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate()
                    .atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant();
            for (var metric : Map.of("reads", "document/read_ops_count", "writes", "document/write_ops_count",
                    "deletes", "document/delete_ops_count", "storageBytes", "storage/data_and_index_storage_bytes").entrySet()) {
                boolean gauge = metric.getKey().equals("storageBytes");
                var points = fetch(project, token, metric.getValue(), gauge ? now.minus(Duration.ofHours(24)) : start, now, gauge);
                result.put(metric.getKey(), metricValue(points, gauge));
            }
            result.put("state", "AVAILABLE");
            result.put("note", "Metriche del database condiviso, aggiornate con ritardo; i dati di fatturazione restano nella console Google.");
        } catch (Exception unavailable) {
            result.put("state", "UNAVAILABLE");
            result.put("note", "Metriche Google non disponibili: verificare Cloud Monitoring API e ruolo Monitoring Viewer dell’account di servizio. I valori non disponibili restano sconosciuti.");
        }
        cached = Collections.unmodifiableMap(result);
        expires = now.plus(Duration.ofMinutes(10));
        return cached;
    }
    private JsonObject fetch(String project, String token, String metric, Instant start, Instant end, boolean gauge) throws Exception {
        String filter = "metric.type=\"firestore.googleapis.com/" + metric + "\" AND resource.labels.database_id=\"(default)\"";
        String query = "filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8)
                + "&interval.startTime=" + start + "&interval.endTime=" + end
                + "&aggregation.alignmentPeriod=3600s&aggregation.perSeriesAligner=" + (gauge ? "ALIGN_MAX" : "ALIGN_SUM")
                + "&aggregation.crossSeriesReducer=REDUCE_SUM&pageSize=100";
        var request = HttpRequest.newBuilder(URI.create("https://monitoring.googleapis.com/v3/projects/" + project + "/timeSeries?" + query))
                .timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + token).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Monitoring HTTP " + response.statusCode());
        var data = JsonParser.parseString(response.body()).getAsJsonObject();
        if (data.has("nextPageToken")) throw new IllegalStateException("Incomplete monitoring result");
        return data;
    }
    static Long metricValue(JsonObject payload, boolean gauge) {
        if (!payload.has("timeSeries") || payload.getAsJsonArray("timeSeries").isEmpty()) return null;
        long total = 0;
        boolean present = false;
        for (var series : payload.getAsJsonArray("timeSeries")) {
            var points = series.getAsJsonObject().getAsJsonArray("points");
            if (points == null || points.isEmpty()) continue;
            if (gauge) { total += points.get(0).getAsJsonObject().getAsJsonObject("value").get("int64Value").getAsLong(); present = true; }
            else for (var point : points) { total += point.getAsJsonObject().getAsJsonObject("value").get("int64Value").getAsLong(); present = true; }
        }
        return present ? total : null;
    }
}
