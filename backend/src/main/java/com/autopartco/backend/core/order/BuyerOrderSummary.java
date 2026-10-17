package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Fila del listado "Mis pedidos" (contrato §4.3 del plan de marketplace). */
public record BuyerOrderSummary(
        UUID orderId,
        long orderNumber,
        String status,
        LocalDateTime createdAt,
        String currency,
        BigDecimal total,
        int itemCount,
        List<SaleRef> sales
) {
    public record SaleRef(UUID saleId, UUID storeId, String storeName, String status) {
    }
}
