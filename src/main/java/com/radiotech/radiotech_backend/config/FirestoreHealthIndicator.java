package com.radiotech.radiotech_backend.config;

import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class FirestoreHealthIndicator implements HealthIndicator {

    private final boolean firebaseEnabled;

    public FirestoreHealthIndicator(@Value("${app.firebase.enabled:true}") boolean firebaseEnabled) {
        this.firebaseEnabled = firebaseEnabled;
    }

    @Override
    public Health health() {
        if (!firebaseEnabled) {
            return Health.up().build();
        }

        try {
            FirestoreClient.getFirestore()
                    .collection("_health")
                    .limit(1)
                    .get()
                    .get(2, TimeUnit.SECONDS);
            return Health.up().build();
        } catch (Exception exception) {
            return Health.down().withDetail("dependency", "firestore").build();
        }
    }
}