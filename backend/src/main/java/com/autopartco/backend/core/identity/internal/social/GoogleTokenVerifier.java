package com.autopartco.backend.core.identity.internal.social;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * ID token de Google Identity Services: firma con las claves públicas de Google, emisor, audiencia
 * ({@code GOOGLE_CLIENT_ID}) y vigencia.
 */
@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {
    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private volatile JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(SocialAuthProperties properties) {
        this(properties, null);
    }

    /** Para pruebas: decodificador ya construido. */
    GoogleTokenVerifier(SocialAuthProperties properties, JwtDecoder decoder) {
        this.clientId = properties.google().clientId();
        this.decoder = decoder;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public boolean enabled() {
        return SocialAuthProperties.present(clientId);
    }

    @Override
    public SocialIdentity verify(String token) {
        Jwt jwt;
        try {
            jwt = decoder().decode(token);
        } catch (JwtException exception) {
            throw SocialTokenVerifier.invalidToken();
        }
        Object verified = jwt.getClaims().get("email_verified");
        return new SocialIdentity(
                SocialProvider.GOOGLE,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(verified) || "true".equals(verified),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
                    built.setJwtValidator(validator(clientId));
                    decoder = built;
                }
                current = decoder;
            }
        }
        return current;
    }

    static OAuth2TokenValidator<Jwt> validator(String clientId) {
        OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss")))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emisor no válido.", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> {
            List<String> aud = jwt.getAudience();
            return aud != null && aud.contains(clientId)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiencia no válida.", null));
        };
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience);
    }
}
