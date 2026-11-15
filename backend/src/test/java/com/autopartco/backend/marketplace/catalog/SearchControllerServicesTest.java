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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionExposureRecorder;
import com.autopartco.backend.core.search.SearchInterpretation;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.ServiceHit;
import com.autopartco.backend.core.search.ServiceSearchQuery;
import com.autopartco.backend.core.search.ServiceSearchResult;
import com.autopartco.backend.core.search.ServiceSearchService;
import com.autopartco.backend.core.search.Suggestions;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

/** Franja de servicios de {@code /marketplace/search}, {@code suggestedTab} y sugerencias de servicios. */
class SearchControllerServicesTest {
    private static final String URL = "/api/v1/marketplace/search";
    private static final UUID PRODUCT = UUID.fromString("3f0c9a52-6f43-4a5e-9a51-2a1f7c0d9b10");
    private static final UUID MAKE = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final UUID STORE = UUID.fromString("7d3d9b20-5f43-4eaa-9f74-2c3e8b4a9c33");

    private final SearchService searchService = mock(SearchService.class);
    private final Suggestions suggestions = mock(Suggestions.class);
    private final SearchLog searchLog = mock(SearchLog.class);
    private final BuyerContexts buyerContexts = mock(BuyerContexts.class);
    private final PromotionExposureRecorder exposureRecorder = mock(PromotionExposureRecorder.class);
    private final BranchService branchService = mock(BranchService.class);
    private final ServiceSearchService serviceSearch = mock(ServiceSearchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(buyerContexts.forBrowsing(any())).thenReturn(BuyerContext.anonymous());
        when(searchService.search(any(), any(), any())).thenReturn(result(null));
        when(serviceSearch.search(any())).thenReturn(services(2, 9));
        mvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService, suggestions, searchLog,
                        buyerContexts, exposureRecorder, branchService, serviceSearch, Clock.systemUTC()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static SearchResult result(SearchInterpretation interpreted) {
        ProductResponse listing = new ProductResponse(PRODUCT, UUID.randomUUID(), UUID.randomUUID(), "Centro",
                "Aceite 20W50", "SKU-1", "ACE-20", null, new BigDecimal("85000.00"), new BigDecimal("80000.00"),
                new BigDecimal("5000.00"), "Promo", 3, null, "Mobil", true, null, "ACTIVE", false, Map.of(), List.of(),
                List.of());
        return new SearchResult(List.of(new SearchResult.Hit(listing, new BigDecimal("3.2"))), 0, 24, 12, false,
                interpreted, null);
    }

    private static SearchInterpretation lubrication(UUID makeId) {
        return new SearchInterpretation("cambio de aceite", makeId, makeId == null ? null : "Chevrolet", null, null,
                null, null, null, null, ServiceCategory.LUBRICATION);
    }

    private static ServiceHit hit(int number) {
        return new ServiceHit(UUID.randomUUID(), "Cambio de aceite " + number, ServiceCategory.LUBRICATION, "desc",
                null, new BigDecimal("45000.00"), new BigDecimal("45000.00"), false, 30, true,
                new ServiceHit.Store(STORE, "Taller " + number, null, new BigDecimal("4.5"), "SERVICES"),
                new ServiceHit.Branch(UUID.randomUUID(), "Sede", "Bogotá, D.C.", "Calle 1", new BigDecimal("4.65"),
                        new BigDecimal("-74.08"), null), new BigDecimal("1.5"));
    }

    private static ServiceSearchResult services(int shown, long total) {
        return new ServiceSearchResult(IntStream.rangeClosed(1, shown)
                .mapToObj(SearchControllerServicesTest::hit).toList(), 0, 4, total, null);
    }

    @Test
    void cambioDeAceiteReturnsTheServiceStripAndTheInterpretedCategory() throws Exception {
        when(searchService.search(any(), any(), any())).thenReturn(result(lubrication(null)));

        mvc.perform(get(URL).param("q", "cambio de aceite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpreted.serviceCategory").value("LUBRICATION"))
                .andExpect(jsonPath("$.services.total").value(9))
                .andExpect(jsonPath("$.services.items.length()").value(2))
                .andExpect(jsonPath("$.services.items[0].name").value("Cambio de aceite 1"))
                .andExpect(jsonPath("$.services.items[0].category").value("LUBRICATION"))
                .andExpect(jsonPath("$.services.items[0].store.businessType").value("SERVICES"))
                .andExpect(jsonPath("$.services.items[0].branch.city").value("Bogotá, D.C."))
                .andExpect(jsonPath("$.services.items[0].distanceKm").value(1.5))
                .andExpect(jsonPath("$.suggestedTab").value("services"));
    }

    @Test
    void theProductItemsKeepExactlyTheirFormat() throws Exception {
        when(searchService.search(any(), any(), any())).thenReturn(result(lubrication(null)));

        mvc.perform(get(URL).param("q", "cambio de aceite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Aceite 20W50"))
                .andExpect(jsonPath("$.items[0].effectivePrice").value(80000.0))
                .andExpect(jsonPath("$.items[0].distanceKm").value(3.2))
                .andExpect(jsonPath("$.items[0].sponsored").value(false))
                .andExpect(jsonPath("$.items[0].listing").doesNotExist())
                .andExpect(jsonPath("$.items[0].services").doesNotExist())
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.fuzzy").value(false))
                .andExpect(jsonPath("$.sponsored").isEmpty());
    }

    @Test
    void theStripUsesTheSameTextAndLocationAndAsksForFourWithoutFacets() throws Exception {
        mvc.perform(get(URL).param("q", "alineacion").param("cityCode", "11001").param("lat", "4.65")
                        .param("lng", "-74.08").param("radiusKm", "10").param("storeId", STORE.toString())
                        .param("size", "12"))
                .andExpect(status().isOk());

        ArgumentCaptor<ServiceSearchQuery> query = ArgumentCaptor.forClass(ServiceSearchQuery.class);
        verify(serviceSearch).search(query.capture());
        ServiceSearchQuery sent = query.getValue();
        assertThat(sent.q()).isEqualTo("alineacion");
        assertThat(sent.cityCode()).isEqualTo("11001");
        assertThat(sent.lat()).isEqualByComparingTo("4.65");
        assertThat(sent.lng()).isEqualByComparingTo("-74.08");
        assertThat(sent.radiusKm()).isEqualTo(10);
        assertThat(sent.storeId()).isEqualTo(STORE);
        assertThat(sent.page()).isZero();
        assertThat(sent.size()).isEqualTo(4);
        assertThat(sent.facets()).isFalse();
    }

    @Test
    void theAreaCookieFeedsTheStripWhenNearestIsRequested() throws Exception {
        mvc.perform(get(URL).param("q", "alineacion").param("sort", "nearest")
                        .cookie(new jakarta.servlet.http.Cookie("ap_area", "4.60%2C-74.10%2C")))
                .andExpect(status().isOk());

        ArgumentCaptor<ServiceSearchQuery> query = ArgumentCaptor.forClass(ServiceSearchQuery.class);
        verify(serviceSearch).search(query.capture());
        assertThat(query.getValue().lat()).isEqualByComparingTo("4.60");
    }

    @Test
    void withoutTextThereIsNoStripAndNoServicesField() throws Exception {
        mvc.perform(get(URL).param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services").doesNotExist())
                .andExpect(jsonPath("$.suggestedTab").doesNotExist());

        verify(serviceSearch, never()).search(any());
    }

    @Test
    void aVehicleOrReferenceInTheQueryKeepsTheProductTab() throws Exception {
        when(searchService.search(any(), any(), any())).thenReturn(result(lubrication(MAKE)));
        mvc.perform(get(URL).param("q", "cambio de aceite chevrolet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpreted.serviceCategory").value("LUBRICATION"))
                .andExpect(jsonPath("$.services.items.length()").value(2))
                .andExpect(jsonPath("$.suggestedTab").doesNotExist());

        SearchInterpretation withReference = new SearchInterpretation("cambio de aceite", null, null, null, null, null,
                null, null, "ACE-20", ServiceCategory.LUBRICATION);
        when(searchService.search(any(), any(), any())).thenReturn(result(withReference));
        mvc.perform(get(URL).param("q", "cambio de aceite ACE-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedTab").doesNotExist());
    }

    @Test
    void noSuggestedTabWithoutServiceIntentWithoutInterpretationOrWithoutServicesToShow() throws Exception {
        // Sin intención de servicio en lo interpretado.
        when(searchService.search(any(), any(), any())).thenReturn(result(new SearchInterpretation("pastillas", MAKE,
                "Chevrolet", null, null, null, null, null, null)));
        mvc.perform(get(URL).param("q", "pastillas chevrolet"))
                .andExpect(jsonPath("$.interpreted.serviceCategory").doesNotExist())
                .andExpect(jsonPath("$.suggestedTab").doesNotExist());

        // interpret=false: el servicio no devuelve interpretación.
        when(searchService.search(any(), any(), any())).thenReturn(result(null));
        mvc.perform(get(URL).param("q", "cambio de aceite").param("interpret", "false"))
                .andExpect(jsonPath("$.suggestedTab").doesNotExist())
                .andExpect(jsonPath("$.services.items.length()").value(2));

        // Intención de servicio pero ningún servicio que mostrar.
        when(searchService.search(any(), any(), any())).thenReturn(result(lubrication(null)));
        when(serviceSearch.search(any())).thenReturn(new ServiceSearchResult(List.of(), 0, 4, 0, null));
        mvc.perform(get(URL).param("q", "cambio de aceite"))
                .andExpect(jsonPath("$.services.total").value(0))
                .andExpect(jsonPath("$.services.items").isEmpty())
                .andExpect(jsonPath("$.suggestedTab").doesNotExist());
    }

    @Test
    void aFailingServiceSearchNeverBreaksTheProductSearch() throws Exception {
        when(serviceSearch.search(any())).thenThrow(new IllegalStateException("db"));

        mvc.perform(get(URL).param("q", "aceite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.services").doesNotExist());
    }

    @Test
    void productValidationStillRunsFirst() throws Exception {
        when(searchService.search(any(), any(), any())).thenAnswer(invocation -> {
            invocation.<com.autopartco.backend.core.search.SearchQuery>getArgument(0)
                    .validate(com.autopartco.backend.core.search.SearchQuery.MAX_SIZE,
                            com.autopartco.backend.core.search.SearchQuery.MAX_PAGE);
            return result(null);
        });

        mvc.perform(get(URL).param("q", "aceite").param("size", "49"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_SIZE_INVALID"));
        verify(serviceSearch, never()).search(any());
    }

    @Test
    void suggestIncludesAtMostThreeServicesAndTwoServiceCategories() throws Exception {
        UUID service = UUID.randomUUID();
        when(suggestions.suggest(eq("alin"), isNull(), isNull())).thenReturn(new Suggestions.Result(
                List.of(new Suggestions.Suggestion("alineacion", "QUERY")), List.of(),
                List.of(new Suggestions.ServiceSuggestion(service, "Alineación y balanceo",
                        ServiceCategory.ALIGNMENT_BALANCING, "Taller Centro")),
                List.of(ServiceCategory.ALIGNMENT_BALANCING)));

        mvc.perform(get(URL + "/suggest?q=alin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].term").value("alineacion"))
                .andExpect(jsonPath("$.products").isEmpty())
                .andExpect(jsonPath("$.services[0].id").value(service.toString()))
                .andExpect(jsonPath("$.services[0].name").value("Alineación y balanceo"))
                .andExpect(jsonPath("$.services[0].category").value("ALIGNMENT_BALANCING"))
                .andExpect(jsonPath("$.services[0].storeName").value("Taller Centro"))
                .andExpect(jsonPath("$.serviceCategories[0]").value("ALIGNMENT_BALANCING"));
    }

    @Test
    void suggestResultNeverHasNullServiceListsAndKeepsTheOldConstructor() {
        Suggestions.Result old = new Suggestions.Result(List.of(), List.of());
        assertThat(old.services()).isEmpty();
        assertThat(old.serviceCategories()).isEmpty();
        assertThat(new Suggestions.Result(List.of(), List.of(), null, null).services()).isEmpty();
        assertThat(Suggestions.Result.EMPTY.serviceCategories()).isEmpty();
    }
}
