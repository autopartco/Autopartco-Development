package com.autopartco.backend.marketplace.reviews;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

import lombok.AllArgsConstructor;

/**
 * Reseñas de almacenes publicados. Solo puede calificar quien compró: pedido propio con una venta entregada
 * ({@code DELIVERED}) en una sucursal del almacén; una reseña por pedido y almacén.
 */
@Service
@AllArgsConstructor
public class StoreReviewService {
    static final int DEFAULT_SIZE = 10;
    static final int MAX_SIZE = 50;
    /** Más allá no hay reseñas reales; evita desbordar el desplazamiento con páginas absurdas. */
    static final int MAX_PAGE = 10_000;

    private final StoreReviewQueries queries;
    private final PublishedStores publishedStores;
    private final AccountLookup accountLookup;
    private final StoreAccess storeAccess;
    private final ApplicationEventPublisher events;

    /** Páginas desde 1, como {@link PageResponse}. */
    @Transactional(readOnly = true)
    public PageResponse<StoreReviewResponse> list(UUID storeId, Integer page, Integer size) {
        publishedStores.requirePublished(storeId);
        int p = page == null ? 1 : Math.min(MAX_PAGE, Math.max(1, page));
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        StoreRating rating = queries.rating(storeId);
        return PageResponse.of(queries.list(storeId, s, (p - 1) * s), p, s, rating.count());
    }

    /** Sin comprobar publicación: lo usa el perfil público, que ya la comprobó. */
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
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        StoreReviewQueries.Inserted inserted;
        try {
            inserted = queries.insert(storeId, account.personId(), request.orderId(), request.rating(), comment);
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("REVIEW_ALREADY_EXISTS", "Ya calificaste este almacén por este pedido.");
        }
        // La calificación del almacén forma parte del índice de búsqueda (filtro y orden por calificación).
        events.publishEvent(ListingsChanged.ofStore(storeId));
        String[] names = splitName(account);
        return new StoreReviewResponse(inserted.id(), request.rating(), comment, authorName(names[0], names[1]),
                inserted.createdAt());
    }

    /** «Ana María» + «Pérez Gómez» → «Ana P.». Sin apellido, solo el primer nombre. */
    static String authorName(String name, String surname) {
        String first = name == null || name.isBlank() ? "Comprador" : name.trim().split("\\s+")[0];
        if (surname == null || surname.isBlank()) {
            return first;
        }
        return first + " " + surname.trim().substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }

    private static String[] splitName(AccountRef account) {
        String given = account.givenName();
        String full = account.fullName() == null ? "" : account.fullName().trim();
        if (given != null && !given.isBlank() && full.startsWith(given.trim())) {
            return new String[] {given, full.substring(given.trim().length()).trim()};
        }
        String[] parts = full.split("\\s+", 2);
        return new String[] {parts[0], parts.length > 1 ? parts[1] : null};
    }

    private AccountRef account(String email) {
        return accountLookup.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }
}
