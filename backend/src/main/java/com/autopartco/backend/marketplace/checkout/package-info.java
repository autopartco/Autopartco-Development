/**
 * Confirmación de compra del comprador (B1): {@code POST /api/v1/marketplace/orders}.
 * <p>
 * {@link com.autopartco.backend.marketplace.checkout.PlaceMarketplaceOrder} valida la dirección y los listados,
 * descuenta el stock de forma atómica, calcula el precio en el servidor, cobra con el
 * {@link com.autopartco.backend.marketplace.checkout.CheckoutPaymentGateway} (hoy solo el simulador) y persiste el
 * pedido en una sola transacción. Un rechazo responde 402 y revierte todo. Es idempotente por
 * {@code clientRequestId}.
 */
package com.autopartco.backend.marketplace.checkout;
