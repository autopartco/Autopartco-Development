package com.autopartco.backend.core.identity.internal.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credenciales de los proveedores sociales ({@code app.social.*}). Vacías: el proveedor queda deshabilitado. */
@ConfigurationProperties(prefix = "app.social")
public record SocialAuthProperties(Google google, Facebook facebook) {
    public SocialAuthProperties {
        google = google == null ? new Google("") : google;
        facebook = facebook == null ? new Facebook("", "") : facebook;
    }

    public record Google(String clientId) {
    }

    public record Facebook(String appId, String appSecret) {
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
