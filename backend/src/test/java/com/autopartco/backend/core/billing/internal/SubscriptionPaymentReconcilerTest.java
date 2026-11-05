package com.autopartco.backend.core.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentReconcilerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

    @Mock
    private SubscriptionPaymentRepository paymentRepository;
    @Mock
    private WompiClient wompiClient;
    @Mock
    private WompiEventRouter eventRouter;

    private SubscriptionPaymentReconciler reconciler() {
        return new SubscriptionPaymentReconciler(paymentRepository, wompiClient, eventRouter,
                new WompiProperties(WompiEnvironment.PRODUCTION, null, null, null, null, null, null));
    }

    @Test
    void appliesFinalTransactionsOfPendingPaymentsInTheWindow() {
        when(paymentRepository.findPendingWompiReferences("PRODUCTION", NOW.minusHours(24), NOW.minusMinutes(2),
                PageRequest.of(0, 50))).thenReturn(List.of("APC-1"));
        WompiTransaction pending = new WompiTransaction("t-1", "APC-1", "PENDING", 100, "COP", null);
        WompiTransaction approved = new WompiTransaction("t-2", "APC-1", "APPROVED", 100, "COP", null);
        WompiTransaction foreign = new WompiTransaction("t-3", "APC-9", "APPROVED", 100, "COP", null);
        when(wompiClient.findTransactionsByReference("APC-1")).thenReturn(List.of(pending, approved, foreign));

        assertThat(reconciler().reconcile(NOW)).isEqualTo(1);

        verify(eventRouter).apply(approved, WompiEventSource.RECONCILIATION);
        verify(eventRouter, never()).apply(eq(pending), any());
        verify(eventRouter, never()).apply(eq(foreign), any());
    }

    @Test
    void stopsWhenWompiIsUnavailable() {
        when(paymentRepository.findPendingWompiReferences(any(), any(), any(), any()))
                .thenReturn(List.of("APC-1", "APC-2"));
        when(wompiClient.findTransactionsByReference("APC-1")).thenThrow(new WompiUnavailableException("down"));

        assertThat(reconciler().reconcile(NOW)).isZero();
        verify(wompiClient, never()).findTransactionsByReference("APC-2");
    }
}
