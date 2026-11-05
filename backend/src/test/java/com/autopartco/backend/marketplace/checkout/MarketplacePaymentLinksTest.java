package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.order.OrderPayment;

class MarketplacePaymentLinksTest {
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    private static OrderPayment payment(String provider, String orderStatus, String status, Instant expiresAt) {
        return new OrderPayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L, orderStatus, provider,
                "APM-1", status, null, null, new BigDecimal("1500.50"), "COP", "SANDBOX", expiresAt, null, false);
    }

    @Test
    void amountInCentsIsExact() {
        assertThat(MarketplacePaymentLinks.amountInCents(new BigDecimal("230000.00"))).isEqualTo(23_000_000L);
        assertThat(MarketplacePaymentLinks.amountInCents(new BigDecimal("1500.5"))).isEqualTo(150_050L);
        assertThatThrownBy(() -> MarketplacePaymentLinks.amountInCents(new BigDecimal("0.001")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void colombiaTimeHasNoOffset() {
        assertThat(MarketplacePaymentLinks.colombiaTime(NOW)).isEqualTo(LocalDateTime.of(2026, 11, 4, 10, 0));
        assertThat(MarketplacePaymentLinks.colombiaTime(null)).isNull();
    }

    @Test
    void payableOnlyWhileAwaitingOpenWompiPaymentBeforeExpiration() {
        Instant later = NOW.plusSeconds(60);
        assertThat(MarketplacePaymentLinks.payable(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", later), NOW))
                .isTrue();
        assertThat(MarketplacePaymentLinks.payable(payment("WOMPI", "AWAITING_PAYMENT", "REJECTED", later), NOW))
                .isTrue();
        assertThat(MarketplacePaymentLinks.payable(payment("WOMPI", "AWAITING_PAYMENT", "CANCELLED", later), NOW))
                .isTrue();
        assertThat(MarketplacePaymentLinks.payable(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", NOW), NOW))
                .isFalse();
        assertThat(MarketplacePaymentLinks.payable(payment("WOMPI", "CONFIRMED", "APPROVED", later), NOW))
                .isFalse();
        assertThat(MarketplacePaymentLinks.payable(payment("WOMPI", "CANCELLED", "EXPIRED", later), NOW))
                .isFalse();
        assertThat(MarketplacePaymentLinks.payable(payment("SIMULATED", "AWAITING_PAYMENT", "PENDING", later), NOW))
                .isFalse();
    }

    @Test
    void redirectUrlPointsToOrderResultPage() {
        UUID orderId = UUID.fromString("9b2f6a3e-1c4d-4e8f-a1b2-3c4d5e6f7a8b");

        assertThat(StartMarketplaceCheckoutTest.links().redirectUrl(orderId))
                .isEqualTo("https://front.example/pedido/" + orderId + "/resultado");
    }
}
