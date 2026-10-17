package com.autopartco.backend.core.catalog;

import java.util.UUID;

/** Regla de compatibilidad con nombres; {@code modelId}/{@code versionId} nulos = «todos». */
public record VehicleRuleResponse(
        UUID makeId,
        String makeName,
        UUID modelId,
        String modelName,
        UUID versionId,
        String versionLabel
) {
}
