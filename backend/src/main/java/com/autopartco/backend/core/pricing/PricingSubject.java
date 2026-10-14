package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que {@link PriceCalculator} necesita saber de un listado (producto en una sucursal) para decidir qué promociones
 * aplican. Lo construye {@code catalog}; no contiene entidades de otros dominios.
 *
 * @param price precio base del listado (inventario)
 * @param storeId almacén dueño del producto
 * @param stateCode código de estado del producto
 * @param compatibilityIds compatibilidades vigentes del producto
 */
public record PricingSubject(
        BigDecimal price,
        UUID productId,
        UUID storeId,
        UUID categoryId,
        UUID manufacturerId,
        String name,
        String description,
        String sku,
        String partNumber,
        String stateCode,
        Map<String, Object> attributes,
        Set<UUID> compatibilityIds
) {
}
