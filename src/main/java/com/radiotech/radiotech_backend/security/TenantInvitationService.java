package com.radiotech.radiotech_backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

@Service
public class TenantInvitationService {

    private static final long MAX_TTL_SECONDS = 7 * 24 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String secret;
    private final Clock clock;

    @Autowired
    public TenantInvitationService(@Value("${radiotech.tenant.invite-secret:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    public TenantInvitationService(String secret, Clock clock) {
        this.secret = secret == null ? "" : secret;
        this.clock = clock;
    }

    public String issue(String tenantId, long ttlSeconds) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Tenant non valido.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("Secret per gli inviti tenant non configurato o troppo corto.");
        }
        if (ttlSeconds < 60 || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("Durata invito non valida.");
        }

        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = tenantId + "|" + expiresAt + "|"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public String verify(String invitation) {
        if (secret.length() < 32 || invitation == null) {
            throw new SecurityException("Invito tenant non valido.");
        }
        String[] parts = invitation.split("\\.", -1);
        if (parts.length != 2 || !MessageDigest.isEqual(
                sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Invito tenant non valido.");
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", -1);
            long expiresAt = Long.parseLong(fields[1]);
            if (fields.length != 3 || !fields[0].matches("[A-Za-z0-9_-]{1,64}")
                    || fields[2].isBlank() || expiresAt <= clock.instant().getEpochSecond()) {
                throw new SecurityException("Invito tenant scaduto o non valido.");
            }
            return fields[0];
        } catch (SecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SecurityException("Invito tenant non valido.");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Impossibile firmare l'invito tenant.", exception);
        }
    }
}