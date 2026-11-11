package com.autopartco.backend.storesuite.reviews;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.reviews.PortalReview;
import com.autopartco.backend.core.reviews.PortalReviewSummary;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Reseñas del almacén y de sus productos en el portal: consulta, resumen y respuesta. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/reviews")
@AllArgsConstructor
public class StoreReviewsController {
    private final StoreReviewsPortal portal;

    @GetMapping
    public PageResponse<PortalReview> list(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean replied,
            @RequestParam(required = false) Integer stars,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return portal.list(storeId, authentication.getName(), type, replied, stars, page, size);
    }

    @GetMapping("/summary")
    public PortalReviewSummary summary(@PathVariable UUID storeId, Authentication authentication) {
        return portal.summary(storeId, authentication.getName());
    }

    @PutMapping("/{type}/{reviewId}/reply")
    public PortalReview reply(
            @PathVariable UUID storeId,
            @PathVariable String type,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewReplyRequest request,
            Authentication authentication
    ) {
        return portal.reply(storeId, authentication.getName(), type, reviewId, request.text());
    }

    @DeleteMapping("/{type}/{reviewId}/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReply(
            @PathVariable UUID storeId,
            @PathVariable String type,
            @PathVariable UUID reviewId,
            Authentication authentication
    ) {
        portal.removeReply(storeId, authentication.getName(), type, reviewId);
    }
}
