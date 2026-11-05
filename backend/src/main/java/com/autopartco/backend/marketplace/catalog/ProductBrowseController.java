package com.autopartco.backend.marketplace.catalog;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.MarketplaceSearch;
import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.analytics.SearchText;
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

    static final String SESSION_HEADER = "X-Session-Id";

    private final ListingQueries listingQueries;
    private final SearchLog searchLog;

    /**
     * Listados publicados. Filtro opcional por vehículo ({@code makeId}, {@code modelId}, {@code versionId}): solo
     * productos con una regla de vehículo que lo cubra. {@code modelId} exige {@code makeId} y {@code versionId}
     * exige {@code modelId} (si no, 400 {@code VEHICLE_FILTER_INVALID}). Filtros opcionales {@code q} (todas sus
     * palabras en nombre, SKU, referencia o fabricante, sin distinguir mayúsculas ni tildes) y {@code categoryId}.
     *
     * <p>Si hay algún criterio (texto, vehículo o categoría) registra la búsqueda en {@code core.analytics.SearchLog}
     * de forma asíncrona y best-effort: un fallo del registro no afecta la respuesta. La sesión se toma de la cabecera
     * {@value #SESSION_HEADER} (solo se guarda su hash diario).</p>
     */
    @GetMapping(PRODUCTS)
    public List<ProductResponse> published(
            @RequestParam(required = false) UUID makeId,
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) UUID versionId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
            Authentication authentication
    ) {
        List<ProductResponse> listings = listingQueries.publishedListings(new VehicleFilter(makeId, modelId, versionId));
        String text = SearchText.normalize(q);
        if (text != null || categoryId != null) {
            listings = listings.stream()
                    .filter(listing -> categoryId == null || categoryId.equals(listing.categoryId()))
                    .filter(listing -> text == null || matches(listing, text))
                    .toList();
        }
        recordSearch(new MarketplaceSearch(q, makeId, modelId, versionId, null, categoryId, null, listings.size(),
                sessionId, isAuthenticated(authentication)));
        return listings;
    }

    private void recordSearch(MarketplaceSearch search) {
        try {
            searchLog.record(search);
        } catch (RuntimeException ignored) {
            // Best-effort: el registro de búsquedas nunca rompe el catálogo.
        }
    }

    /** Todas las palabras del texto aparecen en el nombre, SKU, referencia o fabricante. */
    private static boolean matches(ProductResponse listing, String text) {
        String haystack = Stream.of(listing.name(), listing.sku(), listing.partNumber(), listing.manufacturerName())
                .map(SearchText::normalize)
                .filter(value -> value != null)
                .collect(Collectors.joining(" "));
        return Stream.of(text.split(" ")).allMatch(haystack::contains);
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
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
