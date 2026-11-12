package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.autopartco.backend.core.pricing.PromotionAudience;

/**
 * Listado de un producto en una sucursal (panel del almacén y marketplace). {@code manufacturerName} se muestra
 * aunque el fabricante esté pendiente de aprobación ({@code manufacturerApproved=false}); no se exponen más datos
 * del fabricante pendiente. {@code appliedPromotionId} y {@code appliedPromotionAudience} identifican la promoción
 * ganadora ({@code null} si ninguna); con audiencia distinta de {@code ALL}, el precio es exclusivo del comprador
 * autenticado (p. ej. «Precio para clientes frecuentes»). {@code rating} (un decimal, {@code null} sin reseñas) y
 * {@code reviewCount} resumen las reseñas visibles del producto: los rellenan la búsqueda, el inicio y los anuncios
 * desde {@code search_listings}; en el resto de vistas van {@code null} y 0.
 */
public record ProductResponse(
        UUID id,
        UUID storeId,
        UUID branchId,
        String branchName,
        String name,
        String sku,
        String partNumber,
        String description,
        BigDecimal price,
        BigDecimal effectivePrice,
        BigDecimal discountAmount,
        String appliedPromotionName,
        Integer quantity,
        UUID manufacturerId,
        String manufacturerName,
        boolean manufacturerApproved,
        UUID categoryId,
        String state,
        boolean universal,
        Map<String, Object> attributes,
        List<VehicleRuleResponse> vehicleRules,
        List<ProductImageResponse> images,
        UUID appliedPromotionId,
        PromotionAudience appliedPromotionAudience,
        BigDecimal rating,
        int reviewCount
) {
    /** Sin calificación del producto ({@code rating} nulo y 0 reseñas). */
    public ProductResponse(
            UUID id,
            UUID storeId,
            UUID branchId,
            String branchName,
            String name,
            String sku,
            String partNumber,
            String description,
            BigDecimal price,
            BigDecimal effectivePrice,
            BigDecimal discountAmount,
            String appliedPromotionName,
            Integer quantity,
            UUID manufacturerId,
            String manufacturerName,
            boolean manufacturerApproved,
            UUID categoryId,
            String state,
            boolean universal,
            Map<String, Object> attributes,
            List<VehicleRuleResponse> vehicleRules,
            List<ProductImageResponse> images,
            UUID appliedPromotionId,
            PromotionAudience appliedPromotionAudience
    ) {
        this(id, storeId, branchId, branchName, name, sku, partNumber, description, price, effectivePrice,
                discountAmount, appliedPromotionName, quantity, manufacturerId, manufacturerName, manufacturerApproved,
                categoryId, state, universal, attributes, vehicleRules, images, appliedPromotionId,
                appliedPromotionAudience, null, 0);
    }

    /** Sin promoción ganadora identificada (id y audiencia nulos). */
    public ProductResponse(
            UUID id,
            UUID storeId,
            UUID branchId,
            String branchName,
            String name,
            String sku,
            String partNumber,
            String description,
            BigDecimal price,
            BigDecimal effectivePrice,
            BigDecimal discountAmount,
            String appliedPromotionName,
            Integer quantity,
            UUID manufacturerId,
            String manufacturerName,
            boolean manufacturerApproved,
            UUID categoryId,
            String state,
            boolean universal,
            Map<String, Object> attributes,
            List<VehicleRuleResponse> vehicleRules,
            List<ProductImageResponse> images
    ) {
        this(id, storeId, branchId, branchName, name, sku, partNumber, description, price, effectivePrice,
                discountAmount, appliedPromotionName, quantity, manufacturerId, manufacturerName, manufacturerApproved,
                categoryId, state, universal, attributes, vehicleRules, images, null, null);
    }

    /** Copia con la calificación del producto. */
    public ProductResponse withRating(BigDecimal productRating, int productReviewCount) {
        return new ProductResponse(id, storeId, branchId, branchName, name, sku, partNumber, description, price,
                effectivePrice, discountAmount, appliedPromotionName, quantity, manufacturerId, manufacturerName,
                manufacturerApproved, categoryId, state, universal, attributes, vehicleRules, images,
                appliedPromotionId, appliedPromotionAudience, productRating, Math.max(0, productReviewCount));
    }
}
