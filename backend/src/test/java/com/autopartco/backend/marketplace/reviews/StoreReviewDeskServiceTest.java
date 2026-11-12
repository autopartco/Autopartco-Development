package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.reviews.PortalReview;
import com.autopartco.backend.core.reviews.PortalReviewSummary;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.StoreProfileChanged;
import com.autopartco.backend.shared.web.PageResponse;

class StoreReviewDeskServiceTest {
    private static final String EMAIL = "gerente@almacen.co";

    private final ReviewDeskQueries desk = mock(ReviewDeskQueries.class);
    private final ReviewQueries reviews = mock(ReviewQueries.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final List<Object> published = new ArrayList<>();
    private final StoreReviewDeskService service = new StoreReviewDeskService(desk, reviews, accountLookup,
            published::add);

    private final UUID storeId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-11T10:00:00Z");

    @BeforeEach
    void account() {
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.of(new AccountRef(accountId, UUID.randomUUID(),
                EMAIL, "Gerente Uno", "ACTIVE", "SELLER", "Gerente")));
    }

    private ReviewDeskQueries.DeskRow row(ReviewKind kind, String reply, OffsetDateTime hiddenAt) {
        boolean product = kind == ReviewKind.PRODUCT;
        return new ReviewDeskQueries.DeskRow(kind, reviewId, 2, "Llegó tarde", now, now, reply,
                reply == null ? null : now, hiddenAt, hiddenAt == null ? null : "Insultos", storeId, "Autopartes",
                product ? UUID.randomUUID() : null, product ? "Pastillas de freno" : null, "Ana María",
                "Pérez Gómez");
    }

    @Test
    void listIncludesHiddenReviewsFlaggedAndNeverTheFullSurname() {
        ReviewDeskQueries.DeskRow hidden = row(ReviewKind.PRODUCT, null, now);
        when(desk.list(any(), eq(20), eq(0))).thenReturn(List.of(hidden));
        when(desk.count(any())).thenReturn(1L);
        when(reviews.aspectsOf(eq(ReviewKind.PRODUCT), any(java.util.Collection.class)))
                .thenReturn(Map.of(reviewId, Map.of("QUALITY", 2)));

        PageResponse<PortalReview> page = service.list(storeId, null, null, null, null, null);

        assertThat(page.total()).isEqualTo(1);
        PortalReview item = page.items().get(0);
        assertThat(item.hidden()).isTrue();
        assertThat(item.type()).isEqualTo(ReviewType.PRODUCT);
        assertThat(item.productName()).isEqualTo("Pastillas de freno");
        assertThat(item.author()).isEqualTo("Ana P.");
        assertThat(item.aspects()).containsEntry("QUALITY", 2);
        ArgumentCaptor<ReviewDeskQueries.Filter> filter = ArgumentCaptor.forClass(ReviewDeskQueries.Filter.class);
        verify(desk).list(filter.capture(), eq(20), eq(0));
        assertThat(filter.getValue().storeId()).isEqualTo(storeId);
        assertThat(filter.getValue().hidden()).isNull();
    }

    @Test
    void listNormalizesPaginationAndRejectsBadStars() {
        service.list(storeId, ReviewType.STORE, true, 5, 3, 999);

        ArgumentCaptor<ReviewDeskQueries.Filter> filter = ArgumentCaptor.forClass(ReviewDeskQueries.Filter.class);
        verify(desk).list(filter.capture(), eq(50), eq(100));
        assertThat(filter.getValue().kind()).isEqualTo(ReviewKind.STORE);
        assertThat(filter.getValue().replied()).isTrue();
        assertThat(filter.getValue().stars()).isEqualTo(5);
        assertThatThrownBy(() -> service.list(storeId, null, null, 6, null, null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_STARS_INVALID"));
    }

    @Test
    void listSqlUnionsBothTablesScopedToTheStoreWithoutDeleted() {
        ReviewDeskQueries.Filter all = new ReviewDeskQueries.Filter(storeId, null, null, false, 3, null);
        String sql = ReviewDeskQueries.listSql(all);

        assertThat(sql).contains("FROM store_reviews r").contains("FROM product_reviews r").contains("UNION ALL")
                .contains("r.store_id = :storeId").contains("r.deleted_at IS NULL")
                .contains("x.reply IS NULL").contains("x.rating = :stars").doesNotContain("hidden_at IS NULL AND");
        assertThat(ReviewDeskQueries.listSql(new ReviewDeskQueries.Filter(storeId, ReviewKind.STORE, null, null, null,
                null))).doesNotContain("product_reviews");
        assertThat(ReviewDeskQueries.listSql(new ReviewDeskQueries.Filter(null, null, null, null, null, true)))
                .doesNotContain(":storeId").contains("x.hidden_at IS NOT NULL");
    }

    @Test
    void summaryAddsUnansweredAndProductRankings() {
        PortalReviewSummary.ProductRating best = new PortalReviewSummary.ProductRating(UUID.randomUUID(), "Filtro",
                new BigDecimal("4.8"), 5);
        PortalReviewSummary.ProductRating worst = new PortalReviewSummary.ProductRating(UUID.randomUUID(), "Bujía",
                new BigDecimal("2.0"), 3);
        when(reviews.summary(ReviewKind.STORE, storeId)).thenReturn(ReviewSummary.of(Map.of(5, 3L, 4, 1L),
                List.of(new ReviewSummary.AspectStat("SPEED", new BigDecimal("4.50"), 4)), ReviewKind.STORE.aspects()));
        when(desk.unanswered(storeId)).thenReturn(7L);
        when(desk.rankedProducts(storeId, true)).thenReturn(List.of(best));
        when(desk.rankedProducts(storeId, false)).thenReturn(List.of(worst));

        PortalReviewSummary summary = service.summary(storeId);

        assertThat(summary.count()).isEqualTo(4);
        assertThat(summary.average()).isEqualByComparingTo("4.8");
        assertThat(summary.unanswered()).isEqualTo(7);
        assertThat(summary.topProducts()).containsExactly(best);
        assertThat(summary.bottomProducts()).containsExactly(worst);
        assertThat(summary.aspects()).extracting(PortalReviewSummary.AspectStat::aspect).containsExactly("SPEED");
        assertThat(summary.highlights()).containsExactly("SPEED");
    }

    @Test
    void replySavesTrimmedTextAsTheAccountAndClearsTheProfileCache() {
        when(desk.setReply(ReviewKind.STORE, storeId, reviewId, accountId, "Gracias por tu opinión")).thenReturn(true);
        when(desk.find(ReviewKind.STORE, storeId, reviewId))
                .thenReturn(Optional.of(row(ReviewKind.STORE, "Gracias por tu opinión", null)));

        PortalReview result = service.reply(storeId, ReviewType.STORE, reviewId, EMAIL, "  Gracias por tu opinión  ");

        assertThat(result.reply().text()).isEqualTo("Gracias por tu opinión");
        assertThat(published).containsExactly(new StoreProfileChanged(storeId));
    }

    @Test
    void replyToAReviewOfAnotherStoreIs404() {
        when(desk.setReply(any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.reply(storeId, ReviewType.PRODUCT, reviewId, EMAIL, "Gracias"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_FOUND"));
        assertThat(published).isEmpty();
    }

    @Test
    void replyRejectsLinksEmptyAndLongText() {
        assertThatThrownBy(() -> service.reply(storeId, ReviewType.STORE, reviewId, EMAIL, "Visítanos en www.tienda.co"))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_LINKS_NOT_ALLOWED"));
        assertThatThrownBy(() -> service.reply(storeId, ReviewType.STORE, reviewId, EMAIL, "   "))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.reply(storeId, ReviewType.STORE, reviewId, EMAIL, "a".repeat(1001)))
                .isInstanceOf(BadRequestException.class);
        verify(desk, never()).setReply(any(), any(), any(), any(), any());
    }

    @Test
    void removingTheReplyClearsItOrIs404() {
        when(desk.clearReply(ReviewKind.PRODUCT, storeId, reviewId)).thenReturn(true);

        service.removeReply(storeId, ReviewType.PRODUCT, reviewId);

        assertThat(published).containsExactly(new StoreProfileChanged(storeId));
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.removeReply(storeId, ReviewType.PRODUCT, other))
                .isInstanceOf(NotFoundException.class);
    }
}
