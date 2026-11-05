package com.autopartco.backend.marketplace.checkout;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.WompiCheckoutRequest;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;

/**
 * Presentación del pago Wompi de un pedido: monto en centavos, hora de Colombia y enlace firmado del Web Checkout con
 * {@code redirect-url = <APP_FRONTEND_BASE_URL>/pedido/{orderId}/resultado} (plan Wompi, N6). Firmar no usa red.
 */
@Component
@AllArgsConstructor
class MarketplacePaymentLinks {
    static final String PROVIDER = "WOMPI";
    static final String CURRENCY = "COP";
    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    /** Estados del pago con los que aún se puede intentar pagar en Wompi. */
    static final Set<String> OPEN_STATUSES = Set.of("PENDING", "REJECTED", "CANCELLED");

    private final WompiClient wompiClient;
    private final AppProperties appProperties;

    /** Centavos enteros; lanza {@link ArithmeticException} si el monto tiene más de dos decimales (N10). */
    static long amountInCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    /** Hora de Colombia sin desfase (ADR 0002, N15). */
    static LocalDateTime colombiaTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, COLOMBIA);
    }

    /** Pago Wompi abierto de un pedido en espera y antes de vencer. */
    static boolean payable(OrderPayment payment, Instant now) {
        return PROVIDER.equals(payment.provider())
                && OrderPayments.AWAITING_PAYMENT.equals(payment.orderStatus())
                && OPEN_STATUSES.contains(payment.status())
                && payment.expiresAt() != null
                && now.isBefore(payment.expiresAt());
    }

    /** Enlace firmado si el pedido aún se puede pagar; si no, {@code null}. */
    String checkoutUrlIfPayable(OrderPayment payment, Instant now) {
        if (!payable(payment, now)) {
            return null;
        }
        return wompiClient.checkoutUrl(new WompiCheckoutRequest(
                payment.reference(),
                amountInCents(payment.amount()),
                payment.currency(),
                redirectUrl(payment.orderId()),
                payment.expiresAt()
        ));
    }

    String redirectUrl(UUID orderId) {
        return UriComponentsBuilder.fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/pedido/{orderId}/resultado")
                .buildAndExpand(orderId)
                .toUriString();
    }
}
