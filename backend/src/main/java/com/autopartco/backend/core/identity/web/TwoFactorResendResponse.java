package com.autopartco.backend.core.identity.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Reenvío del código 2FA de login. {@code challengeId} es el desafío vigente: uno nuevo si se envió código, el mismo
 * si hubo que esperar. {@code retryAfterSeconds}: segundos hasta poder pedir otro.
 */
public record TwoFactorResendResponse(UUID challengeId, Instant expiresAt, long retryAfterSeconds) {
}
