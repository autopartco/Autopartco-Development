package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sede para el mapa de cobertura del marketplace. Solo datos públicos: sin teléfono ni dirección exacta
 * ({@code area} es el complemento de la dirección, p. ej. el barrio). {@code distanceKm} es {@code null} si la
 * consulta no recibió el punto del usuario.
 */
public record MapBranchView(
        UUID storeId,
        String storeName,
        UUID branchId,
        String branchName,
        String area,
        String city,
        String department,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal distanceKm) {
}
