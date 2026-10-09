package com.radiotech.radiotech_backend.ops;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.radiotech.radiotech_backend.service.*;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.core.env.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Scheduled tenant monitoring with aggregate queries; no report/user data is logged. */
public class OperationalMonitorTool {
    public static void main(String[] args) throws Exception {
        String project = Objects.requireNonNull(System.getenv("FIREBASE_PROJECT_ID"), "Firebase project required.");
        String account = Objects.requireNonNull(System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH"), "Server credentials required.");
        String[] tenants = JsonMapper.builder().build().readValue(Objects.requireNonNull(System.getenv("RADIOTECH_MONITOR_TENANT_IDS"), "Configured tenant IDs required."), String[].class);
        if (tenants.length == 0 || tenants.length > 100 || Arrays.stream(tenants).anyMatch(id -> id == null || id.isBlank() || id.length() > 1500 || id.contains("/"))) throw new IllegalArgumentException("Invalid monitor tenant configuration.");
        try (var input = Files.newInputStream(Path.of(account))) {
            FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project).setCredentials(GoogleCredentials.fromStream(input)).build());
        }
        boolean failed = false;
        try {
            var env = new StandardEnvironment(); env.setActiveProfiles("production");
            env.getPropertySources().addFirst(new MapPropertySource("monitor", Map.of("app.firebase.service-account-path", account)));
            var usage = new FirestoreUsageService(env);
            var health = new OperationalHealthService(usage, new LocalAttachmentService(env), env);
            for (int i = 0; i < tenants.length; i++) {
                var authentication = new UsernamePasswordAuthenticationToken("scheduled-monitor", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                authentication.setDetails(new FirebaseAuthenticationDetails("scheduled-monitor", null, null, tenants[i]));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                var snapshot = health.snapshot();
                var metrics = (Map<?,?>) snapshot.get("firestore");
                var alarms = (List<?>) snapshot.get("alarms");
                System.out.println("Monitored company " + (i + 1) + ": pending=" + snapshot.get("pendingReports") + ", backup=" + ((Map<?,?>) snapshot.get("backup")).get("state") + ", metricState=" + metrics.get("state"));
                if (!"AVAILABLE".equals(metrics.get("state"))) { failed = true; System.err.println("Alarm: MONITORING_UNAVAILABLE"); }
                for (var item : alarms) { failed = true; System.err.println("Alarm: " + ((Map<?,?>)item).get("code")); }
            }
        } finally { SecurityContextHolder.clearContext(); FirebaseApp.getInstance().delete(); }
        if (failed) throw new IllegalStateException("Operational thresholds require review; open the protected system dashboard.");
    }
}
