package com.autopartco.backend.core.booking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cita vista por el comprador o por el almacén. Fechas con la zona de Bogotá. {@code buyer} solo se incluye en el
 * portal; en una cita cancelada, rechazada o vencida el portal no recibe {@code contactPhone}, {@code buyer.phone} ni la
 * placa. {@code promotion} es la promoción vigente al pedir (nula si no había).
 */
public record Booking(
        UUID id,
        String code,
        BookingStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime proposedStartsAt,
        ServiceRef service,
        StoreRef store,
        BranchRef branch,
        BigDecimal quotedPrice,
        PromotionRef promotion,
        BookingVehicle vehicle,
        String contactPhone,
        String buyerNote,
        String storeNote,
        OffsetDateTime createdAt,
        Long version,
        @JsonInclude(JsonInclude.Include.NON_NULL) Buyer buyer
) {
    public record ServiceRef(UUID id, String name, ServiceCategory category, String imageUrl) {
    }

    public record StoreRef(UUID id, String name, String logoUrl) {
    }

    /** Sucursal con los mismos nombres de campo que {@code StoreBranch} del marketplace. */
    public record BranchRef(UUID id, String name, String address, String city, String department,
            BigDecimal latitude, BigDecimal longitude, String phone, String whatsapp,
            Map<String, Object> openingHours) {
    }

    /** Promoción vigente al pedir la cita: el almacén se comprometió a ese precio aunque ya haya vencido. */
    public record PromotionRef(UUID id, String name, String label) {
    }

    /** Comprador visto por el almacén; {@code phone} es nulo si la cita está cancelada. */
    public record Buyer(String name, String phone) {
    }
}
