package com.radiotech.radiotech_backend.controller;

import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({ "/api/health", "/api/v1/health" })
public class HealthController {

    /**
     * Controllo generale del backend.
     *
     * GET /api/health
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", true);
        response.put("status", "UP");
        response.put("service", "radiotech-backend");
        response.put("release", com.radiotech.radiotech_backend.ops.ReleaseMetadata.current());
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Controllo della connessione a Firestore.
     *
     * GET /api/health/firebase
     */
    @GetMapping("/firebase")
    public ResponseEntity<Map<String, Object>> firebaseHealth() {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("service", "firebase-firestore");
        response.put("timestamp", Instant.now().toString());

        try {

            FirestoreClient.getFirestore()
                    .collection("_health")
                    .limit(1)
                    .get()
                    .get();

            response.put("success", true);
            response.put("status", "UP");

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            if (com.radiotech.radiotech_backend.exception.CloudQuota.exhausted(e)) response.put("error", "CLOUD_QUOTA_EXHAUSTED");

            response.put("success", false);
            response.put("status", "DOWN");
            response.put(
                    "message",
                    "Connessione a Firebase Firestore non disponibile.");

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(response);
        }
    }
}
