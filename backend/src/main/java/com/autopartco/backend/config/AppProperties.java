package com.autopartco.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        String frontendBaseUrl,
    EmailVerification emailVerification,
    PasswordRecovery passwordRecovery,
    Tokens tokens
) {
    public record EmailVerification(
            long expiresMinutes,
            String from
    ) {
        public Duration expiresIn() {
            return Duration.ofMinutes(expiresMinutes);
        }
    }

    public record PasswordRecovery(long expiresMinutes) {
        public Duration expiresIn() {
            return Duration.ofMinutes(expiresMinutes);
        }
    }

    public record Tokens(
            long accessExpiresMinutes,
            long refreshExpiresDays
    ) {
        public Duration accessExpiresIn() {
            return Duration.ofMinutes(accessExpiresMinutes);
        }

        public Duration refreshExpiresIn() {
            return Duration.ofDays(refreshExpiresDays);
        }
    }
}
