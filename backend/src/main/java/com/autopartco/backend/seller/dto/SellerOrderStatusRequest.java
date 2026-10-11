package com.autopartco.backend.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SellerOrderStatusRequest(
        @NotBlank
        @Pattern(regexp = "CONFIRMED|PREPARING|CANCELLED")
        String status
) {
}
