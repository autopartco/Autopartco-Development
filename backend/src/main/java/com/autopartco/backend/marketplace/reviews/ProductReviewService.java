package com.autopartco.backend.marketplace.reviews;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.ListingsChanged;
import com.autopartco.backend.shared.web.PageResponse;

/**
 * Reseñas de producto. Califica quien lo recibió: pedido propio con el producto en una venta {@code DELIVERED}; una
 * reseña vigente por persona y producto (la más reciente compra la habilita). El equipo del almacén del producto no
 * puede calificarlo. El autor edita durante 30 días y borra cuando quiera. Las ocultas no cuentan en promedios,
 * insignias ni listados públicos. Cada escritura publica {@link ListingsChanged} del producto (reindexa
 * {@code product_rating} y {@code product_review_count}).
 */
@Service
public class ProductReviewService {
    private static final ReviewKind KIND = ReviewKind.PRODUCT;

    private final ProductReviewQueries queries;
    private final ReviewQueries reviews;
    private final AccountLookup accountLookup;
    private final StoreAccess storeAccess;
    private final ApplicationEventPublisher events;
    private final ReviewWriteLimiter limiter;
    private final Clock clock;

    @Autowired
    public ProductReviewService(ProductReviewQueries queries, ReviewQueries reviews, AccountLookup accountLookup,
            StoreAccess storeAccess, ApplicationEventPublisher events, ReviewWriteLimiter limiter) {
        this(queries, reviews, accountLookup, storeAccess, events, limiter, Clock.systemUTC());
    }

    ProductReviewService(ProductReviewQueries queries, ReviewQueries reviews, AccountLookup accountLookup,
            StoreAccess storeAccess, ApplicationEventPublisher events, ReviewWriteLimiter limiter, Clock clock) {
        this.queries = queries;
        this.reviews = reviews;
        this.accountLookup = accountLookup;
        this.storeAccess = storeAccess;
        this.events = events;
        this.limiter = limiter;
        this.clock = clock;
    }

    /** Listado público con filtros ({@link ReviewListQuery}); 404 {@code PRODUCT_NOT_FOUND} si no existe. */
    @Transactional(readOnly = true)
    public PageResponse<ProductReviewResponse> list(UUID productId, Integer page, Integer size, String sort,
            Integer stars, Boolean withComment) {
        ReviewListQuery query = ReviewListQuery.of(page, size, sort, stars, withComment);
        requireStore(productId);
        List<ReviewQueries.ReviewRow> rows = reviews.list(KIND, productId, query);
        Map<UUID, Map<String, Integer>> aspects = reviews.aspectsOf(KIND, ReviewQueries.ids(rows));
        List<ProductReviewResponse> items = rows.stream()
                .map(row -> ProductReviewResponse.of(row, aspects.getOrDefault(row.id(), Map.of())))
                .toList();
        return PageResponse.of(items, query.page(), query.size(), reviews.count(KIND, productId, query));
    }

    @Transactional(readOnly = true)
    public ReviewSummary summary(UUID productId) {
        requireStore(productId);
        return reviews.summary(KIND, productId);
    }

    @Transactional(readOnly = true)
    public ProductReviewEligibility eligibility(UUID productId, String email) {
        UUID storeId = requireStore(productId);
        AccountRef account = account(email);
        Optional<ProductReviewQueries.Existing> existing = queries.existing(account.personId(), productId);
        if (existing.isPresent()) {
            return new ProductReviewEligibility(List.of(), existing.get().id(),
                    ReviewRules.editableUntil(existing.get().createdAt()));
        }
        if (storeAccess.isMember(account.personId(), storeId)) {
            return new ProductReviewEligibility(List.of(), null, null);
        }
        return new ProductReviewEligibility(queries.eligibleOrders(productId, account.personId()), null, null);
    }

    /** Reseña propia vigente (aunque esté oculta) con sus categorías. 404 {@code REVIEW_NOT_FOUND} si no hay. */
    @Transactional(readOnly = true)
    public ProductReviewResponse mine(UUID productId, String email) {
        AccountRef account = account(email);
        UUID reviewId = queries.existing(account.personId(), productId)
                .map(ProductReviewQueries.Existing::id)
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "No se encontró la reseña."));
        return load(reviewId);
    }

    /**
     * 403 {@code REVIEW_NOT_ELIGIBLE} sin compra entregada del producto en ese pedido (o pedido ajeno, o equipo del
     * almacén); 409 {@code REVIEW_ALREADY_EXISTS} con una reseña vigente del producto.
     */
    @Transactional
    public ProductReviewResponse create(UUID productId, String email, ProductReviewRequest request) {
        UUID storeId = requireStore(productId);
        AccountRef account = account(email);
        limiter.acquire(account.accountId());
        String comment = ReviewRules.comment(request.comment());
        Map<String, Integer> aspects = ReviewRules.aspects(KIND, request.aspects());
        if (storeAccess.isMember(account.personId(), storeId)
                || !queries.deliveredInOrder(request.orderId(), account.personId(), productId)) {
            throw new ForbiddenException("REVIEW_NOT_ELIGIBLE",
                    "Podrás calificar este producto cuando lo recibas en un pedido tuyo.");
        }
        if (queries.existing(account.personId(), productId).isPresent()) {
            throw alreadyExists();
        }
        UUID reviewId;
        try {
            reviewId = queries.insert(productId, storeId, account.personId(), request.orderId(), request.rating(),
                    comment);
        } catch (DuplicateKeyException duplicate) {
            throw alreadyExists();
        }
        if (!aspects.isEmpty()) {
            reviews.replaceAspects(KIND, reviewId, aspects);
        }
        events.publishEvent(ListingsChanged.ofProduct(productId));
        return load(reviewId);
    }

    /** Solo el autor y dentro de los 30 días (409 {@code REVIEW_EDIT_WINDOW_CLOSED}); ajena o inexistente: 404. */
    @Transactional
    public ProductReviewResponse update(UUID productId, UUID reviewId, String email, ReviewUpdateRequest request) {
        AccountRef account = account(email);
        limiter.acquire(account.accountId());
        ReviewQueries.OwnedReview review = ReviewRules.requireOwn(reviews.owned(KIND, reviewId), productId,
                account.personId());
        ReviewRules.requireEditable(review, OffsetDateTime.now(clock));
        String comment = ReviewRules.comment(request.comment());
        Map<String, Integer> aspects = ReviewRules.aspects(KIND, request.aspects());
        reviews.update(KIND, reviewId, request.rating(), comment);
        reviews.replaceAspects(KIND, reviewId, aspects);
        events.publishEvent(ListingsChanged.ofProduct(productId));
        return load(reviewId);
    }

    /** Borrado lógico por el autor (sin ventana). Ajena o inexistente: 404 {@code REVIEW_NOT_FOUND}. */
    @Transactional
    public void delete(UUID productId, UUID reviewId, String email) {
        AccountRef account = account(email);
        limiter.acquire(account.accountId());
        ReviewRules.requireOwn(reviews.owned(KIND, reviewId), productId, account.personId());
        reviews.softDelete(KIND, reviewId);
        events.publishEvent(ListingsChanged.ofProduct(productId));
    }

    private ProductReviewResponse load(UUID reviewId) {
        ReviewQueries.ReviewRow row = reviews.row(KIND, reviewId)
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "No se encontró la reseña."));
        return ProductReviewResponse.of(row, reviews.aspectsOf(KIND, reviewId));
    }

    private UUID requireStore(UUID productId) {
        return queries.productStore(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
    }

    private static ConflictException alreadyExists() {
        return new ConflictException("REVIEW_ALREADY_EXISTS",
                "Ya calificaste este producto. Puedes editar tu reseña.");
    }

    private AccountRef account(String email) {
        return accountLookup.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }
}
