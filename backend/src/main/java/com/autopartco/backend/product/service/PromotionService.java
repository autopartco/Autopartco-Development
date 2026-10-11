package com.autopartco.backend.product.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.exception.BadRequestException;
import com.autopartco.backend.exception.NotFoundException;
import com.autopartco.backend.product.dto.PromotionResponse;
import com.autopartco.backend.product.dto.PromotionUpsertRequest;
import com.autopartco.backend.product.entity.Promotion;
import com.autopartco.backend.product.entity.PromotionScope;
import com.autopartco.backend.product.entity.PromotionTargetType;
import com.autopartco.backend.product.repository.CategoryRepository;
import com.autopartco.backend.product.repository.ManufacturerRepository;
import com.autopartco.backend.product.repository.ProductRepository;
import com.autopartco.backend.product.repository.PromotionRepository;
import com.autopartco.backend.store.entity.Store;
import com.autopartco.backend.store.repository.StoreRepository;
import com.autopartco.backend.store.service.StoreAccessService;
import com.autopartco.backend.store.service.StorePermissionCodes;
import com.autopartco.backend.seller.service.SellerPlanEntitlementService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class PromotionService {
    private final PromotionRepository promotionRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final SellerPlanEntitlementService entitlementService;

    @Transactional(readOnly = true)
    public List<PromotionResponse> listForStore(UUID storeId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PROMOTION_READ);
        return promotionRepository.findByScopeAndStore_IdOrderByCreatedAtDesc(
                        PromotionScope.STORE, storeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PromotionResponse createForStore(
            UUID storeId,
            String email,
            PromotionUpsertRequest request
    ) {
        entitlementService.requireCapability(storeId, "PROMOTIONS");
        Store store = requirePermission(storeId, email, StorePermissionCodes.PROMOTION_CREATE);
        validateTarget(request, PromotionScope.STORE, store);
        return toResponse(promotionRepository.save(toPromotion(
                request,
                PromotionScope.STORE,
                store,
                storeId
        )));
    }

    @Transactional
    public PromotionResponse updateForStore(
            UUID storeId,
            UUID promotionId,
            String email,
            PromotionUpsertRequest request
    ) {
        entitlementService.requireCapability(storeId, "PROMOTIONS");
        Store store = requirePermission(storeId, email, StorePermissionCodes.PROMOTION_UPDATE);
        Promotion promotion = promotionRepository.findByIdAndScopeAndStore_Id(
                        promotionId, PromotionScope.STORE, storeId)
                .orElseThrow(() -> notFound());
        validateTarget(request, PromotionScope.STORE, store);
        applyRequest(promotion, request, storeId);
        return toResponse(promotion);
    }

    @Transactional
    public void deleteForStore(UUID storeId, UUID promotionId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PROMOTION_DELETE);
        Promotion promotion = promotionRepository.findByIdAndScopeAndStore_Id(
                        promotionId, PromotionScope.STORE, storeId)
                .orElseThrow(() -> notFound());
        promotionRepository.delete(promotion);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listForPlatform() {
        return promotionRepository.findByScopeAndStoreIsNullOrderByCreatedAtDesc(PromotionScope.PLATFORM)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PromotionResponse createForPlatform(PromotionUpsertRequest request) {
        validateTarget(request, PromotionScope.PLATFORM, null);
        return toResponse(promotionRepository.save(toPromotion(
                request,
                PromotionScope.PLATFORM,
                null,
                request.targetId()
        )));
    }

    @Transactional
    public PromotionResponse updateForPlatform(UUID promotionId, PromotionUpsertRequest request) {
        Promotion promotion = promotionRepository.findByIdAndScopeAndStoreIsNull(
                        promotionId, PromotionScope.PLATFORM)
                .orElseThrow(() -> notFound());
        validateTarget(request, PromotionScope.PLATFORM, null);
        applyRequest(
                promotion,
                request,
                request.targetType() == PromotionTargetType.STORE ? request.targetId() : null
        );
        return toResponse(promotion);
    }

    @Transactional
    public void deleteForPlatform(UUID promotionId) {
        Promotion promotion = promotionRepository.findByIdAndScopeAndStoreIsNull(
                        promotionId, PromotionScope.PLATFORM)
                .orElseThrow(() -> notFound());
        promotionRepository.delete(promotion);
    }

    private Promotion toPromotion(
            PromotionUpsertRequest request,
            PromotionScope scope,
            Store store,
            UUID storeTargetId
    ) {
        Promotion promotion = Promotion.builder()
                .scope(scope)
                .store(store)
                .build();
        applyRequest(promotion, request, storeTargetId);
        return promotion;
    }

    private void applyRequest(Promotion promotion, PromotionUpsertRequest request, UUID storeTargetId) {
        promotion.setName(request.name().trim());
        promotion.setTargetType(request.targetType());
        promotion.setTargetId(request.targetType() == PromotionTargetType.STORE
                ? storeTargetId
                : request.targetId());
        promotion.setAttributeKey(trimToNull(request.attributeKey()));
        promotion.setAttributeValue(trimToNull(request.attributeValue()));
        promotion.setAttributeOperator(request.attributeOperator());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setEnabled(request.enabled() == null || request.enabled());
    }

    private void validateTarget(PromotionUpsertRequest request, PromotionScope scope, Store store) {
        PromotionTargetType targetType = request.targetType();
        UUID targetId = request.targetId();
        boolean hasAttribute = request.attributeKey() != null
                || request.attributeValue() != null
                || request.attributeOperator() != null;

        if (targetType == PromotionTargetType.ALL) {
            if (scope != PromotionScope.PLATFORM || targetId != null || hasAttribute) {
                throw invalidTarget("Solo la plataforma puede crear una promoción global.");
            }
            return;
        }

        if (targetType == PromotionTargetType.ATTRIBUTE) {
            if (targetId != null || request.attributeKey() == null || request.attributeKey().isBlank()
                    || request.attributeValue() == null || request.attributeValue().isBlank()) {
                throw invalidTarget("Las promociones por atributo requieren una clave y un valor, sin ID.");
            }
            return;
        }

        if (targetType == PromotionTargetType.STORE && scope == PromotionScope.STORE) {
            if (targetId != null && !targetId.equals(store.getId())) {
                throw invalidTarget("La promoción solo puede aplicarse a este almacén.");
            }
            if (hasAttribute) {
                throw invalidTarget("La promoción por almacén no admite atributos.");
            }
            return;
        }

        if (targetId == null || hasAttribute) {
            throw invalidTarget("Este tipo de promoción requiere un ID de destino válido.");
        }

        switch (targetType) {
            case PRODUCT -> {
                var product = productRepository.findById(targetId)
                        .orElseThrow(() -> invalidTarget("No se encontró el producto de destino."));
                if (scope == PromotionScope.STORE
                        && !store.getId().equals(product.getStore().getId())) {
                    throw invalidTarget("El producto de destino no pertenece a este almacén.");
                }
            }
            case CATEGORY -> {
                if (!categoryRepository.existsById(targetId)) {
                    throw invalidTarget("No se encontró la categoría de destino.");
                }
            }
            case MANUFACTURER -> {
                if (!manufacturerRepository.existsById(targetId)) {
                    throw invalidTarget("No se encontró la marca de destino.");
                }
            }
            case STORE -> {
                if (scope != PromotionScope.PLATFORM || !storeRepository.existsById(targetId)) {
                    throw invalidTarget("No se encontró el almacén de destino.");
                }
            }
            default -> throw invalidTarget("El tipo de destino no es válido.");
        }
    }

    private Store requirePermission(UUID storeId, String email, String permissionCode) {
        storeAccessService.requirePermission(email, storeId, permissionCode);
        return storeRepository.findById(storeId).orElseThrow(PromotionService::notFound);
    }

    private PromotionResponse toResponse(Promotion promotion) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean active = promotion.isEnabled()
                && !promotion.getStartsAt().isAfter(now)
                && promotion.getEndsAt().isAfter(now);
        return new PromotionResponse(
                promotion.getId(),
                promotion.getScope(),
                promotion.getStore() == null ? null : promotion.getStore().getId(),
                promotion.getName(),
                promotion.getTargetType(),
                promotion.getTargetId(),
                promotion.getAttributeKey(),
                promotion.getAttributeValue(),
                promotion.getAttributeOperator(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.isEnabled(),
                active
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BadRequestException invalidTarget(String message) {
        return new BadRequestException("INVALID_PROMOTION_TARGET", message);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("PROMOTION_NOT_FOUND", "No se encontró la promoción.");
    }
}
