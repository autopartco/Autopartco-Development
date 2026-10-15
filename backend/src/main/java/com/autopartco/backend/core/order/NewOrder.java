package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Pedido ya validado y tarificado por el caso de uso, listo para persistir con {@link OrderPlacement}. Los montos
 * llegan calculados (escala 2); {@code core.order} no recalcula precios ni valida stock.
 */
public record NewOrder(
        UUID personId,
        DeliveryAddress address,
        String paymentMethodCode,
        String paymentReference,
        List<NewSale> sales
) {
    /** Dirección congelada en {@code order_addresses}. */
    public record DeliveryAddress(
            String addressLine,
            String addressComplement,
            String city,
            String department,
            String country,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    /** Una venta por sucursal. */
    public record NewSale(
            UUID branchId,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal total,
            List<NewLine> lines
    ) {
    }

    /** Una línea de detalle: listado de inventario, unidades y precio unitario efectivo cobrado. */
    public record NewLine(UUID inventoryId, int units, BigDecimal unitPrice) {
    }
}
