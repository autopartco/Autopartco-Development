package com.autopartco.backend.storesuite.staff;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record StaffInviteRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 100) String roleName,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 100) String> permissionCodes
) {
}
