package com.autopartco.backend.marketplace.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.search.ServiceHit;

/**
 * Tarjeta pública de un servicio (búsqueda, detalle, relacionados, franja de la búsqueda unificada). {@code branch} es
 * la sucursal más cercana (con ubicación) o la primera. {@code promotion}, {@code distanceKm}, {@code imageUrl},
 * {@code priceFrom} y {@code durationMinutes} pueden ser {@code null}.
 */
public record ServiceCard(
        UUID id,
        String name,
        ServiceCategory category,
        String imageUrl,
        BigDecimal priceFrom,
        BigDecimal basePriceFrom,
        Promotion promotion,
        Integer durationMinutes,
        boolean bookingEnabled,
        Store store,
        Branch branch,
        BigDecimal distanceKm
) {
    public record Promotion(UUID id, String label, OffsetDateTime endsAt) {
    }

    /** {@code businessType}: {@code PARTS}, {@code SERVICES} o {@code BOTH}. */
    public record Store(UUID id, String name, String logoUrl, BigDecimal rating, String businessType) {
    }

    /** {@code openNow} es {@code null} si la sucursal no tiene horario semanal. */
    public record Branch(UUID id, String name, String city, String address, BigDecimal latitude, BigDecimal longitude,
            Boolean openNow) {
    }

    public static ServiceCard of(ServiceHit hit) {
        ServiceHit.Store store = hit.store();
        ServiceHit.Branch branch = hit.branch();
        return new ServiceCard(
                hit.id(),
                hit.name(),
                hit.category(),
                hit.imageUrl(),
                hit.priceFrom(),
                hit.basePriceFrom(),
                hit.promotion() == null ? null : new Promotion(hit.promotion().id(), hit.promotion().label(),
                        hit.promotion().endsAt()),
                hit.durationMinutes(),
                hit.bookingEnabled(),
                new Store(store.id(), store.name(), store.logoUrl(), store.rating(), store.businessType()),
                new Branch(branch.id(), branch.name(), branch.city(), branch.address(), branch.latitude(),
                        branch.longitude(), branch.openNow()),
                hit.distanceKm());
    }
}
