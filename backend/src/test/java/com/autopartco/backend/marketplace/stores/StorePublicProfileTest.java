package com.autopartco.backend.marketplace.stores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.organization.PublishedStoreProfile;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.marketplace.reviews.StoreRating;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;

class StorePublicProfileTest {
    private final PublishedStores publishedStores = mock(PublishedStores.class);
    private final StoreReviewService reviews = mock(StoreReviewService.class);
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final StoreBrowseController controller = new StoreBrowseController(
            mock(StoreService.class), mock(StoreFiles.class), publishedStores, reviews, listingQueries);
    private final UUID storeId = UUID.randomUUID();

    private PublishedStoreProfile store(boolean logo, boolean banner) {
        return new PublishedStoreProfile(storeId, "Repuestos Andes", "Frenos",
                logo ? "/api/v1/marketplace/stores/" + storeId + "/brand/logo?v=1" : null,
                banner ? "/api/v1/marketplace/stores/" + storeId + "/brand/banner?v=2" : null, List.of("Bogotá"), 2,
                LocalDate.of(2026, 10, 1), PublishedStoreProfile.PublicContact.EMPTY);
    }

    @Test
    void combinesStoreRatingAndPublishedProducts() {
        when(publishedStores.profile(storeId)).thenReturn(store(true, false));
        when(reviews.rating(storeId)).thenReturn(new StoreRating(new BigDecimal("4.6"), 18));
        when(listingQueries.publishedProductCount(storeId)).thenReturn(120L);

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

        StorePublicProfile profile = controller.profile(storeId);

        assertThat(profile.logoUrl()).isNull();
        assertThat(profile.bannerUrl()).endsWith("/brand/banner?v=2");
        assertThat(profile.rating().average()).isNull();
        assertThat(profile.rating().count()).isZero();
    }
}
