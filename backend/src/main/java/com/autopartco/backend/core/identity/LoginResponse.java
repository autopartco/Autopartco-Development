package com.autopartco.backend.core.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de login. Con sesión: tokens y {@code twoFactorRequired=false}. Con 2FA por correo activo, el primer
 * paso devuelve {@code twoFactorRequired=true}, {@code challengeId} y {@code expiresAt} (vencimiento del desafío),
 * sin tokens ({@code accessToken}, {@code refreshToken} y {@code tokenType} nulos, {@code expiresIn=0}); la sesión
 * se emite en {@code POST /auth/login/two-factor}.
 */
public record LoginResponse(
	String accessToken,
	String refreshToken,
	String tokenType,
	long expiresIn,
	boolean twoFactorRequired,
	UUID challengeId,
	Instant expiresAt
) {
	/** Respuesta con sesión (sin desafío). */
	public LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
		this(accessToken, refreshToken, tokenType, expiresIn, false, null, null);
	}

	/** Primer paso del login con 2FA: sin tokens. */
	public static LoginResponse twoFactorChallenge(UUID challengeId, Instant expiresAt) {
		return new LoginResponse(null, null, null, 0, true, challengeId, expiresAt);
	}
}
