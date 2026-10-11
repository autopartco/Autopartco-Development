package com.autopartco.backend.auth.dto;

public record LoginResponse(
	String accessToken,
	String refreshToken,
	String tokenType,
	long expiresIn
) {
}
