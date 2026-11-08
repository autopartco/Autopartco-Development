package com.autopartco.backend.core.identity.internal.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.autopartco.backend.shared.error.UnauthorizedException;

class GoogleTokenVerifierTest {
    private static final SocialAuthProperties PROPS =
            new SocialAuthProperties(new SocialAuthProperties.Google("client-1"), null);

    @Test
    void mapsClaimsOfAValidToken() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("tok")).thenReturn(jwt("https://accounts.google.com", List.of("client-1"), true));

        SocialIdentity identity = new GoogleTokenVerifier(PROPS, decoder).verify("tok");

        assertEquals("sub-1", identity.subject());
        assertEquals("ana@example.com", identity.email());
        assertTrue(identity.emailVerified());
        assertEquals("Ana", identity.givenName());
    }

    @Test
    void decoderFailureIsInvalidToken() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("bad")).thenThrow(new BadJwtException("firma"));

        UnauthorizedException error = assertThrows(UnauthorizedException.class, () -> new GoogleTokenVerifier(PROPS, decoder).verify("bad"));
        assertEquals("SOCIAL_TOKEN_INVALID", error.getCode());
    }

    @Test
    void validatorRejectsOtherAudienceOrIssuer() {
        var validator = GoogleTokenVerifier.validator("client-1");
        assertTrue(validator.validate(jwt("https://accounts.google.com", List.of("otro"), true)).hasErrors());
        assertTrue(validator.validate(jwt("https://evil.example", List.of("client-1"), true)).hasErrors());
        assertFalse(validator.validate(jwt("accounts.google.com", List.of("client-1"), true)).hasErrors());
    }

    @Test
    void enabledOnlyWithClientId() {
        assertTrue(new GoogleTokenVerifier(PROPS).enabled());
        assertFalse(new GoogleTokenVerifier(new SocialAuthProperties(null, null)).enabled());
    }

    private static Jwt jwt(String issuer, List<String> audience, boolean verified) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(audience)
                .subject("sub-1")
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(600))
                .claim("email", "ana@example.com")
                .claim("email_verified", verified)
                .claim("given_name", "Ana")
                .build();
    }
}
