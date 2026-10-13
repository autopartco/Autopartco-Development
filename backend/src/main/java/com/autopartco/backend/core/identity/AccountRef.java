package com.autopartco.backend.core.identity;

import java.util.UUID;

/**
 * Vista de solo lectura de una cuenta para otros módulos. Nunca expone la contraseña ni la entidad.
 */
public record AccountRef(
        UUID accountId,
        UUID personId,
        String email,
        String fullName,
        String stateCode,
        String accountTypeCode
) {
}
