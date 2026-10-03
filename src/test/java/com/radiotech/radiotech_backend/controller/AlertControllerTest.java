package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.AlertEngineService;
import com.radiotech.radiotech_backend.service.ManutenzioneService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAlertsReturnsTenantScopedPayload() throws Exception {
        ManutenzioneService service = mock(ManutenzioneService.class);
        when(service.getAllAlerts()).thenReturn(List.of(Map.of(
                "id", "alert-1",
                "tenantId", "tenant-a",
                "descrizione", "Cablaggio intermittente",
                "priorita", "CRITICA",
                "letto", false)));

        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_NETWORK_MANAGER")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(service, mock(AlertEngineService.class))).build();

        mockMvc.perform(get("/api/v1/alerts").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("alert-1"))
                .andExpect(jsonPath("$[0].priorita").value("CRITICA"));
    }

    @Test
    void unreadCountEndpointReturnsCurrentCounter() throws Exception {
        ManutenzioneService service = mock(ManutenzioneService.class);
        when(service.countUnreadAlerts()).thenReturn(3L);

        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_NETWORK_MANAGER")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(service, mock(AlertEngineService.class))).build();

        mockMvc.perform(get("/api/v1/alerts/unread-count").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void markAlertAsReadReturnsUpdatedState() throws Exception {
        ManutenzioneService service = mock(ManutenzioneService.class);
        when(service.markAlertAsRead("alert-1")).thenReturn(Map.of(
                "id", "alert-1",
                "tenantId", "tenant-a",
                "letto", true));

        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_NETWORK_MANAGER")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(service, mock(AlertEngineService.class))).build();

        mockMvc.perform(post("/api/v1/alerts/alert-1/read").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("alert-1"))
                .andExpect(jsonPath("$.letto").value(true));
    }

    @Test
    void evaluateEndpointIsAvailableUnderBothApiVersionsToAuthorizedManagers() throws Exception {
        ManutenzioneService service = mock(ManutenzioneService.class);
        AlertEngineService engine = mock(AlertEngineService.class);
        when(engine.evaluate()).thenReturn(Map.of("active", 2, "created", 2, "updated", 0, "resolved", 0));
        authenticateAsManager();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(service, engine)).build();

        mockMvc.perform(post("/api/alerts/evaluate").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));
        mockMvc.perform(post("/api/v1/alerts/evaluate").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(2));
    }

    @Test
    void operatorCannotEvaluateAlerts() throws Exception {
        ManutenzioneService service = mock(ManutenzioneService.class);
        AlertEngineService engine = mock(AlertEngineService.class);
        var authentication = new UsernamePasswordAuthenticationToken(
                "operator-a", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "operator-a", "operator@example.test", "Operator", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(service, engine)).build();

        mockMvc.perform(post("/api/v1/alerts/evaluate").header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isForbidden());
    }

    private void authenticateAsManager() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_NETWORK_MANAGER")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
