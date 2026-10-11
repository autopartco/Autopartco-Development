package com.autopartco.backend.product.dto;

import java.util.List;
import java.util.UUID;

public record ProductOptionsResponse(
        List<ManufacturerOption> manufacturers,
        List<CategoryOption> categories,
        List<CompatibilityOption> compatibilities
) {
    public record ManufacturerOption(UUID id, String name) {
    }

    public record CategoryOption(UUID id, String code) {
    }

    public record CompatibilityOption(UUID id, String make, String model, String version) {
    }
}