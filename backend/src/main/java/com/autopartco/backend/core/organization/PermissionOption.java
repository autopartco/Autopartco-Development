package com.autopartco.backend.core.organization;

/** Permiso de almacén que se puede asignar a un colaborador invitado. */
public record PermissionOption(
        String code,
        String description
) {
}
