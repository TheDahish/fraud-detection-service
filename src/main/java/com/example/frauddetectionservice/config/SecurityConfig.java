package com.example.frauddetectionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Secure by default: every request must present a valid bearer token. Only the health probes are public.
 *
 * <p>Token verification is mocked for this exercise. The decoder accepts two fixed tokens, one per scope, and
 * rejects everything else. The bearer-token flow itself (extraction, scope checks, 401 and 403 responses) is
 * the real Spring Security path, so the endpoint rules are exercised exactly as they would be in production.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    static final String ASSESS_AUTHORITY = "SCOPE_fraud:assess";

    /** Tokens accepted by the mock decoder. Fixed values, for local runs and tests only. */
    static final String ASSESS_TOKEN = "mock-token-fraud-assess";
    static final String OTHER_SCOPE_TOKEN = "mock-token-reports-read";

    private static final Map<String, String> SCOPE_BY_TOKEN = Map.of(
            ASSESS_TOKEN, "fraud:assess",
            OTHER_SCOPE_TOKEN, "reports:read");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API with no cookies, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/fraud/assessments").hasAuthority(ASSESS_AUTHORITY)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            String scope = SCOPE_BY_TOKEN.get(token);
            if (scope == null) {
                throw new org.springframework.security.oauth2.jwt.BadJwtException("Unknown token");
            }
            Instant now = Instant.now();
            return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("local-tester")
                    .claim("scope", scope)
                    .issuedAt(now)
                    .expiresAt(now.plus(Duration.ofDays(1)))
                    .build();
        };
    }
}
