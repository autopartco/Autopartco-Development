package com.autopartco.backend.core.organization;

import java.util.List;
import java.util.UUID;

/**
 * Colaborador de un almacén con sus roles y permisos efectivos. {@code roleCode} es el rol predefinido que tiene
 * ({@link StaffRoles}) o {@code null} si tiene un rol heredado a medida; {@code owner} si es el propietario.
 */
public record StaffMember(
        UUID id,
        String name,
        String surname,
        String email,
        String status,
        List<String> roleNames,
        List<String> permissionCodes,
        String roleCode,
        boolean owner
) {
    static StaffMember of(
            UUID id,
            String name,
            String surname,
            String email,
            String status,
            List<String> roleNames,
            List<String> permissionCodes,
            List<String> roleCodes
    ) {
        boolean owner = roleCodes.contains(StaffRoles.OWNER);
        String roleCode = owner
                ? StaffRoles.OWNER
                : roleCodes.size() == 1 ? StaffRoles.find(roleCodes.get(0)).map(StaffRoles.Definition::code).orElse(null) : null;
        return new StaffMember(id, name, surname, email, status, roleNames, permissionCodes, roleCode, owner);
    }
}
