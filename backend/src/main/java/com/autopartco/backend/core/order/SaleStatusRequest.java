package com.autopartco.backend.core.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SaleStatusRequest(
        @NotBlank
        @Pattern(regexp = "CONFIRMED|PREPARING|CANCELLED")
        String status
) {
}
