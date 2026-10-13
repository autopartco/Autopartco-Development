package com.autopartco.backend.core.billing;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlanRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Min(0) long monthlyPriceCop,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @Size(max = 30) List<@NotBlank @Size(max = 250) String> features,
        boolean recommended,
        boolean enabled,
        @Min(1) Integer maxBranches,
        @Min(0) Integer maxProducts,
        @Min(1) Integer maxUsers,
        @Size(max = 30) List<@NotBlank @Size(max = 80) String> capabilities
) {
}
