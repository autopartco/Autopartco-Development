package com.autopartco.backend.marketplace.reviews;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.reviews.AdminReview;
import com.autopartco.backend.core.reviews.ReviewModeration;
import com.autopartco.backend.core.reviews.ReviewReplyView;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.ListingsChanged;
import com.autopartco.backend.shared.events.StoreProfileChanged;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Moderación por la plataforma. Una reseña oculta deja de contar en promedios, insignias y listados públicos (el autor
 * sigue viendo la suya). Ocultar o mostrar publica {@link ListingsChanged} del producto o del almacén (reindexa
 * {@code product_rating} y {@code store_rating}) y vacía el perfil público cacheado.
 */
@Service
@AllArgsConstructor
public class ReviewModerationService implements ReviewModeration {
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final int REASON_MIN = 3;
    static final int REASON_MAX = 200;

    private final ReviewDeskQueries desk;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReview> list(Boolean hidden, ReviewType type, Integer page, Integer size) {
        int p = page == null ? 1 : Math.min(ReviewListQuery.MAX_PAGE, Math.max(1, page));
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        ReviewDeskQueries.Filter filter = new ReviewDeskQueries.Filter(null, StoreReviewDeskService.kind(type), null,
                null, null, hidden);
        List<AdminReview> items = desk.list(filter, s, (p - 1) * s).stream().map(ReviewModerationService::toAdmin)
                .toList();
        return PageResponse.of(items, p, s, desk.count(filter));
    }

    @Override
    @Transactional
    public AdminReview hide(ReviewType type, UUID reviewId, String reason) {
        String clean = reason == null ? "" : reason.strip();
        if (clean.length() < REASON_MIN || clean.length() > REASON_MAX) {
            throw new BadRequestException("REVIEW_REASON_INVALID",
                    "El motivo debe tener entre " + REASON_MIN + " y " + REASON_MAX + " caracteres.");
        }
        ReviewKind kind = StoreReviewDeskService.kind(type);
        ReviewDeskQueries.DeskRow row = require(kind, reviewId);
        if (row.hidden()) {
            throw new ConflictException("REVIEW_ALREADY_HIDDEN", "La reseña ya está oculta.");
        }
        desk.hide(kind, reviewId, clean);
        publish(row);
        return reload(kind, reviewId);
    }

    @Override
    @Transactional
    public AdminReview unhide(ReviewType type, UUID reviewId) {
        ReviewKind kind = StoreReviewDeskService.kind(type);
        ReviewDeskQueries.DeskRow row = require(kind, reviewId);
        if (!row.hidden()) {
            throw new ConflictException("REVIEW_NOT_HIDDEN", "La reseña no está oculta.");
        }
        desk.unhide(kind, reviewId);
        publish(row);
        return reload(kind, reviewId);
    }

    private void publish(ReviewDeskQueries.DeskRow row) {
        if (row.kind() == ReviewKind.PRODUCT) {
            events.publishEvent(ListingsChanged.ofProduct(row.productId()));
            events.publishEvent(new StoreProfileChanged(row.storeId()));
        } else {
            events.publishEvent(ListingsChanged.ofStore(row.storeId()));
        }
    }

    private ReviewDeskQueries.DeskRow require(ReviewKind kind, UUID reviewId) {
        return desk.find(kind, null, reviewId)
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "No se encontró la reseña."));
    }

    private AdminReview reload(ReviewKind kind, UUID reviewId) {
        return toAdmin(require(kind, reviewId));
    }

    static AdminReview toAdmin(ReviewDeskQueries.DeskRow row) {
        return new AdminReview(row.id(), ReviewType.valueOf(row.kind().name()), row.rating(), row.comment(),
                StoreReviewService.authorName(row.name(), row.surname()), row.createdAt(),
                row.reply() == null ? null : new ReviewReplyView(row.reply(), row.repliedAt()), row.storeId(),
                row.storeName(), row.productId(), row.productName(), row.hidden(), row.hiddenAt(),
                row.hiddenReason());
    }
}
