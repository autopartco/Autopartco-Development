package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.reviews.AdminReview;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.ListingsChanged;
import com.autopartco.backend.shared.events.StoreProfileChanged;

class ReviewModerationServiceTest {
    private final ReviewDeskQueries desk = mock(ReviewDeskQueries.class);
    private final List<Object> published = new ArrayList<>();
    private final ReviewModerationService service = new ReviewModerationService(desk, published::add);
    private final UUID storeId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-11T10:00:00Z");

    private ReviewDeskQueries.DeskRow row(ReviewKind kind, boolean hidden) {
        boolean product = kind == ReviewKind.PRODUCT;
        return new ReviewDeskQueries.DeskRow(kind, reviewId, 1, "Estafadores", now, now, null, null,
                hidden ? now : null, hidden ? "Insultos" : null, storeId, "Autopartes", product ? productId : null,
                product ? "Pastillas" : null, "Ana", "Pérez");
    }

    @Test
    void hidingAStoreReviewReindexesTheStore() {
        when(desk.find(ReviewKind.STORE, null, reviewId)).thenReturn(Optional.of(row(ReviewKind.STORE, false)),
                Optional.of(row(ReviewKind.STORE, true)));

        AdminReview result = service.hide(ReviewType.STORE, reviewId, "  Insultos  ");

        verify(desk).hide(ReviewKind.STORE, reviewId, "Insultos");
        assertThat(result.hidden()).isTrue();
        assertThat(result.hiddenReason()).isEqualTo("Insultos");
        assertThat(result.author()).isEqualTo("Ana P.");
        assertThat(published).containsExactly(ListingsChanged.ofStore(storeId));
    }

    @Test
    void hidingAProductReviewReindexesTheProductAndClearsTheStoreProfile() {
        when(desk.find(ReviewKind.PRODUCT, null, reviewId)).thenReturn(Optional.of(row(ReviewKind.PRODUCT, false)),
                Optional.of(row(ReviewKind.PRODUCT, true)));

        service.hide(ReviewType.PRODUCT, reviewId, "Spam");

        assertThat(published).containsExactly(ListingsChanged.ofProduct(productId), new StoreProfileChanged(storeId));
    }

    @Test
    void unhidingPublishesTheSameEvents() {
        when(desk.find(ReviewKind.PRODUCT, null, reviewId)).thenReturn(Optional.of(row(ReviewKind.PRODUCT, true)),
                Optional.of(row(ReviewKind.PRODUCT, false)));

        AdminReview result = service.unhide(ReviewType.PRODUCT, reviewId);

        verify(desk).unhide(ReviewKind.PRODUCT, reviewId);
        assertThat(result.hidden()).isFalse();
        assertThat(published).containsExactly(ListingsChanged.ofProduct(productId), new StoreProfileChanged(storeId));
    }

    @Test
    void reasonOutsideTheRangeIsRejectedBeforeTouchingAnything() {
        assertThatThrownBy(() -> service.hide(ReviewType.STORE, reviewId, " ab "))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_REASON_INVALID"));
        assertThatThrownBy(() -> service.hide(ReviewType.STORE, reviewId, "a".repeat(201)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.hide(ReviewType.STORE, reviewId, null))
                .isInstanceOf(BadRequestException.class);
        verify(desk, never()).hide(any(), any(), any());
    }

    @Test
    void missingReviewIs404AndStateConflictsAre409() {
        when(desk.find(ReviewKind.STORE, null, reviewId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.hide(ReviewType.STORE, reviewId, "Spam"))
                .isInstanceOf(NotFoundException.class);

        when(desk.find(ReviewKind.PRODUCT, null, reviewId)).thenReturn(Optional.of(row(ReviewKind.PRODUCT, true)));
        assertThatThrownBy(() -> service.hide(ReviewType.PRODUCT, reviewId, "Spam"))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_ALREADY_HIDDEN"));

        when(desk.find(ReviewKind.STORE, null, reviewId)).thenReturn(Optional.of(row(ReviewKind.STORE, false)));
        assertThatThrownBy(() -> service.unhide(ReviewType.STORE, reviewId))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_HIDDEN"));
        assertThat(published).isEmpty();
    }

    @Test
    void listFiltersByHiddenAndTypeAcrossAllStores() {
        service.list(true, ReviewType.PRODUCT, null, null);

        org.mockito.ArgumentCaptor<ReviewDeskQueries.Filter> filter = org.mockito.ArgumentCaptor
                .forClass(ReviewDeskQueries.Filter.class);
        verify(desk).list(filter.capture(), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0));
        assertThat(filter.getValue().storeId()).isNull();
        assertThat(filter.getValue().hidden()).isTrue();
        assertThat(filter.getValue().kind()).isEqualTo(ReviewKind.PRODUCT);
    }
}
