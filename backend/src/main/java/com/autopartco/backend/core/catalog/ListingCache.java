package com.autopartco.backend.core.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.cache.TtlCache;

/**
 * Caché de lectura del catálogo público (listados publicados y opciones de filtro). Va fuera de las transacciones de
 * {@link ListingQueries} para que un acierto no abra conexión a la base. Los listados caducan a los 30 s y se vacían
 * al escribir inventario, productos o imágenes ({@link TtlCache#invalidateAll()}); las opciones caducan a los 5 min.
 * El checkout no la usa: valida con {@link ListingQueries#purchasableListings}.
 */
@Component
public class ListingCache {
    private final ListingQueries queries;
    private final TtlCache<VehicleFilter, List<ProductResponse>> published = new TtlCache<>(30);
    private final TtlCache<UUID, ProductResponse> detail = new TtlCache<>(30);
    private final TtlCache<String, Object> options = new TtlCache<>(300);

    public ListingCache(ListingQueries queries) {
        this.queries = queries;
    }

    public List<ProductResponse> published(VehicleFilter filter) {
        VehicleFilter key = filter == null || filter.isEmpty() ? VehicleFilter.none() : filter;
        return published.get(key, () -> queries.publishedListings(key));
    }

    public ProductResponse detail(UUID productId) {
        return detail.get(productId, () -> queries.publishedListing(productId));
    }

    public ProductOptionsResponse options() {
        return cast("options", queries::options);
    }

    public List<ProductOptionsResponse.ManufacturerOption> manufacturers() {
        return cast("manufacturers", queries::manufacturers);
    }

    public List<ProductOptionsResponse.CategoryOption> categories() {
        return cast("categories", queries::categories);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public List<ProductOptionsResponse.CompatibilityOption> compatibilities() {
        return (List<ProductOptionsResponse.CompatibilityOption>) options.get("compatibilities", queries::compatibilities);
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(String key, java.util.function.Supplier<T> loader) {
        return (T) options.get(key, loader::get);
    }
}
