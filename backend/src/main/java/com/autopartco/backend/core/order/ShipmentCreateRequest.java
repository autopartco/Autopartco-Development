package com.autopartco.backend.core.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipmentCreateRequest(
        @NotBlank @Size(max = 100) String carrier,
        @NotBlank @Size(max = 100) String trackingNumber
) {
}
