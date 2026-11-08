package com.autopartco.backend.core.identity.internal.social;

/**
 * Identidad ya verificada por el proveedor. {@code emailVerified} solo es {@code true} cuando el proveedor lo
 * garantiza (Google {@code email_verified}); Facebook siempre llega en {@code false}.
 */
public record SocialIdentity(
        SocialProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        String givenName,
        String familyName
) {
}
