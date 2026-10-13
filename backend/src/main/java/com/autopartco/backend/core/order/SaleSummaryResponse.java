package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SaleSummaryResponse(
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
        String shipmentStatus,
        int itemCount
) {
}
