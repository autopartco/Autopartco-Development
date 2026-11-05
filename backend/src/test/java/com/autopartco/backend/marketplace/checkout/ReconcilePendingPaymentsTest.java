package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

@ExtendWith(MockitoExtension.class)
class ReconcilePendingPaymentsTest {
    @Mock
    private OrderPayments orderPayments;
    @Mock
    private WompiClient wompiClient;
    @Mock
    private WompiEventRouter eventRouter;

    private ReconcilePendingPayments job() {
        return new ReconcilePendingPayments(orderPayments, wompiClient, eventRouter,
                MarketplaceWompiHandlerTest.settings(WompiEnvironment.SANDBOX),
                TransactionOperations.withoutTransaction());
    }

    private static WompiTransaction tx(String id, String reference, String status) {
        return new WompiTransaction(id, reference, status, 100, "COP", null);
    }

    @Test
    void appliesOnlyNewFinalTransactionsOfTheSameReference() {
        when(orderPayments.lockReconciliationCandidates("WOMPI", "SANDBOX", 120, 50)).thenReturn(List.of(
                new OrderPayments.ReconciliationCandidate("APM-1", "t-1", "DECLINED")));
        WompiTransaction alreadyApplied = tx("t-1", "APM-1", "DECLINED");
        WompiTransaction pending = tx("t-2", "APM-1", "PENDING");
        WompiTransaction otherReference = tx("t-3", "APM-2", "APPROVED");
        WompiTransaction approved = tx("t-4", "APM-1", "APPROVED");
        when(wompiClient.findTransactionsByReference("APM-1"))
                .thenReturn(List.of(alreadyApplied, pending, otherReference, approved));

        assertThat(job().reconcile(50)).isEqualTo(1);

        verify(eventRouter).apply(approved, WompiEventSource.RECONCILIATION);
        verify(eventRouter, never()).apply(alreadyApplied, WompiEventSource.RECONCILIATION);
        verify(eventRouter, never()).apply(pending, WompiEventSource.RECONCILIATION);
        verify(eventRouter, never()).apply(otherReference, WompiEventSource.RECONCILIATION);
    }

    @Test
    void stopsWhenWompiIsUnavailable() {
        when(orderPayments.lockReconciliationCandidates("WOMPI", "SANDBOX", 120, 50)).thenReturn(List.of(
                new OrderPayments.ReconciliationCandidate("APM-1", null, null),
                new OrderPayments.ReconciliationCandidate("APM-2", null, null)));
        when(wompiClient.findTransactionsByReference("APM-1")).thenThrow(new WompiUnavailableException("down"));

        assertThat(job().reconcile(50)).isZero();
        verify(wompiClient, never()).findTransactionsByReference("APM-2");
        verify(eventRouter, never()).apply(any(), any());
    }

    @Test
    void routerFailureOnOneTransactionDoesNotStopTheRound() {
        when(orderPayments.lockReconciliationCandidates("WOMPI", "SANDBOX", 120, 50)).thenReturn(List.of(
                new OrderPayments.ReconciliationCandidate("APM-1", null, null),
                new OrderPayments.ReconciliationCandidate("APM-2", null, null)));
        WompiTransaction first = tx("t-1", "APM-1", "APPROVED");
        WompiTransaction second = tx("t-2", "APM-2", "DECLINED");
        when(wompiClient.findTransactionsByReference("APM-1")).thenReturn(List.of(first));
        when(wompiClient.findTransactionsByReference("APM-2")).thenReturn(List.of(second));
        when(eventRouter.apply(first, WompiEventSource.RECONCILIATION)).thenThrow(new IllegalStateException("db"));

        assertThat(job().reconcile(50)).isEqualTo(1);
        verify(eventRouter).apply(second, WompiEventSource.RECONCILIATION);
    }
}
