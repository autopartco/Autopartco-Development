package com.autopartco.backend.marketplace.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductOptionsResponse;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Catálogo publicado y opciones de filtro, para compradores (público). */
@RestController
@AllArgsConstructor
public class ProductBrowseController {
    private static final String PRODUCTS = ApiPaths.MARKETPLACE + "/products";
    private static final String OPTIONS = ApiPaths.MARKETPLACE + "/product-options";

    private final ListingQueries listingQueries;

    @GetMapping(PRODUCTS)
    public List<ProductResponse> published() {
        return listingQueries.publishedListings();
    }

    @GetMapping(PRODUCTS + "/{productId}")
    public ProductResponse detail(@PathVariable UUID productId) {
        return listingQueries.publishedListing(productId);
    }

    @GetMapping(OPTIONS)
    public ProductOptionsResponse options() {
        return listingQueries.options();
    }

    @GetMapping(OPTIONS + "/manufacturers")
    public List<ProductOptionsResponse.ManufacturerOption> manufacturers() {
        return listingQueries.manufacturers();
    }

    @GetMapping(OPTIONS + "/categories")
    public List<ProductOptionsResponse.CategoryOption> categories() {
        return listingQueries.categories();
    }

    @GetMapping(OPTIONS + "/compatibilities")
    public List<ProductOptionsResponse.CompatibilityOption> compatibilities() {
        return listingQueries.compatibilities();
    }
}
