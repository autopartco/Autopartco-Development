package com.autopartco.backend.marketplace.checkout;

import java.math.BigDecimal;
import java.util.List;

import com.autopartco.backend.core.order.NewOrder;

/**
 * Carrito ya validado y con el stock descontado (dentro de la transacción del checkout): dirección congelada, ventas
 * por sucursal con precios del servidor y total del pedido en pesos (escala 2).
 */
public record PreparedOrder(NewOrder.DeliveryAddress address, List<NewOrder.NewSale> sales, BigDecimal total) {
}
