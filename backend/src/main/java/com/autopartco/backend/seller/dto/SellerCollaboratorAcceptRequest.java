package com.autopartco.backend.seller.dto;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerCollaboratorAcceptRequest(
        @NotBlank String token,
        @Valid AccountDetails account
) {
    public record AccountDetails(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String surname,
            @NotBlank @Size(max = 20) String identification,
            @NotNull @Past LocalDate dateBirth,
            @NotBlank @Size(max = 10) String numberPhone,
            @NotBlank
            @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$")
            String password
    ) {
    }
}
