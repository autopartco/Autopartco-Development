package com.autopartco.backend.core.pricing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.pricing.internal.Promotion;
import com.autopartco.backend.core.pricing.internal.PromotionRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * CRUD de promociones de almacén (acotado por {@code storeId}) y de plataforma. No resuelve permisos ni derechos de
 * plan: lo hacen los casos de uso de los productos. Los destinos se validan con la SPI {@link PromotionTargets}.
 */
@Service
@AllArgsConstructor
public class PromotionService {
    /** Capacidad del plan que habilita la audiencia {@code PLATFORM_FREQUENT} en promociones de almacén. */
    public static final String PLATFORM_AUDIENCE_CAPABILITY = "ADVANCED_REPORTS";

    private final PromotionRepository promotionRepository;
    private final PromotionTargets promotionTargets;

    @Transactional(readOnly = true)
    public List<PromotionResponse> listForStore(UUID storeId) {
        return promotionRepository.findStorePromotions(storeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * @param platformAudienceAllowed el caso de uso confirmó que el almacén tiene la capacidad
     *     {@value #PLATFORM_AUDIENCE_CAPABILITY}; sin ella, la audiencia {@code PLATFORM_FREQUENT} se rechaza
     */
    @Transactional
    public PromotionResponse createForStore(
            UUID storeId,
            PromotionUpsertRequest request,
            boolean platformAudienceAllowed
    ) {
        validateTarget(request, PromotionScope.STORE, storeId);
        validateAudience(request, PromotionScope.STORE, platformAudienceAllowed);
        return toResponse(promotionRepository.save(toPromotion(
                request,
                PromotionScope.STORE,
                storeId,
                storeId
        )));
    }

    /** Ver {@link #createForStore} para {@code platformAudienceAllowed}. */
    @Transactional
    public PromotionResponse updateForStore(
            UUID storeId,
            UUID promotionId,
            PromotionUpsertRequest request,
            boolean platformAudienceAllowed
    ) {
        Promotion promotion = promotionRepository.findStorePromotion(storeId, promotionId)
                .orElseThrow(PromotionService::notFound);
        validateTarget(request, PromotionScope.STORE, storeId);
        validateAudience(request, PromotionScope.STORE, platformAudienceAllowed);
        applyRequest(promotion, request, storeId);
        return toResponse(promotion);
    }

    @Transactional
    public void deleteForStore(UUID storeId, UUID promotionId) {
        Promotion promotion = promotionRepository.findStorePromotion(storeId, promotionId)
                .orElseThrow(PromotionService::notFound);
        promotionRepository.delete(promotion);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listForPlatform() {
        return promotionRepository.findPlatformPromotions()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PromotionResponse createForPlatform(PromotionUpsertRequest request) {
        validateTarget(request, PromotionScope.PLATFORM, null);
        validateAudience(request, PromotionScope.PLATFORM, true);
        return toResponse(promotionRepository.save(toPromotion(
                request,
                PromotionScope.PLATFORM,
                null,
                request.targetId()
        )));
    }

    @Transactional
    public PromotionResponse updateForPlatform(UUID promotionId, PromotionUpsertRequest request) {
        Promotion promotion = promotionRepository.findPlatformPromotion(promotionId)
                .orElseThrow(PromotionService::notFound);
        validateTarget(request, PromotionScope.PLATFORM, null);
        validateAudience(request, PromotionScope.PLATFORM, true);
        applyRequest(
                promotion,
                request,
                request.targetType() == PromotionTargetType.STORE ? request.targetId() : null
        );
        return toResponse(promotion);
    }

    @Transactional
    public void deleteForPlatform(UUID promotionId) {
        Promotion promotion = promotionRepository.findPlatformPromotion(promotionId)
                .orElseThrow(PromotionService::notFound);
        promotionRepository.delete(promotion);
    }

    /**
     * Promociones vigentes ahora con audiencia distinta de {@code ALL} (de almacén y de plataforma). Sin datos de
     * compradores: el filtro por segmento lo hace quien conoce al comprador ({@link BuyerContext#qualifiesFor}).
     */
    @Transactional(readOnly = true)
    public List<PromotionResponse> activeAudiencePromotions() {
        return promotionRepository.findActiveWithAudienceAt(OffsetDateTime.now(ZoneOffset.UTC))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** {@code PROMOTION_NOT_FOUND}; lo usan también los casos de uso cuando el almacén no existe. */
    public static NotFoundException notFound() {
        return new NotFoundException("PROMOTION_NOT_FOUND", "No se encontró la promoción.");
    }

    private Promotion toPromotion(
            PromotionUpsertRequest request,
            PromotionScope scope,
            UUID ownerStoreId,
            UUID storeTargetId
    ) {
        Promotion promotion = Promotion.builder()
                .scope(scope)
                .storeId(ownerStoreId)
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
        promotion.setAudience(request.audienceOrDefault());
    }

    /**
     * Plataforma: solo {@code ALL} o {@code PLATFORM_FREQUENT} (los segmentos {@code STORE_*} son de un almacén; 400
     * {@code INVALID_PROMOTION_AUDIENCE}). Almacén: {@code ALL} y {@code STORE_*}; {@code PLATFORM_FREQUENT} solo con
     * la capacidad {@value #PLATFORM_AUDIENCE_CAPABILITY} (409 {@code SELLER_PLAN_FEATURE_UNAVAILABLE}).
     */
    private void validateAudience(
            PromotionUpsertRequest request,
            PromotionScope scope,
            boolean platformAudienceAllowed
    ) {
        PromotionAudience audience = request.audienceOrDefault();
        if (scope == PromotionScope.PLATFORM) {
            if (audience.isStoreAudience()) {
                throw new BadRequestException(
                        "INVALID_PROMOTION_AUDIENCE",
                        "Las promociones de plataforma solo pueden dirigirse a todos los compradores "
                                + "o a los clientes frecuentes de Autopartco."
                );
            }
            return;
        }
        if (audience == PromotionAudience.PLATFORM_FREQUENT && !platformAudienceAllowed) {
            throw new ConflictException(
                    "SELLER_PLAN_FEATURE_UNAVAILABLE",
                    "Dirigir promociones a los clientes frecuentes de Autopartco requiere un plan con reportes "
                            + "avanzados.",
                    Map.of("capability", PLATFORM_AUDIENCE_CAPABILITY, "audience", audience.name())
            );
        }
    }

    private void validateTarget(PromotionUpsertRequest request, PromotionScope scope, UUID ownerStoreId) {
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
            if (targetId != null && !targetId.equals(ownerStoreId)) {
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
                var product = promotionTargets.findProduct(targetId)
                        .orElseThrow(() -> invalidTarget("No se encontró el producto de destino."));
                if (scope == PromotionScope.STORE && !ownerStoreId.equals(product.storeId())) {
                    throw invalidTarget("El producto de destino no pertenece a este almacén.");
                }
            }
            case CATEGORY -> {
                if (!promotionTargets.categoryExists(targetId)) {
                    throw invalidTarget("No se encontró la categoría de destino.");
                }
            }
            case MANUFACTURER -> {
                if (!promotionTargets.manufacturerExists(targetId)) {
                    throw invalidTarget("No se encontró la marca de destino.");
                }
            }
            case STORE -> {
                if (scope != PromotionScope.PLATFORM || !promotionTargets.storeExists(targetId)) {
                    throw invalidTarget("No se encontró el almacén de destino.");
                }
            }
            default -> throw invalidTarget("El tipo de destino no es válido.");
        }
    }

    private PromotionResponse toResponse(Promotion promotion) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean active = promotion.isEnabled()
                && !promotion.getStartsAt().isAfter(now)
                && promotion.getEndsAt().isAfter(now);
        return new PromotionResponse(
                promotion.getId(),
                promotion.getScope(),
                promotion.getStoreId(),
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
                active,
                promotion.getAudience() == null ? PromotionAudience.ALL : promotion.getAudience()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BadRequestException invalidTarget(String message) {
        return new BadRequestException("INVALID_PROMOTION_TARGET", message);
    }
}
