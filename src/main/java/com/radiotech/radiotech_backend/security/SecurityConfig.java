package com.radiotech.radiotech_backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

        private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;

        public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter) {
                this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                http
                                .cors(withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth

                                                // Login e bootstrap (protetti da rate limit e da secret).
                                                .requestMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/refresh",
                                                                "/api/auth/verify",
                                                                "/api/auth/qr-login",
                                                                "/api/v1/auth/login",
                                                                "/api/v1/auth/refresh",
                                                                "/api/v1/auth/verify",
                                                                "/api/v1/auth/qr-login",
                                                                "/api/bootstrap/**",
                                                                "/api/v1/bootstrap/**")
                                                .permitAll()

                                                .requestMatchers("/api/auth/firebase-user/**",
                                                                "/api/v1/auth/firebase-user/**")
                                                .hasAnyRole(Role.ACCOUNT_ADMINS)

                                                .requestMatchers("/api/auth/link", "/api/v1/auth/link")
                                                .hasAnyRole(Role.ACCOUNT_ADMINS)

                                                .requestMatchers("/api/health/**", "/api/v1/health/**",
                                                                "/actuator/health", "/actuator/health/**")
                                                .permitAll()

                                                .requestMatchers("/api/reports/verify/**", "/api/v1/reports/verify/**")
                                                .permitAll()

                                                .requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                                                .hasAnyRole(Role.ACCOUNT_ADMINS)

                                                // Auto-registrazione: basta un account Firebase valido.
                                                // L'operatore nasce IN_ATTESA e non ha accesso alle API
                                                // finche' un manager non lo approva.
                                                .requestMatchers("/api/operator/register", "/api/v1/operator/register")
                                                .authenticated()

                                                .requestMatchers("/api/operator/**", "/api/v1/operator/**")
                                                .hasAnyRole(Role.OPERATOR_API)

                                                .requestMatchers("/api/capo/**", "/api/v1/capo/**")
                                                .hasAnyRole(Role.MANAGERS)

                                                .requestMatchers(
                                                                "/api/dashboard/**",
                                                                "/api/v1/dashboard/**",
                                                                "/api/tasks/**",
                                                                "/api/v1/tasks/**",
                                                                "/api/operators/**",
                                                                "/api/v1/operators/**",
                                                                "/api/inventory/**",
                                                                "/api/v1/inventory/**",
                                                                "/api/reports/**",
                                                                "/api/v1/reports/**",
                                                                "/api/notifications/**",
                                                                "/api/v1/notifications/**",
                                                                "/api/incidents/**",
                                                                "/api/v1/incidents/**")
                                                .hasAnyRole(Role.CONTROL_ROOM_API)

                                                // Pagine statiche pubbliche.
                                                .requestMatchers(
                                                                "/",
                                                                "/login",
                                                                "/dashboard",
                                                                "/css/**",
                                                                "/js/**",
                                                                "/images/**",
                                                                "/img/**",
                                                                "/fonts/**",
                                                                "/assets/**",
                                                                "/favicon.ico",
                                                                "/error")
                                                .permitAll()

                                                .anyRequest()
                                                .authenticated())
                                .addFilterBefore(firebaseAuthenticationFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
