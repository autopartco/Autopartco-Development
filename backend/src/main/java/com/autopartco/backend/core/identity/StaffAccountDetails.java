package com.autopartco.backend.core.identity;

import java.time.LocalDate;

/**
 * Datos personales con los que se crea la cuenta de un miembro del equipo de la plataforma. La contraseña la define
 * la persona con el enlace de recuperación que recibe por correo. La validación de formato la hace el DTO web.
 */
public record StaffAccountDetails(
        String name,
        String surname,
        String identification,
        LocalDate dateBirth,
        String numberPhone
) {
}
