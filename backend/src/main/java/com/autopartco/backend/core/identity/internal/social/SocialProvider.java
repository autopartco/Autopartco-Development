package com.autopartco.backend.core.identity.internal.social;

import java.util.Locale;

import com.autopartco.backend.shared.error.NotFoundException;

/** Proveedores de ingreso social. El código va en minúsculas en la URL y en {@code account_identities.provider}. */
public enum SocialProvider {
    GOOGLE,
    FACEBOOK;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SocialProvider fromPath(String value) {
        for (SocialProvider provider : values()) {
            if (provider.code().equals(value)) {
                return provider;
            }
        }
        throw new NotFoundException("SOCIAL_PROVIDER_NOT_FOUND", "Proveedor de ingreso no soportado.");
    }
}
