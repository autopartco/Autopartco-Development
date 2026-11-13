package com.autopartco.backend.core.advertising;

import java.util.UUID;

/**
 * Almacén elegido como «Recomendado» en el mapa ({@link AdPlacement#MAP_FEATURED}).
 *
 * @param campaignId        campaña vigente que paga la recomendación
 * @param storeId           almacén recomendado
 * @param adImpressionToken token firmado para registrar impresión y clic (sin producto)
 */
public record MapFeaturedPick(UUID campaignId, UUID storeId, String adImpressionToken) {
}
