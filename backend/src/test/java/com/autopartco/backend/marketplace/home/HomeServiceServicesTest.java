package com.autopartco.backend.marketplace.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.pricing.ServicePrice;
import com.autopartco.backend.core.search.HomeFacts;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.ServiceHit;
import com.autopartco.backend.core.search.ServiceSearchQuery;
import com.autopartco.backend.core.search.ServiceSearchResult;
import com.autopartco.backend.core.search.ServiceSearchService;

/** Bloque de servicios del inicio, con y sin área. */
class HomeServiceServicesTest {
    private final HomeFacts facts = mock(HomeFacts.class);
    private final SearchService searchService = mock(SearchService.class);
    private final ServiceSearchService serviceSearch = mock(ServiceSearchService.class);
    private final HomeService home = new HomeService(facts, searchService, new InMemoryHomeCache(), serviceSearch);

    private final ServiceHit nearby = hit("Alineación", null);
    private final ServiceHit promoted = hit("Cambio de aceite", new ServicePrice.Applied(UUID.randomUUID(), "Aceite",
            "-15%", OffsetDateTime.parse("2026-12-01T00:00:00Z")));

    @BeforeEach
    void setUp() {
        when(facts.stats()).thenReturn(new HomeFacts.Stats(1, 1, 1));
        when(facts.popularSearches()).thenReturn(List.of());
        when(facts.categories()).thenReturn(List.of());
        when(searchService.catalog(any(), any())).thenReturn(new SearchResult(List.of(), 0, 12, 0, false, null, null, List.of()));
        when(searchService.featuredAds(any(), any(), any())).thenReturn(List.of());
        when(serviceSearch.search(argThat(query -> query != null && !Boolean.TRUE.equals(query.onSale()))))
                .thenReturn(result(nearby));
        when(serviceSearch.search(argThat(query -> query != null && Boolean.TRUE.equals(query.onSale()))))
                .thenReturn(result(promoted));
        when(serviceSearch.categories(any())).thenReturn(List.of(
                new ServiceSearchResult.CategoryCount(ServiceCategory.LUBRICATION, 7)));
    }

    @Test
    void withAreaUsesTheNearestServicesWithinTheRadius() {
        HomeResponse response = home.home(HomeArea.of(new BigDecimal("4.61"), new BigDecimal("-74.08"), "11001"),
                "visitor");

        assertThat(response.services().nearby()).extracting(card -> card.name()).containsExactly("Alineación");
        assertThat(response.services().promoted()).singleElement().satisfies(card -> {
            assertThat(card.promotion().label()).isEqualTo("-15%");
            assertThat(card.priceFrom()).isEqualByComparingTo("51000");
            assertThat(card.basePriceFrom()).isEqualByComparingTo("60000");
        });
        assertThat(response.services().categories()).singleElement().satisfies(category -> {
            assertThat(category.category()).isEqualTo(ServiceCategory.LUBRICATION);
            assertThat(category.count()).isEqualTo(7);
            assertThat(category.label()).isNotBlank();
        });
        verify(serviceSearch).search(argThat(query -> query.sort() == ServiceSearchQuery.Sort.NEAREST
                && Integer.valueOf(HomeService.NEARBY_RADIUS_KM).equals(query.radiusKm())
                && !Boolean.TRUE.equals(query.onSale())));
    }

    @Test
    void withoutAreaUsesTheCityWithMoreServices() {
        when(serviceSearch.topCity()).thenReturn(Optional.of("13001"));

        HomeResponse response = home.home(HomeArea.of(null, null, null), "visitor");

        assertThat(response.services().nearby()).hasSize(1);
        verify(serviceSearch).search(argThat(query -> "13001".equals(query.cityCode())
                && query.sort() == ServiceSearchQuery.Sort.RATING && !Boolean.TRUE.equals(query.onSale())));
        verify(serviceSearch).categories("13001");
    }

    @Test
    void aFailureLeavesTheBlockEmpty() {
        when(serviceSearch.search(any())).thenThrow(new IllegalStateException("db"));

        HomeResponse response = home.home(HomeArea.of(null, null, "11001"), "visitor");

        assertThat(response.services().nearby()).isEmpty();
        assertThat(response.services().promoted()).isEmpty();
        assertThat(response.services().categories()).isEmpty();
    }

    private static ServiceSearchResult result(ServiceHit hit) {
        return new ServiceSearchResult(List.of(hit), 0, 8, 1, null);
    }

    private static ServiceHit hit(String name, ServicePrice.Applied promotion) {
        UUID storeId = UUID.randomUUID();
        BigDecimal price = promotion == null ? new BigDecimal("60000") : new BigDecimal("51000");
        return new ServiceHit(UUID.randomUUID(), name, ServiceCategory.LUBRICATION, "desc", null, price,
                new BigDecimal("60000"), promotion != null, 45, true,
                new ServiceHit.Store(storeId, "Taller", null, null, "SERVICES"),
                new ServiceHit.Branch(UUID.randomUUID(), "Centro", "Bogotá", "Calle 1", null, null, null), null,
                promotion);
    }
}
