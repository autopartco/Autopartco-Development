package com.autopartco.backend.core.catalog;

import java.util.UUID;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Filtro del marketplace por vehículo. Un nivel ausente no filtra (solo marca = todo lo compatible con alguna parte
 * de esa marca). {@code modelId} exige {@code makeId} y {@code versionId} exige {@code modelId}; si no, 400
 * {@code VEHICLE_FILTER_INVALID}.
 */
public record VehicleFilter(UUID makeId, UUID modelId, UUID versionId) {
    public VehicleFilter {
        if ((modelId != null && makeId == null) || (versionId != null && modelId == null)) {
            throw new BadRequestException("VEHICLE_FILTER_INVALID",
                    "Para filtrar por modelo indica la marca, y para filtrar por versión indica el modelo.");
        }
    }

    public static VehicleFilter none() {
        return new VehicleFilter(null, null, null);
    }

    public boolean isEmpty() {
        return makeId == null;
    }
}
