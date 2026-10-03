package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.model.CapoProfile;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import com.radiotech.radiotech_backend.service.CapoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({ "/api/capo", "/api/v1/capo" })
public class CapoController {

        private final CapoService capoService;

        public CapoController(
                        CapoService capoService) {

                this.capoService = capoService;
        }

        /**
         * GET /api/capo/profile
         */
        @GetMapping("/profile")
        public ResponseEntity<?> getProfile(
                        @RequestAttribute("firebaseUid") String firebaseUid) {

                try {
                        requirePermission(Permission.USER_READ);
                        CapoProfile profile = scopedProfile(firebaseUid);

                        return ResponseEntity.ok(
                                        success(
                                                        "Profilo CAPO recuperato.",
                                                        profile));

                } catch (SecurityException e) {
                        return forbidden(e);
                } catch (Exception e) {

                        return ResponseEntity
                                        .status(
                                                        HttpStatus.NOT_FOUND)
                                        .body(
                                                        error(
                                                                        e.getMessage()));
                }
        }

        /**
         * PUT /api/capo/profile
         */
        @PutMapping("/profile")
        public ResponseEntity<?> updateProfile(
                        @RequestAttribute("firebaseUid") String firebaseUid,
                        @RequestBody Map<String, String> request) {

                try {

                        requirePermission(Permission.USER_UPDATE);

                        if (request == null) {

                                return ResponseEntity
                                                .badRequest()
                                                .body(
                                                                error(
                                                                                "Body obbligatorio."));
                        }

                        scopedProfile(firebaseUid);
                        CapoProfile updated = capoService.updateProfile(
                                        firebaseUid,
                                        request.get("fullName"),
                                        request.get("phone"),
                                        request.get("company"),
                                        request.get("photoUrl"));

                        return ResponseEntity.ok(
                                        success(
                                                        "Profilo CAPO aggiornato.",
                                                        updated));

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(
                                                        error(
                                                                        e.getMessage()));

                } catch (SecurityException e) {
                        return forbidden(e);
                } catch (Exception e) {

                        return ResponseEntity
                                        .status(
                                                        HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(
                                                        error(
                                                                        "Errore durante l'aggiornamento del profilo."));
                }
        }

        /**
         * POST /api/capo/last-seen
         */
        @PostMapping("/last-seen")
        public ResponseEntity<?> updateLastSeen(
                        @RequestAttribute("firebaseUid") String firebaseUid) {

                try {

                        requirePermission(Permission.USER_UPDATE);
                        scopedProfile(firebaseUid);

                        capoService.updateLastSeen(
                                        firebaseUid);

                        return ResponseEntity.ok(
                                        success(
                                                        "Ultimo accesso aggiornato.",
                                                        null));

                } catch (SecurityException e) {
                        return forbidden(e);
                } catch (Exception e) {

                        return ResponseEntity
                                        .status(
                                                        HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(
                                                        error(
                                                                        "Impossibile aggiornare l'ultimo accesso."));
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

        private void requirePermission(Permission permission) {
                Role role = SecurityContextAccessor.currentRole();
                if (!TenantAccessPolicy.canAccess(role, permission)) {
                        throw new SecurityException("Permessi insufficienti per il profilo CAPO.");
                }
                TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        }

        private CapoProfile scopedProfile(String firebaseUid) throws Exception {
                CapoProfile profile = capoService.getProfile(firebaseUid);
                TenantAccessPolicy.requireTenantAccess(
                                SecurityContextAccessor.currentTenantId(), profile.getTenantId());
                return profile;
        }

        private ResponseEntity<?> forbidden(SecurityException exception) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(error(exception.getMessage()));
        }

        private Map<String, Object> error(
                        String message) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "success",
                                false);

                response.put(
                                "message",
                                message == null ||
                                                message.isBlank()
                                                                ? "Richiesta non valida."
                                                                : message);

                return response;
        }
}