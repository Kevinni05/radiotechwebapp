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
}