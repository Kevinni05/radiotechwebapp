package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.exception.GlobalExceptionHandler;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentControllerTest {

    private IncidentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(IncidentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new IncidentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidCreatePayloadReturnsStructuredBadRequest() throws Exception {
        authenticate("ADMIN", "tenant-a");

        mockMvc.perform(post("/api/v1/incidents")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType("application/json")
                        .content("{\"title\":\" \",\"severity\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void invalidTransitionPayloadReturnsStructuredBadRequest() throws Exception {
        authenticate("ADMIN", "tenant-a");

        mockMvc.perform(post("/api/v1/incidents/incident-1/transitions")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType("application/json")
                        .content("{\"status\":\"WRONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void operatorCanReadIncidentsButCannotCreateOrTransitionThem() throws Exception {
        authenticate("OPERATOR", "tenant-a");

        mockMvc.perform(get("/api/v1/incidents").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk());
        verify(service).getAll();

        mockMvc.perform(post("/api/incidents")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType("application/json")
                        .content("{\"title\":\"Power alarm\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(post("/api/v1/incidents/incident-1/transitions")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType("application/json")
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void viewerCannotReadIncidents() throws Exception {
        authenticate("VIEWER", "tenant-a");

        mockMvc.perform(get("/api/incidents").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(service);
    }

    @Test
    void managerCanCreateAndTransitionIncidents() throws Exception {
        authenticate("NETWORK_MANAGER", "tenant-a");

        mockMvc.perform(post("/api/incidents")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType("application/json")
                        .content("{\"title\":\"Fiber cut\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/incidents/incident-1/transitions")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType("application/json")
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk());

        verify(service).create("Fiber cut", null, "HIGH", null, null, null, null, "manager-a");
        verify(service).transition("incident-1", "ACKNOWLEDGED", "manager-a", null, null);
    }

    @Test
    void authenticatedTenantCannotBeOverriddenByRequestHeader() throws Exception {
        authenticate("ADMIN", "tenant-a");

        mockMvc.perform(get("/api/v1/incidents").header("X-Tenant-Id", "tenant-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(service);
    }

    private void authenticate(String role, String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
