package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Servicio del almacén en el portal. {@code branchIds} son las sucursales (en cualquier estado) que lo prestan. */
public record StoreServiceView(
        UUID id,
        ServiceCategory category,
        String name,
        String description,
        BigDecimal priceFrom,
        Integer durationMinutes,
        boolean active,
        int sortOrder,
        List<UUID> branchIds,
        Long version
) {
}
