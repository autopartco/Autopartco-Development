package com.autopartco.backend.marketplace.stores;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.PublicStoreService;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.pricing.ServicePrice;
import com.autopartco.backend.core.pricing.ServicePricing;
import com.autopartco.backend.marketplace.services.ServiceCard;

/**
 * Servicio en el perfil público del almacén. {@code priceFrom} lleva la promoción vigente y {@code basePriceFrom} es el
 * precio del servicio; {@code promotion} es nula si no hay. {@code branchIds} solo incluye sucursales activas.
 */
public record StoreServiceItem(
        UUID id,
        ServiceCategory category,
        String name,
        String description,
        BigDecimal priceFrom,
        BigDecimal basePriceFrom,
        ServiceCard.Promotion promotion,
        Integer durationMinutes,
        List<UUID> branchIds,
        String imageUrl,
        boolean bookingEnabled
) {
    static StoreServiceItem of(PublicStoreService service) {
        return new StoreServiceItem(service.id(), service.category(), service.name(), service.description(),
                service.priceFrom(), service.priceFrom(), null, service.durationMinutes(), service.branchIds(),
                service.imageUrl(), service.bookingEnabled());
    }

    /** Aplica las promociones vigentes sobre el precio base. */
    StoreServiceItem priced(UUID storeId, ServicePricing.Rules rules) {
        ServicePrice price = rules.price(id, storeId, category == null ? null : category.name(), basePriceFrom);
        ServiceCard.Promotion applied = price.promotion() == null ? null
                : new ServiceCard.Promotion(price.promotion().id(), price.promotion().label(),
                        price.promotion().endsAt());
        return new StoreServiceItem(id, category, name, description, price.priceFrom(), basePriceFrom, applied,
                durationMinutes, branchIds, imageUrl, bookingEnabled);
    }
}
