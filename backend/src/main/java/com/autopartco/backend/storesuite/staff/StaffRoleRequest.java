package com.autopartco.backend.storesuite.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nuevo rol predefinido de un colaborador. */
public record StaffRoleRequest(@NotBlank @Size(max = 40) String roleCode) {
}
