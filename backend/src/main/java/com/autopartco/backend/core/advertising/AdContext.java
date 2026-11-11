package com.autopartco.backend.core.advertising;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Contexto de una página donde se piden anuncios.
 *
 * @param text               texto buscado (se normaliza aquí); nulo si no hay
 * @param categoryId         categoría pedida o del producto que se está viendo
 * @param makeId             marca del vehículo (explícita o interpretada)
 * @param modelId            modelo del vehículo
 * @param cityCode           municipio DIVIPOLA del visitante o de la búsqueda; nulo si no se conoce
 * @param excludedProductIds productos ya mostrados en la página (no se repiten como anuncio)
 * @param visitorKey         identificador opaco del visitante para el tope de frecuencia (sesión anónima o IP); nulo
 *                           = sin tope
 * @param categoryScope      categorías adicionales que cuentan como la pedida (subcategorías); siempre incluye
 *                           {@code categoryId}
 * @param makeScope          marcas adicionales (vehículos compatibles con el producto visto); incluye {@code makeId}
 * @param modelScope         modelos adicionales; incluye {@code modelId}
 * @param excludedStoreId    almacén cuyos productos no se anuncian (el del producto que se está viendo); nulo = ninguno
 */
public record AdContext(
        String text,
        UUID categoryId,
        UUID makeId,
        UUID modelId,
        String cityCode,
        Set<UUID> excludedProductIds,
        String visitorKey,
        Set<UUID> categoryScope,
        Set<UUID> makeScope,
        Set<UUID> modelScope,
        UUID excludedStoreId
) {
    public AdContext {
        text = text == null ? "" : AdText.normalize(text);
        excludedProductIds = excludedProductIds == null ? Set.of() : Set.copyOf(excludedProductIds);
        cityCode = cityCode == null || cityCode.isBlank() ? null : cityCode.strip();
        categoryScope = scope(categoryScope, categoryId);
        makeScope = scope(makeScope, makeId);
        modelScope = scope(modelScope, modelId);
    }

    /** Contexto con un solo valor por criterio (búsqueda e inicio). */
    public AdContext(String text, UUID categoryId, UUID makeId, UUID modelId, String cityCode,
            Set<UUID> excludedProductIds, String visitorKey) {
        this(text, categoryId, makeId, modelId, cityCode, excludedProductIds, visitorKey, null, null, null, null);
    }

    /** Sin consulta, categoría ni vehículo. */
    public boolean hasSignals() {
        return !text.isEmpty() || !categoryScope.isEmpty() || !makeScope.isEmpty() || !modelScope.isEmpty();
    }

    private static Set<UUID> scope(Set<UUID> values, UUID single) {
        Set<UUID> all = new LinkedHashSet<>();
        if (single != null) {
            all.add(single);
        }
        if (values != null) {
            values.stream().filter(java.util.Objects::nonNull).forEach(all::add);
        }
        return Set.copyOf(all);
    }
}
