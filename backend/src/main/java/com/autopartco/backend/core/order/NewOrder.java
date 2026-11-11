package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Pedido ya validado y tarificado por el caso de uso, listo para persistir con {@link OrderPlacement}. Los montos
 * llegan calculados (escala 2); {@code core.order} no recalcula precios ni valida stock. {@code anonymousId}: id anónimo
 * del navegador de quien compra (opcional); si llega, se guarda en {@code order_attributions} para atribuir el pedido a
 * los anuncios que clicó antes de iniciar sesión.
 */
public record NewOrder(
        UUID personId,
        DeliveryAddress address,
        String paymentMethodCode,
        String paymentReference,
        List<NewSale> sales,
        UUID anonymousId
) {
    /** Pedido sin id anónimo (sin fila en {@code order_attributions}). */
    public NewOrder(UUID personId, DeliveryAddress address, String paymentMethodCode, String paymentReference,
            List<NewSale> sales) {
        this(personId, address, paymentMethodCode, paymentReference, sales, null);
    }

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

    /**
     * Una línea de detalle: listado de inventario, producto, unidades, precio unitario efectivo cobrado y promoción
     * que lo fijó ({@code null} si ninguna; se guarda en {@code order_details.promotion_id}).
     */
    public record NewLine(UUID inventoryId, UUID productId, int units, BigDecimal unitPrice, UUID promotionId) {
        /** Línea sin promoción. */
        public NewLine(UUID inventoryId, UUID productId, int units, BigDecimal unitPrice) {
            this(inventoryId, productId, units, unitPrice, null);
        }
    }
}
