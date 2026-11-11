package com.autopartco.backend.core.advertising;

import java.util.UUID;

/**
 * Anuncio elegido por {@link AdServer#pick}: el llamador hidrata el producto con su listado publicado y lo muestra con
 * la etiqueta «Patrocinado» y {@code adImpressionToken}.
 */
public record AdPick(UUID campaignId, UUID productId, AdPlacement placement, String adImpressionToken) {
}
