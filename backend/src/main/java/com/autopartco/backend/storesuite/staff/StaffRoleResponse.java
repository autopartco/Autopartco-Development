package com.autopartco.backend.storesuite.staff;

import java.util.List;

import com.autopartco.backend.core.organization.StaffRoles;

/** Rol predefinido asignable con los permisos que concede. */
public record StaffRoleResponse(String code, String name, String description, List<String> permissionCodes) {
    static StaffRoleResponse from(StaffRoles.Definition definition) {
        return new StaffRoleResponse(
                definition.code(),
                definition.name(),
                definition.description(),
                definition.permissionCodes().stream().sorted().toList()
        );
    }
}
