package com.radiotech.radiotech_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.enabled:true}")
    private boolean enabled;

    @Value("${app.firebase.storage-bucket:}")
    private String storageBucket;

    @Value("${app.firebase.project-id:gestionale-radio}")
    private String projectId;

    @Value("${app.firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${app.firebase.database-url:}")
    private String databaseUrl;

    @PostConstruct
    public void init() throws IOException {
        if (!enabled) {
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        GoogleCredentials credentials;
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            Path credentialsPath = Path.of(serviceAccountPath.trim());
            try (var credentialsStream = Files.newInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(credentialsStream);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId);
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            optionsBuilder.setDatabaseUrl(databaseUrl.trim());
        }
        if (storageBucket != null && !storageBucket.isBlank()) {
            optionsBuilder.setStorageBucket(storageBucket.trim());
        }
        FirebaseOptions options = optionsBuilder.build();

        FirebaseApp.initializeApp(options);
    }
}
