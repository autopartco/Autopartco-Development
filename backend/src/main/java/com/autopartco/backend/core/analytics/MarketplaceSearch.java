package com.autopartco.backend.core.analytics;

import java.util.UUID;

/**
 * Búsqueda del marketplace a registrar. {@code sessionId} es el valor crudo de la cabecera {@code X-Session-Id}: no
 * se guarda, solo su hash diario. Sin datos personales.
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
        boolean authenticated
) {
    /** Hay algún criterio: texto, vehículo o categoría (igual que la restricción de la tabla). */
    public boolean hasCriteria() {
        return SearchText.normalize(queryText) != null || makeId != null || modelId != null || categoryId != null;
    }
}
