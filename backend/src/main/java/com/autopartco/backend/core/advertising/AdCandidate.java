package com.autopartco.backend.core.advertising;

import java.util.List;
import java.util.UUID;

/**
 * Producto anunciable de una campaña {@code ACTIVE} vigente hoy, con el producto publicado. Inmutable: se guarda en la
 * caché de {@link AdCandidateCache}.
 *
 * @param campaignId        campaña
 * @param storeId           almacén de la campaña
 * @param productId         producto anunciado
 * @param productWords      palabras normalizadas del nombre del producto (coincidencia con la consulta)
 * @param productCategoryId categoría del producto (coincidencia con la categoría pedida)
 * @param targeting         segmentación ya normalizada
 */
public record AdCandidate(
        UUID campaignId,
        UUID storeId,
        UUID productId,
        List<String> productWords,
        UUID productCategoryId,
        AdTargeting targeting
) {
    public AdCandidate {
        productWords = productWords == null ? List.of() : List.copyOf(productWords);
        targeting = targeting == null ? AdTargeting.NONE : targeting;
    }
}
