package com.autopartco.backend.core.identity;

import java.time.LocalDate;

/**
 * Datos personales y contraseña con los que se crea la cuenta de un colaborador invitado. La validación de
 * formato (Bean Validation) la hace el DTO web del producto que la recibe.
 */
public record InvitedAccountDetails(
        String name,
        String surname,
        String identification,
        LocalDate dateBirth,
        String numberPhone,
        String password
) {
}
