package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.service.MaintenanceReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.firebase.enabled=false",
        "radiotech.reports.verification-secret=0123456789abcdef0123456789abcdef",
        "radiotech.reports.public-base-url=https://reports.example.test",
        "app.firebase.storage-bucket=radio.appspot.com"
})
class ReportVerificationHttpSecurityTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private MaintenanceReportService reportService;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain).build();
    }

    @Test
    void signedReportVerificationRouteIsPublicAndReturnsOnlyVerificationMetadata() throws Exception {
        when(reportService.verifyPublicToken("signed-token")).thenReturn(Map.of(
                "valid", true, "reportId", "report-1", "submittedAt", "2026-10-02T00:00:00Z", "integrity", "VALID"));

        mockMvc.perform(get("/api/v1/reports/verify/signed-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.reportId").value("report-1"))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.operatorFirebaseUid").doesNotExist())
                .andExpect(jsonPath("$.attachments").doesNotExist());
    }

    @Test
    void ordinaryReportListingStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isUnauthorized());
    }
}
