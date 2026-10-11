package com.autopartco.backend.seller.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SellerCollaboratorInviteRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 100) String roleName,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 100) String> permissionCodes
) {
}
