package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.UUID;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.pricing.ServicePrice;

/**
 * Servicio publicado con la sucursal elegida (la más cercana si la consulta trae un punto; si no, la primera con
 * ubicación). {@code priceFrom} es el precio «desde» con la promoción vigente y {@code basePriceFrom} el del servicio. {@code distanceKm} es {@code null} sin punto o sin ubicación de la
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
        BigDecimal distanceKm,
        /** Promoción vigente aplicada a {@code priceFrom}; nula si no hay. */
        ServicePrice.Applied promotion
) {
    public ServiceHit(UUID id, String name, ServiceCategory category, String description, String imageUrl,
            BigDecimal priceFrom, BigDecimal basePriceFrom, boolean hasPromotion, Integer durationMinutes,
            boolean bookingEnabled, Store store, Branch branch, BigDecimal distanceKm) {
        this(id, name, category, description, imageUrl, priceFrom, basePriceFrom, hasPromotion, durationMinutes,
                bookingEnabled, store, branch, distanceKm, null);
    }

    /** {@code rating} con un decimal o {@code null}; {@code businessType} es {@code PARTS}, {@code SERVICES} o {@code BOTH}. */
    public record Store(UUID id, String name, String logoUrl, BigDecimal rating, String businessType) {
    }

    public record Branch(UUID id, String name, String city, String address, BigDecimal latitude, BigDecimal longitude,
            Boolean openNow) {
    }
}
