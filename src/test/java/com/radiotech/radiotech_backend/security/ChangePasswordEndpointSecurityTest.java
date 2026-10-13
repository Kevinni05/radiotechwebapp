package com.radiotech.radiotech_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class ChangePasswordEndpointSecurityTest {
    private static final String[] PATHS = {"/api/auth/change-password", "/api/v1/auth/change-password"};

    @Test
    void bothAliasesRequireAuthentication() throws Exception {
        for (String path : PATHS) {
            var chain = new MockFilterChain();
            var response = new MockHttpServletResponse();
            new FirebaseAuthenticationFilter().doFilter(new MockHttpServletRequest("POST", path), response, chain);
            assertEquals(401, response.getStatus());
            assertNull(chain.getRequest());
        }
    }

    @Test
    void bothAliasesLimitPasswordAttempts() throws Exception {
        for (String path : PATHS) {
            var filter = new RateLimitFilter(1);
            filter.doFilter(new MockHttpServletRequest("POST", path), new MockHttpServletResponse(), new MockFilterChain());
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST", path), response, chain);
            assertEquals(429, response.getStatus());
            assertNotNull(response.getHeader("Retry-After"));
            assertNull(chain.getRequest());
        }
    }
}
