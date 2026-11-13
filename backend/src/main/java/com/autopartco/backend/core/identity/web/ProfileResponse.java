package com.autopartco.backend.core.identity.web;

import java.util.UUID;

public record ProfileResponse(
        UUID accountId,
        UUID personId,
        String email,
        String name,
        String surname,
        String identification,
        String dateBirth,
        String numberPhone,
        /** {@code false}: cuenta creada con Google sin contraseña definida (debe crearla con la recuperación). */
        boolean passwordSet
) {
}