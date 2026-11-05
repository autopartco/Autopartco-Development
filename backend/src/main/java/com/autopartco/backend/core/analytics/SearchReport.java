package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Demanda del marketplace en un rango: búsquedas, búsquedas sin resultados ({@code unmet}) y su fracción
 * ({@code unmetRate}, 0 a 1 con 4 decimales), con los 20 primeros de cada lista.
 */
public record SearchReport(
        LocalDate from,
        LocalDate to,
        long totalSearches,
        long unmetSearches,
        BigDecimal unmetRate,
        List<QueryCount> topQueries,
        List<UnmetQuery> topUnmetQueries,
        List<VehicleCount> topVehicles,
        List<MunicipalityCount> byMunicipality
) {
    public record QueryCount(String query, long searches, long unmet) {
    }

    public record UnmetQuery(String query, long searches) {
    }

    public record VehicleCount(UUID makeId, String makeName, UUID modelId, String modelName, Integer year,
                               long searches, long unmet) {
    }

    public record MunicipalityCount(String municipalityCode, String municipalityName, long searches, long unmet) {
    }
}
