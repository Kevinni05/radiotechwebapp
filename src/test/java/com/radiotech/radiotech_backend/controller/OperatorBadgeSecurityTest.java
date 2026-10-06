package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.security.TenantInvitationService;
import com.radiotech.radiotech_backend.service.OperatorService;
import com.radiotech.radiotech_backend.service.QrCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatorBadgeSecurityTest {
    private final OperatorService service = mock(OperatorService.class);
    private final QrCodeService qr = mock(QrCodeService.class);
    private final OperatorController controller = new OperatorController(service, qr, mock(TenantInvitationService.class));

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void operatorSerializationNeverDisclosesLoginOrPushCredentials() {
        Operator operator = badgeOperator();
        operator.setFcmTokens(List.of("private-push-token"));
        var json = JsonMapper.builder().build().valueToTree(operator);
        assertFalse(json.has("qrCodeToken"));
        assertFalse(json.has("fcmTokens"));
        assertEquals("operator-a", json.get("id").asText());
    }

    @Test
    void readOnlyUsersCannotFetchBadgeOrGenerateQrImage() {
        authenticate("VIEWER");
        assertEquals(HttpStatus.FORBIDDEN, controller.badge("operator-a", null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.qrImage("operator-a", Map.of("qrCodeToken", "private-login-token"), null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.findByQrToken("private-login-token", null).getStatusCode());
        verifyNoInteractions(service, qr);
    }

    @Test
    void managerGetsBadgeOnlyThroughDedicatedEndpoint() throws Exception {
        authenticate("ADMIN");
        when(service.getById("operator-a")).thenReturn(badgeOperator());
        when(qr.toDataUri("private-login-token")).thenReturn("data:image/png;base64,test");
        var response = controller.badge("operator-a", null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("private-login-token"));
        verify(qr).toDataUri("private-login-token");
    }

    @Test
    void usedBadgeCannotBeReprinted() throws Exception {
        authenticate("ADMIN");
        Operator operator = badgeOperator();
        operator.setQrUsedAt("2026-01-01T00:00:00Z");
        when(service.getById("operator-a")).thenReturn(operator);
        assertEquals(HttpStatus.CONFLICT, controller.badge("operator-a", null).getStatusCode());
        verifyNoInteractions(qr);
    }

    private Operator badgeOperator() {
        Operator operator = new Operator();
        operator.setId("operator-a");
        operator.setTenantId("tenant-a");
        operator.setQrCodeToken("private-login-token");
        operator.setQrExpiresAt("2099-01-01T00:00:00Z");
        return operator;
    }

    private void authenticate(String role) {
        var authentication = new UsernamePasswordAuthenticationToken("user-a", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new FirebaseAuthenticationDetails("user-a", "user@example.test", "Test", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
