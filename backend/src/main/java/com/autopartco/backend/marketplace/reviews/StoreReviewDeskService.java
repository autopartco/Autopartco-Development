package com.autopartco.backend.marketplace.reviews;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.reviews.PortalReview;
import com.autopartco.backend.core.reviews.PortalReviewSummary;
import com.autopartco.backend.core.reviews.ReviewReplyView;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.core.reviews.StoreReviewDesk;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.StoreProfileChanged;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Portal del almacén: lista las reseñas del almacén y de sus productos (con las ocultas marcadas), su resumen y la
 * respuesta. Reutiliza las consultas y el resumen de las reseñas públicas. Responder o quitar la respuesta vacía el
 * perfil público cacheado ({@link StoreProfileChanged}); el promedio no cambia, así que no reindexa.
 */
@Service
@AllArgsConstructor
public class StoreReviewDeskService implements StoreReviewDesk {
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final ReviewDeskQueries desk;
    private final ReviewQueries reviews;
    private final AccountLookup accountLookup;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PortalReview> list(UUID storeId, ReviewType type, Boolean replied, Integer stars,
            Integer page, Integer size) {
        if (stars != null && (stars < 1 || stars > 5)) {
            throw new BadRequestException("REVIEW_STARS_INVALID", "Las estrellas deben estar entre 1 y 5.");
        }
        int p = page == null ? 1 : Math.min(ReviewListQuery.MAX_PAGE, Math.max(1, page));
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        ReviewDeskQueries.Filter filter = new ReviewDeskQueries.Filter(storeId, kind(type), null, replied, stars,
                null);
        List<ReviewDeskQueries.DeskRow> rows = desk.list(filter, s, (p - 1) * s);
        Map<UUID, Map<String, Integer>> aspects = aspectsOf(rows);
        List<PortalReview> items = rows.stream()
                .map(row -> toPortal(row, aspects.getOrDefault(row.id(), Map.of())))
                .toList();
        return PageResponse.of(items, p, s, desk.count(filter));
    }

    @Override
    @Transactional(readOnly = true)
    public PortalReviewSummary summary(UUID storeId) {
        ReviewSummary base = reviews.summary(ReviewKind.STORE, storeId);
        return new PortalReviewSummary(
                base.average(),
                base.count(),
                base.distribution(),
                base.aspects().stream()
                        .map(aspect -> new PortalReviewSummary.AspectStat(aspect.aspect(), aspect.average(),
                                aspect.count()))
                        .toList(),
                base.highlights(),
                desk.unanswered(storeId),
                desk.rankedProducts(storeId, true),
                desk.rankedProducts(storeId, false));
    }

    @Override
    @Transactional
    public PortalReview reply(UUID storeId, ReviewType type, UUID reviewId, String email, String text) {
        ReviewKind kind = kind(type);
        String reply = ReviewRules.reply(text);
        AccountRef account = accountLookup.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
        if (!desk.setReply(kind, storeId, reviewId, account.accountId(), reply)) {
            throw notFound();
        }
        events.publishEvent(new StoreProfileChanged(storeId));
        return load(kind, storeId, reviewId);
    }

    @Override
    @Transactional
    public void removeReply(UUID storeId, ReviewType type, UUID reviewId) {
        if (!desk.clearReply(kind(type), storeId, reviewId)) {
            throw notFound();
        }
        events.publishEvent(new StoreProfileChanged(storeId));
    }

    private PortalReview load(ReviewKind kind, UUID storeId, UUID reviewId) {
        ReviewDeskQueries.DeskRow row = desk.find(kind, storeId, reviewId).orElseThrow(
                StoreReviewDeskService::notFound);
        return toPortal(row, reviews.aspectsOf(kind, reviewId));
    }

    private Map<UUID, Map<String, Integer>> aspectsOf(List<ReviewDeskQueries.DeskRow> rows) {
        Map<UUID, Map<String, Integer>> result = new HashMap<>();
        for (ReviewKind kind : ReviewKind.values()) {
            List<UUID> ids = rows.stream().filter(row -> row.kind() == kind).map(ReviewDeskQueries.DeskRow::id)
                    .toList();
            result.putAll(reviews.aspectsOf(kind, ids));
        }
        return result;
    }

    static PortalReview toPortal(ReviewDeskQueries.DeskRow row, Map<String, Integer> aspects) {
        return new PortalReview(row.id(), ReviewType.valueOf(row.kind().name()), row.rating(), row.comment(), aspects,
                StoreReviewService.authorName(row.name(), row.surname()), row.createdAt(), row.updatedAt(),
                row.reply() == null ? null : new ReviewReplyView(row.reply(), row.repliedAt()), row.hidden(),
                row.productId(), row.productName());
    }

    static ReviewKind kind(ReviewType type) {
        return type == null ? null : ReviewKind.valueOf(type.name());
    }

    private static NotFoundException notFound() {
        return new NotFoundException("REVIEW_NOT_FOUND", "No se encontró la reseña.");
    }
}
