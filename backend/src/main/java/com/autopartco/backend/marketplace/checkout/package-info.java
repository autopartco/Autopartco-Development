/**
 * Confirmación de compra del comprador (B1): {@code POST /api/v1/marketplace/orders}, según
 * {@code APP_CHECKOUT_PAYMENT_PROVIDER}.
 * <ul>
 *   <li>SIMULATED: {@link com.autopartco.backend.marketplace.checkout.PlaceMarketplaceOrder} valida, descuenta el stock,
 *       cobra con el simulador y persiste el pedido CONFIRMED en una sola transacción. Un rechazo responde 402 y
 *       revierte todo.</li>
 *   <li>WOMPI (ADR 0004): {@link com.autopartco.backend.marketplace.checkout.StartMarketplaceCheckout} reserva el stock
 *       y crea el pedido AWAITING_PAYMENT con un pago PENDING ({@code APM-…}); responde 202 con el enlace firmado.
 *       {@code MarketplaceWompiHandler} (SPI de {@code core.payments}) aplica las transacciones de Wompi, y
 *       {@code MarketplacePaymentJobs} concilia con la API y vence las reservas.</li>
 * </ul>
 * Los dos flujos son idempotentes por {@code clientRequestId}. También expone
 * {@code GET /api/v1/marketplace/checkout-options} y {@code GET|POST /api/v1/me/orders/{orderId}/payment[/sync]}.
 */
package com.autopartco.backend.marketplace.checkout;
