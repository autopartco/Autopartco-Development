package com.autopartco.backend.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerShipmentStatusRequest(
        @NotBlank
        @Pattern(regexp = "DISPATCHED|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED")
        String status,
        @Size(max = 255) String notes
) {
}
