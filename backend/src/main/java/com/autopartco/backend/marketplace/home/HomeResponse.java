package com.autopartco.backend.marketplace.home;

import com.autopartco.backend.marketplace.services.ServiceBrowseController;
import com.autopartco.backend.marketplace.services.ServiceCard;
import java.util.List;

import com.autopartco.backend.core.search.HomeFacts;
import com.autopartco.backend.marketplace.catalog.SearchResponse;

/**
 * Respuesta de {@code GET /api/v1/marketplace/home}. {@code nearby} y {@code featured} usan el mismo formato de
 * producto que {@code /marketplace/search} (campos del producto al mismo nivel, más {@code distanceKm},
 * {@code sponsored} y {@code adImpressionToken}). Los precios son los generales (sin promociones por comprador).
 */
public record HomeResponse(
        HomeFacts.Stats stats,
        List<String> popularSearches,
        List<HomeFacts.CategoryCount> categories,
        List<SearchResponse.Item> nearby,
        List<SearchResponse.Item> featured,
        Services services
) {
    /** {@code nearby} y {@code promoted}: hasta 8 tarjetas de servicio cada uno. */
    public record Services(List<ServiceBrowseController.CategoryItem> categories, List<ServiceCard> nearby,
            List<ServiceCard> promoted) {
    }
}
