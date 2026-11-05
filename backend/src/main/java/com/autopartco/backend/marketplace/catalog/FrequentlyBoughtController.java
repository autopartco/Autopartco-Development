package com.autopartco.backend.marketplace.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.ProductAssociations;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductImageResponse;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** «Se compran juntos» del detalle de producto (público). */
@RestController
@AllArgsConstructor
public class FrequentlyBoughtController {
    static final int MAX_ITEMS = 6;

    private final ProductAssociations productAssociations;
    private final ListingQueries listingQueries;
    private final BuyerContexts buyerContexts;

    /** Producto recomendado con el precio efectivo del comprador. {@code reason}: ORDERS, COMPATIBILITY o CATEGORY. */
    public record FrequentlyBoughtItem(UUID productId, String name, BigDecimal price, String image, String reason) {
    }

    /**
     * Hasta {@value #MAX_ITEMS} productos publicados con stock que suelen comprarse con el producto. El precio es el
     * efectivo del comprador (anónimo sin sesión), igual que en el catálogo.
     */
    @GetMapping(ApiPaths.MARKETPLACE + "/products/{productId}/frequently-bought")
    public List<FrequentlyBoughtItem> frequentlyBought(@PathVariable UUID productId, Authentication authentication) {
        List<ProductAssociations.Related> related = productAssociations.related(productId, MAX_ITEMS);
        if (related.isEmpty()) {
            return List.of();
        }
        BuyerContext buyer = buyerContexts.forBrowsing(authentication);
        List<FrequentlyBoughtItem> items = new ArrayList<>();
        for (ProductAssociations.Related item : related) {
            ProductResponse listing;
            try {
                listing = listingQueries.publishedListing(item.productId(), buyer);
            } catch (NotFoundException unavailable) {
                continue; // dejó de estar publicado entre la consulta y el listado
            }
            items.add(new FrequentlyBoughtItem(listing.id(), listing.name(), listing.effectivePrice(),
                    image(listing), item.reason()));
        }
        return items;
    }

    private static String image(ProductResponse listing) {
        List<ProductImageResponse> images = listing.images();
        if (images == null || images.isEmpty()) {
            return null;
        }
        ProductImageResponse chosen = images.stream().filter(ProductImageResponse::isMain).findFirst()
                .orElse(images.get(0));
        return chosen.cardUrl() != null ? chosen.cardUrl() : chosen.url();
    }
}
