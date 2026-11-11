package com.autopartco.backend.marketplace.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.MarketplaceSearch;
import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.analytics.SearchText;
import com.autopartco.backend.core.catalog.ListingCache;
import com.autopartco.backend.core.catalog.ProductOptionsResponse;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.catalog.VehicleFilter;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionExposureRecorder;
import com.autopartco.backend.core.search.SearchQuery;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.SearchSort;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.Paging;

import lombok.AllArgsConstructor;

/** Catálogo publicado y opciones de filtro, para compradores (público). */
@RestController
@AllArgsConstructor
public class ProductBrowseController {
    private static final String PRODUCTS = ApiPaths.MARKETPLACE + "/products";
    private static final String OPTIONS = ApiPaths.MARKETPLACE + "/product-options";

    static final String SESSION_HEADER = "X-Session-Id";

    private final ListingCache listingQueries;
    private final SearchLog searchLog;
    private final BuyerContexts buyerContexts;
    private final PromotionExposureRecorder exposureRecorder;
    private final BranchService branchService;
    private final SearchService searchService;

    /**
     * Listados publicados. Filtro opcional por vehículo ({@code makeId}, {@code modelId}, {@code versionId}): solo
     * productos con una regla de vehículo que lo cubra. {@code modelId} exige {@code makeId} y {@code versionId}
     * exige {@code modelId} (si no, 400 {@code VEHICLE_FILTER_INVALID}). Filtros opcionales {@code q} (texto, sin
     * distinguir mayúsculas ni tildes) y {@code categoryId}.
     *
     * <p>Contrato de compatibilidad: el contrato nuevo es {@code GET /marketplace/search}. Con el índice de búsqueda
     * listo, esta ruta usa el mismo motor ({@link SearchService#catalog}) sin interpretar {@code q}, con orden por
     * relevancia si hay texto y por nombre si no; mientras el índice esté vacío, sigue con la consulta anterior.</p>
     *
     * <p>Si hay algún criterio (texto, vehículo o categoría) registra la búsqueda en {@code core.analytics.SearchLog}
     * de forma asíncrona y best-effort: un fallo del registro no afecta la respuesta. La sesión se toma de la cabecera
     * {@value #SESSION_HEADER} (solo se guarda su hash diario).</p>
     *
     * <p>{@code branchId} deja solo los listados de esa sucursal y exige {@code storeId} de su almacén (si no, 400
     * {@code BRANCH_NOT_IN_STORE}). Se aplica antes de elegir un resultado por producto.</p>
     */
    @GetMapping(PRODUCTS)
    public ResponseEntity<List<ProductResponse>> published(
            @RequestParam(required = false) UUID makeId,
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) UUID versionId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
            Authentication authentication
    ) {
        if (branchId != null && (storeId == null || !branchService.isInStore(storeId, branchId))) {
            throw new BadRequestException("BRANCH_NOT_IN_STORE", "La sucursal no pertenece al almacén indicado.");
        }
        VehicleFilter vehicle = new VehicleFilter(makeId, modelId, versionId);
        BuyerContext buyer = buyerContexts.forBrowsing(authentication);
        ResponseEntity<List<ProductResponse>> response;
        long total;
        if (searchService.indexReady()) {
            boolean paged = Paging.requested(page, size);
            int p = Paging.page(page);
            int s = paged ? Paging.size(size) : SearchService.MAX_CATALOG_SIZE;
            SearchResult result = searchService.catalog(SearchQuery.builder()
                    .q(q)
                    .makeId(vehicle.makeId())
                    .modelId(vehicle.modelId())
                    .versionId(vehicle.versionId())
                    .categoryId(categoryId)
                    .storeId(storeId)
                    .branchId(branchId)
                    .sort(SearchText.normalize(q) == null ? SearchSort.NAME : SearchSort.RELEVANCE)
                    .interpret(false)
                    .facets(false)
                    .page(paged ? p : 0)
                    .size(s)
                    .build(), buyer);
            List<ProductResponse> items = result.items().stream().map(SearchResult.Hit::listing).toList();
            total = result.total();
            response = paged ? Paging.of(items, total, p, s) : ResponseEntity.ok(items);
        } else {
            List<ProductResponse> listings = legacyListings(vehicle, q, categoryId, storeId, branchId, buyer);
            total = listings.size();
            response = Paging.slice(listings, page, size);
        }
        CatalogTracking.recordSearch(searchLog, new MarketplaceSearch(q, makeId, modelId, versionId, null,
                categoryId, null, (int) Math.min(Integer.MAX_VALUE, total), sessionId,
                CatalogTracking.isAuthenticated(authentication)));
        CatalogTracking.recordExposures(exposureRecorder, buyer, response.getBody());
        return response;
    }

    /** Consulta anterior al índice de búsqueda: todos los listados publicados filtrados en memoria. */
    private List<ProductResponse> legacyListings(VehicleFilter vehicle, String q, UUID categoryId, UUID storeId,
            UUID branchId, BuyerContext buyer) {
        List<ProductResponse> listings = listingQueries.published(vehicle, buyer);
        String text = SearchText.normalize(q);
        if (text != null || categoryId != null || storeId != null || branchId != null) {
            listings = listings.stream()
                    .filter(listing -> categoryId == null || categoryId.equals(listing.categoryId()))
                    .filter(listing -> storeId == null || storeId.equals(listing.storeId()))
                    .filter(listing -> branchId == null || branchId.equals(listing.branchId()))
                    .filter(listing -> text == null || matches(listing, text))
                    .toList();
        }
        return cheapestPerProduct(listings);
    }

    /**
     * Un resultado por producto: el listado de menor precio base, el mismo criterio con que el detalle elige la
     * sucursal, de modo que la tarjeta y la ficha muestran el mismo precio. Conserva el orden del listado.
     */
    private static List<ProductResponse> cheapestPerProduct(List<ProductResponse> listings) {
        Map<UUID, ProductResponse> best = new LinkedHashMap<>();
        for (ProductResponse listing : listings) {
            best.merge(listing.id(), listing,
                    (current, candidate) -> candidate.price().compareTo(current.price()) < 0 ? candidate : current);
        }
        return List.copyOf(best.values());
    }

    /** Todas las palabras del texto aparecen en el nombre, SKU, referencia o fabricante. */
    private static boolean matches(ProductResponse listing, String text) {
        String haystack = Stream.of(listing.name(), listing.sku(), listing.partNumber(), listing.manufacturerName())
                .map(SearchText::normalize)
                .filter(value -> value != null)
                .collect(Collectors.joining(" "));
        return Stream.of(text.split(" ")).allMatch(haystack::contains);
    }

    /**
     * Listado publicado más barato del producto, con el precio del comprador autenticado (anónimo sin sesión). La
     * sesión es opcional: la ruta es pública.
     */
    @GetMapping(PRODUCTS + "/{productId}")
    public ProductResponse detail(@PathVariable UUID productId, Authentication authentication) {
        BuyerContext buyer = buyerContexts.forBrowsing(authentication);
        ProductResponse listing = listingQueries.detail(productId, buyer);
        CatalogTracking.recordExposures(exposureRecorder, buyer, List.of(listing));
        return listing;
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
