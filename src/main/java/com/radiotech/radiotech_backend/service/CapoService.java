package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;

import com.google.firebase.cloud.FirestoreClient;

import com.radiotech.radiotech_backend.model.CapoProfile;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class CapoService {

        private static final String COLLECTION = "users";

        private static final String ROLE_CAPO = "CAPO";

        private static final String STATUS_ATTIVO = "ATTIVO";

        @org.springframework.beans.factory.annotation.Value("${radiotech.capo.email:capo@radiotech.it}")
        private String configuredCapoEmail;

        @org.springframework.beans.factory.annotation.Value("${radiotech.bootstrap.tenant-id:}")
        private String configuredBootstrapTenantId;

        /*
         * ================================================================
         * GET PROFILE
         * ================================================================
         */

        public CapoProfile getProfile(
                        String uid)
                        throws Exception {

                validateUid(uid);

                String normalizedUid = uid.trim();

                Firestore db = FirestoreClient.getFirestore();

                DocumentSnapshot document = db.collection(COLLECTION)
                                .document(normalizedUid)
                                .get()
                                .get();

                if (!document.exists()) {

                        throw new IllegalArgumentException(
                                        "Profilo CAPO non trovato.");
                }
                requireDocumentTenant(document);

                CapoProfile profile = document.toObject(
                                CapoProfile.class);

                if (profile == null) {

                        throw new IllegalStateException(
                                        "Impossibile leggere il profilo CAPO.");
                }

                profile.setUid(
                                normalizedUid);

                return profile;
        }

        /*
         * ================================================================
         * CREATE / UPDATE PROFILE
         * ================================================================
         */

        public CapoProfile createOrUpdateProfile(
                        String uid,
                        String fullName,
                        String email,
                        String phone,
                        String company,
                        String photoUrl)
                        throws Exception {

                validateUid(uid);

                String normalizedUid = uid.trim();

                FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

                UserRecord firebaseUser = firebaseAuth.getUser(
                                normalizedUid);

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document(normalizedUid);

                DocumentSnapshot existing = document.get()
                                .get();

                requireDocumentTenant(existing);

                String currentTenant = SecurityContextAccessor.currentTenantId();
                String profileTenant = currentTenant == null || currentTenant.isBlank()
                                ? configuredBootstrapTenantId
                                : TenantAccessPolicy.requireTenantAccess(currentTenant, null);

                String now = Instant.now().toString();

                String createdAt = now;

                if (existing.exists()) {

                        String existingCreatedAt = existing.getString(
                                        "createdAt");

                        if (existingCreatedAt != null
                                        && !existingCreatedAt.isBlank()) {

                                createdAt = existingCreatedAt;
                        }
                }

                CapoProfile profile = new CapoProfile();

                profile.setUid(
                                normalizedUid);
                if (profileTenant == null || profileTenant.isBlank()) {
                        throw new IllegalStateException("Tenant CAPO non configurato.");
                }
                profile.setTenantId(profileTenant.trim());

                profile.setFullName(
                                normalize(
                                                fullName,
                                                firebaseUser.getDisplayName()));

                profile.setEmail(
                                normalize(
                                                email,
                                                firebaseUser.getEmail()));

                profile.setPhone(
                                normalize(
                                                phone,
                                                firebaseUser.getPhoneNumber()));

                profile.setCompany(
                                normalize(
                                                company,
                                                "RadioTech"));

                profile.setRole(
                                ROLE_CAPO);

                profile.setStatus(
                                STATUS_ATTIVO);

                profile.setPhotoUrl(
                                normalize(
                                                photoUrl,
                                                firebaseUser.getPhotoUrl()));

                if (existing.exists()) {

                        profile.setLastSeen(
                                        existing.getString(
                                                        "lastSeen"));

                } else {

                        profile.setLastSeen(
                                        now);
                }

                profile.setCreatedAt(
                                createdAt);

                profile.setUpdatedAt(
                                now);

                document
                                .set(profile)
                                .get();

                return profile;
        }

        /*
         * ================================================================
         * UPDATE PROFILE
         * ================================================================
         */

        public CapoProfile updateProfile(
                        String uid,
                        String fullName,
                        String phone,
                        String company,
                        String photoUrl)
                        throws Exception {

                validateUid(uid);

                CapoProfile existing = getProfile(uid);

                if (fullName != null
                                && !fullName.isBlank()) {

                        existing.setFullName(
                                        fullName.trim());
                }

                if (phone != null) {

                        existing.setPhone(
                                        phone.trim());
                }

                if (company != null
                                && !company.isBlank()) {

                        existing.setCompany(
                                        company.trim());
                }

                if (photoUrl != null) {

                        existing.setPhotoUrl(
                                        photoUrl.trim());
                }

                existing.setRole(
                                ROLE_CAPO);

                existing.setStatus(
                                STATUS_ATTIVO);

                existing.setUpdatedAt(
                                Instant.now().toString());

                Firestore db = FirestoreClient.getFirestore();

                db.collection(COLLECTION)
                                .document(uid.trim())
                                .set(existing)
                                .get();

                return existing;
        }

        /*
         * ================================================================
         * LAST SEEN
         * ================================================================
         */

        public void updateLastSeen(
                        String uid)
                        throws Exception {

                validateUid(uid);

                String normalizedUid = uid.trim();

                String now = Instant.now().toString();

                Firestore db = FirestoreClient.getFirestore();

                db.collection(COLLECTION)
                                .document(normalizedUid)
                                .update(
                                                "lastSeen",
                                                now,
                                                "updatedAt",
                                                now)
                                .get();
        }

        /*
         * ================================================================
         * SET CAPO ROLE
         * ================================================================
         */

        public void setCapoRole(
                        String uid)
                        throws Exception {

                validateUid(uid);

                if (configuredBootstrapTenantId == null || configuredBootstrapTenantId.isBlank()) {
                        throw new IllegalStateException("RADIOTECH_BOOTSTRAP_TENANT_ID non configurato.");
                }

                String normalizedUid = uid.trim();
                UserRecord user = FirebaseAuth.getInstance().getUser(normalizedUid);
                Map<String, Object> claims = new HashMap<>();
                if (user.getCustomClaims() != null) {
                        claims.putAll(user.getCustomClaims());
                }
                Object existingTenant = claims.get("tenantId");
                if (existingTenant instanceof String value && !value.isBlank()
                                && !configuredBootstrapTenantId.trim().equals(value.trim())) {
                        throw new SecurityException("Account CAPO già assegnato a un tenant diverso.");
                }
                claims.put("role", ROLE_CAPO);
                claims.put("admin", true);
                claims.put("tenantId", configuredBootstrapTenantId.trim());

                FirebaseAuth
                                .getInstance()
                                .setCustomUserClaims(
                                                normalizedUid,
                                                claims);
        }

        /*
         * ================================================================
         * CHECK CAPO
         * ================================================================
         */

        public boolean isCapo(
                        FirebaseToken token) {

                if (token == null) {
                        return false;
                }

                // La promozione automatica via email vale solo se l'email e' verificata:
                // altrimenti chiunque potrebbe registrare l'indirizzo del CAPO e diventarlo.
                if (configuredBootstrapTenantId != null && !configuredBootstrapTenantId.isBlank()
                                && token.isEmailVerified() && token.getEmail() != null && configuredCapoEmail != null &&
                                configuredCapoEmail.equalsIgnoreCase(token.getEmail().trim())) {
                        try {
                                UserRecord user = FirebaseAuth.getInstance().getUser(token.getUid());
                                Map<String, Object> capoClaims = new HashMap<>();
                                if (user.getCustomClaims() != null) {
                                        capoClaims.putAll(user.getCustomClaims());
                                }
                                Object existingTenant = capoClaims.get("tenantId");
                                if (existingTenant instanceof String value && !value.isBlank()
                                                && !configuredBootstrapTenantId.trim().equals(value.trim())) {
                                        return false;
                                }
                                capoClaims.put("role", ROLE_CAPO);
                                capoClaims.put("admin", true);
                                capoClaims.put("tenantId", configuredBootstrapTenantId.trim());
                                FirebaseAuth.getInstance().setCustomUserClaims(token.getUid(), capoClaims);
                        } catch (Exception error) {
                                return false;
                        }
                        return true;
                }

                Map<String, Object> claims = token.getClaims();

                if (claims == null) {
                        return false;
                }

                Object role = claims.get("role");

                Object admin = claims.get("admin");

                boolean roleCapo = ROLE_CAPO.equals(String.valueOf(role)) ||
                                "ADMIN".equals(String.valueOf(role)) ||
                                "SUPER_ADMIN".equals(String.valueOf(role)) ||
                                "CHIEF_EXECUTIVE".equals(String.valueOf(role));

                boolean adminEnabled = Boolean.TRUE.equals(admin);

                return roleCapo
                                || adminEnabled;
        }

        /*
         * ================================================================
         * ENSURE PROFILE
         * ================================================================
         */

        public CapoProfile ensureCapoProfile(
                        String uid)
                        throws Exception {

                validateUid(uid);

                String normalizedUid = uid.trim();

                UserRecord user = FirebaseAuth
                                .getInstance()
                                .getUser(
                                                normalizedUid);

                try {

                        return getProfile(
                                        normalizedUid);

                } catch (IllegalArgumentException ignored) {

                        return createOrUpdateProfile(
                                        normalizedUid,
                                        user.getDisplayName(),
                                        user.getEmail(),
                                        user.getPhoneNumber(),
                                        "RadioTech",
                                        user.getPhotoUrl());
                }
        }

        /*
         * ================================================================
         * VALIDATE UID
         * ================================================================
         */

        private void validateUid(
                        String uid) {

                if (uid == null
                                || uid.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Firebase UID obbligatorio.");
                }
        }

        /*
         * ================================================================
         * NORMALIZE
         * ================================================================
         */

        private String normalize(
                        String value,
                        String fallback) {

                if (value != null
                                && !value.isBlank()) {

                        return value.trim();
                }

                if (fallback != null
                                && !fallback.isBlank()) {

                        return fallback.trim();
                }

                return null;
        }

        private void requireDocumentTenant(DocumentSnapshot document) {
                if (document == null || !document.exists()) {
                        return;
                }
                String authenticatedTenant = SecurityContextAccessor.currentTenantId();
                if (authenticatedTenant != null && !authenticatedTenant.isBlank()
                                && !TenantAccessPolicy.canAccessTenant(authenticatedTenant,
                                                document.getString("tenantId"))) {
                        throw new SecurityException("Profilo CAPO non appartenente al tenant autenticato.");
                }
        }
}