package com.autopartco.backend.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerShipmentCreateRequest(
        @NotBlank @Size(max = 100) String carrier,
        @NotBlank @Size(max = 100) String trackingNumber
) {
}
