package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.AntennaService;
import com.radiotech.radiotech_backend.service.AuditService;
import com.radiotech.radiotech_backend.service.CapoService;
import com.radiotech.radiotech_backend.service.DashboardService;
import com.radiotech.radiotech_backend.service.NotificationService;
import com.radiotech.radiotech_backend.service.RicambioService;
import com.radiotech.radiotech_backend.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class DashboardControllerErrorHandlingTest {
    private final DashboardService dashboardService = mock(DashboardService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final CapoService capoService = mock(CapoService.class);
    private final AntennaService antennaService = mock(AntennaService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final RicambioService ricambioService = mock(RicambioService.class);
    private final DashboardController controller = new DashboardController(
            dashboardService, notificationService, capoService, antennaService,
            auditService, taskService, ricambioService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permissionFailureIsForbiddenInsteadOfInternalServerError() {
        authenticate("NONE");

        var response = controller.getStats("tenant-a");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void internalFailureKeepsProviderDetailsOutOfResponse() throws Exception {
        authenticate("ADMIN");
        when(dashboardService.getDashboardStats()).thenThrow(new IllegalStateException("private firestore detail"));

        var response = controller.getStats("tenant-a");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().toString().contains("private firestore detail"));
    }

    private void authenticate(String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "test-uid", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "test-uid", "test@example.test", "Test", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
