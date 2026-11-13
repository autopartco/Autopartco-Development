package com.autopartco.backend.marketplace.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.organization.PublicBranches;
import com.autopartco.backend.core.organization.PublicBranches.PublicBranch;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.search.ServiceHit;
import com.autopartco.backend.core.search.ServiceSearchQuery;
import com.autopartco.backend.core.search.ServiceSearchResult;
import com.autopartco.backend.core.search.ServiceSearchService;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class ServiceBrowseControllerTest {
    private static final String URL = "/api/v1/marketplace/services";
    private static final UUID SERVICE = UUID.fromString("5b1b7f0e-3d21-4c8e-9d52-0a1c6f2e7a11");
    private static final UUID OTHER_SERVICE = UUID.fromString("6c2c8a1f-4e32-4d9f-8e63-1b2d7a3f8b22");
    private static final UUID STORE = UUID.fromString("7d3d9b20-5f43-4eaa-9f74-2c3e8b4a9c33");
    private static final UUID BRANCH = UUID.fromString("8e4eac31-6a54-4fbb-8a85-3d4f9c5bad44");
    private static final UUID BRANCH_OTHER = UUID.fromString("9f5fbd42-7b65-40cc-9b96-4e5aad6cbe55");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneOffset.UTC);

    private final ServiceSearchService searchService = mock(ServiceSearchService.class);
    private final PublicBranches publicBranches = mock(PublicBranches.class);
    private final PublishedStores publishedStores = mock(PublishedStores.class);
    private final SettingsQueries settingsQueries = mock(SettingsQueries.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // El servicio real valida; aquí se replica para comprobar que el error llega como 400.
        when(searchService.search(any())).thenAnswer(invocation -> {
            invocation.<ServiceSearchQuery>getArgument(0).validate();
            return new ServiceSearchResult(List.of(hit(SERVICE, "Alineación y balanceo")), 0, 24, 1,
                    new ServiceSearchResult.Facets(
                            List.of(new ServiceSearchResult.CategoryCount(ServiceCategory.ALIGNMENT_BALANCING, 1)),
                            List.of(new SearchResult.CityCount("11001", "Bogotá, D.C.", 1))));
        });
        mvc = MockMvcBuilders.standaloneSetup(
                        new ServiceBrowseController(searchService, publicBranches, publishedStores, settingsQueries,
                                CLOCK))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ServiceHit hit(UUID id, String name) {
        return new ServiceHit(id, name, ServiceCategory.ALIGNMENT_BALANCING, "Con equipo láser", "https://img/1.jpg",
                new BigDecimal("60000.00"), new BigDecimal("60000.00"), false, 45, true,
                new ServiceHit.Store(STORE, "Taller Centro", "/logo", new BigDecimal("4.6"), "BOTH"),
                new ServiceHit.Branch(BRANCH, "Sede Chapinero", "Bogotá, D.C.", "Calle 60 # 10-20",
                        new BigDecimal("4.65"), new BigDecimal("-74.08"), true),
                new BigDecimal("2.3"));
    }

    private static PublicBranch branch(UUID id, String whatsapp, List<UUID> serviceIds) {
        return new PublicBranch(id, "Sede " + id.toString().substring(0, 4), "Calle 1", "Bogotá, D.C.", "Bogotá",
                new BigDecimal("4.65"), new BigDecimal("-74.08"), false, "+573001112233", whatsapp, null, true,
                serviceIds);
    }

    private static StoreSettings settings(boolean whatsappEnabled, String serviceMessage) {
        return new StoreSettings(new StoreSettings.Contact(whatsappEnabled, "Hola {producto} {enlace}", serviceMessage),
                new StoreSettings.Profile(true, List.of(), null), new StoreSettings.Catalog(false),
                new StoreSettings.Notifications(true, true, true, true, true, List.of()),
                new StoreSettings.Security(false));
    }

    // --- /search ---------------------------------------------------------------------------------------------------

    @Test
    void searchReturnsServiceCardsWithThePlanFormatAndFacets() throws Exception {
        mvc.perform(get(URL + "/search").param("q", "alineacion bogota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SERVICE.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Alineación y balanceo"))
                .andExpect(jsonPath("$.items[0].category").value("ALIGNMENT_BALANCING"))
                .andExpect(jsonPath("$.items[0].imageUrl").value("https://img/1.jpg"))
                .andExpect(jsonPath("$.items[0].priceFrom").value(60000.0))
                .andExpect(jsonPath("$.items[0].basePriceFrom").value(60000.0))
                .andExpect(jsonPath("$.items[0].promotion").doesNotExist())
                .andExpect(jsonPath("$.items[0].durationMinutes").value(45))
                .andExpect(jsonPath("$.items[0].bookingEnabled").value(true))
                .andExpect(jsonPath("$.items[0].store.name").value("Taller Centro"))
                .andExpect(jsonPath("$.items[0].store.rating").value(4.6))
                .andExpect(jsonPath("$.items[0].store.businessType").value("BOTH"))
                .andExpect(jsonPath("$.items[0].branch.id").value(BRANCH.toString()))
                .andExpect(jsonPath("$.items[0].branch.city").value("Bogotá, D.C."))
                .andExpect(jsonPath("$.items[0].branch.latitude").value(4.65))
                .andExpect(jsonPath("$.items[0].branch.openNow").value(true))
                .andExpect(jsonPath("$.items[0].distanceKm").value(2.3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(24))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.facets.categories[0].category").value("ALIGNMENT_BALANCING"))
                .andExpect(jsonPath("$.facets.categories[0].count").value(1))
                .andExpect(jsonPath("$.facets.cities[0].code").value("11001"))
                .andExpect(jsonPath("$.facets.cities[0].name").value("Bogotá, D.C."))
                .andExpect(jsonPath("$.facets.cities[0].count").value(1));
    }

    @Test
    void searchPassesEveryFilterToTheService() throws Exception {
        mvc.perform(get(URL + "/search").param("q", "aceite").param("category", "lubrication")
                        .param("cityCode", "05001").param("departmentCode", "05").param("lat", "4.65")
                        .param("lng", "-74.08").param("radiusKm", "10").param("storeId", STORE.toString())
                        .param("priceMax", "90000").param("onSale", "true").param("bookable", "true")
                        .param("openNow", "true").param("sort", "price_asc").param("page", "2").param("size", "12")
                        .param("facets", "false"))
                .andExpect(status().isOk());

        ArgumentCaptor<ServiceSearchQuery> query = ArgumentCaptor.forClass(ServiceSearchQuery.class);
        verify(searchService).search(query.capture());
        ServiceSearchQuery sent = query.getValue();
        assertThat(sent.q()).isEqualTo("aceite");
        assertThat(sent.category()).isEqualTo(ServiceCategory.LUBRICATION);
        assertThat(sent.cityCode()).isEqualTo("05001");
        assertThat(sent.departmentCode()).isEqualTo("05");
        assertThat(sent.lat()).isEqualByComparingTo("4.65");
        assertThat(sent.radiusKm()).isEqualTo(10);
        assertThat(sent.storeId()).isEqualTo(STORE);
        assertThat(sent.priceMax()).isEqualByComparingTo("90000");
        assertThat(sent.onSale()).isTrue();
        assertThat(sent.bookable()).isTrue();
        assertThat(sent.openNow()).isTrue();
        assertThat(sent.sort()).isEqualTo(ServiceSearchQuery.Sort.PRICE_ASC);
        assertThat(sent.page()).isEqualTo(2);
        assertThat(sent.size()).isEqualTo(12);
        assertThat(sent.facets()).isFalse();
    }

    @Test
    void invalidParametersAre400WithTheirCode() throws Exception {
        mvc.perform(get(URL + "/search?size=49")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_SIZE_INVALID"));
        mvc.perform(get(URL + "/search?lat=4.6&lng=-74.1&radiusKm=500")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_RADIUS_INVALID"));
        mvc.perform(get(URL + "/search?lat=4.6")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_POINT_INCOMPLETE"));
        mvc.perform(get(URL + "/search?lat=40.4&lng=-3.7")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_POINT_OUTSIDE_COVERAGE"));
        mvc.perform(get(URL + "/search?sort=nearest")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LOCATION_REQUIRED"));
        mvc.perform(get(URL + "/search?sort=cheapest")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_SORT_INVALID"));
        mvc.perform(get(URL + "/search?category=PLOMERIA")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SERVICE_CATEGORY_INVALID"));
    }

    @Test
    void nearestWithoutPointUsesTheAreaCookieAndIgnoresAnInvalidOne() throws Exception {
        mvc.perform(get(URL + "/search").param("sort", "nearest")
                        .cookie(new jakarta.servlet.http.Cookie("ap_area", "4.60%2C-74.10%2C")))
                .andExpect(status().isOk());
        ArgumentCaptor<ServiceSearchQuery> query = ArgumentCaptor.forClass(ServiceSearchQuery.class);
        verify(searchService).search(query.capture());
        assertThat(query.getValue().lat()).isEqualByComparingTo("4.60");
        assertThat(query.getValue().lng()).isEqualByComparingTo("-74.10");

        mvc.perform(get(URL + "/search").param("sort", "nearest")
                        .cookie(new jakarta.servlet.http.Cookie("ap_area", "40.71%2C-74.00%2C")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LOCATION_REQUIRED"));
    }

    @Test
    void theCookieIsNotUsedWithoutNearestOrRadius() throws Exception {
        mvc.perform(get(URL + "/search").param("q", "aceite")
                        .cookie(new jakarta.servlet.http.Cookie("ap_area", "4.60%2C-74.10%2C")))
                .andExpect(status().isOk());
        ArgumentCaptor<ServiceSearchQuery> query = ArgumentCaptor.forClass(ServiceSearchQuery.class);
        verify(searchService).search(query.capture());
        assertThat(query.getValue().lat()).isNull();
    }

    @Test
    void searchIsRateLimitedTo120PerMinutePerIp() throws Exception {
        for (int i = 0; i < 120; i++) {
            mvc.perform(get(URL + "/search")).andExpect(status().isOk());
        }
        mvc.perform(get(URL + "/search"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("SEARCH_RATE_LIMITED"));
    }

    // --- /categories -----------------------------------------------------------------------------------------------

    @Test
    void categoriesCarryLabelIconAndCount() throws Exception {
        when(searchService.categories("11001")).thenReturn(List.of(
                new ServiceSearchResult.CategoryCount(ServiceCategory.ALIGNMENT_BALANCING, 9),
                new ServiceSearchResult.CategoryCount(ServiceCategory.AC, 2)));

        mvc.perform(get(URL + "/categories").param("cityCode", "11001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("ALIGNMENT_BALANCING"))
                .andExpect(jsonPath("$[0].label").value("Alineación y balanceo"))
                .andExpect(jsonPath("$[0].icon").value("gauge"))
                .andExpect(jsonPath("$[0].count").value(9))
                .andExpect(jsonPath("$[1].category").value("AC"))
                .andExpect(jsonPath("$[1].label").value("Aire acondicionado"))
                .andExpect(jsonPath("$[1].icon").value("snowflake"));
    }

    @Test
    void categoriesWithoutCityAskForAllOfThem() throws Exception {
        when(searchService.categories(isNull())).thenReturn(List.of());

        mvc.perform(get(URL + "/categories")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        verify(searchService).categories(isNull());
    }

    // --- /{serviceId} ----------------------------------------------------------------------------------------------

    @Test
    void detailAddsDescriptionOnlyTheBranchesThatOfferItWhatsappAndRelated() throws Exception {
        when(searchService.find(eq(SERVICE), any(), any())).thenReturn(Optional.of(hit(SERVICE, "Alineación y balanceo")));
        when(searchService.related(any(), any(), any(), eq(6))).thenReturn(
                List.of(hit(OTHER_SERVICE, "Balanceo")));
        when(publicBranches.list(STORE)).thenReturn(List.of(
                branch(BRANCH, null, List.of(SERVICE)),
                branch(BRANCH_OTHER, "+573009998877", List.of(OTHER_SERVICE))));
        when(publishedStores.publicWhatsapp(STORE)).thenReturn("+573101234567");
        when(settingsQueries.storeSettings(STORE)).thenReturn(settings(true,
                "Hola, vi el servicio {servicio} y quiero agendar: {enlace}"));

        mvc.perform(get(URL + "/" + SERVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SERVICE.toString()))
                .andExpect(jsonPath("$.name").value("Alineación y balanceo"))
                .andExpect(jsonPath("$.store.id").value(STORE.toString()))
                .andExpect(jsonPath("$.branch.id").value(BRANCH.toString()))
                .andExpect(jsonPath("$.card").doesNotExist())
                .andExpect(jsonPath("$.description").value("Con equipo láser"))
                .andExpect(jsonPath("$.branches.length()").value(1))
                .andExpect(jsonPath("$.branches[0].id").value(BRANCH.toString()))
                .andExpect(jsonPath("$.branches[0].whatsapp").value("+573101234567"))
                .andExpect(jsonPath("$.branches[0].serviceIds[0]").value(SERVICE.toString()))
                .andExpect(jsonPath("$.whatsappEnabled").value(true))
                .andExpect(jsonPath("$.whatsappServiceMessage")
                        .value("Hola, vi el servicio {servicio} y quiero agendar: {enlace}"))
                .andExpect(jsonPath("$.related.length()").value(1))
                .andExpect(jsonPath("$.related[0].id").value(OTHER_SERVICE.toString()));
    }

    @Test
    void detailWithWhatsappDisabledHidesItAndUsesTheDefaultMessageWhenTheStoreHasNone() throws Exception {
        when(searchService.find(eq(SERVICE), any(), any())).thenReturn(Optional.of(hit(SERVICE, "Alineación")));
        when(searchService.related(any(), any(), any(), eq(6))).thenReturn(List.of());
        when(publicBranches.list(STORE)).thenReturn(List.of(branch(BRANCH, "+573009998877", List.of(SERVICE))));
        when(settingsQueries.storeSettings(STORE)).thenReturn(settings(false, null));

        mvc.perform(get(URL + "/" + SERVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappEnabled").value(false))
                .andExpect(jsonPath("$.branches[0].whatsapp").doesNotExist())
                .andExpect(jsonPath("$.whatsappServiceMessage").value(ServiceBrowseController.DEFAULT_SERVICE_MESSAGE))
                .andExpect(jsonPath("$.related").isEmpty());
    }

    @Test
    void aServiceThatIsNotVisibleIs404() throws Exception {
        when(searchService.find(eq(SERVICE), any(), any())).thenReturn(Optional.empty());

        mvc.perform(get(URL + "/" + SERVICE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SERVICE_NOT_FOUND"));
        verify(searchService, org.mockito.Mockito.never()).related(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void detailUsesTheAreaCookieAsThePointAndRejectsAnIncompleteOne() throws Exception {
        when(searchService.find(eq(SERVICE), any(), any())).thenReturn(Optional.of(hit(SERVICE, "Alineación")));
        when(publicBranches.list(STORE)).thenReturn(List.of());
        when(settingsQueries.storeSettings(STORE)).thenReturn(settings(true, null));

        mvc.perform(get(URL + "/" + SERVICE).cookie(new jakarta.servlet.http.Cookie("ap_area", "4.60%2C-74.10%2C")))
                .andExpect(status().isOk());
        verify(searchService).find(eq(SERVICE), eq(new BigDecimal("4.60")), eq(new BigDecimal("-74.10")));

        mvc.perform(get(URL + "/" + SERVICE).param("lat", "4.6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_POINT_INCOMPLETE"));
    }

    @Test
    void aMalformedServiceIdIsNotAServer500() throws Exception {
        int statusCode = mvc.perform(get(URL + "/no-es-un-uuid")).andReturn().getResponse().getStatus();

        assertThat(statusCode).isBetween(400, 499);
    }
}
