package com.radiotech.radiotech_backend;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.radiotech.radiotech_backend.controller.HealthController;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationFilter;
import com.radiotech.radiotech_backend.security.RateLimitFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiVersioningTest {

    @Test
    void healthEndpointIsAvailableOnVersionedApi() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void versionedHealthAndBootstrapRoutesBypassFirebaseAuthentication() throws Exception {
        for (String[] route : new String[][] {
                { "POST", "/api/v1/auth/login" },
                { "POST", "/api/v1/auth/refresh" },
                { "POST", "/api/v1/auth/verify" },
                { "POST", "/api/v1/auth/qr-login" },
                { "GET", "/api/v1/health" },
                { "GET", "/api/v1/health/firebase" },
                { "GET", "/api/v1/reports/verify/opaque.signed.token" },
                { "POST", "/api/v1/bootstrap/capo" },
                { "GET", "/actuator/health/readiness" },
                { "GET", "/actuator/health/liveness" }
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest(route[0], route[1]);
            MockFilterChain chain = new MockFilterChain();

            new FirebaseAuthenticationFilter().doFilter(request, new MockHttpServletResponse(), chain);

            assertNotNull(chain.getRequest(), route[1] + " should be public");
        }
    }

    @Test
    void versionedAuthenticationRoutesAreRateLimited() throws Exception {
        for (String path : new String[] {
                "/api/v1/auth/login",
                "/api/v1/auth/qr-login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/verify",
                "/api/v1/bootstrap/capo"
        }) {
            RateLimitFilter filter = new RateLimitFilter(1);
            MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", path);
            MockFilterChain firstChain = new MockFilterChain();
            filter.doFilter(firstRequest, new MockHttpServletResponse(), firstChain);
            assertNotNull(firstChain.getRequest(), path + " should allow the first request");

            MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse secondResponse = new MockHttpServletResponse();
            MockFilterChain secondChain = new MockFilterChain();
            filter.doFilter(secondRequest, secondResponse, secondChain);

            assertEquals(429, secondResponse.getStatus(), path + " should rate limit the retry");
            assertNull(secondChain.getRequest(), path + " should stop the limited request");
        }
    }

    @Test
    void publicReportVerificationLinksAreRateLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1);
        String path = "/api/v1/reports/verify/opaque.signed.token";
        filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), limited, new MockFilterChain());
        assertEquals(429, limited.getStatus());
    }

    @Test
    void firebaseAuthenticationFilterPassesCorsPreflightToSecurityCorsHandling() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/operator/tasks");
        MockFilterChain chain = new MockFilterChain();

        new FirebaseAuthenticationFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void downstreamServletErrorsAreNotRemappedAsInvalidTokens() throws Exception {
        try (var firebaseAuthStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            FirebaseToken decodedToken = mock(FirebaseToken.class);
            firebaseAuthStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.verifyIdToken("valid-token", true)).thenReturn(decodedToken);
            when(decodedToken.getUid()).thenReturn("operator-uid");
            when(decodedToken.getClaims()).thenReturn(Map.of("role", "OPERATOR", "tenantId", "tenant-a"));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/operator/me");
            request.addHeader("Authorization", "Bearer valid-token");
            FilterChain failingChain = (servletRequest, servletResponse) -> {
                throw new ServletException("controller failure");
            };

            assertThrows(ServletException.class, () -> new FirebaseAuthenticationFilter().doFilter(
                    request, new MockHttpServletResponse(), failingChain));
        }
    }
}
