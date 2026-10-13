package com.autopartco.backend.storesuite.onboarding;

import java.util.UUID;

import com.autopartco.backend.core.identity.RegisterRequest;

import com.autopartco.backend.core.organization.StoreRegistrationRequest;
import com.autopartco.backend.core.organization.LegalType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SellerSignupRequest(
        @NotNull(message = "Los datos de la cuenta son obligatorios.")
        @Valid
        RegisterRequest account,

        @NotNull(message = "Los datos de la empresa son obligatorios.")
        @Valid
        StoreRegistrationRequest store,

        @NotNull(message = "El tipo de persona es obligatorio.")
        LegalType legalType,

        UUID planId
) {
}
