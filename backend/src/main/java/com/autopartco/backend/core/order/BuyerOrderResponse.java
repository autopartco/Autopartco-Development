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
            List<Item> items
    ) {
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
