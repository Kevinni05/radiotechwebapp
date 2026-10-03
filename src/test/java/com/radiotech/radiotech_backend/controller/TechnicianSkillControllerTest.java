package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.exception.GlobalExceptionHandler;
import com.radiotech.radiotech_backend.model.TechnicianSkill;
import com.radiotech.radiotech_backend.model.TechnicianSkillType;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.TechnicianSkillService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TechnicianSkillControllerTest {

    private TechnicianSkillService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TechnicianSkillService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TechnicianSkillController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void versionedAndUnversionedRoutesShareTenantScopedSkillMatrix() throws Exception {
        authenticate("ADMIN", "tenant-a");
        when(service.list("tech-a")).thenReturn(List.of());
        when(service.supportedSkills("tech-a")).thenReturn(List.of(TechnicianSkillType.values()));
        when(service.upsert(org.mockito.ArgumentMatchers.eq("tech-a"),
                org.mockito.ArgumentMatchers.eq(TechnicianSkillType.FIVE_G),
                org.mockito.ArgumentMatchers.any())).thenReturn(new TechnicianSkill(
                        "tech-a", TechnicianSkillType.FIVE_G, 4, "5G-NR",
                        LocalDate.parse("2027-12-31"), true, "2026-10-01T00:00:00Z"));

        mockMvc.perform(get("/api/operators/tech-a/skills"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/operators/tech-a/skills/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$", contains(
                        "RF", "LTE", "5G", "FIBER", "IP", "MICROWAVE", "POWER", "HVAC", "SAFETY")));
        mockMvc.perform(put("/api/v1/operators/tech-a/skills/5G")
                        .contentType("application/json")
                        .content("""
                                {"level":4,"certification":"5G-NR","expiration":"2027-12-31","authorized":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill").value("5G"));
        mockMvc.perform(delete("/api/operators/tech-a/skills/RF"))
                .andExpect(status().isOk());

        verify(service).list("tech-a");
        verify(service).supportedSkills("tech-a");
        verify(service).upsert(org.mockito.ArgumentMatchers.eq("tech-a"),
                org.mockito.ArgumentMatchers.eq(TechnicianSkillType.FIVE_G),
                org.mockito.ArgumentMatchers.argThat(request -> request.getLevel() == 4
                        && "5G-NR".equals(request.getCertification())
                        && LocalDate.parse("2027-12-31").equals(request.getExpiration())
                        && Boolean.TRUE.equals(request.getAuthorized())));
        verify(service).delete("tech-a", TechnicianSkillType.RF);
    }

    @Test
    void invalidSkillPayloadIsRejectedBeforeServiceCall() throws Exception {
        authenticate("ADMIN", "tenant-a");

        mockMvc.perform(put("/api/v1/operators/tech-a/skills/RF")
                        .contentType("application/json")
                        .content("""
                                {"level":6,"certification":" ","expiration":null,"authorized":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    @Test
    void expiredCertificationCannotBeAuthorized() throws Exception {
        authenticate("ADMIN", "tenant-a");

        mockMvc.perform(put("/api/v1/operators/tech-a/skills/RF")
                        .contentType("application/json")
                        .content("""
                                {"level":3,"certification":"RF-L3","expiration":"2020-01-01","authorized":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    @Test
    void readerMayReadButCannotChangeSkills() throws Exception {
        authenticate("VIEWER", "tenant-a");
        when(service.list("tech-a")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/operators/tech-a/skills"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/operators/tech-a/skills/RF")
                        .contentType("application/json")
                        .content("""
                                {"level":2,"certification":"RF-L1","expiration":"2027-12-31","authorized":true}
                                """))
                .andExpect(status().isForbidden());

        verify(service).list("tech-a");
    }

    @Test
    void requestTenantHeaderCannotChangeAuthenticatedTenant() throws Exception {
        authenticate("ADMIN", "tenant-a");
        when(service.list("tech-a")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/operators/tech-a/skills").header("X-Tenant-Id", "tenant-b"))
                .andExpect(status().isOk());

        verify(service).list("tech-a");
    }

    private void authenticate(String role, String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "manager-a", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "manager-a", "manager@example.test", "Manager", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
