package com.autopartco.backend.storesuite.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invitación por correo con uno de los roles predefinidos ({@code StaffRoles}). */
public record StaffInviteRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 40) String roleCode
) {
}
