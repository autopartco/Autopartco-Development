package com.autopartco.backend.seller.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SellerCheckoutRequest(
        @NotNull UUID planId,
        @Min(1) @Max(12) int months
) {
}
