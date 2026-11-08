package com.autopartco.backend.core.identity.internal.social;

import com.autopartco.backend.shared.error.UnauthorizedException;

/** Valida en el servidor el token que el navegador obtuvo del proveedor. */
public interface SocialTokenVerifier {
    SocialProvider provider();

    /** {@code false} si faltan las credenciales del proveedor en la configuración. */
    boolean enabled();

    /** @throws UnauthorizedException {@code SOCIAL_TOKEN_INVALID} */
    SocialIdentity verify(String token);

    static UnauthorizedException invalidToken() {
        return new UnauthorizedException("SOCIAL_TOKEN_INVALID", "No pudimos validar tu ingreso con el proveedor. Inténtalo de nuevo.");
    }
}
