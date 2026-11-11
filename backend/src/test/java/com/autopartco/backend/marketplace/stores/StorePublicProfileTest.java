package com.autopartco.backend.marketplace.stores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.organization.PublicBranches;
import com.autopartco.backend.core.organization.PublicStoreService;
import com.autopartco.backend.core.organization.PublishedStoreProfile;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreServices;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.marketplace.reviews.StoreRating;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;

class StorePublicProfileTest {
    private final PublishedStores publishedStores = mock(PublishedStores.class);
    private final StoreReviewService reviews = mock(StoreReviewService.class);
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final SettingsQueries settingsQueries = mock(SettingsQueries.class);
    private final StoreServices storeServices = mock(StoreServices.class);
    private final StoreBrowseController controller = new StoreBrowseController(
            mock(StoreService.class), mock(StoreFiles.class), publishedStores, reviews, listingQueries,
            settingsQueries, storeServices, mock(PublicBranches.class));
    private final UUID storeId = UUID.randomUUID();

    private PublishedStoreProfile store(boolean logo, boolean banner) {
        return new PublishedStoreProfile(storeId, "Repuestos Andes", "Frenos",
                logo ? "/api/v1/marketplace/stores/" + storeId + "/brand/logo?v=1" : null,
                banner ? "/api/v1/marketplace/stores/" + storeId + "/brand/banner?v=2" : null, List.of("Bogotá"), 2,
                LocalDate.of(2026, 10, 1), PublishedStoreProfile.PublicContact.EMPTY);
    }

    static StoreSettings settings(boolean whatsappEnabled, String message, List<UUID> featured, String tagline) {
        return new StoreSettings(
                new StoreSettings.Contact(whatsappEnabled, message),
                new StoreSettings.Profile(true, featured, tagline),
                new StoreSettings.Catalog(false),
                new StoreSettings.Notifications(true, true, true, true, List.of()),
                new StoreSettings.Security(false));
    }

    @Test
    void combinesStoreRatingAndPublishedProducts() {
        when(publishedStores.profile(storeId)).thenReturn(store(true, false));
        when(reviews.rating(storeId)).thenReturn(new StoreRating(new BigDecimal("4.6"), 18));
        when(listingQueries.publishedProductCount(storeId)).thenReturn(120L);
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(true, "Hola {enlace}", List.of(), null));

        StorePublicProfile profile = controller.profile(storeId);

        assertThat(profile.logoUrl()).isEqualTo("/api/v1/marketplace/stores/" + storeId + "/brand/logo?v=1");
        assertThat(profile.bannerUrl()).isNull();
        assertThat(profile.rating()).isEqualTo(new StoreRating(new BigDecimal("4.6"), 18));
        assertThat(profile.publishedProducts()).isEqualTo(120);
        assertThat(profile.cities()).containsExactly("Bogotá");
        assertThat(profile.branchCount()).isEqualTo(2);
    }

    @Test
    void storeWithoutReviewsHasNoAverage() {
        when(publishedStores.profile(storeId)).thenReturn(store(false, true));
        when(reviews.rating(storeId)).thenReturn(StoreRating.NONE);
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(true, "Hola {enlace}", List.of(), null));

        StorePublicProfile profile = controller.profile(storeId);

        assertThat(profile.logoUrl()).isNull();
        assertThat(profile.bannerUrl()).endsWith("/brand/banner?v=2");
        assertThat(profile.rating().average()).isNull();
        assertThat(profile.rating().count()).isZero();
    }

    @Test
    void exposesTaglineWhatsappSettingsServicesAndTheContactChannels() {
        PublishedStoreProfile withContact = new PublishedStoreProfile(storeId, "Repuestos Andes", null, null, null,
                List.of(), 0, LocalDate.of(2026, 10, 1),
                new PublishedStoreProfile.PublicContact(null, null, null, null, null, null, "andes", "andes_x",
                        "ventas@andes.co"));
        UUID branch = UUID.randomUUID();
        PublicStoreService service = new PublicStoreService(UUID.randomUUID(), ServiceCategory.ALIGNMENT_BALANCING,
                "Alineación", "Con láser", new BigDecimal("60000"), 45, List.of(branch));
        when(publishedStores.profile(storeId)).thenReturn(withContact);
        when(reviews.rating(storeId)).thenReturn(StoreRating.NONE);
        when(settingsQueries.storeSettings(storeId))
                .thenReturn(settings(false, "Hola {producto} {enlace}", List.of(), "Todo para tu carro"));
        when(storeServices.publicServices(storeId)).thenReturn(List.of(service));

        StorePublicProfile profile = controller.profile(storeId);

        assertThat(profile.tagline()).isEqualTo("Todo para tu carro");
        assertThat(profile.verified()).isTrue();
        assertThat(profile.whatsappEnabled()).isFalse();
        assertThat(profile.whatsappMessage()).isEqualTo("Hola {producto} {enlace}");
        assertThat(profile.services()).containsExactly(service);
        assertThat(profile.contact().youtube()).isEqualTo("andes");
        assertThat(profile.contact().x()).isEqualTo("andes_x");
        assertThat(profile.contact().email()).isEqualTo("ventas@andes.co");
    }

    @Test
    void featuredKeepsOnlyStillPublishedProductsInConfiguredOrder() {
        UUID first = UUID.randomUUID();
        UUID unpublished = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        List<UUID> featured = List.of(first, unpublished, third);
        when(publishedStores.profile(storeId)).thenReturn(store(false, false));
        when(reviews.rating(storeId)).thenReturn(StoreRating.NONE);
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(true, "Hola {enlace}", featured, null));
        when(listingQueries.publishedProductIdsAmong(storeId, featured)).thenReturn(Set.of(third, first));

        assertThat(controller.profile(storeId).featuredProductIds()).containsExactly(first, third);
    }

    @Test
    void withoutFeaturedProductsNothingIsQueried() {
        when(publishedStores.profile(storeId)).thenReturn(store(false, false));
        when(reviews.rating(storeId)).thenReturn(StoreRating.NONE);
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(true, "Hola {enlace}", List.of(), null));

        assertThat(controller.profile(storeId).featuredProductIds()).isEmpty();
        verify(listingQueries, never()).publishedProductIdsAmong(any(), any());
    }
}
