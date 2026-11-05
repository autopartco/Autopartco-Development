package com.autopartco.backend.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.analytics.ProductAssociations;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductImageResponse;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;
import com.autopartco.backend.shared.error.NotFoundException;

class FrequentlyBoughtControllerTest {
    private final ProductAssociations associations = mock(ProductAssociations.class);
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final BuyerContexts buyerContexts = mock(BuyerContexts.class);
    private final FrequentlyBoughtController controller =
            new FrequentlyBoughtController(associations, listingQueries, buyerContexts);
    private final UUID product = UUID.randomUUID();

    private static ProductResponse listing(UUID id, String name, String price, List<ProductImageResponse> images) {
        return new ProductResponse(id, UUID.randomUUID(), UUID.randomUUID(), "Sede", name, "SKU", null, null,
                new BigDecimal("100000"), new BigDecimal(price), BigDecimal.ZERO, null, 5, UUID.randomUUID(),
                "Fab", true, UUID.randomUUID(), "ACTIVE", false, Map.of(), List.of(), images);
    }

    private static ProductImageResponse image(String card, boolean main, int order) {
        return new ProductImageResponse(UUID.randomUUID(), "o-" + card, "t-" + card, card, main, order,
                LocalDateTime.now());
    }

    @Test
    void returnsBuyerEffectivePriceMainImageAndReason() {
        UUID brakes = UUID.randomUUID();
        UUID oil = UUID.randomUUID();
        BuyerContext buyer = new BuyerContext(UUID.randomUUID(), Set.of("FREQUENT"), Map.of());
        when(buyerContexts.forBrowsing(any())).thenReturn(buyer);
        when(associations.related(product, 6)).thenReturn(List.of(
                new ProductAssociations.Related(brakes, "ORDERS"), new ProductAssociations.Related(oil, "CATEGORY")));
        when(listingQueries.publishedListing(brakes, buyer)).thenReturn(listing(brakes, "Pastillas", "85000",
                List.of(image("secundaria", false, 1), image("principal", true, 0))));
        when(listingQueries.publishedListing(oil, buyer)).thenReturn(listing(oil, "Aceite", "40000", List.of()));

        List<FrequentlyBoughtController.FrequentlyBoughtItem> result = controller.frequentlyBought(product, null);

        assertThat(result).containsExactly(
                new FrequentlyBoughtController.FrequentlyBoughtItem(
                        brakes, "Pastillas", new BigDecimal("85000"), "principal", "ORDERS"),
                new FrequentlyBoughtController.FrequentlyBoughtItem(
                        oil, "Aceite", new BigDecimal("40000"), null, "CATEGORY"));
    }

    @Test
    void skipsProductsThatStoppedBeingPublished() {
        UUID gone = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        when(buyerContexts.forBrowsing(any())).thenReturn(BuyerContext.anonymous());
        when(associations.related(product, 6)).thenReturn(List.of(
                new ProductAssociations.Related(gone, "ORDERS"), new ProductAssociations.Related(kept, "ORDERS")));
        when(listingQueries.publishedListing(eq(gone), any()))
                .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
        when(listingQueries.publishedListing(eq(kept), any())).thenReturn(listing(kept, "Filtro", "30000", List.of()));

        assertThat(controller.frequentlyBought(product, null)).extracting("productId").containsExactly(kept);
    }

    @Test
    void noRelatedProductsMeansEmptyListWithoutTouchingListings() {
        when(associations.related(product, 6)).thenReturn(List.of());

        assertThat(controller.frequentlyBought(product, null)).isEmpty();
        verify(listingQueries, never()).publishedListing(any(), any());
    }
}
