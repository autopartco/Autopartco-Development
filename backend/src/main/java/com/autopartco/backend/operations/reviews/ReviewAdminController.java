package com.autopartco.backend.operations.reviews;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.reviews.AdminReview;
import com.autopartco.backend.core.reviews.ReviewModeration;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.operations.auditlog.AdminAudit;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Moderación de reseñas (solo root): lista, oculta y muestra, con auditoría. */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/reviews")
@AllArgsConstructor
public class ReviewAdminController {
    static final String HIDDEN_ACTION = "REVIEW_HIDDEN";
    static final String UNHIDDEN_ACTION = "REVIEW_UNHIDDEN";

    private final ReviewModeration moderation;
    private final AdminAudit adminAudit;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public PageResponse<AdminReview> list(
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return moderation.list(hidden, ReviewType.parseOrNull(type), page, size);
    }

    @PostMapping("/{type}/{reviewId}/hide")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public AdminReview hide(
            @PathVariable String type,
            @PathVariable UUID reviewId,
            @Valid @RequestBody HideReviewRequest request,
            Authentication authentication
    ) {
        ReviewType reviewType = ReviewType.parse(type);
        AdminReview review = moderation.hide(reviewType, reviewId, request.reason());
        adminAudit.record(authentication, HIDDEN_ACTION, entityType(reviewType), reviewId,
                Map.of("reason", review.hiddenReason(), "storeId", review.storeId()));
        return review;
    }

    @PostMapping("/{type}/{reviewId}/unhide")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public AdminReview unhide(@PathVariable String type, @PathVariable UUID reviewId, Authentication authentication) {
        ReviewType reviewType = ReviewType.parse(type);
        AdminReview review = moderation.unhide(reviewType, reviewId);
        adminAudit.record(authentication, UNHIDDEN_ACTION, entityType(reviewType), reviewId,
                Map.of("storeId", review.storeId()));
        return review;
    }

    static String entityType(ReviewType type) {
        return type == ReviewType.STORE ? "STORE_REVIEW" : "PRODUCT_REVIEW";
    }
}
