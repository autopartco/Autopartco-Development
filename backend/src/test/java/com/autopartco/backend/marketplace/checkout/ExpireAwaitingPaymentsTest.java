package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import com.autopartco.backend.core.inventory.StockReturn;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

@ExtendWith(MockitoExtension.class)
class ExpireAwaitingPaymentsTest {
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID INVENTORY_A = UUID.randomUUID();
    private static final UUID INVENTORY_B = UUID.randomUUID();
    private static final String REFERENCE = "APM-1";
    private static final java.time.Instant EXPIRES_AT = java.time.Instant.parse("2026-11-04T15:30:00Z");
    private static final java.util.function.Function<String, OrderPayments.ExpiredCandidate> CANDIDATE_OF =
            environment -> new OrderPayments.ExpiredCandidate(PAYMENT_ID, ORDER_ID, REFERENCE, environment,
                    EXPIRES_AT);

    @Mock
    private OrderPayments orderPayments;
    @Mock
    private StockService stockService;
    @Mock
    private WompiClient wompiClient;
    @Mock
    private WompiEventRouter eventRouter;

    private ExpireAwaitingPayments job() {
        return new ExpireAwaitingPayments(orderPayments, stockService, wompiClient, eventRouter,
                MarketplaceWompiHandlerTest.settings(WompiEnvironment.SANDBOX),
                TransactionOperations.withoutTransaction());
    }

    private void candidate(String environment) {
        when(orderPayments.findExpired("WOMPI", null, 50)).thenReturn(List.of(CANDIDATE_OF.apply(environment)));

    }

    @Test
    void expiredPaymentCancelsOrderWithTimeoutReasonReleasesStockAndExpiresPayment() {
        candidate("SANDBOX");
        when(wompiClient.findTransactionsByReference(REFERENCE)).thenReturn(List.of(
                new WompiTransaction("t-1", REFERENCE, "DECLINED", 100, "COP", null)));
        when(orderPayments.lockExpired(PAYMENT_ID))
                .thenReturn(Optional.of(new OrderPayments.ExpiredPayment(PAYMENT_ID, ORDER_ID)));
        when(orderPayments.cancelAwaitingOrder(ORDER_ID, "PAYMENT_TIMEOUT")).thenReturn(true);
        when(orderPayments.reservedLines(ORDER_ID)).thenReturn(List.of(
                new OrderPayments.ReservedLine(INVENTORY_A, 2), new OrderPayments.ReservedLine(INVENTORY_B, 1)));

        ExpireAwaitingPayments.Batch batch = job().expireBatch(50);

        assertThat(batch).isEqualTo(new ExpireAwaitingPayments.Batch(1, 1, false, CANDIDATE_OF.apply("SANDBOX")));
        verify(eventRouter, never()).apply(any(), any());
        InOrder order = inOrder(wompiClient, orderPayments, stockService);
        order.verify(wompiClient).findTransactionsByReference(REFERENCE);
        order.verify(orderPayments).lockExpired(PAYMENT_ID);
        order.verify(orderPayments).cancelAwaitingOrder(ORDER_ID, "PAYMENT_TIMEOUT");
        order.verify(stockService).returnStock(List.of(new StockReturn(INVENTORY_A, 2), new StockReturn(INVENTORY_B, 1)));
        order.verify(orderPayments).markExpired(eq(PAYMENT_ID), anyString());
    }

    @Test
    void approvedInWompiIsAppliedBeforeExpiringAndThePaymentNoLongerExpires() {
        candidate("SANDBOX");
        WompiTransaction approved = new WompiTransaction("t-1", REFERENCE, "APPROVED", 100, "COP", null);
        when(wompiClient.findTransactionsByReference(REFERENCE)).thenReturn(List.of(approved));
        // Ya aprobado por el router: la fila deja de estar abierta y no se vuelve a tomar.
        when(orderPayments.lockExpired(PAYMENT_ID)).thenReturn(Optional.empty());

        ExpireAwaitingPayments.Batch batch = job().expireBatch(50);

        assertThat(batch.expired()).isZero();
        verify(eventRouter).apply(approved, WompiEventSource.RECONCILIATION);
        verify(orderPayments, never()).cancelAwaitingOrder(any(), any());
        verify(orderPayments, never()).markExpired(any(), any());
        verify(stockService, never()).returnStock(any());
    }

    @Test
    void wompiUnavailableSkipsExpirationThisRound() {
        candidate("SANDBOX");
        when(wompiClient.findTransactionsByReference(REFERENCE)).thenThrow(new WompiUnavailableException("down"));

        ExpireAwaitingPayments.Batch batch = job().expireBatch(50);

        assertThat(batch).isEqualTo(new ExpireAwaitingPayments.Batch(1, 0, true, CANDIDATE_OF.apply("SANDBOX")));
        verify(orderPayments, never()).lockExpired(any());
        verify(orderPayments, never()).cancelAwaitingOrder(any(), any());
    }

    @Test
    void failureApplyingApprovalSkipsExpiration() {
        candidate("SANDBOX");
        WompiTransaction approved = new WompiTransaction("t-1", REFERENCE, "APPROVED", 100, "COP", null);
        when(wompiClient.findTransactionsByReference(REFERENCE)).thenReturn(List.of(approved));
        when(eventRouter.apply(approved, WompiEventSource.RECONCILIATION)).thenThrow(new IllegalStateException("db"));

        assertThat(job().expireBatch(50).expired()).isZero();
        verify(orderPayments, never()).lockExpired(any());
    }

    @Test
    void paymentOfAnotherEnvironmentExpiresWithoutLookup() {
        candidate("PRODUCTION");
        when(orderPayments.lockExpired(PAYMENT_ID))
                .thenReturn(Optional.of(new OrderPayments.ExpiredPayment(PAYMENT_ID, ORDER_ID)));
        when(orderPayments.cancelAwaitingOrder(ORDER_ID, "PAYMENT_TIMEOUT")).thenReturn(false);

        assertThat(job().expireBatch(50).expired()).isEqualTo(1);
        verify(wompiClient, never()).findTransactionsByReference(anyString());
        verify(stockService, never()).returnStock(any());
        verify(orderPayments).markExpired(eq(PAYMENT_ID), anyString());
    }

    @Test
    void nothingToExpire() {
        when(orderPayments.findExpired("WOMPI", null, 50)).thenReturn(List.of());

        assertThat(job().expireBatch(50)).isEqualTo(new ExpireAwaitingPayments.Batch(0, 0, false, null));
        verify(stockService, never()).returnStock(any());
    }
}
