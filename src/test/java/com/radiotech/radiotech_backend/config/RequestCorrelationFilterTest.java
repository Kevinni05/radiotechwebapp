package com.radiotech.radiotech_backend.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationFilterTest {
    @Test void correlatesFailuresWithoutTrustingClientInputOrLeakingThreadContext() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "untrusted-caller-content");
        var response = new MockHttpServletResponse();
        assertThrows(IllegalStateException.class, () -> new RequestCorrelationFilter().doFilter(request, response, (req, res) -> {
            assertEquals(response.getHeader("X-Request-Id"), MDC.get("requestId"));
            assertNotEquals("untrusted-caller-content", MDC.get("requestId"));
            throw new IllegalStateException("failure");
        }));
        assertNull(MDC.get("requestId"));
        assertNotNull(response.getHeader("X-Request-Id"));
    }
}
