package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

class StoreReviewServiceTest {
    private static final String EMAIL = "ana@correo.co";

    private final StoreReviewQueries queries = mock(StoreReviewQueries.class);
    private final PublishedStores publishedStores = mock(PublishedStores.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final StoreReviewService service = new StoreReviewService(queries, publishedStores, accountLookup, storeAccess);

    private final UUID storeId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();

    @BeforeEach
    void buyer() {
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.of(new AccountRef(UUID.randomUUID(), personId,
                EMAIL, "Ana María Pérez Gómez", "ACTIVE", "CUSTOMER", "Ana María")));
    }

    @Test
    void eligibleBuyerCreatesReview() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID reviewId = UUID.randomUUID();
        when(queries.orderOwner(orderId)).thenReturn(Optional.of(personId));
        when(queries.hasDeliveredSale(orderId, storeId)).thenReturn(true);
        when(queries.insert(storeId, personId, orderId, 5, "Muy buen servicio"))
                .thenReturn(new StoreReviewQueries.Inserted(reviewId, now));

        StoreReviewResponse review = service.create(storeId, EMAIL,
                new StoreReviewRequest(orderId, 5, "  Muy buen servicio  "));

        assertThat(review).isEqualTo(new StoreReviewResponse(reviewId, 5, "Muy buen servicio", "Ana P.", now));
    }

    @Test
    void blankCommentIsStoredAsNull() {
        when(queries.orderOwner(orderId)).thenReturn(Optional.of(personId));
        when(queries.hasDeliveredSale(orderId, storeId)).thenReturn(true);
        when(queries.insert(eq(storeId), eq(personId), eq(orderId), eq(4), isNull()))
                .thenReturn(new StoreReviewQueries.Inserted(UUID.randomUUID(), OffsetDateTime.now()));

        assertThat(service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 4, "   ")).comment()).isNull();
    }

    @Test
    void orderNotDeliveredIsRejected() {
        when(queries.orderOwner(orderId)).thenReturn(Optional.of(personId));
        when(queries.hasDeliveredSale(orderId, storeId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 5, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_ALLOWED"));
        verify(queries, never()).insert(any(), any(), any(), anyInt(), any());
    }

    @Test
    void someoneElsesOrderIsNotFound() {
        when(queries.orderOwner(orderId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 5, null)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        verify(queries, never()).hasDeliveredSale(any(), any());
    }

    @Test
    void missingOrderIsNotFound() {
        when(queries.orderOwner(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 5, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void duplicateReviewIsConflict() {
        when(queries.orderOwner(orderId)).thenReturn(Optional.of(personId));
        when(queries.hasDeliveredSale(orderId, storeId)).thenReturn(true);
        when(queries.insert(any(), any(), any(), anyInt(), any())).thenThrow(new DuplicateKeyException("uq"));

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 3, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void unpublishedStoreCannotBeReviewed() {
        doThrow(new NotFoundException("STORE_NOT_FOUND", "x")).when(publishedStores).requirePublished(storeId);

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 5, null)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORE_NOT_FOUND"));
        assertThatThrownBy(() -> service.list(storeId, null, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsPagesFromOneWithBoundedSize() {
        when(queries.rating(storeId)).thenReturn(new StoreRating(new BigDecimal("4.5"), 23));
        when(queries.list(storeId, 50, 50)).thenReturn(List.of());

        PageResponse<StoreReviewResponse> page = service.list(storeId, 2, 500);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.total()).isEqualTo(23);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void eligibilityListsBuyerOrders() {
        when(queries.eligibleOrders(storeId, personId)).thenReturn(List.of(orderId));

        assertThat(service.eligibility(storeId, EMAIL).eligibleOrderIds()).containsExactly(orderId);
    }

    @Test
    void authorNameShowsFirstNameAndSurnameInitial() {
        assertThat(StoreReviewService.authorName("Ana María", "pérez Gómez")).isEqualTo("Ana P.");
        assertThat(StoreReviewService.authorName("Luis", null)).isEqualTo("Luis");
        assertThat(StoreReviewService.authorName(" ", "Ruiz")).isEqualTo("Comprador R.");
    }
}
