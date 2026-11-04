package com.autopartco.backend.core.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Opciones de filtro del marketplace. Ya no incluye la lista completa de compatibilidades: los vehículos se consultan
 * en cascada en {@code /marketplace/vehicles/...}.
 */
public record ProductOptionsResponse(
        List<ManufacturerOption> manufacturers,
        List<CategoryOption> categories
) {
    public record ManufacturerOption(UUID id, String name) {
    }

    public record CategoryOption(UUID id, String code) {
    }

    /** Solo para el endpoint obsoleto {@code /product-options/compatibilities} (tabla {@code compatibilities}). */
    public record CompatibilityOption(UUID id, String make, String model, String version) {
    }
}
