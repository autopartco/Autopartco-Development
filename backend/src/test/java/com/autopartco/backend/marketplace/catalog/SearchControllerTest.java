package com.autopartco.backend.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.analytics.MarketplaceSearch;
import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionExposureRecorder;
import com.autopartco.backend.core.search.SearchInterpretation;
import com.autopartco.backend.core.search.SearchQuery;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.SearchSort;
import com.autopartco.backend.core.search.Suggestions;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class SearchControllerTest {
    private static final String URL = "/api/v1/marketplace/search";
    private static final UUID PRODUCT = UUID.fromString("3f0c9a52-6f43-4a5e-9a51-2a1f7c0d9b10");
    private static final UUID MODEL = UUID.fromString("9b7d1c2e-0a4f-4d33-8e1b-5c6a7f8e9d01");
    private static final UUID MAKE = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");

    private final SearchService searchService = mock(SearchService.class);
    private final Suggestions suggestions = mock(Suggestions.class);
    private final SearchLog searchLog = mock(SearchLog.class);
    private final BuyerContexts buyerContexts = mock(BuyerContexts.class);
    private final PromotionExposureRecorder exposureRecorder = mock(PromotionExposureRecorder.class);
    private final BranchService branchService = mock(BranchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(buyerContexts.forBrowsing(any())).thenReturn(BuyerContext.anonymous());
        // El servicio real valida; aquí se replica para comprobar que el error llega como 400.
        when(searchService.search(any(), any())).thenAnswer(invocation -> {
            invocation.<SearchQuery>getArgument(0).validate(SearchQuery.MAX_SIZE, SearchQuery.MAX_PAGE);
            return result();
        });
        mvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService, suggestions, searchLog,
                        buyerContexts, exposureRecorder, branchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static SearchResult result() {
        ProductResponse listing = new ProductResponse(PRODUCT, UUID.randomUUID(), UUID.randomUUID(), "Centro",
                "Pastillas de freno", "SKU-1", "BKR6E", null, new BigDecimal("85000.00"), new BigDecimal("80000.00"),
                new BigDecimal("5000.00"), "Promo", 3, null, "Bosch", true, null, "ACTIVE", false, Map.of(), List.of(),
                List.of());
        SearchInterpretation interpreted = new SearchInterpretation("pastillas", MAKE, "Chevrolet", MODEL, "Spark",
                2015, "11001", "Bogotá, D.C.", null);
        SearchResult.Facets facets = new SearchResult.Facets(List.of(), List.of(), List.of(),
                new SearchResult.PriceRange(new BigDecimal("18000"), new BigDecimal("420000")), 1, 1);
        return new SearchResult(List.of(new SearchResult.Hit(listing, new BigDecimal("3.2"))), 0, 24, 312, false,
                interpreted, facets);
    }

    @Test
    void returnsFlatItemsWithDistanceInterpretationFacetsAndEmptySponsored() throws Exception {
        mvc.perform(get(URL).param("q", "pastillas spark 2015 bogota").param("lat", "4.65").param("lng", "-74.08")
                        .param("sort", "nearest").param("manufacturerId", PRODUCT.toString(), MODEL.toString())
                        .header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Pastillas de freno"))
                .andExpect(jsonPath("$.items[0].effectivePrice").value(80000.0))
                .andExpect(jsonPath("$.items[0].distanceKm").value(3.2))
                .andExpect(jsonPath("$.items[0].sponsored").value(false))
                .andExpect(jsonPath("$.items[0].listing").doesNotExist())
                .andExpect(jsonPath("$.total").value(312))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.fuzzy").value(false))
                .andExpect(jsonPath("$.interpreted.modelId").value(MODEL.toString()))
                .andExpect(jsonPath("$.interpreted.cityCode").value("11001"))
                .andExpect(jsonPath("$.facets.price.max").value(420000))
                .andExpect(jsonPath("$.sponsored").isEmpty());

        ArgumentCaptor<SearchQuery> query = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchService).search(query.capture(), eq(BuyerContext.anonymous()));
        assertThat(query.getValue().sort()).isEqualTo(SearchSort.NEAREST);
        assertThat(query.getValue().manufacturerIds()).containsExactly(PRODUCT, MODEL);
        assertThat(query.getValue().lat()).isEqualByComparingTo("4.65");
        ArgumentCaptor<MarketplaceSearch> search = ArgumentCaptor.forClass(MarketplaceSearch.class);
        verify(searchLog).record(search.capture());
        assertThat(search.getValue().resultsCount()).isEqualTo(312);
        assertThat(search.getValue().modelId()).isEqualTo(MODEL);
        assertThat(search.getValue().makeId()).isEqualTo(MAKE);
        assertThat(search.getValue().vehicleYear()).isEqualTo(2015);
        assertThat(search.getValue().municipalityCode()).isEqualTo("11001");
        assertThat(search.getValue().sessionId()).isEqualTo("sess");
        verify(exposureRecorder, never()).record(any(), any());
    }

    @Test
    void invalidParametersAre400WithTheirCode() throws Exception {
        mvc.perform(get(URL + "?sort=nearest")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LOCATION_REQUIRED"));
        mvc.perform(get(URL + "?size=49")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_SIZE_INVALID"));
        mvc.perform(get(URL + "?lat=4.6&lng=-74.1&radiusKm=500")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_RADIUS_INVALID"));
        mvc.perform(get(URL + "?lat=40.4&lng=-3.7")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_POINT_OUTSIDE_COVERAGE"));
        mvc.perform(get(URL + "?priceMin=10&priceMax=5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_PRICE_RANGE_INVALID"));
        mvc.perform(get(URL + "?sort=cheapest")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_SORT_INVALID"));
    }

    @Test
    void branchOfAnotherStoreIsRejected() throws Exception {
        UUID store = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        when(branchService.isInStore(store, branch)).thenReturn(false);

        mvc.perform(get(URL + "?storeId=" + store + "&branchId=" + branch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BRANCH_NOT_IN_STORE"));
        verify(searchService, never()).search(any(), any());
    }

    @Test
    void suggestIsPubliclyCacheableForFiveMinutes() throws Exception {
        when(suggestions.suggest(eq("pas"), isNull(), isNull())).thenReturn(new Suggestions.Result(
                List.of(new Suggestions.Suggestion("pastillas de freno", "QUERY")), List.of()));

        mvc.perform(get(URL + "/suggest?q=pas"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.suggestions[0].term").value("pastillas de freno"))
                .andExpect(jsonPath("$.suggestions[0].kind").value("QUERY"))
                .andExpect(jsonPath("$.products").isEmpty());
    }
}
