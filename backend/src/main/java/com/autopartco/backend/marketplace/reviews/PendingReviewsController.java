package com.autopartco.backend.marketplace.reviews;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** {@code GET /api/v1/account/reviews/pending}: lo que el comprador autenticado puede calificar. */
@RestController
@AllArgsConstructor
public class PendingReviewsController {
    private final PendingReviews pendingReviews;

    @GetMapping(ApiPaths.BASE + "/account/reviews/pending")
    public List<PendingReview> pending(Authentication authentication) {
        return pendingReviews.pending(authentication.getName());
    }
}
