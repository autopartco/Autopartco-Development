package com.autopartco.backend.core.identity;

public record LoginResponse(
	String accessToken,
	String refreshToken,
	String tokenType,
	long expiresIn
) {
}
