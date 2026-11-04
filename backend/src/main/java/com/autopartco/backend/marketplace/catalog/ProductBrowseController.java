package com.autopartco.backend.marketplace.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductOptionsResponse;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.catalog.VehicleFilter;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Catálogo publicado y opciones de filtro, para compradores (público). */
@RestController
@AllArgsConstructor
public class ProductBrowseController {
    private static final String PRODUCTS = ApiPaths.MARKETPLACE + "/products";
    private static final String OPTIONS = ApiPaths.MARKETPLACE + "/product-options";

    private final ListingQueries listingQueries;

    /**
     * Listados publicados. Filtro opcional por vehículo ({@code makeId}, {@code modelId}, {@code versionId}): solo
     * productos con una regla de vehículo que lo cubra. {@code modelId} exige {@code makeId} y {@code versionId}
     * exige {@code modelId} (si no, 400 {@code VEHICLE_FILTER_INVALID}).
     */
    @GetMapping(PRODUCTS)
    public List<ProductResponse> published(
            @RequestParam(required = false) UUID makeId,
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) UUID versionId
    ) {
        return listingQueries.publishedListings(new VehicleFilter(makeId, modelId, versionId));
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

    /** Obsoleto: lista de la tabla {@code compatibilities}; los productos usan reglas de vehículo. */
    @Deprecated
    @GetMapping(OPTIONS + "/compatibilities")
    public List<ProductOptionsResponse.CompatibilityOption> compatibilities() {
        return listingQueries.compatibilities();
    }
}
