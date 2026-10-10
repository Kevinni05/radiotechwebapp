package com.radiotech.radiotech_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateLimitFilterCapacityTest {

    @Test
    void activeWindowKeysStayBoundedAndUnknownIpsAreRejectedAtCapacity() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 2);
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(request("192.0.2.1"), new MockHttpServletResponse(), firstChain);
        assertNotNull(firstChain.getRequest());

        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(request("192.0.2.2"), new MockHttpServletResponse(), secondChain);
        assertNotNull(secondChain.getRequest());

        MockHttpServletResponse overflowResponse = new MockHttpServletResponse();
        MockFilterChain overflowChain = new MockFilterChain();
        filter.doFilter(request("192.0.2.3"), overflowResponse, overflowChain);

        assertEquals(429, overflowResponse.getStatus());
        assertEquals("60", overflowResponse.getHeader("Retry-After"));
        assertNull(overflowChain.getRequest());
    }

    @Test
    void knownIpCanContinueWhenTheTableIsAtCapacity() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 1);
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(request("192.0.2.10"), new MockHttpServletResponse(), firstChain);

        MockFilterChain knownChain = new MockFilterChain();
        MockHttpServletResponse knownResponse = new MockHttpServletResponse();
        filter.doFilter(request("192.0.2.10"), knownResponse, knownChain);

        assertEquals(200, knownResponse.getStatus());
        assertNotNull(knownChain.getRequest());
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    @Test
    void changingReportReferenceOrApiVersionCannotBypassTheSameIpBudget() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 10);
        for (String path : new String[]{"/api/reports/verify/first", "/api/v1/reports/verify/second"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr("192.0.2.20");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reports/verify/third");
        request.setRemoteAddr("192.0.2.20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(429, response.getStatus());
    }

    @Test
    void versionedAndLegacyLoginShareTheSameBudget() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 10);
        filter.doFilter(request("192.0.2.21"), new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletRequest versioned = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        versioned.setRemoteAddr("192.0.2.21");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(versioned, response, new MockFilterChain());
        assertEquals(429, response.getStatus());
    }
}
