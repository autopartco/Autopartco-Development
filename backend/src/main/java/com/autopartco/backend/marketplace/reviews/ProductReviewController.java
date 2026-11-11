package com.autopartco.backend.marketplace.reviews;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Reseñas de producto: listado y resumen públicos; elegibilidad, creación, edición y borrado con sesión de comprador.
 * Errores: 404 {@code PRODUCT_NOT_FOUND}/{@code REVIEW_NOT_FOUND}; 403 {@code REVIEW_NOT_ELIGIBLE}; 409
 * {@code REVIEW_ALREADY_EXISTS}/{@code REVIEW_EDIT_WINDOW_CLOSED}; 400 {@code REVIEW_ASPECT_INVALID}/
 * {@code REVIEW_LINKS_NOT_ALLOWED}/{@code REVIEW_SORT_INVALID}/{@code REVIEW_STARS_INVALID}; 429
 * {@code REVIEW_RATE_LIMITED}.
 */
@RestController
@RequestMapping(ApiPaths.MARKETPLACE + "/products/{productId}/reviews")
@AllArgsConstructor
public class ProductReviewController {
    private final ProductReviewService productReviewService;

    @GetMapping
    public PageResponse<ProductReviewResponse> list(
            @PathVariable UUID productId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer stars,
            @RequestParam(required = false) Boolean withComment
    ) {
        return productReviewService.list(productId, page, size, sort, stars, withComment);
    }

    @GetMapping("/summary")
    public ReviewSummary summary(@PathVariable UUID productId) {
        return productReviewService.summary(productId);
    }

    @GetMapping("/eligibility")
    public ProductReviewEligibility eligibility(@PathVariable UUID productId, Authentication authentication) {
        return productReviewService.eligibility(productId, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReviewResponse create(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductReviewRequest request,
            Authentication authentication
    ) {
        return productReviewService.create(productId, authentication.getName(), request);
    }

    @PutMapping("/{reviewId}")
    public ProductReviewResponse update(
            @PathVariable UUID productId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequest request,
            Authentication authentication
    ) {
        return productReviewService.update(productId, reviewId, authentication.getName(), request);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID productId, @PathVariable UUID reviewId, Authentication authentication) {
        productReviewService.delete(productId, reviewId, authentication.getName());
    }
}
