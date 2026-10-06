// src/main/java/com/radiotech/radiotech_backend/service/OperatorService.java

package com.radiotech.radiotech_backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class OperatorService {

        private static final String COLLECTION = "operators";

        public List<Operator> getAllOperators() throws Exception {

                Firestore db = FirestoreClient.getFirestore();

                ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                                .whereEqualTo("tenantId", currentTenant())
                                .orderBy("createdAt", Query.Direction.DESCENDING)
                                .get();

                List<QueryDocumentSnapshot> documents = future.get().getDocuments();

                List<Operator> operators = new ArrayList<>();

                for (QueryDocumentSnapshot document : documents) {

                        Operator operator = document.toObject(Operator.class);

                        if (operator == null) {
                                continue;
                        }

                        operator.setId(document.getId());

                        if (operator.getFcmTokens() == null) {
                                operator.setFcmTokens(new ArrayList<>());
                        }

                        operators.add(operator);
                }

                return operators;
        }

        public Operator getById(String id) throws Exception {

                validateId(id);

                Firestore db = FirestoreClient.getFirestore();

                DocumentSnapshot document = db.collection(COLLECTION)
                                .document(id)
                                .get()
                                .get();

                if (!document.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + id);
                }
                requireDocumentTenant(document);

                Operator operator = document.toObject(Operator.class);

                if (operator == null) {
                        throw new IllegalStateException(
                                        "Impossibile convertire l'operatore.");
                }

                operator.setId(document.getId());

                ensureFcmTokens(operator);

                return operator;
        }

        public Operator createOperator(
                        Operator operator)
                        throws ExecutionException, InterruptedException {

                validate(operator);
                if (isBlank(operator.getTenantId())) {
                        throw new IllegalArgumentException("Tenant operatore obbligatorio.");
                }

                String authenticatedTenant = SecurityContextAccessor.currentTenantId();
                if (!isBlank(authenticatedTenant)) {
                        TenantAccessPolicy.requireTenantAccess(authenticatedTenant, operator.getTenantId());
                        operator.setTenantId(authenticatedTenant);
                }

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION).document();

                String now = Instant.now().toString();

                operator.setId(document.getId());

                operator.setFirebaseUid(
                                normalizeNullable(operator.getFirebaseUid()));

                operator.setQrCodeToken(
                                "AUTH_OP_" + UUID.randomUUID());
                operator.setQrExpiresAt(qrExpiry());
                operator.setQrUsedAt(null);
                operator.setQrLastUsedAt(null);

                ensureFcmTokens(operator);

                operator.setLastSeen(null);
                operator.setCreatedAt(now);
                operator.setUpdatedAt(now);

                document.set(operator).get();

                return operator;
        }

        public Operator syncFirebaseUser(String email, String fullName, String tenantId) throws Exception {
                if (tenantId == null || tenantId.isBlank()) {
                        throw new IllegalArgumentException("Tenant obbligatorio.");
                }
                if (email == null || email.isBlank()) {
                        throw new IllegalArgumentException("Email Firebase obbligatoria.");
                }

                UserRecord firebaseUser = FirebaseAuth.getInstance().getUserByEmail(email.trim());
                assertTenantClaimMatches(firebaseUser, tenantId);
                Operator existing = getByFirebaseUid(firebaseUser.getUid());
                if (existing != null) {
                        if (!tenantId.equals(existing.getTenantId())) {
                                throw new SecurityException("Operatore appartenente a un tenant diverso.");
                        }
                        return existing;
                }

                String resolvedName = fullName == null || fullName.isBlank()
                                ? firebaseUser.getDisplayName()
                                : fullName.trim();
                if (resolvedName == null || resolvedName.isBlank()) {
                        resolvedName = email.substring(0, email.indexOf('@'));
                }

                Operator operator = new Operator();
                operator.setFullName(resolvedName);
                operator.setEmail(firebaseUser.getEmail());
                operator.setFirebaseUid(firebaseUser.getUid());
                operator.setTenantId(tenantId);
                operator.setLevel("TECNICO");
                operator.setShift("GIORNALIERO");
                operator.setStatus("ATTIVO");
                operator.setRole("OPERATOR");
                Operator created = createOperator(operator);
                setOperatorClaims(firebaseUser, created);
                return created;
        }

        public java.util.Map<String, Object> generateOperatorWithCredentials(
                        String tenantId,
                        String fullName,
                        String email,
                        String phone,
                        String level,
                        String shift,
                        String customPassword) throws Exception {

                if (tenantId == null || tenantId.isBlank()) {
                        throw new IllegalArgumentException("Tenant obbligatorio.");
                }

                if (fullName == null || fullName.isBlank()) {
                        throw new IllegalArgumentException("Nome e cognome obbligatori.");
                }

                String cleanName = fullName.trim();
                String resolvedEmail = (email != null && !email.isBlank())
                                ? email.trim()
                                : cleanName.toLowerCase().replaceAll("[^a-z0-9]", ".") + "@radiotech.it";

                String password = (customPassword != null && !customPassword.isBlank())
                                ? customPassword
                                : generateTemporaryPassword();
                validatePassword(password);

                UserRecord existingUser = null;
                try {
                        existingUser = FirebaseAuth.getInstance().getUserByEmail(resolvedEmail);
                } catch (FirebaseAuthException lookupError) {
                        if (lookupError.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                                throw lookupError;
                        }
                }

                UserRecord firebaseUser;
                Operator existingOperator = null;
                if (existingUser != null) {
                        assertTenantClaimMatches(existingUser, tenantId);
                        // Non riusare (e non resettare la password di) account con ruoli diversi da
                        // operatore.
                        assertAccountReusableAsOperator(existingUser);
                        existingOperator = getByFirebaseUid(existingUser.getUid());
                        if (existingOperator != null
                                        && (isBlank(existingOperator.getTenantId())
                                                        || !tenantId.equals(existingOperator.getTenantId()))) {
                                throw new SecurityException("Account già associato a un tenant diverso o non mappato.");
                        }
                        UserRecord.UpdateRequest updateReq = new UserRecord.UpdateRequest(existingUser.getUid())
                                        .setPassword(password)
                                        .setDisplayName(cleanName);
                        firebaseUser = FirebaseAuth.getInstance().updateUser(updateReq);
                } else {
                        UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                                        .setEmail(resolvedEmail)
                                        .setPassword(password)
                                        .setDisplayName(cleanName)
                                        .setEmailVerified(true);
                        firebaseUser = FirebaseAuth.getInstance().createUser(createReq);
                }

                if (existingOperator == null) {
                        existingOperator = getByFirebaseUid(firebaseUser.getUid());
                }
                Operator operator;
                if (existingOperator != null) {
                        if (isBlank(existingOperator.getTenantId())
                                        || !tenantId.equals(existingOperator.getTenantId())) {
                                throw new SecurityException("Account già associato a un tenant diverso.");
                        }
                        existingOperator.setTenantId(tenantId);
                        existingOperator.setFullName(cleanName);
                        existingOperator.setEmail(resolvedEmail);
                        if (phone != null && !phone.isBlank())
                                existingOperator.setPhone(phone.trim());
                        if (level != null && !level.isBlank())
                                existingOperator.setLevel(level.trim().toUpperCase());
                        if (shift != null && !shift.isBlank())
                                existingOperator.setShift(shift.trim().toUpperCase());
                        existingOperator.setStatus("ATTIVO");
                        if (existingOperator.getQrCodeToken() == null || existingOperator.getQrCodeToken().isBlank()) {
                                existingOperator.setQrCodeToken("AUTH_OP_" + UUID.randomUUID());
                        }
                        existingOperator.setQrExpiresAt(qrExpiry());
                        existingOperator.setQrUsedAt(null);
                        existingOperator.setQrLastUsedAt(null);
                        operator = updateOperator(existingOperator.getId(), existingOperator);
                        regenerateQrToken(operator.getId());
                        operator = getById(operator.getId());
                } else {
                        operator = new Operator();
                        operator.setFullName(cleanName);
                        operator.setEmail(resolvedEmail);
                        operator.setPhone(phone != null ? phone.trim() : null);
                        operator.setFirebaseUid(firebaseUser.getUid());
                        operator.setTenantId(tenantId);
                        operator.setLevel((level != null && !level.isBlank()) ? level.trim().toUpperCase() : "TECNICO");
                        operator.setShift((shift != null && !shift.isBlank()) ? shift.trim().toUpperCase()
                                        : "GIORNALIERO");
                        operator.setStatus("ATTIVO");
                        operator.setRole("OPERATOR");
                        operator = createOperator(operator);
                }

                setOperatorClaims(firebaseUser, operator);

                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("operator", operator);
                result.put("email", resolvedEmail);
                result.put("password", password);
                result.put("passwordSetupRequired", true);
                result.put("qrCodeToken", operator.getQrCodeToken());
                result.put("firebaseUid", firebaseUser.getUid());
                return result;
        }

        public String regenerateQrToken(String operatorId) throws Exception {
                validateId(operatorId);
                String newQrToken = "AUTH_OP_" + UUID.randomUUID();
                DocumentReference reference = FirestoreClient.getFirestore()
                                .collection(COLLECTION).document(operatorId);
                dbRotateBadge(reference, newQrToken);
                return newQrToken;
        }

        private void dbRotateBadge(DocumentReference reference, String token) throws Exception {
                String tenantId = currentTenant();
                try {
                    FirestoreClient.getFirestore().runTransaction(transaction -> {
                        DocumentSnapshot current = transaction.get(reference).get();
                        if (!current.exists()) {
                                throw new IllegalArgumentException("Operatore non trovato.");
                        }
                        requireDocumentTenant(current, tenantId);
                        Map<String, Object> updates = new java.util.HashMap<>();
                        updates.put("qrCodeToken", token);
                        updates.put("qrExpiresAt", qrExpiry());
                        updates.put("qrUsedAt", null);
                        updates.put("qrLastUsedAt", null);
                        updates.put("updatedAt", Instant.now().toString());
                        transaction.update(reference, updates);
                        return null;
                    }).get();
                } catch (ExecutionException exception) {
                        if (exception.getCause() instanceof SecurityException denied) throw denied;
                        if (exception.getCause() instanceof IllegalArgumentException invalid) throw invalid;
                        throw exception;
                }
        }

        /** Validate without consuming, so Firebase failures leave the badge usable. */
        public Operator validateQrToken(String qrCodeToken) throws Exception {
                return resolveQrToken(qrCodeToken, false);
        }

        /** Atomically consumes the badge after the session token is prepared. */
        public Operator consumeQrToken(String qrCodeToken) throws Exception {
                return resolveQrToken(qrCodeToken, true);
        }

        private Operator resolveQrToken(String qrCodeToken, boolean consume) throws Exception {
                if (isBlank(qrCodeToken)) {
                        throw new IllegalArgumentException("QR operatore non valido.");
                }

                // QR login is a public credential exchange: before validating the
                // bearer QR there is no authenticated tenant claim to scope by.
                // If a verified session exists, keep its tenant as an additional
                // constraint; otherwise the high-entropy QR itself identifies the
                // operator and its persisted tenant.
                String authenticatedTenant = SecurityContextAccessor.currentTenantId();
                Firestore db = FirestoreClient.getFirestore();

                Query query = db.collection(COLLECTION)
                                .whereEqualTo("qrCodeToken", qrCodeToken.trim());
                if (!isBlank(authenticatedTenant)) {
                        query = query.whereEqualTo("tenantId", authenticatedTenant);
                }
                QuerySnapshot matches = query.limit(2).get().get();

                if (matches.size() != 1) {
                        throw new IllegalArgumentException("QR operatore non valido.");
                }

                DocumentReference reference = matches.getDocuments().get(0).getReference();
                String tenantId = matches.getDocuments().get(0).getString("tenantId");
                if (isBlank(tenantId)) {
                        throw new IllegalArgumentException("QR operatore non valido.");
                }
                if (!isBlank(authenticatedTenant) && !authenticatedTenant.equals(tenantId)) {
                        throw new SecurityException("Operatore non appartenente al tenant autenticato.");
                }

                String now = Instant.now().toString();

                try {
                        return db.runTransaction(transaction -> {
                                DocumentSnapshot current = transaction.get(reference).get();
                                Operator operator = current.toObject(Operator.class);

                                if (operator == null || !tenantId.equals(operator.getTenantId())
                                                || !qrCodeToken.trim().equals(operator.getQrCodeToken())) {
                                        throw new IllegalArgumentException("QR operatore non valido.");
                                }

                                if (!"ATTIVO".equalsIgnoreCase(operator.getStatus())) {
                                        throw new IllegalArgumentException("L'account operatore non è attivo.");
                                }

                                if (!isBlank(operator.getQrUsedAt()) || !isBlank(operator.getQrLastUsedAt())) {
                                        throw new IllegalArgumentException(
                                                        "QR già utilizzato. Richiedi un nuovo badge.");
                                }

                                if (!isBlank(operator.getQrExpiresAt())) {
                                        try {
                                                if (Instant.parse(operator.getQrExpiresAt()).isBefore(Instant.now())) {
                                                        throw new IllegalArgumentException(
                                                                        "QR scaduto. Chiedi al capo di rigenerare il badge.");
                                                }
                                        } catch (java.time.format.DateTimeParseException malformed) {
                                                throw new IllegalArgumentException(
                                                                "Scadenza del QR non valida. Chiedi al capo di rigenerare il badge.");
                                        }
                                }

                                if (consume) {
                                        transaction.update(reference,
                                                        "qrLastUsedAt", now, "qrUsedAt", now,
                                                        "lastSeen", now, "updatedAt", now);
                                        operator.setQrUsedAt(now);
                                        operator.setQrLastUsedAt(now);
                                }
                                operator.setId(reference.getId());
                                ensureFcmTokens(operator);
                                return operator;
                        }).get();
                } catch (ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        if (cause instanceof IllegalArgumentException illegalArgumentException) {
                                throw illegalArgumentException;
                        }
                        if (cause instanceof SecurityException securityException) {
                                throw securityException;
                        }
                        throw exception;
                }

        }

        private String qrExpiry() {
                return Instant.now().plus(30, ChronoUnit.DAYS).toString();
        }

        private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

        /**
         * Password temporanea di 16 caratteri da SecureRandom, con tutte le classi
         * richieste.
         */
        private String generateTemporaryPassword() {
                final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
                final String lower = "abcdefghijkmnopqrstuvwxyz";
                final String digits = "23456789";
                final String symbols = "!@#$%*?-_";
                final String all = upper + lower + digits + symbols;

                List<Character> chars = new ArrayList<>();
                chars.add(upper.charAt(RANDOM.nextInt(upper.length())));
                chars.add(lower.charAt(RANDOM.nextInt(lower.length())));
                chars.add(digits.charAt(RANDOM.nextInt(digits.length())));
                chars.add(symbols.charAt(RANDOM.nextInt(symbols.length())));
                while (chars.size() < 16) {
                        chars.add(all.charAt(RANDOM.nextInt(all.length())));
                }
                java.util.Collections.shuffle(chars, RANDOM);

                StringBuilder password = new StringBuilder(chars.size());
                for (char c : chars) {
                        password.append(c);
                }
                return password.toString();
        }

        /**
         * Blocca il riuso di account che hanno gia' un ruolo diverso da operatore (es.
         * CAPO/ADMIN).
         */
        private void assertAccountReusableAsOperator(UserRecord user) {
                java.util.Map<String, Object> claims = user.getCustomClaims();
                if (claims == null || claims.isEmpty()) {
                        return;
                }
                Object role = claims.get("role");
                String roleValue = role == null ? "" : String.valueOf(role).trim();
                boolean operatorRole = roleValue.isEmpty()
                                || "OPERATOR".equalsIgnoreCase(roleValue)
                                || "OPERATORE".equalsIgnoreCase(roleValue);

                if (Boolean.TRUE.equals(claims.get("admin")) || !operatorRole) {
                        throw new IllegalArgumentException(
                                        "Esiste gia' un account con questa email e un ruolo diverso da operatore: usa un'altra email.");
                }
        }

        /**
         * Approva un operatore auto-registrato: assegna il claim OPERATOR e lo porta ad
         * ATTIVO.
         */
        public Operator approveOperator(String id) throws Exception {
                Operator operator = getById(id);

                if (isBlank(operator.getFirebaseUid())) {
                        throw new IllegalArgumentException("L'operatore non ha un account Firebase collegato.");
                }
                if (isBlank(operator.getTenantId())) {
                        throw new IllegalStateException(
                                        "Tenant operatore mancante: completare la migrazione prima dell'approvazione.");
                }

                UserRecord user = FirebaseAuth.getInstance().getUser(operator.getFirebaseUid());
                assertAccountReusableAsOperator(user);
                assertTenantClaimMatches(user, operator.getTenantId());

                FirestoreClient.getFirestore().collection(COLLECTION).document(id)
                                .update("status", "ATTIVO", "updatedAt", Instant.now().toString())
                                .get();

                setOperatorClaims(user, operator);

                return getById(id);
        }

        public Operator updateOperatorRole(String id, String requestedRole) throws Exception {
                String role = requestedRole == null ? "" : requestedRole.trim().toUpperCase(Locale.ROOT);
                if (!"OPERATOR".equals(role) && !"VIEWER".equals(role)) {
                        throw new IllegalArgumentException("Ruolo operatore non valido.");
                }

                Operator operator = getById(id);
                if (isBlank(operator.getFirebaseUid())) {
                        throw new IllegalStateException("Collega prima un account Firebase all'operatore.");
                }

                UserRecord user = FirebaseAuth.getInstance().getUser(operator.getFirebaseUid());
                Map<String, Object> currentClaims = user.getCustomClaims();
                if (currentClaims != null && (Boolean.TRUE.equals(currentClaims.get("admin"))
                                || isPrivilegedRole(currentClaims.get("role")))) {
                        throw new SecurityException("Non è possibile modificare il ruolo di un account amministrativo.");
                }
                assertTenantClaimMatches(user, operator.getTenantId());

                operator.setRole(role);
                Operator updated = updateOperator(id, operator);
                setOperatorClaims(user, updated);
                return updated;
        }

        private boolean isPrivilegedRole(Object roleClaim) {
                if (roleClaim == null) {
                        return false;
                }
                String role = String.valueOf(roleClaim).trim().toUpperCase(Locale.ROOT);
                return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role)
                                || "CHIEF_EXECUTIVE".equals(role) || "CAPO".equals(role)
                                || "NETWORK_MANAGER".equals(role);
        }

        private void setOperatorClaims(UserRecord user, Operator operator) throws Exception {
                if (isBlank(operator.getTenantId())) {
                        throw new IllegalStateException("Tenant operatore obbligatorio per assegnare i claim.");
                }
                assertTenantClaimMatches(user, operator.getTenantId());
                java.util.Map<String, Object> claims = new java.util.HashMap<>();
                if (user.getCustomClaims() != null) {
                        claims.putAll(user.getCustomClaims());
                }
                String role = operator.getRole() == null || operator.getRole().isBlank()
                                ? "OPERATOR"
                                : operator.getRole().trim().toUpperCase(Locale.ROOT);
                if (!"OPERATOR".equals(role) && !"VIEWER".equals(role)) {
                        throw new IllegalArgumentException("Ruolo operatore non valido.");
                }
                claims.put("role", role);
                claims.put("tenantId", operator.getTenantId());
                claims.put("operatorId", operator.getId());
                FirebaseAuth.getInstance().setCustomUserClaims(user.getUid(), claims);
        }

        private void assertTenantClaimMatches(UserRecord user, String tenantId) {
                if (user == null || isBlank(tenantId) || user.getCustomClaims() == null) {
                        return;
                }
                Object existingTenant = user.getCustomClaims().get("tenantId");
                if (existingTenant instanceof String value && !value.isBlank()
                                && !tenantId.equals(value.trim())) {
                        throw new SecurityException("Account Firebase già assegnato a un tenant diverso.");
                }
        }

        private void validatePassword(String password) {
                if (password == null || password.length() < 8
                                || !password.matches(".*[A-Z].*")
                                || !password.matches(".*[a-z].*")
                                || !password.matches(".*\\d.*")
                                || !password.matches(".*[^A-Za-z0-9].*")) {
                        throw new IllegalArgumentException(
                                        "La password deve contenere almeno 8 caratteri, maiuscola, minuscola, numero e simbolo.");
                }
        }

        public Operator updateOperator(
                        String id,
                        Operator operator)
                        throws ExecutionException, InterruptedException {

                validateId(id);
                validate(operator);

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION).document(id);

                DocumentSnapshot existing = document.get().get();

                if (!existing.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + id);
                }
                requireDocumentTenant(existing);
                TenantAccessPolicy.requireTenantAccess(existing.getString("tenantId"), operator.getTenantId());

                Operator existingOperator = existing.toObject(Operator.class);

                if (existingOperator == null) {
                        throw new IllegalStateException(
                                        "Operatore esistente non valido.");
                }

                operator.setId(id);
                operator.setTenantId(existing.getString("tenantId"));

                operator.setQrCodeToken(
                                existingOperator.getQrCodeToken());
                operator.setQrExpiresAt(existingOperator.getQrExpiresAt());
                operator.setQrUsedAt(existingOperator.getQrUsedAt());
                operator.setQrLastUsedAt(existingOperator.getQrLastUsedAt());

                if (operator.getFcmTokens() == null) {
                        operator.setFcmTokens(
                                        existingOperator.getFcmTokens() == null
                                                        ? new ArrayList<>()
                                                        : new ArrayList<>(
                                                                        existingOperator.getFcmTokens()));
                }

                if (isBlank(operator.getFirebaseUid())) {
                        operator.setFirebaseUid(
                                        existingOperator.getFirebaseUid());
                } else {
                        operator.setFirebaseUid(
                                        operator.getFirebaseUid().trim());
                }

                if (operator.getLastSeen() == null) {
                        operator.setLastSeen(
                                        existingOperator.getLastSeen());
                }

                operator.setCreatedAt(
                                existingOperator.getCreatedAt());

                operator.setUpdatedAt(
                                Instant.now().toString());

                document.set(operator).get();

                return operator;
        }

        public void deleteOperator(String id)
                        throws ExecutionException, InterruptedException {

                validateId(id);

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION).document(id);

                DocumentSnapshot existing = document.get().get();

                if (!existing.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + id);
                }
                requireDocumentTenant(existing);

                document.delete().get();
        }

        /**
         * Collega un Firebase UID ad un operatore.
         *
         * L'UID deve essere già verificato da AuthService.
         */
        public Operator assignFirebaseUid(
                        String operatorId,
                        String firebaseUid) throws Exception {

                validateId(operatorId);

                if (isBlank(firebaseUid)) {
                        throw new IllegalArgumentException(
                                        "Firebase UID obbligatorio.");
                }

                String uid = firebaseUid.trim();

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference operatorDocument = db.collection(COLLECTION)
                                .document(operatorId.trim());

                DocumentSnapshot operatorSnapshot = operatorDocument.get().get();

                if (!operatorSnapshot.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + operatorId);
                }
                requireDocumentTenant(operatorSnapshot);
                Operator operator = operatorSnapshot.toObject(Operator.class);
                if (operator == null || isBlank(operator.getTenantId())) {
                        throw new IllegalStateException("Tenant operatore obbligatorio per collegare Firebase UID.");
                }

                /*
                 * Controlliamo che lo stesso Firebase UID
                 * non sia già collegato ad un altro operatore.
                 */
                ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                                .whereEqualTo("firebaseUid", uid)
                                .limit(2)
                                .get();

                List<QueryDocumentSnapshot> documents = future.get().getDocuments();

                for (QueryDocumentSnapshot document : documents) {

                        if (!document.getId().equals(operatorId.trim())) {
                                throw new IllegalArgumentException(
                                                "Il Firebase UID è già associato ad un altro operatore.");
                        }
                }

                UserRecord linkedUser = FirebaseAuth.getInstance().getUser(uid);
                assertAccountReusableAsOperator(linkedUser);
                assertTenantClaimMatches(linkedUser, operator.getTenantId());

                String now = Instant.now().toString();

                operatorDocument.update(
                                "firebaseUid",
                                uid,
                                "updatedAt",
                                now).get();

                if ("ATTIVO".equalsIgnoreCase(operator.getStatus())) {
                        operator.setId(operatorSnapshot.getId());
                        operator.setFirebaseUid(uid);
                        setOperatorClaims(linkedUser, operator);
                }

                return getById(operatorId.trim());
        }

        /**
         * Rimuove il collegamento Firebase dall'operatore.
         */
        public Operator unassignFirebaseUid(
                        String operatorId) throws Exception {

                validateId(operatorId);

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document(operatorId.trim());

                DocumentSnapshot snapshot = document.get().get();

                if (!snapshot.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + operatorId);
                }
                requireDocumentTenant(snapshot);

                String firebaseUid = snapshot.getString("firebaseUid");
                if (!isBlank(firebaseUid)) {
                        UserRecord user = FirebaseAuth.getInstance().getUser(firebaseUid);
                        java.util.Map<String, Object> claims = new java.util.HashMap<>();
                        if (user.getCustomClaims() != null) {
                                claims.putAll(user.getCustomClaims());
                        }
                        claims.remove("operatorId");
                        claims.remove("tenantId");
                        if ("OPERATOR".equals(String.valueOf(claims.get("role")))) {
                                claims.remove("role");
                        }
                        FirebaseAuth.getInstance().setCustomUserClaims(firebaseUid, claims);
                }

                String now = Instant.now().toString();

                document.update(
                                "firebaseUid",
                                null,
                                "updatedAt",
                                now).get();

                return getById(operatorId.trim());
        }

        public void addFcmToken(
                        String operatorId,
                        String fcmToken) throws Exception {

                validateId(operatorId);

                if (isBlank(fcmToken)) {
                        throw new IllegalArgumentException(
                                        "FCM token obbligatorio.");
                }

                String tenantId = currentTenant();

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document(operatorId);

                DocumentSnapshot snapshot = document.get().get();

                if (!snapshot.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + operatorId);
                }
                requireDocumentTenant(snapshot, tenantId);

                List<String> tokens = readFcmTokens(snapshot);

                String token = fcmToken.trim();

                if (!tokens.contains(token)) {
                        tokens.add(token);
                }

                document.update(
                                "fcmTokens",
                                tokens,
                                "updatedAt",
                                Instant.now().toString()).get();
        }

        public void removeFcmToken(
                        String operatorId,
                        String fcmToken) throws Exception {

                validateId(operatorId);

                if (isBlank(fcmToken)) {
                        throw new IllegalArgumentException(
                                        "FCM token obbligatorio.");
                }

                String tenantId = currentTenant();

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document(operatorId);

                DocumentSnapshot snapshot = document.get().get();

                if (!snapshot.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + operatorId);
                }
                requireDocumentTenant(snapshot, tenantId);

                List<String> tokens = readFcmTokens(snapshot);

                tokens.remove(fcmToken.trim());

                document.update(
                                "fcmTokens",
                                tokens,
                                "updatedAt",
                                Instant.now().toString()).get();
        }

        public void updateFcmTokens(
                        String operatorId,
                        List<String> fcmTokens) throws Exception {

                validateId(operatorId);

                String tenantId = currentTenant();

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document(operatorId.trim());

                DocumentSnapshot snapshot = document.get().get();

                if (!snapshot.exists()) {
                        throw new IllegalArgumentException(
                                        "Operatore non trovato: " + operatorId);
                }
                requireDocumentTenant(snapshot, tenantId);

                List<String> normalizedTokens = new ArrayList<>();

                if (fcmTokens != null) {

                        for (String token : fcmTokens) {

                                if (token == null || token.isBlank()) {
                                        continue;
                                }

                                String normalized = token.trim();

                                if (!normalizedTokens.contains(normalized)) {
                                        normalizedTokens.add(normalized);
                                }
                        }
                }

                document.update(
                                "fcmTokens",
                                normalizedTokens,
                                "updatedAt",
                                Instant.now().toString()).get();
        }

        public void updateLastSeen(
                        String operatorId) throws Exception {

                updateLastSeen(operatorId, currentTenant());
        }

        public void updateLastSeen(String operatorId, String tenantId) throws Exception {

                validateId(operatorId);
                if (isBlank(tenantId)) {
                        throw new SecurityException("Tenant autenticato obbligatorio.");
                }

                Firestore db = FirestoreClient.getFirestore();

                DocumentReference document = db.collection(COLLECTION)
                                .document(operatorId.trim());
                DocumentSnapshot snapshot = document.get().get();
                if (!snapshot.exists()) {
                        throw new IllegalArgumentException("Operatore non trovato: " + operatorId);
                }
                requireDocumentTenant(snapshot, tenantId);

                String now = Instant.now().toString();

                document.update("lastSeen", now, "updatedAt", now).get();
        }

        public Operator findByQrToken(
                        String qrCodeToken) throws Exception {

                if (isBlank(qrCodeToken)) {
                        throw new IllegalArgumentException(
                                        "QR token obbligatorio.");
                }

                Firestore db = FirestoreClient.getFirestore();

                ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                                .whereEqualTo("tenantId", currentTenant())
                                .whereEqualTo(
                                                "qrCodeToken",
                                                qrCodeToken.trim())
                                .limit(1)
                                .get();

                List<QueryDocumentSnapshot> documents = future.get().getDocuments();

                if (documents.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "QR operatore non valido.");
                }

                QueryDocumentSnapshot document = documents.get(0);

                Operator operator = document.toObject(Operator.class);

                if (operator == null) {
                        throw new IllegalStateException(
                                        "Operatore non valido.");
                }

                operator.setId(document.getId());

                ensureFcmTokens(operator);

                return operator;
        }

        public long countOperators() throws Exception {

                Firestore db = FirestoreClient.getFirestore();

                return db.collection(COLLECTION)
                                .whereEqualTo("tenantId", currentTenant())
                                .get()
                                .get()
                                .size();
        }

        public Operator getByFirebaseUid(
                        String firebaseUid) throws Exception {

                if (isBlank(firebaseUid)) {
                        throw new IllegalArgumentException(
                                        "Firebase UID non valido.");
                }

                Firestore db = FirestoreClient.getFirestore();

                ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                                .whereEqualTo(
                                                "firebaseUid",
                                                firebaseUid.trim())
                                .limit(1)
                                .get();

                List<QueryDocumentSnapshot> documents = future.get().getDocuments();

                if (documents.isEmpty()) {
                        return null;
                }

                QueryDocumentSnapshot document = documents.get(0);
                String authenticatedUid = SecurityContextAccessor.currentUid();
                if (authenticatedUid != null && !authenticatedUid.equals(firebaseUid.trim())) {
                        throw new SecurityException("Operatore non appartenente all'identita' autenticata.");
                }
                requireDocumentTenant(document);

                Operator operator = document.toObject(Operator.class);

                if (operator == null) {
                        return null;
                }

                operator.setId(document.getId());

                ensureFcmTokens(operator);

                return operator;
        }

        /**
         * Migrazione degli operatori esistenti.
         *
         * Non inventa Firebase UID.
         * Normalizza soltanto i documenti già presenti.
         */
        public int migrateOperatorsToFirebaseUid()
                        throws Exception {

                Firestore db = FirestoreClient.getFirestore();

                List<QueryDocumentSnapshot> documents = db.collection(COLLECTION)
                                .get()
                                .get()
                                .getDocuments();

                int updated = 0;

                for (QueryDocumentSnapshot document : documents) {

                        Operator operator = document.toObject(Operator.class);

                        if (operator == null) {
                                continue;
                        }

                        boolean needsUpdate = false;

                        if (operator.getFcmTokens() == null) {
                                operator.setFcmTokens(new ArrayList<>());
                                needsUpdate = true;
                        }

                        if (operator.getFirebaseUid() != null) {

                                String uid = operator.getFirebaseUid().trim();

                                if (!uid.equals(
                                                operator.getFirebaseUid())) {

                                        operator.setFirebaseUid(uid);
                                        needsUpdate = true;
                                }
                        }

                        if (needsUpdate) {

                                document.getReference()
                                                .update(
                                                                "firebaseUid",
                                                                operator.getFirebaseUid(),
                                                                "fcmTokens",
                                                                operator.getFcmTokens(),
                                                                "updatedAt",
                                                                Instant.now().toString())
                                                .get();

                                updated++;
                        }
                }

                return updated;
        }

        private void validateId(String id) {

                if (isBlank(id)) {
                        throw new IllegalArgumentException(
                                        "ID operatore non valido.");
                }
        }

        private void validate(Operator operator) {

                if (operator == null) {
                        throw new IllegalArgumentException(
                                        "Operatore obbligatorio.");
                }

                if (isBlank(operator.getFullName())) {
                        throw new IllegalArgumentException(
                                        "Nome operatore obbligatorio.");
                }

                if (isBlank(operator.getEmail())) {
                        throw new IllegalArgumentException(
                                        "Email operatore obbligatoria.");
                }

                if (isBlank(operator.getLevel())) {
                        throw new IllegalArgumentException(
                                        "Livello operatore obbligatorio.");
                }

                if (isBlank(operator.getStatus())) {
                        operator.setStatus("ATTIVO");
                }

                ensureFcmTokens(operator);
        }

        private void ensureFcmTokens(Operator operator) {

                if (operator.getFcmTokens() == null) {
                        operator.setFcmTokens(new ArrayList<>());
                }
        }

        @SuppressWarnings("unchecked")
        private List<String> readFcmTokens(
                        DocumentSnapshot snapshot) {

                Object value = snapshot.get("fcmTokens");

                if (value == null) {
                        return new ArrayList<>();
                }

                if (!(value instanceof List<?>)) {
                        return new ArrayList<>();
                }

                List<String> tokens = new ArrayList<>();

                for (Object item : (List<?>) value) {

                        if (item != null) {

                                String token = item.toString().trim();

                                if (!token.isEmpty()) {
                                        tokens.add(token);
                                }
                        }
                }

                return tokens;
        }

        private String currentTenant() {
                return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null);
        }

        private void requireDocumentTenant(DocumentSnapshot document) {
                String authenticatedTenant = SecurityContextAccessor.currentTenantId();
                if (authenticatedTenant != null && !authenticatedTenant.isBlank()
                                && !TenantAccessPolicy.canAccessTenant(authenticatedTenant,
                                                document.getString("tenantId"))) {
                        throw new SecurityException("Operatore non appartenente al tenant autenticato.");
                }
        }

        private void requireDocumentTenant(DocumentSnapshot document, String tenantId) {
                if (!TenantAccessPolicy.canAccessTenant(tenantId, document.getString("tenantId"))) {
                        throw new SecurityException("Operatore non appartenente al tenant autenticato.");
                }
        }

        private boolean isBlank(String value) {

                return value == null ||
                                value.isBlank();
        }

        private String normalizeNullable(String value) {

                if (isBlank(value)) {
                        return null;
                }

                return value.trim();
        }
}
