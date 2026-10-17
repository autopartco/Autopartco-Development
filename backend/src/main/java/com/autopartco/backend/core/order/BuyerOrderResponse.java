package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Pedido desde el punto de vista del comprador (contrato §2.3 del plan de checkout). */
public record BuyerOrderResponse(
        UUID orderId,
        long orderNumber,
        String status,
        LocalDateTime createdAt,
        String currency,
        BigDecimal subtotal,
        BigDecimal shippingTotal,
        BigDecimal total,
        Payment payment,
        DeliveryAddress deliveryAddress,
        List<Sale> sales
) {
    public record Payment(
            String status,
            String method,
            String methodName,
            String reference,
            BigDecimal amount,
            LocalDateTime paidAt
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

    public record Sale(
            UUID saleId,
            UUID storeId,
            String storeName,
            UUID branchId,
            String branchName,
            String status,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal total,
            List<Item> items,
            Shipment shipment
    ) {
    }

    /** Envío de una venta; {@code null} si no tiene (siempre {@code null} en el recibo del checkout). */
    public record Shipment(
            String carrier,
            String trackingNumber,
            String status,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt,
            List<Event> events
    ) {
        public record Event(String status, String notes, LocalDateTime occurredAt) {
        }
    }

    public record Item(
            UUID productId,
            String name,
            String partNumber,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }
}
