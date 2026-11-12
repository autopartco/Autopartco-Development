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
 * Reseñas de almacén: listado y resumen públicos; elegibilidad, creación, edición y borrado con sesión de comprador.
 * Errores: 404 {@code STORE_NOT_FOUND}/{@code ORDER_NOT_FOUND}/{@code REVIEW_NOT_FOUND}; 409
 * {@code REVIEW_NOT_ALLOWED}/{@code REVIEW_ALREADY_EXISTS}/{@code REVIEW_EDIT_WINDOW_CLOSED}; 400
 * {@code REVIEW_ASPECT_INVALID}/{@code REVIEW_LINKS_NOT_ALLOWED}/{@code REVIEW_SORT_INVALID}/
 * {@code REVIEW_STARS_INVALID}; 429 {@code REVIEW_RATE_LIMITED}.
 */
@RestController
@RequestMapping(ApiPaths.MARKETPLACE + "/stores/{storeId}/reviews")
@AllArgsConstructor
public class StoreReviewController {
    private final StoreReviewService storeReviewService;

    @GetMapping
    public PageResponse<StoreReviewResponse> list(
            @PathVariable UUID storeId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer stars,
            @RequestParam(required = false) Boolean withComment
    ) {
        return storeReviewService.list(storeId, page, size, sort, stars, withComment);
    }

    @GetMapping("/summary")
    public ReviewSummary summary(@PathVariable UUID storeId) {
        return storeReviewService.summary(storeId);
    }

    @GetMapping("/eligibility")
    public ReviewEligibility eligibility(@PathVariable UUID storeId, Authentication authentication) {
        return storeReviewService.eligibility(storeId, authentication.getName());
    }

    /** La reseña propia (del pedido o la más reciente) con sus categorías; 404 {@code REVIEW_NOT_FOUND}. */
    @GetMapping("/mine")
    public StoreReviewResponse mine(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID orderId,
            Authentication authentication
    ) {
        return storeReviewService.mine(storeId, authentication.getName(), orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreReviewResponse create(
            @PathVariable UUID storeId,
            @Valid @RequestBody StoreReviewRequest request,
            Authentication authentication
    ) {
        return storeReviewService.create(storeId, authentication.getName(), request);
    }

    @PutMapping("/{reviewId}")
    public StoreReviewResponse update(
            @PathVariable UUID storeId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequest request,
            Authentication authentication
    ) {
        return storeReviewService.update(storeId, reviewId, authentication.getName(), request);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID storeId, @PathVariable UUID reviewId, Authentication authentication) {
        storeReviewService.delete(storeId, reviewId, authentication.getName());
    }
}
