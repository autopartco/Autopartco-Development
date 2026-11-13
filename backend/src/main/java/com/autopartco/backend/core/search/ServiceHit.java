package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.UUID;

import com.autopartco.backend.core.organization.ServiceCategory;

/**
 * Servicio publicado con la sucursal elegida (la más cercana si la consulta trae un punto; si no, la primera con
 * ubicación). {@code priceFrom} es el precio «desde» con la promoción vigente y {@code basePriceFrom} el del servicio
 * (iguales mientras no se apliquen promociones). {@code distanceKm} es {@code null} sin punto o sin ubicación de la
 * sucursal; {@code openNow} es {@code null} sin horario semanal.
 */
public record ServiceHit(
        UUID id,
        String name,
        ServiceCategory category,
        String description,
        String imageUrl,
        BigDecimal priceFrom,
        BigDecimal basePriceFrom,
        boolean hasPromotion,
        Integer durationMinutes,
        boolean bookingEnabled,
        Store store,
        Branch branch,
        BigDecimal distanceKm
) {
    /** {@code rating} con un decimal o {@code null}; {@code businessType} es {@code PARTS}, {@code SERVICES} o {@code BOTH}. */
    public record Store(UUID id, String name, String logoUrl, BigDecimal rating, String businessType) {
    }

    public record Branch(UUID id, String name, String city, String address, BigDecimal latitude, BigDecimal longitude,
            Boolean openNow) {
    }
}
