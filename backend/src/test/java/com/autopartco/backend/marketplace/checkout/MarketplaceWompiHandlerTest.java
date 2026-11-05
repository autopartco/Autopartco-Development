package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;

@ExtendWith(MockitoExtension.class)
class MarketplaceWompiHandlerTest {
    static final String REFERENCE = "APM-3f9a0c2b7d6e4f1a9b8c7d6e5f4a3b2c";
    static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-00000000aa01");
    static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000bb01");
    static final BigDecimal AMOUNT = new BigDecimal("230000.00");
    static final long CENTS = 23_000_000L;

    @Mock
    private OrderPayments orderPayments;

    private MarketplaceWompiHandler handler() {
        return new MarketplaceWompiHandler(orderPayments, settings(WompiEnvironment.SANDBOX));
    }

    static PaymentSettings settings(WompiEnvironment environment) {
        return new PaymentSettings("WOMPI", 30,
                new WompiProperties(environment, null, null, null, null, null, null));
    }

    static OrderPayment payment(String orderStatus, String status, String providerStatus, String transactionId) {
        return new OrderPayment(PAYMENT_ID, ORDER_ID, UUID.randomUUID(), 1042L, orderStatus, "WOMPI", REFERENCE,
                status, providerStatus, transactionId, AMOUNT, "COP", "SANDBOX",
                Instant.now().plusSeconds(600), null, false);
    }

    static WompiTransaction tx(String id, String status) {
        return new WompiTransaction(id, REFERENCE, status, CENTS, "COP", null);
    }

    private void locked(OrderPayment payment) {
        when(orderPayments.lockByReference("WOMPI", REFERENCE)).thenReturn(Optional.of(payment));
    }

    @Test
    void supportsOnlyMarketplaceReferences() {
        assertThat(handler().supports("APM-abc")).isTrue();
        assertThat(handler().supports("APC-abc")).isFalse();
        assertThat(handler().supports(null)).isFalse();
    }

    @Test
    void unknownReferenceIsReportedWithoutThrowing() {
        when(orderPayments.lockByReference("WOMPI", REFERENCE)).thenReturn(Optional.empty());

        assertThat(handler().apply(tx("t-1", "APPROVED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.UNKNOWN_REFERENCE);
    }

    @Test
    void approvedWithinTermApprovesPaymentAndConfirmsOrder() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));
        when(orderPayments.transactionLinkedToOtherPayment("WOMPI", "t-1", PAYMENT_ID)).thenReturn(false);
        when(orderPayments.confirmAwaitingOrder(ORDER_ID)).thenReturn(true);

        assertThat(handler().apply(tx("t-1", "APPROVED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.APPLIED);

        verify(orderPayments).markApproved(eq(PAYMENT_ID), eq("t-1"), eq("APPROVED"), eq(false), anyString());
        verify(orderPayments).confirmAwaitingOrder(ORDER_ID);
    }

    @Test
    void rejectedPaymentCanStillBeApprovedWithinTerm() {
        locked(payment("AWAITING_PAYMENT", "REJECTED", "DECLINED", "t-1"));
        when(orderPayments.transactionLinkedToOtherPayment("WOMPI", "t-2", PAYMENT_ID)).thenReturn(false);
        when(orderPayments.confirmAwaitingOrder(ORDER_ID)).thenReturn(true);

        assertThat(handler().apply(tx("t-2", "APPROVED"), WompiEventSource.RECONCILIATION))
                .isEqualTo(WompiEventOutcome.APPLIED);
        verify(orderPayments).markApproved(eq(PAYMENT_ID), eq("t-2"), eq("APPROVED"), eq(false), anyString());
    }

    @Test
    void replayOfAppliedApprovalIsIgnored() {
        locked(payment("CONFIRMED", "APPROVED", "APPROVED", "t-1"));

        assertThat(handler().apply(tx("t-1", "APPROVED"), WompiEventSource.REDIRECT_SYNC))
                .isEqualTo(WompiEventOutcome.IGNORED);
        verify(orderPayments, never()).markApproved(any(), any(), any(), anyBoolean(), any());
        verify(orderPayments, never()).confirmAwaitingOrder(any());
    }

    @Test
    void secondApprovedTransactionIsFlaggedForManualRefund() {
        locked(payment("CONFIRMED", "APPROVED", "APPROVED", "t-1"));

        assertThat(handler().apply(tx("t-2", "APPROVED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.REJECTED_MISMATCH);
        verify(orderPayments).flagManualRefund(PAYMENT_ID);
        verify(orderPayments, never()).markApproved(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void approvalAfterExpirationMarksManualRefundAndKeepsOrderCancelled() {
        locked(payment("CANCELLED", "EXPIRED", null, null));
        when(orderPayments.transactionLinkedToOtherPayment("WOMPI", "t-1", PAYMENT_ID)).thenReturn(false);

        assertThat(handler().apply(tx("t-1", "APPROVED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.APPLIED);
        verify(orderPayments).markApproved(eq(PAYMENT_ID), eq("t-1"), eq("APPROVED"), eq(true), anyString());
        verify(orderPayments, never()).confirmAwaitingOrder(any());
    }

    @Test
    void approvalOnCancelledOrderWithOpenPaymentAlsoRequiresManualRefund() {
        locked(payment("CANCELLED", "PENDING", null, null));
        when(orderPayments.transactionLinkedToOtherPayment("WOMPI", "t-1", PAYMENT_ID)).thenReturn(false);

        assertThat(handler().apply(tx("t-1", "APPROVED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.APPLIED);
        verify(orderPayments).markApproved(eq(PAYMENT_ID), eq("t-1"), eq("APPROVED"), eq(true), anyString());
        verify(orderPayments, never()).confirmAwaitingOrder(any());
    }

    @Test
    void lostOrderLockIsAnInternalError() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));
        when(orderPayments.confirmAwaitingOrder(ORDER_ID)).thenReturn(false);

        assertThatThrownBy(() -> handler().apply(tx("t-1", "APPROVED"), WompiEventSource.WEBHOOK))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void declinedRejectsPaymentAndKeepsOrderAwaiting() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));

        assertThat(handler().apply(tx("t-1", "DECLINED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.APPLIED);
        verify(orderPayments).markFailed(eq(PAYMENT_ID), eq("REJECTED"), eq("t-1"), eq("DECLINED"), anyString());
        verify(orderPayments, never()).confirmAwaitingOrder(any());
        verify(orderPayments, never()).cancelAwaitingOrder(any(), any());
    }

    @Test
    void errorRejectsAndVoidedCancelsPayment() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));
        assertThat(handler().apply(tx("t-1", "ERROR"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.APPLIED);
        verify(orderPayments).markFailed(eq(PAYMENT_ID), eq("REJECTED"), eq("t-1"), eq("ERROR"), anyString());

        locked(payment("AWAITING_PAYMENT", "REJECTED", "ERROR", "t-1"));
        assertThat(handler().apply(tx("t-2", "VOIDED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.APPLIED);
        verify(orderPayments).markFailed(eq(PAYMENT_ID), eq("CANCELLED"), eq("t-2"), eq("VOIDED"), anyString());
    }

    @Test
    void replayOfAppliedRejectionIsIgnored() {
        locked(payment("AWAITING_PAYMENT", "REJECTED", "DECLINED", "t-1"));

        assertThat(handler().apply(tx("t-1", "DECLINED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.IGNORED);
        verify(orderPayments, never()).markFailed(any(), any(), any(), any(), any());
    }

    @Test
    void declinedAfterApprovalOrExpirationDoesNotDowngrade() {
        locked(payment("CONFIRMED", "APPROVED", "APPROVED", "t-1"));
        assertThat(handler().apply(tx("t-2", "DECLINED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.IGNORED);

        locked(payment("CANCELLED", "EXPIRED", null, null));
        assertThat(handler().apply(tx("t-3", "DECLINED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.IGNORED);
        verify(orderPayments, never()).markFailed(any(), any(), any(), any(), any());
    }

    @Test
    void voidedAfterApprovalFlagsManualRefundWithoutTouchingTheOrder() {
        locked(payment("CONFIRMED", "APPROVED", "APPROVED", "t-1"));

        assertThat(handler().apply(tx("t-1", "VOIDED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.APPLIED);
        verify(orderPayments).flagManualRefund(PAYMENT_ID);
        verify(orderPayments, never()).markFailed(any(), any(), any(), any(), any());
        verify(orderPayments, never()).cancelAwaitingOrder(any(), any());
        verify(orderPayments, never()).confirmAwaitingOrder(any());
    }

    @Test
    void repeatedVoidedAfterApprovalOrVoidOfAnotherTransactionIsIgnored() {
        OrderPayment flagged = new OrderPayment(PAYMENT_ID, ORDER_ID, UUID.randomUUID(), 1042L, "CONFIRMED",
                "WOMPI", REFERENCE, "APPROVED", "APPROVED", "t-1", AMOUNT, "COP", "SANDBOX",
                Instant.now(), null, true);
        locked(flagged);
        assertThat(handler().apply(tx("t-1", "VOIDED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.IGNORED);

        locked(payment("CONFIRMED", "APPROVED", "APPROVED", "t-1"));
        assertThat(handler().apply(tx("t-9", "VOIDED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.IGNORED);
        verify(orderPayments, never()).flagManualRefund(any());
    }

    @Test
    void pendingTransactionChangesNothing() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));

        assertThat(handler().apply(tx("t-1", "PENDING"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.IGNORED);
        verify(orderPayments, never()).markFailed(any(), any(), any(), any(), any());
        verify(orderPayments, never()).markApproved(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void amountMismatchInCentsIsRejected() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));

        assertThat(handler().apply(new WompiTransaction("t-1", REFERENCE, "APPROVED", CENTS - 1, "COP", null),
                WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.REJECTED_MISMATCH);
        verify(orderPayments, never()).markApproved(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void currencyMismatchIsRejected() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));

        assertThat(handler().apply(new WompiTransaction("t-1", REFERENCE, "APPROVED", CENTS, "USD", null),
                WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.REJECTED_MISMATCH);
    }

    @Test
    void paymentOfAnotherEnvironmentIsRejected() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));
        MarketplaceWompiHandler production =
                new MarketplaceWompiHandler(orderPayments, settings(WompiEnvironment.PRODUCTION));

        assertThat(production.apply(tx("t-1", "APPROVED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.REJECTED_MISMATCH);
        verify(orderPayments, never()).markApproved(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void transactionLinkedToAnotherPaymentIsRejected() {
        locked(payment("AWAITING_PAYMENT", "PENDING", null, null));
        when(orderPayments.transactionLinkedToOtherPayment("WOMPI", "t-1", PAYMENT_ID)).thenReturn(true);

        assertThat(handler().apply(tx("t-1", "APPROVED"), WompiEventSource.WEBHOOK))
                .isEqualTo(WompiEventOutcome.REJECTED_MISMATCH);
        verify(orderPayments, never()).markApproved(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void neverReturnsDuplicate() {
        for (String status : new String[] {"APPROVED", "DECLINED", "ERROR", "VOIDED", "PENDING", "WEIRD"}) {
            locked(payment("CONFIRMED", "APPROVED", "APPROVED", "t-1"));
            assertThat(handler().apply(tx("t-1", status), WompiEventSource.WEBHOOK))
                    .isNotEqualTo(WompiEventOutcome.DUPLICATE);
        }
    }
}
