package com.autopartco.backend.marketplace.reviews;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Reseñas de almacén: listado público; elegibilidad y creación con sesión de comprador. */
@RestController
@RequestMapping(ApiPaths.MARKETPLACE + "/stores/{storeId}/reviews")
@AllArgsConstructor
public class StoreReviewController {
    private final StoreReviewService storeReviewService;

    @GetMapping
    public PageResponse<StoreReviewResponse> list(
            @PathVariable UUID storeId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return storeReviewService.list(storeId, page, size);
    }

    @GetMapping("/eligibility")
    public ReviewEligibility eligibility(@PathVariable UUID storeId, Authentication authentication) {
        return storeReviewService.eligibility(storeId, authentication.getName());
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
}
