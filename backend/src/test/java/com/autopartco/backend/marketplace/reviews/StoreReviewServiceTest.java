package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.events.ListingsChanged;
import com.autopartco.backend.shared.web.PageResponse;

class StoreReviewServiceTest {
    private static final String EMAIL = "ana@correo.co";
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    private final StoreReviewQueries queries = mock(StoreReviewQueries.class);
    private final ReviewQueries reviews = mock(ReviewQueries.class);
    private final PublishedStores publishedStores = mock(PublishedStores.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final List<Object> published = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final StoreReviewService service = new StoreReviewService(queries, reviews, publishedStores,
            accountLookup, storeAccess, published::add, ReviewWriteLimiter.inMemory(clock), clock);

    private final UUID storeId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeEach
    void buyer() {
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.of(new AccountRef(UUID.randomUUID(), personId,
                EMAIL, "Ana María Pérez Gómez", "ACTIVE", "CUSTOMER", "Ana María")));
    }

    private ReviewQueries.ReviewRow row(int rating, String comment) {
        return new ReviewQueries.ReviewRow(reviewId, rating, comment, now, now, null, null, "Ana María",
                "Pérez Gómez");
    }

    private void eligible() {
        when(queries.orderOwner(orderId)).thenReturn(Optional.of(personId));
        when(queries.hasDeliveredSale(orderId, storeId)).thenReturn(true);
    }

    @Test
    void eligibleBuyerCreatesReviewWithAspects() {
        eligible();
        when(queries.insert(storeId, personId, orderId, 5, "Muy buen servicio")).thenReturn(reviewId);
        when(reviews.row(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(row(5, "Muy buen servicio")));
        when(reviews.aspectsOf(ReviewKind.STORE, reviewId)).thenReturn(Map.of("SPEED", 5));

        StoreReviewResponse review = service.create(storeId, EMAIL,
                new StoreReviewRequest(orderId, 5, "  Muy buen servicio  ", Map.of("speed", 5)));

        assertThat(review.author()).isEqualTo("Ana P.");
        assertThat(review.authorName()).isEqualTo("Ana P.");
        assertThat(review.verified()).isTrue();
        assertThat(review.aspects()).containsExactly(Map.entry("SPEED", 5));
        assertThat(review.reply()).isNull();
        verify(reviews).replaceAspects(ReviewKind.STORE, reviewId, Map.of("SPEED", 5));
        assertThat(published).containsExactly(ListingsChanged.ofStore(storeId));
    }

    @Test
    void blankCommentIsStoredAsNull() {
        eligible();
        when(queries.insert(eq(storeId), eq(personId), eq(orderId), eq(4), isNull())).thenReturn(reviewId);
        when(reviews.row(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(row(4, null)));

        assertThat(service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 4, "   ")).comment()).isNull();
        verify(reviews, never()).replaceAspects(any(), any(), anyMap());
    }

    @Test
    void commentWithLinkIsRejected() {
        eligible();

        assertThatThrownBy(() -> service.create(storeId, EMAIL,
                new StoreReviewRequest(orderId, 5, "Compren en www.otro-almacen.co")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_LINKS_NOT_ALLOWED"));
        verify(queries, never()).insert(any(), any(), any(), anyInt(), any());
    }

    @Test
    void productAspectOnAStoreReviewIsRejected() {
        eligible();

        assertThatThrownBy(() -> service.create(storeId, EMAIL,
                new StoreReviewRequest(orderId, 5, null, Map.of("FIT", 5))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ASPECT_INVALID"));
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
    void duplicateReviewIsConflict() {
        eligible();
        when(queries.insert(any(), any(), any(), anyInt(), any())).thenThrow(new DuplicateKeyException("uq"));

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 3, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));
        assertThat(published).isEmpty();
    }

    @Test
    void unpublishedStoreCannotBeReviewed() {
        doThrow(new NotFoundException("STORE_NOT_FOUND", "x")).when(publishedStores).requirePublished(storeId);

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 5, null)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORE_NOT_FOUND"));
        assertThatThrownBy(() -> service.list(storeId, null, null, null, null, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.summary(storeId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsPagesFromOneWithBoundedSizeFiltersAndReply() {
        ReviewQueries.ReviewRow replied = new ReviewQueries.ReviewRow(reviewId, 5, "Excelente", now, now,
                "¡Gracias!", now, "Luis", "Torres");
        when(reviews.list(eq(ReviewKind.STORE), eq(storeId), any())).thenReturn(List.of(replied));
        when(reviews.count(eq(ReviewKind.STORE), eq(storeId), any())).thenReturn(23L);
        when(reviews.aspectsOf(eq(ReviewKind.STORE), any(java.util.Collection.class)))
                .thenReturn(Map.of(reviewId, Map.of("SERVICE", 5)));

        PageResponse<StoreReviewResponse> page = service.list(storeId, 2, 500, "highest", 5, true);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.total()).isEqualTo(23);
        StoreReviewResponse item = page.items().getFirst();
        assertThat(item.author()).isEqualTo("Luis T.");
        assertThat(item.reply()).isEqualTo(new ReviewReply("¡Gracias!", now));
        assertThat(item.aspects()).containsEntry("SERVICE", 5);
        verify(reviews).list(ReviewKind.STORE, storeId,
                new ReviewListQuery(2, 50, ReviewListQuery.Sort.HIGHEST, 5, true));
    }

    @Test
    void invalidSortOrStarsAreBadRequests() {
        assertThatThrownBy(() -> service.list(storeId, null, null, "best", null, null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_SORT_INVALID"));
        assertThatThrownBy(() -> service.list(storeId, null, null, null, 6, null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_STARS_INVALID"));
    }

    @Test
    void eligibilityListsBuyerOrders() {
        when(queries.eligibleOrders(storeId, personId)).thenReturn(List.of(orderId));

        ReviewEligibility eligibility = service.eligibility(storeId, EMAIL);

        assertThat(eligibility.eligibleOrderIds()).containsExactly(orderId);
        assertThat(eligibility.existingReviewId()).isNull();
        assertThat(eligibility.editableUntil()).isNull();
    }

    @Test
    void eligibilityIncludesTheLatestReviewedOrderAndItsEditWindow() {
        when(queries.eligibleOrders(storeId, personId)).thenReturn(List.of(orderId));
        when(queries.latestReviewed(storeId, personId)).thenReturn(Optional.of(
                new StoreReviewQueries.Reviewed(reviewId, now.minusDays(5))));

        ReviewEligibility eligibility = service.eligibility(storeId, EMAIL);

        assertThat(eligibility.eligibleOrderIds()).containsExactly(orderId);
        assertThat(eligibility.existingReviewId()).isEqualTo(reviewId);
        assertThat(eligibility.editableUntil()).isEqualTo(now.plusDays(25));
    }

    @Test
    void teamMembersGetAnEmptyEligibility() {
        when(storeAccess.isMember(personId, storeId)).thenReturn(true);

        assertThat(service.eligibility(storeId, EMAIL)).isEqualTo(new ReviewEligibility(List.of(), null, null));
        verify(queries, never()).latestReviewed(any(), any());
    }

    @Test
    void mineReturnsTheOwnReviewOfTheOrderWithItsAspects() {
        when(queries.reviewOfOrder(storeId, personId, orderId)).thenReturn(Optional.of(reviewId));
        when(reviews.row(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(row(4, "Bien")));
        when(reviews.aspectsOf(ReviewKind.STORE, reviewId)).thenReturn(Map.of("SPEED", 5));

        StoreReviewResponse mine = service.mine(storeId, EMAIL, orderId);

        assertThat(mine.id()).isEqualTo(reviewId);
        assertThat(mine.aspects()).containsEntry("SPEED", 5);
        assertThat(mine.author()).isEqualTo("Ana P.");
    }

    @Test
    void mineWithoutOrderUsesTheLatestReviewedOrder() {
        when(queries.latestReviewed(storeId, personId)).thenReturn(Optional.of(
                new StoreReviewQueries.Reviewed(reviewId, now)));
        when(reviews.row(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(row(5, null)));

        assertThat(service.mine(storeId, EMAIL, null).id()).isEqualTo(reviewId);
    }

    @Test
    void mineWithoutReviewIsNotFound() {
        assertThatThrownBy(() -> service.mine(storeId, EMAIL, orderId))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_FOUND"));
    }

    @Test
    void latestReviewedOnlyReadsOwnLiveReviewsOfDeliveredOrders() {
        assertThat(StoreReviewQueries.LATEST_REVIEWED_SQL)
                .contains("r.person_id = :personId", "r.deleted_at IS NULL", "st.code = 'DELIVERED'");
        assertThat(StoreReviewQueries.REVIEW_OF_ORDER_SQL)
                .contains("r.person_id = :personId", "r.order_id = :orderId", "r.deleted_at IS NULL");
    }

    @Test
    void authorEditsWithinThirtyDays() {
        when(reviews.owned(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, storeId, personId, now.minusDays(29))));
        when(reviews.row(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(row(2, "Tardó")));

        StoreReviewResponse updated = service.update(storeId, reviewId, EMAIL,
                new ReviewUpdateRequest(2, " Tardó ", Map.of("SPEED", 1)));

        assertThat(updated.rating()).isEqualTo(2);
        verify(reviews).update(ReviewKind.STORE, reviewId, 2, "Tardó");
        verify(reviews).replaceAspects(ReviewKind.STORE, reviewId, Map.of("SPEED", 1));
        assertThat(published).containsExactly(ListingsChanged.ofStore(storeId));
    }

    @Test
    void editingAfterThirtyDaysIsConflict() {
        when(reviews.owned(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, storeId, personId, now.minusDays(31))));

        assertThatThrownBy(() -> service.update(storeId, reviewId, EMAIL, new ReviewUpdateRequest(4, null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_EDIT_WINDOW_CLOSED"));
        verify(reviews, never()).update(any(), any(), anyInt(), any());
    }

    @Test
    void someoneElsesReviewIsNotFoundForEditOrDelete() {
        when(reviews.owned(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, storeId, UUID.randomUUID(), now)));

        assertThatThrownBy(() -> service.update(storeId, reviewId, EMAIL, new ReviewUpdateRequest(4, null, null)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_FOUND"));
        assertThatThrownBy(() -> service.delete(storeId, reviewId, EMAIL)).isInstanceOf(NotFoundException.class);
        verify(reviews, never()).softDelete(any(), any());
    }

    @Test
    void authorDeletesEvenAfterTheEditWindow() {
        when(reviews.owned(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, storeId, personId, now.minusDays(90))));

        service.delete(storeId, reviewId, EMAIL);

        verify(reviews).softDelete(ReviewKind.STORE, reviewId);
        assertThat(published).containsExactly(ListingsChanged.ofStore(storeId));
    }

    @Test
    void writesAreLimitedToTenPerHourPerAccount() {
        when(reviews.owned(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, storeId, personId, now)));
        for (int i = 0; i < ReviewWriteLimiter.MAX_PER_HOUR; i++) {
            service.delete(storeId, reviewId, EMAIL);
        }

        assertThatThrownBy(() -> service.delete(storeId, reviewId, EMAIL))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_RATE_LIMITED"));
    }

    @Test
    void recreatingAReviewHiddenByModerationIsConflict() {
        eligible();
        when(queries.deleted(storeId, personId, orderId)).thenReturn(Optional.of(
                new ReviewQueries.Deleted(reviewId, true)));

        assertThatThrownBy(() -> service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 5, "Excelente")))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_MODERATED"));
        verify(queries, never()).insert(any(), any(), any(), anyInt(), any());
        verify(reviews, never()).restore(any(), any(), any(), anyInt(), any());
        assertThat(published).isEmpty();
    }

    @Test
    void recreatingADeletedReviewReusesTheRow() {
        eligible();
        when(queries.deleted(storeId, personId, orderId)).thenReturn(Optional.of(
                new ReviewQueries.Deleted(reviewId, false)));
        when(reviews.restore(ReviewKind.STORE, reviewId, orderId, 4, "Volví a comprar")).thenReturn(true);
        when(reviews.row(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(row(4, "Volví a comprar")));

        StoreReviewResponse review = service.create(storeId, EMAIL,
                new StoreReviewRequest(orderId, 4, "Volví a comprar"));

        assertThat(review.id()).isEqualTo(reviewId);
        verify(queries, never()).insert(any(), any(), any(), anyInt(), any());
        verify(reviews).replaceAspects(ReviewKind.STORE, reviewId, Map.of());
        assertThat(published).containsExactly(ListingsChanged.ofStore(storeId));
        assertThat(StoreReviewQueries.DELETED_SQL).contains("order_id = :orderId", "deleted_at IS NOT NULL");
        assertThat(StoreReviewQueries.ELIGIBLE_ORDERS_SQL)
                .contains("r.deleted_at IS NULL OR r.hidden_at IS NOT NULL");
    }

    @Test
    void rejectedAttemptsDoNotSpendTheWriteQuota() {
        eligible();
        when(queries.insert(storeId, personId, orderId, 5, null)).thenReturn(reviewId);
        when(reviews.row(ReviewKind.STORE, reviewId)).thenReturn(Optional.of(row(5, null)));
        for (int i = 0; i <= ReviewWriteLimiter.MAX_PER_HOUR; i++) {
            assertThatThrownBy(() -> service.create(storeId, EMAIL,
                    new StoreReviewRequest(orderId, 5, "Mejor en https://otro.co")))
                    .isInstanceOf(BadRequestException.class);
        }

        assertThat(service.create(storeId, EMAIL, new StoreReviewRequest(orderId, 5, null)).id()).isEqualTo(reviewId);
    }

    @Test
    void authorNameShowsFirstNameAndSurnameInitial() {
        assertThat(StoreReviewService.authorName("Ana María", "pérez Gómez")).isEqualTo("Ana P.");
        assertThat(StoreReviewService.authorName("Luis", null)).isEqualTo("Luis");
        assertThat(StoreReviewService.authorName(" ", "Ruiz")).isEqualTo("Comprador R.");
    }
}
