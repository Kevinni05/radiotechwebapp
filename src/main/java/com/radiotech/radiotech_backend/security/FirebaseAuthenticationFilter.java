package com.radiotech.radiotech_backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Component
public class FirebaseAuthenticationFilter
                extends OncePerRequestFilter {

        private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

        private static final String BEARER_PREFIX = "Bearer ";

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
                return "OPTIONS".equalsIgnoreCase(request.getMethod());
        }

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain)
                        throws ServletException, IOException {

                String path = request.getRequestURI();

                /*
                 * Endpoint pubblici.
                 */
                if (isPublicEndpoint(path)) {

                        filterChain.doFilter(
                                        request,
                                        response);

                        return;
                }

                String authorization = request.getHeader("Authorization");

                if (authorization == null ||
                                authorization.isBlank()) {

                        sendUnauthorized(
                                        response,
                                        "Authorization Bearer obbligatorio.");

                        return;
                }

                if (!authorization
                                .regionMatches(
                                                true,
                                                0,
                                                BEARER_PREFIX,
                                                0,
                                                BEARER_PREFIX.length())) {

                        sendUnauthorized(
                                        response,
                                        "Formato Authorization non valido.");

                        return;
                }

                String idToken = authorization
                                .substring(
                                                BEARER_PREFIX.length())
                                .trim();

                if (idToken.isBlank()) {

                        sendUnauthorized(
                                        response,
                                        "Firebase ID token obbligatorio.");

                        return;
                }

                FirebaseToken decoded;
                try {
                        decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
                } catch (Exception e) {
                        log.warn("Firebase ID token rifiutato: {}", e.getMessage());
                        SecurityContextHolder.clearContext();
                        sendUnauthorized(response, "Firebase ID token non valido o scaduto.");
                        return;
                }

                if (isSelfRegistrationRequest(request)) {
                        request.setAttribute("firebaseUid", decoded.getUid());
                        request.setAttribute("firebaseToken", decoded);
                        request.setAttribute("firebaseEmail", decoded.getEmail());
                        request.setAttribute("firebaseRole", Role.NONE.name());

                        UsernamePasswordAuthenticationToken registrationAuthentication = new UsernamePasswordAuthenticationToken(
                                        decoded.getUid(), null, List.of(new SimpleGrantedAuthority("ROLE_NONE")));
                        registrationAuthentication.setDetails(new FirebaseAuthenticationDetails(
                                        decoded.getUid(), decoded.getEmail(), decoded.getName(), null));
                        SecurityContextHolder.getContext().setAuthentication(registrationAuthentication);
                        filterChain.doFilter(request, response);
                        return;
                }

                Role authenticatedRole = Role.fromClaims(decoded.getClaims());
                if (authenticatedRole == Role.NONE) {
                        sendForbidden(response, "Ruolo applicativo non assegnato.");
                        return;
                }

                Object rawTenantId = decoded.getClaims().get("tenantId");
                String tenantId = rawTenantId instanceof String value ? value.trim() : "";
                if (tenantId.isBlank()) {
                        sendForbidden(response, "Tenant non assegnato all'identita' autenticata.");
                        return;
                }
                String role = authenticatedRole.name();

                request.setAttribute("firebaseUid", decoded.getUid());
                request.setAttribute("tenantId", tenantId);
                request.setAttribute("firebaseToken", decoded);
                request.setAttribute("firebaseEmail", decoded.getEmail());
                request.setAttribute("firebaseRole", role);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                decoded.getUid(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authentication.setDetails(new FirebaseAuthenticationDetails(
                                decoded.getUid(), decoded.getEmail(), decoded.getName(), tenantId));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                filterChain.doFilter(request, response);
        }

        private boolean isPublicEndpoint(
                        String path) {

                if (path == null) {
                        return false;
                }

                return path.equals("/")
                                || path.equals("/dashboard")
                                || path.equals("/login")

                                || path.startsWith("/css/")
                                || path.startsWith("/js/")
                                || path.startsWith("/images/")
                                || path.startsWith("/img/")
                                || path.startsWith("/fonts/")
                                || path.startsWith("/assets/")

                                || path.equals("/favicon.ico")
                                || path.startsWith("/favicon")

                                || path.equals("/api/auth/login")
                                || path.equals("/api/auth/verify")
                                || path.equals("/api/auth/refresh")
                                || path.equals("/api/auth/qr-login")
                                || path.equals("/api/v1/auth/login")
                                || path.equals("/api/v1/auth/verify")
                                || path.equals("/api/v1/auth/refresh")
                                || path.equals("/api/v1/auth/qr-login")
                                || path.startsWith("/api/reports/verify/")
                                || path.startsWith("/api/v1/reports/verify/")
                                || path.startsWith("/api/bootstrap/")
                                || path.startsWith("/api/v1/bootstrap/")

                                || path.equals("/api/health")
                                || path.equals("/api/health/firebase")
                                || path.equals("/api/v1/health")
                                || path.equals("/api/v1/health/firebase")

                                || path.equals("/actuator/health")
                                || path.startsWith("/actuator/health/")

                                || path.startsWith("/error");
        }

        private boolean isSelfRegistrationRequest(HttpServletRequest request) {
                if (!"POST".equalsIgnoreCase(request.getMethod())) {
                        return false;
                }
                String path = request.getRequestURI();
                return "/api/operator/register".equals(path)
                                || "/api/v1/operator/register".equals(path);
        }

        private void sendUnauthorized(
                        HttpServletResponse response,
                        String message)
                        throws IOException {

                response.setStatus(
                                HttpServletResponse.SC_UNAUTHORIZED);

                response.setContentType(
                                "application/json");

                response.setCharacterEncoding(
                                "UTF-8");

                response.getWriter().write(
                                "{\"success\":false," +
                                                "\"status\":401," +
                                                "\"error\":\"UNAUTHORIZED\"," +
                                                "\"message\":\""
                                                + escapeJson(message)
                                                + "\"}");
        }

        private void sendForbidden(HttpServletResponse response, String message) throws IOException {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                                "{\"success\":false,\"status\":403,\"error\":\"FORBIDDEN\",\"message\":\""
                                                + escapeJson(message) + "\"}");
        }

        private String escapeJson(
                        String value) {

                return value == null
                                ? ""
                                : value
                                                .replace("\\", "\\\\")
                                                .replace("\"", "\\\"")
                                                .replace("\r", "\\r")
                                                .replace("\n", "\\n");
        }
}
