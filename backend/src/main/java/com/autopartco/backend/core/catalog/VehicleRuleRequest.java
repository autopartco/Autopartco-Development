package com.autopartco.backend.core.catalog;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Regla de compatibilidad enviada por el almacén. {@code modelId}/{@code versionId} ausentes significan «todos»;
 * {@code versionId} exige {@code modelId}. La coherencia con el catálogo la valida {@link ProductVehicleRules}.
 */
public record VehicleRuleRequest(@NotNull UUID makeId, UUID modelId, UUID versionId) {

    /** {@code true} si esta regla cubre a {@code other} (igual o más general dentro de la misma marca). */
    boolean covers(VehicleRuleRequest other) {
        return makeId.equals(other.makeId)
                && (modelId == null || modelId.equals(other.modelId))
                && (versionId == null || versionId.equals(other.versionId));
    }
}
