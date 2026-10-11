package com.autopartco.backend.seller.dto;

import java.util.UUID;

import com.autopartco.backend.seller.entity.SellerLegalType;
import com.autopartco.backend.store.dto.StoreRegistrationRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SellerApplicationRequest(
        @NotNull @Valid StoreRegistrationRequest store,
        @NotNull SellerLegalType legalType,
        UUID planId
) {
}
