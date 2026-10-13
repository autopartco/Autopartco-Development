package com.autopartco.backend.storesuite.promotions;

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

import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionUpsertRequest;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Promociones propias del almacén (permiso por código y capacidad {@code PROMOTIONS} del plan). */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/promotions")
@AllArgsConstructor
public class StorePromotionController {
    private final ListStorePromotions listStorePromotions;
    private final CreateStorePromotion createStorePromotion;
    private final UpdateStorePromotion updateStorePromotion;
    private final DeleteStorePromotion deleteStorePromotion;

    @GetMapping
    public List<PromotionResponse> list(@PathVariable UUID storeId, Authentication authentication) {
        return listStorePromotions.execute(storeId, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<PromotionResponse> create(
            @PathVariable UUID storeId,
            @Valid @RequestBody PromotionUpsertRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createStorePromotion.execute(storeId, authentication.getName(), request));
    }

    @PutMapping("/{promotionId}")
    public PromotionResponse update(
            @PathVariable UUID storeId,
            @PathVariable UUID promotionId,
            @Valid @RequestBody PromotionUpsertRequest request,
            Authentication authentication
    ) {
        return updateStorePromotion.execute(storeId, promotionId, authentication.getName(), request);
    }

    @DeleteMapping("/{promotionId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID storeId,
            @PathVariable UUID promotionId,
            Authentication authentication
    ) {
        deleteStorePromotion.execute(storeId, promotionId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
