package com.autopartco.backend.core.pricing;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI: {@link PromotionService} la usa para validar el destino de una promoción sin depender de {@code catalog} ni
 * de {@code organization}. La implementa {@code catalog} (que sí puede depender de ambos).
 */
public interface PromotionTargets {

    /** Producto no borrado con ese ID, con su almacén dueño. */
    Optional<TargetProduct> findProduct(UUID productId);

    boolean categoryExists(UUID categoryId);

    boolean manufacturerExists(UUID manufacturerId);

    boolean storeExists(UUID storeId);

    record TargetProduct(UUID productId, UUID storeId) {
    }
}
