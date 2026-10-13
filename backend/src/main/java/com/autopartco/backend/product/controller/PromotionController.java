package com.autopartco.backend.product.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionUpsertRequest;
import com.autopartco.backend.product.service.PromotionUseCases;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE)
@AllArgsConstructor
public class PromotionController {
    private final PromotionUseCases promotionService;

    @GetMapping("/stores/{storeId}/promotions")
    public List<PromotionResponse> listForStore(
            @PathVariable UUID storeId,
            Authentication authentication
    ) {
        return promotionService.listForStore(storeId, authentication.getName());
    }

    @PostMapping("/stores/{storeId}/promotions")
    public ResponseEntity<PromotionResponse> createForStore(
            @PathVariable UUID storeId,
            @Valid @RequestBody PromotionUpsertRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(promotionService.createForStore(storeId, authentication.getName(), request));
    }

    @PutMapping("/stores/{storeId}/promotions/{promotionId}")
    public PromotionResponse updateForStore(
            @PathVariable UUID storeId,
            @PathVariable UUID promotionId,
            @Valid @RequestBody PromotionUpsertRequest request,
            Authentication authentication
    ) {
        return promotionService.updateForStore(
                storeId, promotionId, authentication.getName(), request);
    }

    @DeleteMapping("/stores/{storeId}/promotions/{promotionId}")
    public ResponseEntity<Void> deleteForStore(
            @PathVariable UUID storeId,
            @PathVariable UUID promotionId,
            Authentication authentication
    ) {
        promotionService.deleteForStore(storeId, promotionId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/platform/promotions")
    public List<PromotionResponse> listForPlatform() {
        return promotionService.listForPlatform();
    }

    @PostMapping("/platform/promotions")
    public ResponseEntity<PromotionResponse> createForPlatform(
            @Valid @RequestBody PromotionUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(promotionService.createForPlatform(request));
    }

    @PutMapping("/platform/promotions/{promotionId}")
    public PromotionResponse updateForPlatform(
            @PathVariable UUID promotionId,
            @Valid @RequestBody PromotionUpsertRequest request
    ) {
        return promotionService.updateForPlatform(promotionId, request);
    }

    @DeleteMapping("/platform/promotions/{promotionId}")
    public ResponseEntity<Void> deleteForPlatform(@PathVariable UUID promotionId) {
        promotionService.deleteForPlatform(promotionId);
        return ResponseEntity.noContent().build();
    }
}
