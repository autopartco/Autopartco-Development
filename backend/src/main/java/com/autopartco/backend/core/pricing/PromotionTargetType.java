package com.autopartco.backend.core.pricing;

public enum PromotionTargetType {
    ALL,
    STORE,
    PRODUCT,
    CATEGORY,
    MANUFACTURER,
    ATTRIBUTE,
    /** Un servicio del almacén ({@code target_id}); solo promociones de almacén. */
    SERVICE,
    /** Una categoría de servicio ({@code target_key}, sin {@code target_id}); almacén o plataforma. */
    SERVICE_CATEGORY;

    /** Destinos que aplican a servicios (y nunca a productos). */
    public boolean isService() {
        return this == SERVICE || this == SERVICE_CATEGORY;
    }
}
