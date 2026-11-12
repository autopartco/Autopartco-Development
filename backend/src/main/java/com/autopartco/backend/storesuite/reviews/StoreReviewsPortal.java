package com.autopartco.backend.storesuite.reviews;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.reviews.PortalReview;
import com.autopartco.backend.core.reviews.PortalReviewSummary;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.core.reviews.StoreReviewDesk;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Reseñas del almacén en el portal. Leer exige {@code STORE_READ}; responder o quitar la respuesta, {@code STORE_UPDATE}.
 * Una reseña de otro almacén responde 404 {@code REVIEW_NOT_FOUND}.
 */
@Service
@AllArgsConstructor
public class StoreReviewsPortal {
    private final StoreAccess storeAccess;
    private final StoreReviewDesk desk;

    @Transactional(readOnly = true)
    public PageResponse<PortalReview> list(UUID storeId, String email, String type, Boolean replied, Integer stars,
            Integer page, Integer size) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return desk.list(storeId, ReviewType.parseOrNull(type), replied, stars, page, size);
    }

    @Transactional(readOnly = true)
    public PortalReviewSummary summary(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return desk.summary(storeId);
    }

    @Transactional
    public PortalReview reply(UUID storeId, String email, String type, UUID reviewId, String text) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        return desk.reply(storeId, ReviewType.parse(type), reviewId, email, text);
    }

    @Transactional
    public void removeReply(UUID storeId, String email, String type, UUID reviewId) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        desk.removeReply(storeId, ReviewType.parse(type), reviewId);
    }
}
