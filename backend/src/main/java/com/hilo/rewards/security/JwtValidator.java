package com.hilo.rewards.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final JwtDecoder jwtDecoder;
    private final String expectedIssuer;

    public JwtValidator(
            JwtDecoder jwtDecoder,
            @Value("${supabase.jwt-issuer}") String expectedIssuer) {
        this.jwtDecoder = jwtDecoder;
        this.expectedIssuer = expectedIssuer;
    }

    public Optional<Jwt> validate(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);

            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                log.warn("JWT has empty or missing subject");
                return Optional.empty();
            }

            if (jwt.getIssuer() == null || !expectedIssuer.equals(jwt.getIssuer().toString())) {
                log.warn("JWT issuer mismatch: expected={}, got={}", expectedIssuer, jwt.getIssuer());
                return Optional.empty();
            }

            Object kid = jwt.getHeaders().get("kid");
            if (kid == null || kid.toString().isBlank()) {
                log.warn("JWT missing key ID (kid)");
                return Optional.empty();
            }

            return Optional.of(jwt);

        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected JWT validation error", e);
            return Optional.empty();
        }
    }

    public String getUserId(Jwt jwt) {
        return jwt.getSubject();
    }
}
