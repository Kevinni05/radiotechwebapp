package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.controller.CapoController;
import com.radiotech.radiotech_backend.model.CapoProfile;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.CapoService;
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
import static org.mockito.Mockito.when;

class CapoControllerSecurityTest {

    private final CapoService capoService = mock(CapoService.class);
    private final CapoController controller = new CapoController(capoService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operatorCannotPromoteOwnFirestoreProfile() throws Exception {
        authenticate("OPERATOR", "tenant-a");

        assertEquals(HttpStatus.FORBIDDEN,
                controller.updateProfile("operator-uid", Map.of("fullName", "Changed"))
                        .getStatusCode());
        verify(capoService, never()).getProfile("operator-uid");
        verify(capoService, never()).updateProfile(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void managerCannotReadCapoProfileFromAnotherTenant() throws Exception {
        authenticate("ADMIN", "tenant-b");
        CapoProfile profile = new CapoProfile();
        profile.setTenantId("tenant-a");
        when(capoService.getProfile("admin-uid")).thenReturn(profile);

        assertEquals(HttpStatus.FORBIDDEN, controller.getProfile("admin-uid").getStatusCode());
    }

    @Test
    void managerCanUpdateCapoProfileWithinItsTenant() throws Exception {
        authenticate("ADMIN", "tenant-a");
        CapoProfile profile = new CapoProfile();
        profile.setTenantId("tenant-a");
        when(capoService.getProfile("admin-uid")).thenReturn(profile);
        when(capoService.updateProfile("admin-uid", "Changed", null, null, null))
                .thenReturn(profile);

        assertEquals(HttpStatus.OK,
                controller.updateProfile("admin-uid", Map.of("fullName", "Changed")).getStatusCode());
        verify(capoService).updateProfile("admin-uid", "Changed", null, null, null);
    }

    private void authenticate(String role, String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                role.toLowerCase() + "-uid", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                authentication.getName(), "user@example.test", "Test User", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}