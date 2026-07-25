package com.hilo.rewards.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtValidatorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    private JwtValidator jwtValidator;

    private static final String EXPECTED_ISSUER = "https://test.supabase.co/auth/v1";

    private Jwt validJwt;

    @BeforeEach
    void setUp() {
        jwtValidator = new JwtValidator(jwtDecoder, EXPECTED_ISSUER);

        validJwt = Jwt.withTokenValue("test-token")
            .subject("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            .issuer(EXPECTED_ISSUER)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .headers(h -> h.put("kid", "test-key-id-001"))
            .build();
    }

    @Test
    void validate_validToken_returnsJwt() {
        when(jwtDecoder.decode("test-token")).thenReturn(validJwt);

        var result = jwtValidator.validate("test-token");

        assertTrue(result.isPresent());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", result.get().getSubject());
    }

    @Test
    void validate_invalidSignature_returnsEmpty() {
        when(jwtDecoder.decode("bad-sig-token"))
            .thenThrow(new JwtException("Invalid signature"));

        var result = jwtValidator.validate("bad-sig-token");

        assertTrue(result.isEmpty());
        verify(jwtDecoder).decode("bad-sig-token");
    }

    @Test
    void validate_wrongIssuer_returnsEmpty() {
        Jwt wrongIssuer = Jwt.withTokenValue("token")
            .subject("user-123")
            .issuer("https://evil.com/auth")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .headers(h -> h.put("kid", "key-1"))
            .build();

        when(jwtDecoder.decode("token")).thenReturn(wrongIssuer);

        var result = jwtValidator.validate("token");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_expiredToken_returnsEmpty() {
        when(jwtDecoder.decode("expired-token"))
            .thenThrow(new JwtException("Jwt expired"));

        var result = jwtValidator.validate("expired-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_notYetValidToken_returnsEmpty() {
        when(jwtDecoder.decode("future-token"))
            .thenThrow(new JwtException("Jwt not valid yet"));

        var result = jwtValidator.validate("future-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_malformedToken_returnsEmpty() {
        when(jwtDecoder.decode("not-a-jwt"))
            .thenThrow(new JwtException("Invalid JWT"));

        var result = jwtValidator.validate("not-a-jwt");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_emptySubject_returnsEmpty() {
        Jwt emptySubject = Jwt.withTokenValue("token")
            .subject("")
            .issuer(EXPECTED_ISSUER)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .headers(h -> h.put("kid", "key-1"))
            .build();

        when(jwtDecoder.decode("token")).thenReturn(emptySubject);

        var result = jwtValidator.validate("token");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_noSubject_returnsEmpty() {
        Jwt noSubject = Jwt.withTokenValue("token")
            .issuer(EXPECTED_ISSUER)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .headers(h -> h.put("kid", "key-1"))
            .build();

        when(jwtDecoder.decode("token")).thenReturn(noSubject);

        var result = jwtValidator.validate("token");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_noKid_returnsEmpty() {
        Jwt noKid = Jwt.withTokenValue("token")
            .subject("user-123")
            .issuer(EXPECTED_ISSUER)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .headers(h -> h.put("alg", "ES256"))
            .build();

        when(jwtDecoder.decode("token")).thenReturn(noKid);

        var result = jwtValidator.validate("token");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_algorithmNotAllowed_returnsEmpty() {
        when(jwtDecoder.decode("alg-token"))
            .thenThrow(new JwtException("Unsupported algorithm"));

        var result = jwtValidator.validate("alg-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void getUserId_validJwt_returnsSubject() {
        when(jwtDecoder.decode("test-token")).thenReturn(validJwt);

        var jwt = jwtValidator.validate("test-token");

        assertTrue(jwt.isPresent());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", jwtValidator.getUserId(jwt.get()));
    }
}
