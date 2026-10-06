package com.radiotech.radiotech_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Generate server-side IDs; never trust arbitrary caller content in logs.
        String id = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", id);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", id)) {
            chain.doFilter(request, response);
        }
    }
}
