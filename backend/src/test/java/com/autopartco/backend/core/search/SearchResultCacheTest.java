package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.SharedCounters;

/** Claves de {@code search:results}: versión de catálogo, parámetros normalizados y grupo de precio. */
class SearchResultCacheTest {
    private final SearchResultCache cache = new SearchResultCache(ResilientCacheManager.local(), SharedCounters.local());
    private final AtomicInteger loads = new AtomicInteger();

    private SearchResultCache.Organic load() {
        loads.incrementAndGet();
        return new SearchResultCache.Organic(List.of(), 0, false, null, null);
    }

    private static SearchCriteria criteria(SearchQuery query) {
        return SearchCriteria.of(query, ParsedQuery.EMPTY);
    }

    private SearchResultCache.Organic get(SearchQuery query, BuyerContext buyer) {
        return cache.get(query.q(), criteria(query), query, buyer, this::load);
    }

    @Test
    void sameNormalizedQueryHitsAndCatalogChangeMisses() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        get(SearchQuery.builder().q("Pastillas  Freno").manufacturerIds(List.of(a, b))
                .priceMin(new BigDecimal("100.00")).build(), BuyerContext.anonymous());
        get(SearchQuery.builder().q(" pastillas freno ").manufacturerIds(List.of(b, a))
                .priceMin(new BigDecimal("100")).build(), BuyerContext.anonymous());
        assertThat(loads).hasValue(1);

        get(SearchQuery.builder().q("pastillas freno").page(1).build(), BuyerContext.anonymous());
        assertThat(loads).hasValue(2);

        cache.catalogChanged();
        get(SearchQuery.builder().q("Pastillas  Freno").manufacturerIds(List.of(a, b))
                .priceMin(new BigDecimal("100.00")).build(), BuyerContext.anonymous());
        assertThat(loads).hasValue(3);
    }

    @Test
    void priceGroupSeparatesSegmentsAndSkipsIndividualPromotions() {
        SearchQuery query = SearchQuery.builder().q("bujia").build();
        BuyerContext withoutSegments = new BuyerContext(UUID.randomUUID(), Set.of(), Map.of());
        BuyerContext frequent = new BuyerContext(UUID.randomUUID(), Set.of("FREQUENT"), Map.of());
        BuyerContext perStore = new BuyerContext(UUID.randomUUID(), Set.of(),
                Map.of(UUID.randomUUID(), Set.of("STORE_FREQUENT")));

        get(query, BuyerContext.anonymous());
        get(query, withoutSegments);
        assertThat(loads).hasValue(1);
        get(query, frequent);
        get(query, frequent);
        assertThat(loads).hasValue(2);
        get(query, perStore);
        get(query, perStore);
        assertThat(loads).hasValue(4);

        assertThat(SearchResultCache.priceGroup(perStore)).isEmpty();
        assertThat(SearchResultCache.priceGroup(frequent)).contains("P:FREQUENT");
        assertThat(SearchResultCache.priceGroup(null)).contains("ALL");
    }
}
