package com.autopartco.backend.core.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.TtlCache;
import com.autopartco.backend.shared.cache.TypedCache;
import com.autopartco.backend.shared.events.CatalogOptionsChanged;

/**
 * Caché de lectura del catálogo público (listados publicados y opciones de filtro). Va fuera de las transacciones de
 * {@link ListingQueries} para que un acierto no abra conexión a la base. Los listados caducan a los 30 s y se vacían
 * al escribir inventario, productos o imágenes ({@link TtlCache#invalidateAll()}); las opciones de filtro van en
 * {@code catalog:options} (1 h, compartida en Redis si está configurado) y se vacían al cambiar categorías o
 * fabricantes.
 * El checkout no la usa: valida con {@link ListingQueries#purchasableListings}.
 */
@Component
public class ListingCache {
    private final ListingQueries queries;
    private final TtlCache<VehicleFilter, List<ProductResponse>> published = new TtlCache<>(30);
    private final TtlCache<UUID, ProductResponse> detail = new TtlCache<>(30);
    private final TtlCache<String, Object> options = new TtlCache<>(300);
    /** {@code catalog:options} (1 h; Redis si está configurado). Se vacía con {@link CatalogOptionsChanged}. */
    private final TypedCache<ProductOptionsResponse> catalogOptions;

    @Autowired
    public ListingCache(ListingQueries queries, ResilientCacheManager caches) {
        this.queries = queries;
        this.catalogOptions = caches == null ? null
                : caches.typed(AppCaches.CATALOG_OPTIONS, ProductOptionsResponse.class);
    }

    /** Opciones con la caché local de 5 min (pruebas). */
    public ListingCache(ListingQueries queries) {
        this(queries, null);
    }

    public List<ProductResponse> published(VehicleFilter filter) {
        VehicleFilter key = filter == null || filter.isEmpty() ? VehicleFilter.none() : filter;
        return published.get(key, () -> queries.publishedListings(key));
    }

    public ProductResponse detail(UUID productId) {
        return detail.get(productId, () -> queries.publishedListing(productId));
    }

    /**
     * Listados con el precio del comprador. Solo un comprador con segmentos puede recibir un precio distinto del
     * general, así que solo él consulta sin caché; la caché nunca guarda precios personalizados.
     */
    public List<ProductResponse> published(VehicleFilter filter, BuyerContext buyer) {
        if (buyer == null || !buyer.hasSegments()) {
            return published(filter);
        }
        return queries.publishedListings(filter == null || filter.isEmpty() ? VehicleFilter.none() : filter, buyer);
    }

    /** Detalle con el precio del comprador; misma regla de caché que {@link #published(VehicleFilter, BuyerContext)}. */
    public ProductResponse detail(UUID productId, BuyerContext buyer) {
        if (buyer == null || !buyer.hasSegments()) {
            return detail(productId);
        }
        return queries.publishedListing(productId, buyer);
    }

    public ProductOptionsResponse options() {
        if (catalogOptions == null) {
            return cast("options", queries::options);
        }
        return catalogOptions.get("options", queries::options);
    }

    public List<ProductOptionsResponse.ManufacturerOption> manufacturers() {
        return options().manufacturers();
    }

    public List<ProductOptionsResponse.CategoryOption> categories() {
        return options().categories();
    }

    /** Tras confirmar un cambio de categorías o fabricantes. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCatalogOptionsChanged(CatalogOptionsChanged event) {
        options.clear();
        if (catalogOptions != null) {
            catalogOptions.clear();
        }
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
