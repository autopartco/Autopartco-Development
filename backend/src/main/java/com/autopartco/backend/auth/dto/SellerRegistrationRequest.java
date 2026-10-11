package com.autopartco.backend.auth.dto;

import com.autopartco.backend.store.dto.StoreRegistrationRequest;
import com.autopartco.backend.seller.entity.SellerLegalType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SellerRegistrationRequest(
        @NotNull(message = "Los datos de la cuenta son obligatorios.")
        @Valid
        RegisterRequest account,

        @NotNull(message = "Los datos de la empresa son obligatorios.")
        @Valid
        StoreRegistrationRequest store,

        @NotNull(message = "El tipo de persona es obligatorio.")
        SellerLegalType legalType,

        java.util.UUID planId
) {
}
