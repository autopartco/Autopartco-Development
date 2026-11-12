package com.autopartco.backend.marketplace.reviews;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.storage.ImageUrls;
import com.autopartco.backend.shared.storage.ImageVariant;

import lombok.AllArgsConstructor;

/**
 * Lo pendiente por calificar del comprador autenticado: pedidos propios entregados en los últimos 90 días, uno por
 * almacén, que tienen el almacén o algún producto sin reseña. Se omiten los almacenes de los que la persona es o fue
 * del equipo (no puede calificarlos). Solo lee datos de la propia persona.
 */
@Service
@AllArgsConstructor
public class PendingReviews {
    private final PendingReviewQueries queries;
    private final AccountLookup accountLookup;
    private final StoreAccess storeAccess;
    private final ImageUrls imageUrls;

    @Transactional(readOnly = true)
    public List<PendingReview> pending(String email) {
        UUID personId = accountLookup.requireByEmail(email).personId();
        List<PendingReviewQueries.OrderStore> pairs = queries.orderStores(personId);
        if (pairs.isEmpty()) {
            return List.of();
        }
        Map<UUID, Boolean> member = new HashMap<>();
        List<PendingReviewQueries.OrderStore> allowed = pairs.stream()
                .filter(pair -> !member.computeIfAbsent(pair.storeId(),
                        storeId -> storeAccess.isMember(personId, storeId)))
                .toList();
        Set<UUID> orderIds = new LinkedHashSet<>();
        allowed.forEach(pair -> orderIds.add(pair.orderId()));
        Map<String, List<PendingReview.Product>> productsByPair = new HashMap<>();
        for (PendingReviewQueries.ProductLine line : queries.products(personId, orderIds)) {
            productsByPair.computeIfAbsent(key(line.orderId(), line.storeId()), ignored -> new ArrayList<>())
                    .add(new PendingReview.Product(line.productId(), line.name(), image(line.storagePath()),
                            line.reviewed(), line.reviewId()));
        }
        List<PendingReview> result = new ArrayList<>();
        for (PendingReviewQueries.OrderStore pair : allowed) {
            List<PendingReview.Product> products = productsByPair.getOrDefault(key(pair.orderId(), pair.storeId()),
                    List.of()).stream()
                    .sorted(Comparator.comparing(PendingReview.Product::name,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            boolean somethingLeft = !pair.storeReviewed() || products.stream().anyMatch(p -> !p.reviewed());
            if (somethingLeft) {
                result.add(new PendingReview(pair.orderId(), pair.orderNumber(), pair.deliveredAt(),
                        new PendingReview.Store(pair.storeId(), pair.storeName(), pair.storeReviewed(),
                                pair.storeReviewId()), products));
            }
        }
        return result;
    }

    private String image(String storagePath) {
        return storagePath == null ? null : imageUrls.variant(storagePath, ImageVariant.THUMB);
    }

    private static String key(UUID orderId, UUID storeId) {
        return orderId + ":" + storeId;
    }
}
