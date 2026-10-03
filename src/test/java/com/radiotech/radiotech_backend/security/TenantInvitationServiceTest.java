package com.radiotech.radiotech_backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantInvitationServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void invitationCarriesOnlyItsSignedTenant() {
        TenantInvitationService service = new TenantInvitationService(SECRET, CLOCK);
        String invitation = service.issue("tenant-a", 600);

        assertEquals("tenant-a", service.verify(invitation));
        assertThrows(SecurityException.class, () -> service.verify(invitation + "x"));
    }

    @Test
    void expiredInvitationIsRejected() {
        TenantInvitationService service = new TenantInvitationService(SECRET, CLOCK);
        String invitation = service.issue("tenant-a", 60);
        TenantInvitationService expired = new TenantInvitationService(
                SECRET, Clock.fixed(Instant.parse("2026-09-30T12:01:00Z"), ZoneOffset.UTC));

        assertThrows(SecurityException.class, () -> expired.verify(invitation));
    }
}