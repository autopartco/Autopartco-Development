package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SaleDetailResponse(
        UUID saleId,
        UUID orderId,
        long orderNumber,
        UUID branchId,
        String branchName,
        String status,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal total,
        LocalDateTime createdAt,
        String customerName,
        String paymentStatus,
        String paymentMethod,
        Shipment shipment,
        List<Item> items,
        DeliveryAddress deliveryAddress,
        /** Factura adjunta; {@code null} si el almacén no la ha subido. */
        SaleInvoice invoice
) {
    public record Item(
            UUID inventoryId,
            UUID productId,
            String productName,
            String partNumber,
            int units,
            BigDecimal unitPrice
    ) {
    }

    public record Shipment(
            UUID id,
            String carrier,
            String trackingNumber,
            String status,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt,
            List<Event> events
    ) {
    }

    public record Event(
            String status,
            String notes,
            LocalDateTime occurredAt
    ) {
    }

    public record DeliveryAddress(
            String addressLine,
            String addressComplement,
            String city,
            String department,
            String country,
            String postalCode
    ) {
    }
}
