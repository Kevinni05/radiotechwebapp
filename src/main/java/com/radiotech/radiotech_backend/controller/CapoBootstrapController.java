package com.radiotech.radiotech_backend.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.radiotech.radiotech_backend.model.CapoProfile;
import com.radiotech.radiotech_backend.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping({ "/api/bootstrap", "/api/v1/bootstrap" })
public class CapoBootstrapController {

        private final AuthService authService;

        @Value("${radiotech.capo.email}")
        private String configuredEmail;

        @Value("${radiotech.bootstrap.secret:}")
        private String bootstrapSecret;

        public CapoBootstrapController(
                        AuthService authService) {

                this.authService = authService;
        }

        /**
         * Promuove un account Firebase esistente a CAPO.
         *
         * ATTENZIONE:
         * questo endpoint è pensato esclusivamente
         * per il bootstrap iniziale.
         */
        @PostMapping("/capo")
        public ResponseEntity<?> bootstrapCapo(
                        @RequestBody Map<String, String> request) {

                try {

                        if (request == null) {

                                return ResponseEntity
                                                .badRequest()
                                                .body(
                                                                error(
                                                                                "Body obbligatorio."));
                        }

                        String email = request.get("email");
                        String providedSecret = request.get("secret");

                        if (bootstrapSecret == null || bootstrapSecret.isBlank()
                                        || providedSecret == null
                                        || !MessageDigest.isEqual(
                                                        bootstrapSecret.getBytes(StandardCharsets.UTF_8),
                                                        providedSecret.getBytes(StandardCharsets.UTF_8))) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(error("Secret bootstrap non valido o non configurato."));
                        }

                        if (email == null ||
                                        email.isBlank()) {

                                return ResponseEntity
                                                .badRequest()
                                                .body(
                                                                error(
                                                                                "Email obbligatoria."));
                        }

                        if (!configuredEmail.equalsIgnoreCase(
                                        email.trim())) {

                                return ResponseEntity
                                                .status(
                                                                HttpStatus.FORBIDDEN)
                                                .body(
                                                                error(
                                                                                "Questo account non è autorizzato al bootstrap CAPO."));
                        }

                        UserRecord user = FirebaseAuth
                                        .getInstance()
                                        .getUserByEmail(
                                                        email.trim());

                        CapoProfile profile = authService.promoteToCapo(
                                        user.getUid());

                        return ResponseEntity.ok(
                                        success(
                                                        "Account CAPO configurato correttamente.",
                                                        profile));

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(
                                                        HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(
                                                        error(
                                                                        "Impossibile configurare l'account CAPO: "
                                                                                        + e.getMessage()));
                }
        }

        private Map<String, Object> success(
                        String message,
                        Object data) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "success",
                                true);

                response.put(
                                "message",
                                message);

                response.put(
                                "data",
                                data);

                return response;
        }

        private Map<String, Object> error(
                        String message) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "success",
                                false);

                response.put(
                                "message",
                                message);

                return response;
        }
}