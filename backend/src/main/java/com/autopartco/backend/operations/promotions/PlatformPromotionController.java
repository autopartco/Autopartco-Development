package com.autopartco.backend.operations.promotions;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionService;
import com.autopartco.backend.core.pricing.PromotionUpsertRequest;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Promociones de plataforma, aplicables a todos los almacenes (solo root). */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/promotions")
@AllArgsConstructor
public class PlatformPromotionController {
    private final PromotionService promotionService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<PromotionResponse> list() {
        return promotionService.listForPlatform();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<PromotionResponse> create(@Valid @RequestBody PromotionUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.createForPlatform(request));
    }

    @PutMapping("/{promotionId}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public PromotionResponse update(
            @PathVariable UUID promotionId,
            @Valid @RequestBody PromotionUpsertRequest request
    ) {
        return promotionService.updateForPlatform(promotionId, request);
    }

    @DeleteMapping("/{promotionId}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<Void> delete(@PathVariable UUID promotionId) {
        promotionService.deleteForPlatform(promotionId);
        return ResponseEntity.noContent().build();
    }
}
