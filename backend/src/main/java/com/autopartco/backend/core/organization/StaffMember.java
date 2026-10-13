package com.autopartco.backend.core.organization;

import java.util.List;
import java.util.UUID;

/** Colaborador de un almacén con sus roles y permisos efectivos. */
public record StaffMember(
        UUID id,
        String name,
        String surname,
        String email,
        String status,
        List<String> roleNames,
        List<String> permissionCodes
) {
}
