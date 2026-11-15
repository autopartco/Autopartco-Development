package com.autopartco.backend.core.analytics;

import java.util.UUID;

/**
 * Búsqueda del marketplace a registrar. {@code sessionId} es el valor crudo de la cabecera {@code X-Session-Id}: no
 * se guarda, solo su hash diario. Sin datos personales. {@code searchId} es el identificador que se devolvió al
 * cliente (para asociarle los clics); si es nulo, {@link SearchLog} genera uno.
 */
public record MarketplaceSearch(
        String queryText,
        UUID makeId,
        UUID modelId,
        UUID versionId,
        Integer vehicleYear,
        UUID categoryId,
        String municipalityCode,
        int resultsCount,
        String sessionId,
        boolean authenticated,
        UUID searchId
) {
    /** Sin identificador de búsqueda (el registro genera uno). */
    public MarketplaceSearch(String queryText, UUID makeId, UUID modelId, UUID versionId, Integer vehicleYear,
            UUID categoryId, String municipalityCode, int resultsCount, String sessionId, boolean authenticated) {
        this(queryText, makeId, modelId, versionId, vehicleYear, categoryId, municipalityCode, resultsCount,
                sessionId, authenticated, null);
    }

    /** Hay algún criterio: texto, vehículo o categoría (igual que la restricción de la tabla). */
    public boolean hasCriteria() {
        return SearchText.normalize(queryText) != null || makeId != null || modelId != null || categoryId != null;
    }
}
