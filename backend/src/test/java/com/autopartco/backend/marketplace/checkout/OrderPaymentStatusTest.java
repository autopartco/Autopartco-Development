package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.PaymentSyncRateLimiter;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPaymentStatusTest {
    private static final String EMAIL = "comprador@example.com";
    private static final UUID PERSON_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ORDER_ID = UUID.fromString("9b2f6a3e-1c4d-4e8f-a1b2-3c4d5e6f7a8b");
    private static final String REFERENCE = "APM-3f9a0c2b7d6e4f1a9b8c7d6e5f4a3b2c";
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    @Mock
    private AccountLookup accountLookup;
    @Mock
    private OrderPayments orderPayments;
    @Mock
    private WompiClient wompiClient;
    @Mock
    private WompiEventRouter eventRouter;
    @Mock
    private PaymentSyncRateLimiter rateLimiter;

    private OrderPaymentStatus service;

    @BeforeEach
    void setUp() {
        MarketplacePaymentLinks links = new MarketplacePaymentLinks(
                new WompiClient(StartMarketplaceCheckoutTest.WOMPI, new ObjectMapper()),
                new com.autopartco.backend.shared.config.AppProperties(
                        "http://localhost:8081", "https://front.example", null, null, null));
        service = new OrderPaymentStatus(accountLookup, orderPayments, links, wompiClient, eventRouter, rateLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(accountLookup.requireByEmail(EMAIL)).thenReturn(
                new AccountRef(UUID.randomUUID(), PERSON_ID, EMAIL, "Ana Pérez", "ACTIVE", "BUYER", "Ana"));
    }

    private static OrderPayment payment(String provider, String orderStatus, String status, Instant expiresAt) {
        return new OrderPayment(UUID.randomUUID(), ORDER_ID, PERSON_ID, 1042L, orderStatus, provider, REFERENCE,
                status, null, null, new BigDecimal("230000.00"), "COP", "SANDBOX", expiresAt,
                null, false);
    }

    private void current(OrderPayment payment) {
        when(orderPayments.findForBuyer(PERSON_ID, ORDER_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    void ownerSeesPendingPaymentWithCheckoutLink() {
        current(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", NOW.plusSeconds(1800)));

        OrderPaymentView view = service.get(EMAIL, ORDER_ID);

        assertThat(view.orderId()).isEqualTo(ORDER_ID);
        assertThat(view.reference()).isEqualTo(REFERENCE);
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.orderStatus()).isEqualTo("AWAITING_PAYMENT");
        assertThat(view.amountInCents()).isEqualTo(23_000_000L);
        assertThat(view.expiresAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 10, 30));
        assertThat(view.checkoutUrl()).contains("reference=" + REFERENCE);
    }

    @Test
    void expiredOrSimulatedPaymentHasNoCheckoutLink() {
        current(payment("WOMPI", "CANCELLED", "EXPIRED", NOW.minusSeconds(1)));
        assertThat(service.get(EMAIL, ORDER_ID).checkoutUrl()).isNull();

        current(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", NOW.minusSeconds(1)));
        assertThat(service.get(EMAIL, ORDER_ID).checkoutUrl()).isNull();

        current(payment("SIMULATED", "CONFIRMED", "APPROVED", null));
        OrderPaymentView simulated = service.get(EMAIL, ORDER_ID);
        assertThat(simulated.checkoutUrl()).isNull();
        assertThat(simulated.expiresAt()).isNull();
    }

    @Test
    void foreignOrMissingOrderIsTheSame404() {
        when(orderPayments.findForBuyer(PERSON_ID, ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(EMAIL, ORDER_ID))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        assertThatThrownBy(() -> service.sync(EMAIL, ORDER_ID, "1234-1730733600-49201"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        verifyNoInteractions(wompiClient, eventRouter);
    }

    @Test
    void syncAppliesTransactionOfThisOrderAndReturnsFreshState() {
        WompiTransaction transaction = new WompiTransaction("t-1", REFERENCE, "APPROVED", 23_000_000L, "COP", null);
        when(orderPayments.findForBuyer(PERSON_ID, ORDER_ID)).thenReturn(
                Optional.of(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", NOW.plusSeconds(1800))),
                Optional.of(payment("WOMPI", "CONFIRMED", "APPROVED", NOW.plusSeconds(1800))));
        when(wompiClient.findTransaction("t-1")).thenReturn(Optional.of(transaction));

        OrderPaymentView view = service.sync(EMAIL, ORDER_ID, "t-1");

        verify(rateLimiter).acquire(EMAIL);
        verify(eventRouter).apply(transaction, WompiEventSource.REDIRECT_SYNC);
        assertThat(view.status()).isEqualTo("APPROVED");
        assertThat(view.orderStatus()).isEqualTo("CONFIRMED");
        assertThat(view.checkoutUrl()).isNull();
    }

    @Test
    void syncRejectsTransactionOfAnotherReference() {
        current(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", NOW.plusSeconds(1800)));
        when(wompiClient.findTransaction("t-1")).thenReturn(Optional.of(
                new WompiTransaction("t-1", "APM-other", "APPROVED", 23_000_000L, "COP", null)));

        assertThatThrownBy(() -> service.sync(EMAIL, ORDER_ID, "t-1"))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PAYMENT_REFERENCE_MISMATCH"));
        verify(eventRouter, never()).apply(any(), any());
    }

    @Test
    void syncWithUnknownTransactionIs404() {
        current(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", NOW.plusSeconds(1800)));
        when(wompiClient.findTransaction("t-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sync(EMAIL, ORDER_ID, "t-1"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PAYMENT_TRANSACTION_NOT_FOUND"));
    }

    @Test
    void syncWhenWompiIsDownReturnsCurrentState() {
        current(payment("WOMPI", "AWAITING_PAYMENT", "PENDING", NOW.plusSeconds(1800)));
        when(wompiClient.findTransaction("t-1")).thenThrow(new WompiUnavailableException("down"));

        assertThat(service.sync(EMAIL, ORDER_ID, "t-1").status()).isEqualTo("PENDING");
        verify(eventRouter, never()).apply(any(), any());
    }

    @Test
    void syncOfApprovedPaymentDoesNotCallWompi() {
        current(payment("WOMPI", "CONFIRMED", "APPROVED", NOW.plusSeconds(1800)));

        assertThat(service.sync(EMAIL, ORDER_ID, "t-1").status()).isEqualTo("APPROVED");
        verifyNoInteractions(wompiClient, eventRouter);
    }

    @Test
    void rateLimitedSyncStopsBeforeAnyLookup() {
        doThrow(new TooManyRequestsException("PAYMENT_SYNC_RATE_LIMITED", "Demasiadas"))
                .when(rateLimiter).acquire(EMAIL);

        assertThatThrownBy(() -> service.sync(EMAIL, ORDER_ID, "t-1"))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(orderPayments, wompiClient, eventRouter);
    }
}
