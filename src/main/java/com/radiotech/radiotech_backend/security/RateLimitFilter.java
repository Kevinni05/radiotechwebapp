package com.radiotech.radiotech_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita i tentativi sugli endpoint pubblici di autenticazione (login, QR
 * login,
 * refresh, bootstrap) per IP e per endpoint, a finestra fissa di un minuto.
 *
 * Limite noto: lo stato e' in memoria, quindi vale per singola istanza. Con
 * piu'
 * istanze va spostato su Redis (previsto in Fase 1). Dietro un reverse proxy
 * imposta server.forward-headers-strategy=framework, altrimenti l'IP visto e'
 * quello del proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

        private static final Set<String> LIMITED_PATHS = Set.of(
                        "/api/auth/login",
                        "/api/auth/qr-login",
                        "/api/auth/refresh",
                        "/api/auth/verify",
                        "/api/bootstrap/capo",
                        "/api/v1/auth/login",
                        "/api/v1/auth/qr-login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/verify",
                        "/api/v1/bootstrap/capo");

        private static final long WINDOW_MS = 60_000L;
        private static final int MAX_TRACKED_KEYS = 10_000;

        private final int maxRequests;
        private final int maxTrackedKeys;
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

        @Autowired
        public RateLimitFilter(@Value("${radiotech.security.rate-limit-per-minute:10}") int maxRequests) {
                this(maxRequests, MAX_TRACKED_KEYS);
        }

        RateLimitFilter(int maxRequests, int maxTrackedKeys) {
                this.maxRequests = Math.max(1, maxRequests);
                this.maxTrackedKeys = Math.max(1, maxTrackedKeys);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI();
                boolean reportVerification = path.startsWith("/api/reports/verify/")
                                || path.startsWith("/api/v1/reports/verify/");
                return !(LIMITED_PATHS.contains(path) || reportVerification)
                                || "OPTIONS".equalsIgnoreCase(request.getMethod());
        }

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                long now = System.currentTimeMillis();
                String key = request.getRemoteAddr() + "|" + request.getRequestURI();

                Window window;
                int count;
                boolean capacityExceeded;
                synchronized (windows) {
                        if (!windows.containsKey(key) && windows.size() >= maxTrackedKeys) {
                                windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MS);
                        }
                        capacityExceeded = !windows.containsKey(key) && windows.size() >= maxTrackedKeys;
                        if (capacityExceeded) {
                                window = null;
                                count = 0;
                        } else {
                                window = windows.compute(key,
                                                (k, current) -> current == null || now - current.start >= WINDOW_MS
                                                                ? new Window(now)
                                                                : current);
                                count = window.count.incrementAndGet();
                        }
                }

                if (capacityExceeded) {
                        writeRateLimitResponse(response, WINDOW_MS / 1000);
                        return;
                }
                if (count > maxRequests) {
                        long retryAfter = Math.max(1, (WINDOW_MS - (now - window.start)) / 1000);
                        writeRateLimitResponse(response, retryAfter);
                        return;
                }

                filterChain.doFilter(request, response);
        }

        private void writeRateLimitResponse(HttpServletResponse response, long retryAfter)
                        throws IOException {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                                "{\"success\":false,\"status\":429,\"error\":\"TOO_MANY_REQUESTS\","
                                                + "\"message\":\"Troppi tentativi. Riprova tra qualche istante.\"}");
        }

        private static final class Window {
                final long start;
                final AtomicInteger count = new AtomicInteger();

                Window(long start) {
                        this.start = start;
                }
        }
}
