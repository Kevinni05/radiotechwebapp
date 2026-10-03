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

class AuthControllerFirebaseUserTenantTest {

    private final AuthService authService = mock(AuthService.class);
    private final OperatorService operatorService = mock(OperatorService.class);
    private final AuthController controller = new AuthController(authService, operatorService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crossTenantFirebaseUserCannotBeReadOrDeleted() throws Exception {
        authenticate("tenant-a");
        when(operatorService.getByFirebaseUid("uid-b"))
                .thenThrow(new SecurityException("Tenant non autorizzato."));

        assertEquals(HttpStatus.NOT_FOUND, controller.getFirebaseUser("uid-b").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteFirebaseUser("uid-b").getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void sameTenantFirebaseUserCanBeManaged() throws Exception {
        authenticate("tenant-a");
        when(operatorService.getByFirebaseUid("uid-a")).thenReturn(new Operator());
        when(authService.getFirebaseUser("uid-a")).thenReturn(Map.of("uid", "uid-a"));

        assertEquals(HttpStatus.OK, controller.getFirebaseUser("uid-a").getStatusCode());
        assertEquals(HttpStatus.OK, controller.deleteFirebaseUser("uid-a").getStatusCode());
        verify(authService).getFirebaseUser("uid-a");
        verify(authService).deleteFirebaseUser("uid-a");
    }

    private void authenticate(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin-a", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "admin-a", "admin@example.test", "Admin", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}