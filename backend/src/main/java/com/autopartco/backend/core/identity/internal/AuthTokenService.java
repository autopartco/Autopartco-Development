package com.autopartco.backend.core.identity.internal;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AuthTokenService {
    private final JwtEncoder jwtEncoder;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenPair issue(Account account) {
        return issue(account, UUID.randomUUID());
    }

    public TokenPair issue(Account account, UUID sessionId) {
        Instant issuedAt = Instant.now();
        long accessTokenMinutes = appProperties.tokens().accessExpiresMinutes();
        long refreshTokenDays = appProperties.tokens().refreshExpiresDays();
        Instant expiresAt = issuedAt.plus(accessTokenMinutes, ChronoUnit.MINUTES);
        String authority = account.getAccountType().getAuthority();
        List<String> roles = "STORE_MANAGER".equals(authority)
                ? List.of("ROLE_CUSTOMER", "ROLE_STORE_MANAGER")
                : List.of("ROLE_" + authority);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(account.getEmail())
                .claim("email", account.getEmail())
                .claim("name", account.getPerson().getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .issuer("autopartco")
                .claim("sid", sessionId.toString())
                .claim("roles", roles)
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        String refreshToken = generateRefreshToken();
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(refreshTokenDays);

        return new TokenPair(
            new LoginResponse(accessToken, refreshToken, "Bearer", accessTokenMinutes * 60),
                refreshExpiresAt,
                sessionId
        );
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record TokenPair(LoginResponse response, LocalDateTime refreshExpiresAt, UUID sessionId) {
    }
    
}