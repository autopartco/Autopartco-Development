package com.autopartco.backend.core.identity.web;

/**
 * Refresh token opcional: si falta, el logout usa la cookie {@link RefreshCookie#NAME}.
 */
public record LogoutRequest(String refreshToken) {
}
