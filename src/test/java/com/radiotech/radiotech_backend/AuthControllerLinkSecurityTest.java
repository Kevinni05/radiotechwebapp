package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.controller.AuthController;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.AuthService;
import com.radiotech.radiotech_backend.service.OperatorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthControllerLinkSecurityTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(authService, mock(OperatorService.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operatorCannotLinkFirebaseAccount() {
        authenticate("OPERATOR");

        assertEquals(HttpStatus.FORBIDDEN, controller.linkFirebaseUser(Map.of(
                "operatorId", "operator-a", "firebaseUid", "uid-b")).getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void accountAdminCanLinkFirebaseAccount() throws Exception {
        authenticate("ADMIN");
        when(authService.linkFirebaseUserToOperator("operator-a", "uid-b"))
                .thenReturn(new Operator());

        assertEquals(HttpStatus.OK, controller.linkFirebaseUser(Map.of(
                "operatorId", "operator-a", "firebaseUid", "uid-b")).getStatusCode());
        verify(authService).linkFirebaseUserToOperator("operator-a", "uid-b");
    }

    private void authenticate(String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "actor-uid", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "actor-uid", "actor@example.test", "Actor", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}