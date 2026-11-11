package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Servicio activo visible en el perfil público; {@code branchIds} solo incluye sucursales activas. */
public record PublicStoreService(
        UUID id,
        ServiceCategory category,
        String name,
        String description,
        BigDecimal priceFrom,
        Integer durationMinutes,
        List<UUID> branchIds
) {
}
