package com.autopartco.backend.marketplace.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.search.HomeFacts;
import com.autopartco.backend.core.search.SearchQuery;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.SearchSort;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class HomeControllerTest {
    private static final String URL = "/api/v1/marketplace/home";

    private final HomeFacts facts = mock(HomeFacts.class);
    private final SearchService searchService = mock(SearchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(facts.stats()).thenReturn(new HomeFacts.Stats(12, 5, 340));
        when(facts.popularSearches()).thenReturn(List.of("pastillas de freno", "bujias"));
        when(facts.categories()).thenReturn(List.of(new HomeFacts.CategoryCount(id(1), "FRENOS", 40)));
        when(searchService.featuredAds(any(), any(), any())).thenReturn(List.of());
        mvc = standalone(new InMemoryHomeCache());
    }

    private MockMvc standalone(HomeCache cache) {
        return MockMvcBuilders.standaloneSetup(new HomeController(new HomeService(facts, searchService, cache)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
    }

    private static SearchResult.Hit hit(int n, String km) {
        ProductResponse listing = new ProductResponse(id(n), id(900), id(901), "Centro", "Producto " + n, "SKU-" + n,
                null, null, new BigDecimal("85000.00"), new BigDecimal("80000.00"), new BigDecimal("5000.00"), null, 3,
                null, "Bosch", true, null, "ACTIVE", false, Map.of(), List.of(), List.of());
        return new SearchResult.Hit(listing, km == null ? null : new BigDecimal(km));
    }

    private static List<SearchResult.Hit> hits(int from, int count) {
        List<SearchResult.Hit> hits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            hits.add(hit(from + i, "1." + i));
        }
        return hits;
    }

    private static SearchResult page(List<SearchResult.Hit> items) {
        return new SearchResult(items, 0, items.size(), items.size(), false, null, null);
    }

    private void catalogReturns(SearchSort sort, List<SearchResult.Hit> items) {
        when(searchService.catalog(any(), any())).thenAnswer(invocation -> {
            SearchQuery query = invocation.getArgument(0);
            return page(query.sort() == sort ? items : List.of());
        });
    }

    @Test
    void withLocationReturnsNearestWithinRadiusAndContractShape() throws Exception {
        when(searchService.catalog(any(), any())).thenAnswer(invocation -> {
            SearchQuery query = invocation.getArgument(0);
            return page(query.sort() == SearchSort.NEAREST ? hits(1, 3) : hits(100, 2));
        });

        mvc.perform(get(URL).param("lat", "4.6512").param("lng", "-74.0834").param("cityCode", "11001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.stats.stores").value(12))
                .andExpect(jsonPath("$.stats.cities").value(5))
                .andExpect(jsonPath("$.stats.products").value(340))
                .andExpect(jsonPath("$.popularSearches[0]").value("pastillas de freno"))
                .andExpect(jsonPath("$.categories[0].code").value("FRENOS"))
                .andExpect(jsonPath("$.categories[0].productCount").value(40))
                .andExpect(jsonPath("$.nearby.length()").value(3))
                .andExpect(jsonPath("$.nearby[0].id").value(id(1).toString()))
                .andExpect(jsonPath("$.nearby[0].name").value("Producto 1"))
                .andExpect(jsonPath("$.nearby[0].distanceKm").value(1.0))
                .andExpect(jsonPath("$.nearby[0].sponsored").value(false))
                .andExpect(jsonPath("$.nearby[0].listing").doesNotExist());

        ArgumentCaptor<SearchQuery> query = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchService, atLeast(1)).catalog(query.capture(), eq(BuyerContext.anonymous()));
        SearchQuery nearest = query.getAllValues().stream().filter(q -> q.sort() == SearchSort.NEAREST).findFirst()
                .orElseThrow();
        assertThat(nearest.radiusKm()).isEqualTo(50);
        assertThat(nearest.size()).isEqualTo(12);
        assertThat(nearest.cityCode()).isEqualTo("11001");
        assertThat(nearest.lat()).isEqualByComparingTo("4.7");
        assertThat(nearest.lng()).isEqualByComparingTo("-74.1");
        assertThat(nearest.facets()).isFalse();
    }

    @Test
    void nearbyFallsBackToNearestWithoutRadiusWhenNothingIsWithin50Km() throws Exception {
        when(searchService.catalog(any(), any())).thenAnswer(invocation -> {
            SearchQuery query = invocation.getArgument(0);
            return page(query.sort() == SearchSort.NEAREST && query.radiusKm() == null ? hits(1, 2) : List.of());
        });

        mvc.perform(get(URL).param("lat", "4.65").param("lng", "-74.08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearby.length()").value(2));
    }

    @Test
    void withoutLocationUsesNewestAndNoRadius() throws Exception {
        catalogReturns(SearchSort.NEWEST, hits(1, 4));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearby.length()").value(4));

        ArgumentCaptor<SearchQuery> query = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchService, atLeast(1)).catalog(query.capture(), any());
        assertThat(query.getAllValues()).allSatisfy(q -> {
            assertThat(q.radiusKm()).isNull();
            assertThat(q.lat()).isNull();
            assertThat(q.sort()).isNotEqualTo(SearchSort.NEAREST);
        });
    }

    @Test
    void featuredPutsAdsFirstThenOrganicWithoutDuplicates() throws Exception {
        // Ads: productos 1 y 2. Pool orgánico (RATING): 2 (duplicado), 3, 4, 5. Cerca de ti: 3.
        when(searchService.catalog(any(), any())).thenAnswer(invocation -> {
            SearchQuery query = invocation.getArgument(0);
            return page(switch (query.sort()) {
                case NEWEST -> List.of(hit(3, null));
                case RATING -> List.of(hit(2, null), hit(3, null), hit(4, null), hit(5, null));
                default -> List.of();
            });
        });
        when(searchService.featuredAds(any(), any(), any())).thenReturn(List.of(
                new SearchResult.Sponsored(hit(1, null), "tok-1"), new SearchResult.Sponsored(hit(2, null), "tok-2")));

        mvc.perform(get(URL).header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured.length()").value(5))
                .andExpect(jsonPath("$.featured[0].id").value(id(1).toString()))
                .andExpect(jsonPath("$.featured[0].sponsored").value(true))
                .andExpect(jsonPath("$.featured[0].adImpressionToken").value("tok-1"))
                .andExpect(jsonPath("$.featured[1].adImpressionToken").value("tok-2"))
                // Orgánicos: primero los que no están en «Cerca de ti» (4, 5) y luego los que sí (3); el 2 ya es anuncio.
                .andExpect(jsonPath("$.featured[2].id").value(id(4).toString()))
                .andExpect(jsonPath("$.featured[2].sponsored").value(false))
                .andExpect(jsonPath("$.featured[2].adImpressionToken").doesNotExist())
                .andExpect(jsonPath("$.featured[3].id").value(id(5).toString()))
                .andExpect(jsonPath("$.featured[4].id").value(id(3).toString()));

        verify(searchService).featuredAds(isNull(), eq("s:sess"), eq(BuyerContext.anonymous()));
    }

    @Test
    void featuredNeverExceedsTwelve() throws Exception {
        when(searchService.catalog(any(), any())).thenReturn(page(hits(100, 24)));
        List<SearchResult.Sponsored> ads = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            ads.add(new SearchResult.Sponsored(hit(i, null), "tok-" + i));
        }
        when(searchService.featuredAds(any(), any(), any())).thenReturn(ads);

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured.length()").value(12))
                .andExpect(jsonPath("$.featured[11].sponsored").value(true));
    }

    @Test
    void adsAreRequestedOnEveryCallButPublicContentIsCachedPerArea() throws Exception {
        when(searchService.catalog(any(), any())).thenReturn(page(hits(1, 2)));

        // 4,61/-74,06 y 4,64/-74,09 redondean a la misma área (4,6/-74,1); la tercera cae en otra.
        mvc.perform(get(URL).param("lat", "4.61").param("lng", "-74.06")).andExpect(status().isOk());
        mvc.perform(get(URL).param("lat", "4.64").param("lng", "-74.09")).andExpect(status().isOk());
        verify(facts, times(1)).stats();
        verify(searchService, times(2)).featuredAds(any(), any(), any());

        mvc.perform(get(URL).param("lat", "6.25").param("lng", "-75.56")).andExpect(status().isOk());
        verify(facts, times(2)).stats();
        verify(searchService, times(3)).featuredAds(any(), any(), any());
    }

    @Test
    void invalidParametersAre400WithTheirCode() throws Exception {
        mvc.perform(get(URL).param("lat", "4.6")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("HOME_POINT_INCOMPLETE"));
        mvc.perform(get(URL).param("lat", "40.4").param("lng", "-3.7")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("HOME_POINT_OUTSIDE_COVERAGE"));
        mvc.perform(get(URL).param("cityCode", "bogota")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("HOME_CITY_INVALID"));
    }

    @Test
    void emptyPlatformReturnsEmptyListsNotErrors() throws Exception {
        when(facts.stats()).thenReturn(new HomeFacts.Stats(0, 0, 0));
        when(facts.popularSearches()).thenReturn(List.of());
        when(facts.categories()).thenReturn(List.of());
        when(searchService.catalog(any(), any())).thenReturn(page(List.of()));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.popularSearches").isEmpty())
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.nearby").isEmpty())
                .andExpect(jsonPath("$.featured").isEmpty());
    }

    @Test
    void rateLimitsPerIp() throws Exception {
        when(searchService.catalog(any(), any())).thenReturn(page(List.of()));
        for (int i = 0; i < HomeController.HOMES_PER_MINUTE; i++) {
            mvc.perform(get(URL)).andExpect(status().isOk());
        }
        mvc.perform(get(URL)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("HOME_RATE_LIMITED"));
    }
}
