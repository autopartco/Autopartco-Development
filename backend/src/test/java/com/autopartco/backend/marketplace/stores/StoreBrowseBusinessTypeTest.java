package com.autopartco.backend.marketplace.stores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.organization.BusinessType;
import com.autopartco.backend.core.organization.PublicBranches;
import com.autopartco.backend.core.organization.PublicStoreService;
import com.autopartco.backend.core.organization.PublishedStoreProfile;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreServices;
import com.autopartco.backend.core.pricing.PriceCalculator;
import com.autopartco.backend.core.pricing.PromotionAudience;
import com.autopartco.backend.core.pricing.PromotionDiscountType;
import com.autopartco.backend.core.pricing.PromotionRule;
import com.autopartco.backend.core.pricing.PromotionScope;
import com.autopartco.backend.core.pricing.PromotionTargetType;
import com.autopartco.backend.core.pricing.ServicePricing;
import com.autopartco.backend.core.pricing.internal.PromotionRepository;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.marketplace.reviews.StoreRating;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;

/** Filtros {@code businessType}/{@code serviceCategory} del listado y servicios del perfil público. */
class StoreBrowseBusinessTypeTest {
    private final StoreService storeService = mock(StoreService.class);
    private final PublishedStores publishedStores = mock(PublishedStores.class);
    private final StoreReviewService reviews = mock(StoreReviewService.class);
    private final SettingsQueries settingsQueries = mock(SettingsQueries.class);
    private final StoreServices storeServices = mock(StoreServices.class);
    private final ServicePricing pricing = mock(ServicePricing.class);
    private final StoreBrowseController controller = new StoreBrowseController(storeService, mock(StoreFiles.class),
            publishedStores, reviews, mock(ListingQueries.class), settingsQueries, storeServices,
            mock(PublicBranches.class), StorePublicCache.NONE, pricing);

    private final StoreResponse parts = store("Repuestos", BusinessType.PARTS);
    private final StoreResponse workshop = store("Taller", BusinessType.SERVICES);
    private final StoreResponse both = store("Mixto", BusinessType.BOTH);

    @BeforeEach
    void setUp() {
        when(storeService.published()).thenReturn(List.of(parts, workshop, both));
        when(pricing.cached()).thenReturn(new ServicePricing.Rules(
                new PriceCalculator(mock(PromotionRepository.class)), List.of()));
    }

    @Test
    void filtersByBusinessTypeAndBothCountsForEither() {
        assertThat(controller.published(BusinessType.SERVICES, null))
                .extracting(PublicStoreSummary::businessName).containsExactly("Taller", "Mixto");
        assertThat(controller.published(BusinessType.PARTS, null))
                .extracting(PublicStoreSummary::businessName).containsExactly("Repuestos", "Mixto");
        assertThat(controller.published(BusinessType.BOTH, null))
                .extracting(PublicStoreSummary::businessName).containsExactly("Mixto");
        assertThat(controller.published(null, null)).hasSize(3)
                .extracting(PublicStoreSummary::businessType)
                .containsExactly(BusinessType.PARTS, BusinessType.SERVICES, BusinessType.BOTH);
    }

    @Test
    void filtersByStoresOfferingTheServiceCategory() {
        when(storeServices.storesWithActiveServices(ServiceCategory.AC)).thenReturn(Set.of(parts.id()));

        assertThat(controller.published(null, ServiceCategory.AC))
                .extracting(PublicStoreSummary::businessName).containsExactly("Repuestos");
        assertThat(controller.published(BusinessType.SERVICES, ServiceCategory.AC)).isEmpty();
    }

    @Test
    void profileExposesBusinessTypeAndPricedServices() {
        UUID storeId = workshop.id();
        UUID serviceId = UUID.randomUUID();
        when(publishedStores.profile(storeId)).thenReturn(new PublishedStoreProfile(storeId, "Taller", null, null,
                null, List.of(), 1, LocalDate.of(2026, 10, 1), PublishedStoreProfile.PublicContact.EMPTY,
                BusinessType.SERVICES));
        when(reviews.rating(storeId)).thenReturn(StoreRating.NONE);
        when(settingsQueries.storeSettings(storeId))
                .thenReturn(StorePublicProfileTest.settings(true, "Hola {enlace}", List.of(), null));
        when(storeServices.publicServices(storeId)).thenReturn(List.of(new PublicStoreService(serviceId,
                ServiceCategory.ALIGNMENT_BALANCING, "Alineación", null, new BigDecimal("60000"), 45, List.of(),
                "https://img/1.webp", true)));
        when(pricing.cached()).thenReturn(new ServicePricing.Rules(new PriceCalculator(mock(PromotionRepository.class)),
                List.of(new PromotionRule(UUID.randomUUID(), PromotionScope.STORE, storeId, "Alineación",
                        PromotionTargetType.SERVICE, serviceId, null, null, null, PromotionDiscountType.PERCENTAGE,
                        new BigDecimal("15"), PromotionAudience.ALL, null,
                        OffsetDateTime.parse("2026-12-01T00:00:00Z")))));

        StorePublicProfile profile = controller.profile(storeId);

        assertThat(profile.businessType()).isEqualTo(BusinessType.SERVICES);
        assertThat(profile.services()).singleElement().satisfies(service -> {
            assertThat(service.priceFrom()).isEqualByComparingTo("51000");
            assertThat(service.basePriceFrom()).isEqualByComparingTo("60000");
            assertThat(service.promotion().label()).isEqualTo("-15%");
            assertThat(service.imageUrl()).isEqualTo("https://img/1.webp");
            assertThat(service.bookingEnabled()).isTrue();
        });
    }

    private static StoreResponse store(String name, BusinessType type) {
        return new StoreResponse(UUID.randomUUID(), name, "900", name + "@x.co", "ACTIVE", type);
    }
}
