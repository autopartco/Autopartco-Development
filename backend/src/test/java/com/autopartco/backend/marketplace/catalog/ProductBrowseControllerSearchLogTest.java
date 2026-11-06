package com.autopartco.backend.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import com.autopartco.backend.core.analytics.MarketplaceSearch;
import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.catalog.ListingCache;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionAudience;
import com.autopartco.backend.core.pricing.PromotionExposureRecorder;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;

class ProductBrowseControllerSearchLogTest {
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final SearchLog searchLog = mock(SearchLog.class);
    private final BuyerContexts buyerContexts = mock(BuyerContexts.class);
    private final PromotionExposureRecorder exposureRecorder = mock(PromotionExposureRecorder.class);
    private final ProductBrowseController controller =
            new ProductBrowseController(new ListingCache(listingQueries), searchLog, buyerContexts, exposureRecorder);
    private final UUID brakes = UUID.randomUUID();

    @BeforeEach
    void anonymousByDefault() {
        when(buyerContexts.forBrowsing(any())).thenReturn(BuyerContext.anonymous());
    }

    @Test
    void authenticatedBuyerSeesItsPriceAndAudienceWinsAreRecordedAsExposures() {
        UUID person = UUID.randomUUID();
        UUID promotion = UUID.randomUUID();
        BuyerContext buyer = new BuyerContext(person, Set.of("FREQUENT"), Map.of());
        when(buyerContexts.forBrowsing(any())).thenReturn(buyer);
        ProductResponse frequentPrice = listing("Pastillas", brakes, promotion, PromotionAudience.PLATFORM_FREQUENT);
        ProductResponse generalPrice = listing("Filtro", brakes, UUID.randomUUID(), PromotionAudience.ALL);
        ProductResponse noPromotion = listing("Bujía", brakes);
        when(listingQueries.publishedListings(any(), eq(buyer)))
                .thenReturn(List.of(frequentPrice, generalPrice, noPromotion));

        List<ProductResponse> result = controller.published(null, null, null, null, null, null, null, null,
                new UsernamePasswordAuthenticationToken("a@b.co", null, AuthorityUtils.NO_AUTHORITIES)).getBody();

        assertThat(result).hasSize(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PromotionExposureRecorder.Exposure>> exposures =
                ArgumentCaptor.forClass(Collection.class);
        verify(exposureRecorder).record(eq(person), exposures.capture());
        assertThat(exposures.getValue())
                .containsExactly(new PromotionExposureRecorder.Exposure(promotion, frequentPrice.id()));
    }

    @Test
    void anonymousBuyerNeverRecordsExposures() {
        when(listingQueries.publishedListings(any())).thenReturn(List.of(
                listing("Pastillas", brakes, UUID.randomUUID(), PromotionAudience.ALL)));

        controller.published(null, null, null, null, null, null, null, null, null);

        verify(exposureRecorder, never()).record(any(), any());
    }

    @Test
    void exposureFailureDoesNotBreakTheDetail() {
        UUID productId = UUID.randomUUID();
        BuyerContext buyer = new BuyerContext(UUID.randomUUID(), Set.of(), Map.of(UUID.randomUUID(), Set.of("FREQUENT")));
        when(buyerContexts.forBrowsing(any())).thenReturn(buyer);
        ProductResponse detail = listing("Pastillas", brakes, UUID.randomUUID(), PromotionAudience.STORE_FREQUENT);
        when(listingQueries.publishedListing(productId, buyer)).thenReturn(detail);
        doThrow(new IllegalStateException("fallo")).when(exposureRecorder).record(any(), any());

        assertThat(controller.detail(productId,
                new UsernamePasswordAuthenticationToken("a@b.co", null, AuthorityUtils.NO_AUTHORITIES)))
                .isSameAs(detail);
    }

    @Test
    void filtersByTextAndCategoryAndRecordsResultCount() {
        when(listingQueries.publishedListings(any())).thenReturn(List.of(
                listing("Pastillas de freno Mazdá", brakes), listing("Filtro de aceite", UUID.randomUUID())));

        List<ProductResponse> result = controller.published(null, null, null, " PASTILLAS mazda", brakes, null, null, "sess",
                new UsernamePasswordAuthenticationToken("a@b.co", null, AuthorityUtils.NO_AUTHORITIES)).getBody();

        assertThat(result).extracting(ProductResponse::name).containsExactly("Pastillas de freno Mazdá");
        ArgumentCaptor<MarketplaceSearch> search = ArgumentCaptor.forClass(MarketplaceSearch.class);
        verify(searchLog).record(search.capture());
        assertThat(search.getValue().resultsCount()).isEqualTo(1);
        assertThat(search.getValue().authenticated()).isTrue();
        assertThat(search.getValue().sessionId()).isEqualTo("sess");
        assertThat(search.getValue().categoryId()).isEqualTo(brakes);
    }

    @Test
    void searchLogFailureDoesNotBreakTheCatalog() {
        when(listingQueries.publishedListings(any())).thenReturn(List.of(listing("Filtro", brakes)));
        doThrow(new IllegalStateException("fallo")).when(searchLog).record(any());

        List<ProductResponse> result = controller.published(UUID.randomUUID(), null, null, null, null, null, null, null,
                new AnonymousAuthenticationToken("key", "anonymous",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))).getBody();

        assertThat(result).hasSize(1);
    }

    @Test
    void anonymousIsNotAuthenticated() {
        when(listingQueries.publishedListings(any())).thenReturn(List.of());
        controller.published(null, null, null, "filtro", null, null, null, null, null);
        ArgumentCaptor<MarketplaceSearch> search = ArgumentCaptor.forClass(MarketplaceSearch.class);
        verify(searchLog).record(search.capture());
        assertThat(search.getValue().authenticated()).isFalse();
        assertThat(search.getValue().resultsCount()).isZero();
    }

    @Test
    void generalPriceIsCachedButSegmentedBuyerNeverUsesTheSharedCache() {
        when(listingQueries.publishedListings(any())).thenReturn(List.of(listing("Filtro", brakes)));
        controller.published(null, null, null, null, null, null, null, null, null);
        controller.published(null, null, null, null, null, null, null, null, null);
        verify(listingQueries, org.mockito.Mockito.times(1)).publishedListings(any());

        BuyerContext buyer = new BuyerContext(UUID.randomUUID(), Set.of("FREQUENT"), Map.of());
        when(buyerContexts.forBrowsing(any())).thenReturn(buyer);
        when(listingQueries.publishedListings(any(), eq(buyer))).thenReturn(List.of(listing("Bujía", brakes)));
        List<ProductResponse> result = controller.published(null, null, null, null, null, null, null, null,
                new UsernamePasswordAuthenticationToken("a@b.co", null, AuthorityUtils.NO_AUTHORITIES)).getBody();

        assertThat(result).extracting(ProductResponse::name).containsExactly("Bujía");
        verify(listingQueries).publishedListings(any(), eq(buyer));
    }

    private static ProductResponse listing(String name, UUID categoryId) {
        return new ProductResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sede", name, "SKU",
                null, null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, 1, null, null, false, categoryId,
                "ACTIVE", false, Map.of(), List.of(), List.of());
    }

    private static ProductResponse listing(String name, UUID categoryId, UUID promotionId,
                                           PromotionAudience audience) {
        return new ProductResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sede", name, "SKU",
                null, null, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("9"), "Promo", 1, null, null, false,
                categoryId, "ACTIVE", false, Map.of(), List.of(), List.of(), promotionId, audience);
    }
}
