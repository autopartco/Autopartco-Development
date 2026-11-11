package com.autopartco.backend.core.advertising;

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
 */
public record AdContext(
        String text,
        UUID categoryId,
        UUID makeId,
        UUID modelId,
        String cityCode,
        Set<UUID> excludedProductIds,
        String visitorKey
) {
    public AdContext {
        text = text == null ? "" : AdText.normalize(text);
        excludedProductIds = excludedProductIds == null ? Set.of() : Set.copyOf(excludedProductIds);
        cityCode = cityCode == null || cityCode.isBlank() ? null : cityCode.strip();
    }

    /** Sin consulta, categoría ni vehículo. */
    public boolean hasSignals() {
        return !text.isEmpty() || categoryId != null || makeId != null || modelId != null;
    }
}
