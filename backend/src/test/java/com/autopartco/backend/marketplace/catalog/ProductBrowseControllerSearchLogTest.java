package com.autopartco.backend.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import com.autopartco.backend.core.analytics.MarketplaceSearch;
import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;

class ProductBrowseControllerSearchLogTest {
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final SearchLog searchLog = mock(SearchLog.class);
    private final ProductBrowseController controller = new ProductBrowseController(listingQueries, searchLog);
    private final UUID brakes = UUID.randomUUID();

    @Test
    void filtersByTextAndCategoryAndRecordsResultCount() {
        when(listingQueries.publishedListings(any())).thenReturn(List.of(
                listing("Pastillas de freno Mazdá", brakes), listing("Filtro de aceite", UUID.randomUUID())));

        List<ProductResponse> result = controller.published(null, null, null, " PASTILLAS mazda", brakes, "sess",
                new UsernamePasswordAuthenticationToken("a@b.co", null, AuthorityUtils.NO_AUTHORITIES));

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

        List<ProductResponse> result = controller.published(UUID.randomUUID(), null, null, null, null, null,
                new AnonymousAuthenticationToken("key", "anonymous",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(result).hasSize(1);
    }

    @Test
    void anonymousIsNotAuthenticated() {
        when(listingQueries.publishedListings(any())).thenReturn(List.of());
        controller.published(null, null, null, "filtro", null, null, null);
        ArgumentCaptor<MarketplaceSearch> search = ArgumentCaptor.forClass(MarketplaceSearch.class);
        verify(searchLog).record(search.capture());
        assertThat(search.getValue().authenticated()).isFalse();
        assertThat(search.getValue().resultsCount()).isZero();
    }

    private static ProductResponse listing(String name, UUID categoryId) {
        return new ProductResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Sede", name, "SKU",
                null, null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, 1, null, null, false, categoryId,
                "ACTIVE", false, Map.of(), List.of(), List.of());
    }
}
