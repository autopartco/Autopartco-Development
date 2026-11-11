package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.ListingsChanged;
import com.autopartco.backend.shared.web.PageResponse;

class ProductReviewServiceTest {
    private static final String EMAIL = "ana@correo.co";
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    private final ProductReviewQueries queries = mock(ProductReviewQueries.class);
    private final ReviewQueries reviews = mock(ReviewQueries.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final List<Object> published = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ProductReviewService service = new ProductReviewService(queries, reviews, accountLookup,
            storeAccess, published::add, ReviewWriteLimiter.inMemory(clock), clock);

    private final UUID productId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeEach
    void buyerAndProduct() {
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.of(new AccountRef(UUID.randomUUID(), personId,
                EMAIL, "Ana María Pérez Gómez", "ACTIVE", "CUSTOMER", "Ana María")));
        when(queries.productStore(productId)).thenReturn(Optional.of(storeId));
    }

    private ReviewQueries.ReviewRow row(int rating, String comment) {
        return new ReviewQueries.ReviewRow(reviewId, rating, comment, now, now, null, null, "Ana María",
                "Pérez Gómez");
    }

    private ProductReviewRequest request(int rating, String comment, Map<String, Integer> aspects) {
        return new ProductReviewRequest(orderId, rating, comment, aspects);
    }

    @Test
    void buyerWhoReceivedTheProductCreatesAReview() {
        when(queries.deliveredInOrder(orderId, personId, productId)).thenReturn(true);
        when(queries.existing(personId, productId)).thenReturn(Optional.empty());
        when(queries.insert(productId, storeId, personId, orderId, 5, "Encajó perfecto")).thenReturn(reviewId);
        when(reviews.row(ReviewKind.PRODUCT, reviewId)).thenReturn(Optional.of(row(5, "Encajó perfecto")));
        when(reviews.aspectsOf(ReviewKind.PRODUCT, reviewId)).thenReturn(Map.of("FIT", 5, "QUALITY", 4));

        ProductReviewResponse review = service.create(productId, EMAIL,
                request(5, " Encajó perfecto ", Map.of("fit", 5, "QUALITY", 4)));

        assertThat(review.id()).isEqualTo(reviewId);
        assertThat(review.author()).isEqualTo("Ana P.");
        assertThat(review.verified()).isTrue();
        assertThat(review.aspects()).containsEntry("FIT", 5).containsEntry("QUALITY", 4);
        verify(reviews).replaceAspects(eq(ReviewKind.PRODUCT), eq(reviewId), eq(Map.of("QUALITY", 4, "FIT", 5)));
        assertThat(published).containsExactly(ListingsChanged.ofProduct(productId));
    }

    @Test
    void withoutADeliveredPurchaseIsForbidden() {
        when(queries.deliveredInOrder(orderId, personId, productId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(productId, EMAIL, request(5, null, null)))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_ELIGIBLE"));
        verify(queries, never()).insert(any(), any(), any(), any(), anyInt(), any());
        assertThat(published).isEmpty();
    }

    @Test
    void storeTeamCannotReviewItsOwnProducts() {
        when(storeAccess.isMember(personId, storeId)).thenReturn(true);
        when(queries.deliveredInOrder(orderId, personId, productId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(productId, EMAIL, request(5, null, null)))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_ELIGIBLE"));
    }

    @Test
    void aSecondReviewIsConflict() {
        when(queries.deliveredInOrder(orderId, personId, productId)).thenReturn(true);
        when(queries.existing(personId, productId)).thenReturn(Optional.of(
                new ProductReviewQueries.Existing(reviewId, now)));

        assertThatThrownBy(() -> service.create(productId, EMAIL, request(4, null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));
        verify(queries, never()).insert(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void concurrentDuplicateIsConflict() {
        when(queries.deliveredInOrder(orderId, personId, productId)).thenReturn(true);
        when(queries.existing(personId, productId)).thenReturn(Optional.empty());
        when(queries.insert(any(), any(), any(), any(), anyInt(), any())).thenThrow(new DuplicateKeyException("uq"));

        assertThatThrownBy(() -> service.create(productId, EMAIL, request(4, null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void invalidAspectsAndLinksAreBadRequests() {
        assertThatThrownBy(() -> service.create(productId, EMAIL, request(5, null, Map.of("SERVICE", 5))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ASPECT_INVALID"));
        assertThatThrownBy(() -> service.create(productId, EMAIL, request(5, null, Map.of("FIT", 6))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ASPECT_INVALID"));
        assertThatThrownBy(() -> service.create(productId, EMAIL, request(5, "Más barato en https://x.com", null)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_LINKS_NOT_ALLOWED"));
        verify(queries, never()).deliveredInOrder(any(), any(), any());
    }

    @Test
    void unknownProductIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(queries.productStore(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summary(missing))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
        assertThatThrownBy(() -> service.list(missing, null, null, null, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void editingAfterThirtyDaysIsConflict() {
        when(reviews.owned(ReviewKind.PRODUCT, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, productId, personId, now.minusDays(30).minusMinutes(1))));

        assertThatThrownBy(() -> service.update(productId, reviewId, EMAIL, new ReviewUpdateRequest(3, null, null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_EDIT_WINDOW_CLOSED"));
        verify(reviews, never()).update(any(), any(), anyInt(), any());
    }

    @Test
    void authorEditsWithinTheWindowReplacingAspects() {
        when(reviews.owned(ReviewKind.PRODUCT, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, productId, personId, now.minusDays(10))));
        when(reviews.row(ReviewKind.PRODUCT, reviewId)).thenReturn(Optional.of(row(3, null)));

        service.update(productId, reviewId, EMAIL, new ReviewUpdateRequest(3, "  ", null));

        verify(reviews).update(ReviewKind.PRODUCT, reviewId, 3, null);
        verify(reviews).replaceAspects(ReviewKind.PRODUCT, reviewId, Map.of());
        assertThat(published).containsExactly(ListingsChanged.ofProduct(productId));
    }

    @Test
    void reviewOfAnotherProductOrPersonIsNotFound() {
        when(reviews.owned(ReviewKind.PRODUCT, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, UUID.randomUUID(), personId, now)));

        assertThatThrownBy(() -> service.delete(productId, reviewId, EMAIL))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_FOUND"));
        verify(reviews, never()).softDelete(any(), any());
    }

    @Test
    void authorDeletesAndTheIndexIsRefreshed() {
        when(reviews.owned(ReviewKind.PRODUCT, reviewId)).thenReturn(Optional.of(
                new ReviewQueries.OwnedReview(reviewId, productId, personId, now.minusDays(60))));

        service.delete(productId, reviewId, EMAIL);

        verify(reviews).softDelete(ReviewKind.PRODUCT, reviewId);
        assertThat(published).containsExactly(ListingsChanged.ofProduct(productId));
    }

    @Test
    void eligibilityWithoutReviewListsOrders() {
        when(queries.existing(personId, productId)).thenReturn(Optional.empty());
        when(queries.eligibleOrders(productId, personId)).thenReturn(List.of(orderId));

        ProductReviewEligibility eligibility = service.eligibility(productId, EMAIL);

        assertThat(eligibility.eligibleOrderIds()).containsExactly(orderId);
        assertThat(eligibility.existingReviewId()).isNull();
        assertThat(eligibility.editableUntil()).isNull();
    }

    @Test
    void eligibilityWithReviewReturnsItAndTheEditDeadline() {
        when(queries.existing(personId, productId)).thenReturn(Optional.of(
                new ProductReviewQueries.Existing(reviewId, now)));

        ProductReviewEligibility eligibility = service.eligibility(productId, EMAIL);

        assertThat(eligibility.eligibleOrderIds()).isEmpty();
        assertThat(eligibility.existingReviewId()).isEqualTo(reviewId);
        assertThat(eligibility.editableUntil()).isEqualTo(now.plusDays(30));
        verify(queries, never()).eligibleOrders(any(), any());
    }

    @Test
    void publicListNeverExposesFullSurnameOrEmail() {
        when(reviews.list(eq(ReviewKind.PRODUCT), eq(productId), any())).thenReturn(List.of(row(4, "Bien")));
        when(reviews.count(eq(ReviewKind.PRODUCT), eq(productId), any())).thenReturn(1L);
        when(reviews.aspectsOf(eq(ReviewKind.PRODUCT), any(java.util.Collection.class))).thenReturn(Map.of());

        PageResponse<ProductReviewResponse> page = service.list(productId, null, null, "lowest", null, null);

        ProductReviewResponse item = page.items().getFirst();
        assertThat(item.author()).isEqualTo("Ana P.");
        assertThat(item.toString()).doesNotContain("Pérez", "Gómez", EMAIL);
        assertThat(page.total()).isEqualTo(1);
        verify(reviews).list(ReviewKind.PRODUCT, productId,
                new ReviewListQuery(1, 10, ReviewListQuery.Sort.LOWEST, null, false));
    }

    @Test
    void listSqlHidesDeletedAndHiddenReviewsAndUsesParameters() {
        String sql = ReviewQueries.listSql(ReviewKind.PRODUCT,
                new ReviewListQuery(1, 10, ReviewListQuery.Sort.HIGHEST, 4, true));

        assertThat(sql).contains("FROM product_reviews r", "r.product_id = :scopeId",
                "r.deleted_at IS NULL AND r.hidden_at IS NULL", "r.rating = :stars", "r.comment IS NOT NULL",
                "ORDER BY r.rating DESC, r.created_at DESC, r.id", "LIMIT :limit OFFSET :offset");
        assertThat(sql).doesNotContain("email");
        assertThat(ReviewQueries.distributionSql(ReviewKind.PRODUCT)).contains("hidden_at IS NULL");
        assertThat(ReviewQueries.aspectStatsSql(ReviewKind.STORE))
                .contains("FROM store_review_aspects a", "JOIN store_reviews r", "hidden_at IS NULL");
    }
}
