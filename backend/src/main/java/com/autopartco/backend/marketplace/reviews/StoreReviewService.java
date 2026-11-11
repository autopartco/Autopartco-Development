package com.autopartco.backend.marketplace.reviews;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.ListingsChanged;
import com.autopartco.backend.shared.web.PageResponse;

/**
 * Reseñas de almacenes publicados. Solo puede calificar quien compró: pedido propio con una venta entregada
 * ({@code DELIVERED}) en una sucursal del almacén; una reseña por pedido y almacén. El autor edita durante 30 días y
 * borra cuando quiera. Las ocultas no cuentan en promedios, insignias ni listados públicos. Cada escritura publica
 * {@link ListingsChanged} del almacén (reindexa {@code store_rating} y vacía {@code store:profile} tras el commit).
 */
@Service
public class StoreReviewService {
    private static final ReviewKind KIND = ReviewKind.STORE;

    private final StoreReviewQueries queries;
    private final ReviewQueries reviews;
    private final PublishedStores publishedStores;
    private final AccountLookup accountLookup;
    private final StoreAccess storeAccess;
    private final ApplicationEventPublisher events;
    private final ReviewWriteLimiter limiter;
    private final Clock clock;

    @Autowired
    public StoreReviewService(StoreReviewQueries queries, ReviewQueries reviews, PublishedStores publishedStores,
            AccountLookup accountLookup, StoreAccess storeAccess, ApplicationEventPublisher events,
            ReviewWriteLimiter limiter) {
        this(queries, reviews, publishedStores, accountLookup, storeAccess, events, limiter, Clock.systemUTC());
    }

    StoreReviewService(StoreReviewQueries queries, ReviewQueries reviews, PublishedStores publishedStores,
            AccountLookup accountLookup, StoreAccess storeAccess, ApplicationEventPublisher events,
            ReviewWriteLimiter limiter, Clock clock) {
        this.queries = queries;
        this.reviews = reviews;
        this.publishedStores = publishedStores;
        this.accountLookup = accountLookup;
        this.storeAccess = storeAccess;
        this.events = events;
        this.limiter = limiter;
        this.clock = clock;
    }

    /** Listado público con filtros ({@link ReviewListQuery}); páginas desde 1. */
    @Transactional(readOnly = true)
    public PageResponse<StoreReviewResponse> list(UUID storeId, Integer page, Integer size, String sort,
            Integer stars, Boolean withComment) {
        publishedStores.requirePublished(storeId);
        ReviewListQuery query = ReviewListQuery.of(page, size, sort, stars, withComment);
        List<ReviewQueries.ReviewRow> rows = reviews.list(KIND, storeId, query);
        Map<UUID, Map<String, Integer>> aspects = reviews.aspectsOf(KIND, ReviewQueries.ids(rows));
        List<StoreReviewResponse> items = rows.stream()
                .map(row -> StoreReviewResponse.of(row, aspects.getOrDefault(row.id(), Map.of())))
                .toList();
        return PageResponse.of(items, query.page(), query.size(), reviews.count(KIND, storeId, query));
    }

    /** Promedio, distribución, categorías e insignias de las reseñas visibles del almacén publicado. */
    @Transactional(readOnly = true)
    public ReviewSummary summary(UUID storeId) {
        publishedStores.requirePublished(storeId);
        return reviews.summary(KIND, storeId);
    }

    /** Sin comprobar publicación: lo usa el perfil público, que ya la comprobó. Sin reseñas ocultas. */
    @Transactional(readOnly = true)
    public StoreRating rating(UUID storeId) {
        return queries.rating(storeId);
    }

    @Transactional(readOnly = true)
    public ReviewEligibility eligibility(UUID storeId, String email) {
        publishedStores.requirePublished(storeId);
        AccountRef account = account(email);
        if (storeAccess.isMember(account.personId(), storeId)) {
            return new ReviewEligibility(List.of());
        }
        return new ReviewEligibility(queries.eligibleOrders(storeId, account.personId()));
    }

    @Transactional
    public StoreReviewResponse create(UUID storeId, String email, StoreReviewRequest request) {
        publishedStores.requirePublished(storeId);
        AccountRef account = account(email);
        limiter.acquire(account.accountId());
        String comment = ReviewRules.comment(request.comment());
        Map<String, Integer> aspects = ReviewRules.aspects(KIND, request.aspects());
        UUID owner = queries.orderOwner(request.orderId()).orElse(null);
        if (owner == null || !owner.equals(account.personId())) {
            throw new NotFoundException("ORDER_NOT_FOUND", "No se encontró el pedido.");
        }
        // Quien es o fue del equipo del almacén no puede calificarlo, aunque haya comprado en él.
        if (storeAccess.isMember(account.personId(), storeId)) {
            throw new ConflictException("REVIEW_NOT_ALLOWED", "El equipo de un almacén no puede calificar su propia tienda.");
        }
        if (!queries.hasDeliveredSale(request.orderId(), storeId)) {
            throw new ConflictException("REVIEW_NOT_ALLOWED",
                    "Podrás calificar este almacén cuando recibas tu pedido.");
        }
        UUID reviewId;
        try {
            reviewId = queries.insert(storeId, account.personId(), request.orderId(), request.rating(), comment);
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("REVIEW_ALREADY_EXISTS", "Ya calificaste este almacén por este pedido.");
        }
        if (!aspects.isEmpty()) {
            reviews.replaceAspects(KIND, reviewId, aspects);
        }
        // La calificación del almacén forma parte del índice de búsqueda y del perfil público cacheado.
        events.publishEvent(ListingsChanged.ofStore(storeId));
        return load(reviewId);
    }

    /** Solo el autor y dentro de los 30 días (409 {@code REVIEW_EDIT_WINDOW_CLOSED}); ajena o inexistente: 404. */
    @Transactional
    public StoreReviewResponse update(UUID storeId, UUID reviewId, String email, ReviewUpdateRequest request) {
        AccountRef account = account(email);
        limiter.acquire(account.accountId());
        ReviewQueries.OwnedReview review = ReviewRules.requireOwn(reviews.owned(KIND, reviewId), storeId,
                account.personId());
        ReviewRules.requireEditable(review, OffsetDateTime.now(clock));
        String comment = ReviewRules.comment(request.comment());
        Map<String, Integer> aspects = ReviewRules.aspects(KIND, request.aspects());
        reviews.update(KIND, reviewId, request.rating(), comment);
        reviews.replaceAspects(KIND, reviewId, aspects);
        events.publishEvent(ListingsChanged.ofStore(storeId));
        return load(reviewId);
    }

    /** Borrado lógico por el autor (sin ventana). Ajena o inexistente: 404 {@code REVIEW_NOT_FOUND}. */
    @Transactional
    public void delete(UUID storeId, UUID reviewId, String email) {
        AccountRef account = account(email);
        limiter.acquire(account.accountId());
        ReviewRules.requireOwn(reviews.owned(KIND, reviewId), storeId, account.personId());
        reviews.softDelete(KIND, reviewId);
        events.publishEvent(ListingsChanged.ofStore(storeId));
    }

    private StoreReviewResponse load(UUID reviewId) {
        ReviewQueries.ReviewRow row = reviews.row(KIND, reviewId)
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "No se encontró la reseña."));
        return StoreReviewResponse.of(row, reviews.aspectsOf(KIND, reviewId));
    }

    /** «Ana María» + «Pérez Gómez» → «Ana P.». Sin apellido, solo el primer nombre. */
    static String authorName(String name, String surname) {
        String first = name == null || name.isBlank() ? "Comprador" : name.trim().split("\\s+")[0];
        if (surname == null || surname.isBlank()) {
            return first;
        }
        return first + " " + surname.trim().substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }

    private AccountRef account(String email) {
        return accountLookup.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }
}
