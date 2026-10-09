package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.MaintenanceReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaintenanceReportRemovalControllerTest {
    private final MaintenanceReportService service = mock(MaintenanceReportService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MaintenanceReportController(service)).build();

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private void authenticate(String role) {
        var auth = new UsernamePasswordAuthenticationToken("manager", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new FirebaseAuthenticationDetails("manager", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test void bothApiRoutesExposeAuthorizedLogicalRemoval() throws Exception {
        authenticate("ADMIN");
        for (String prefix : List.of("/api/reports", "/api/v1/reports")) {
            mvc.perform(delete(prefix + "/report-1").requestAttr("firebaseUid", "manager")
                    .header("X-Tenant-Id", "tenant-a"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.removed").value(true)).andExpect(jsonPath("$.id").value("report-1"));
        }
        verify(service, times(2)).remove("report-1", "manager");
    }

    @Test void readOnlyRoleCannotReachRemovalService() throws Exception {
        authenticate("VIEWER");
        mvc.perform(delete("/api/reports/report-1").requestAttr("firebaseUid", "manager"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    @Test void tenantHeaderCannotOverrideAuthenticatedTenant() throws Exception {
        authenticate("SUPER_ADMIN");
        mvc.perform(delete("/api/reports/report-1").requestAttr("firebaseUid", "manager")
                .header("X-Tenant-Id", "tenant-b"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
