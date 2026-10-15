package com.autopartco.backend.core.order;

import java.math.BigDecimal;

/** Resumen de ventas de un almacén: mes en curso y pedidos abiertos. */
public record SalesSummary(
        long ordersThisMonth,
        BigDecimal revenueThisMonth,
        long pendingOrders,
        long preparingOrders
) {
}
