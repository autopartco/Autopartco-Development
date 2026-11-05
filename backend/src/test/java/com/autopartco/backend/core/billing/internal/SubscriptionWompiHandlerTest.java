package com.autopartco.backend.core.billing.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;

@ExtendWith(MockitoExtension.class)
class SubscriptionWompiHandlerTest {
    private static final String REFERENCE = "APC-1";

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private StoreSubscriptionRepository applicationRepository;

    @Mock
    private SubscriptionEventRepository eventRepository;

    private SubscriptionWompiHandler handler() {
        return new SubscriptionWompiHandler(
                paymentRepository,
                applicationRepository,
                new SubscriptionEvents(eventRepository),
                new WompiProperties(WompiEnvironment.SANDBOX, null, null, null, null, null, null)
        );
    }

    private static SubscriptionPayment pending(UUID applicationId, long amountInCents) {
        return SubscriptionPayment.builder()
                .storeApplicationId(applicationId)
                .plan(Plan.builder().code("BUSINESS").build())
                .reference(REFERENCE)
                .amountInCents(amountInCents)
                .prepaidMonths(3)
                .currency("COP")
                .status("PENDING")
                .provider("WOMPI")
                .environment("SANDBOX")
                .build();
    }

    private static WompiTransaction tx(String id, String status, long amountInCents, String currency) {
        return new WompiTransaction(id, REFERENCE, status, amountInCents, currency, null);
    }

    @Test
    void supportsOnlySubscriptionReferences() {
        assertTrue(handler().supports("APC-abc"));
        assertFalse(handler().supports("APM-abc"));
        assertFalse(handler().supports(null));
    }

    @Test
    void unknownReferenceIsReportedWithoutThrowing() {
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.empty());

        assertEquals(WompiEventOutcome.UNKNOWN_REFERENCE,
                handler().apply(tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
    }

    @Test
    void approvedPaymentExtendsPaidThroughFromTrialEndAndSetsPlan() {
        UUID applicationId = UUID.randomUUID();
        SubscriptionPayment payment = pending(applicationId, 10000);
        LocalDateTime trialEndsAt = LocalDateTime.now().plusDays(10);
        StoreSubscription subscription = StoreSubscription.builder()
                .id(applicationId)
                .trialEndsAt(trialEndsAt)
                .build();
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));
        when(paymentRepository.existsByProviderTransactionId("transaction-1")).thenReturn(false);
        when(applicationRepository.lockById(applicationId)).thenReturn(Optional.of(subscription));

        WompiEventOutcome outcome = handler().apply(
                tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK);

        assertEquals(WompiEventOutcome.APPLIED, outcome);
        assertEquals("APPROVED", payment.getStatus());
        assertEquals("transaction-1", payment.getProviderTransactionId());
        assertEquals(payment.getPlan(), subscription.getPlan());
        assertEquals(trialEndsAt.plusMonths(3), subscription.getPaidThrough());
        assertEquals(trialEndsAt, payment.getPeriodStartsAt());
        verify(eventRepository).save(any());
    }

    @Test
    void replayOfAlreadyApprovedTransactionIsIgnored() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        payment.setStatus("APPROVED");
        payment.setProviderTransactionId("transaction-1");
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.IGNORED,
                handler().apply(tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.REDIRECT_SYNC));
        verify(applicationRepository, never()).lockById(payment.getStoreApplicationId());
    }

    @Test
    void secondApprovedTransactionOnPaidReferenceIsFlaggedForManualRefund() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        payment.setStatus("APPROVED");
        payment.setProviderTransactionId("transaction-1");
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.REJECTED_MISMATCH,
                handler().apply(tx("transaction-2", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertTrue(payment.isRequiresManualRefund());
        assertEquals("transaction-1", payment.getProviderTransactionId());
        verify(applicationRepository, never()).lockById(payment.getStoreApplicationId());
    }

    @Test
    void amountMismatchIsRejectedWithoutChanges() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 20000);
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.REJECTED_MISMATCH,
                handler().apply(tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertEquals("PENDING", payment.getStatus());
        assertNull(payment.getProviderTransactionId());
    }

    @Test
    void currencyMismatchIsRejected() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.REJECTED_MISMATCH,
                handler().apply(tx("transaction-1", "APPROVED", 10000, "USD"), WompiEventSource.WEBHOOK));
        assertEquals("PENDING", payment.getStatus());
    }

    @Test
    void paymentFromAnotherEnvironmentIsRejected() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        payment.setEnvironment("PRODUCTION");
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.REJECTED_MISMATCH,
                handler().apply(tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertEquals("PENDING", payment.getStatus());
    }

    @Test
    void transactionAlreadyLinkedToAnotherPaymentIsRejected() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));
        when(paymentRepository.existsByProviderTransactionId("transaction-1")).thenReturn(true);

        assertEquals(WompiEventOutcome.REJECTED_MISMATCH,
                handler().apply(tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertEquals("PENDING", payment.getStatus());
    }

    @Test
    void declinedStoresRawStatusAndCanBeApprovedLater() {
        UUID applicationId = UUID.randomUUID();
        SubscriptionPayment payment = pending(applicationId, 10000);
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));
        when(applicationRepository.lockById(applicationId))
                .thenReturn(Optional.of(StoreSubscription.builder().id(applicationId).build()));

        assertEquals(WompiEventOutcome.APPLIED,
                handler().apply(tx("transaction-1", "DECLINED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertEquals("DECLINED", payment.getStatus());
        assertEquals(WompiEventOutcome.IGNORED,
                handler().apply(tx("transaction-1", "DECLINED", 10000, "COP"), WompiEventSource.WEBHOOK));

        assertEquals(WompiEventOutcome.APPLIED,
                handler().apply(tx("transaction-2", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertEquals("APPROVED", payment.getStatus());
        assertEquals("transaction-2", payment.getProviderTransactionId());
    }

    @Test
    void declinedAfterApprovalDoesNotDowngrade() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        payment.setStatus("APPROVED");
        payment.setProviderTransactionId("transaction-1");
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.IGNORED,
                handler().apply(tx("transaction-2", "VOIDED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertEquals("APPROVED", payment.getStatus());
    }

    @Test
    void voidedOfTheApprovedTransactionFlagsManualRefundAndKeepsTheSubscription() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        payment.setStatus("APPROVED");
        payment.setProviderTransactionId("transaction-1");
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.APPLIED,
                handler().apply(tx("transaction-1", "VOIDED", 10000, "COP"), WompiEventSource.WEBHOOK));
        assertTrue(payment.isRequiresManualRefund());
        assertEquals("APPROVED", payment.getStatus());
        verify(applicationRepository, never()).lockById(payment.getStoreApplicationId());

        assertEquals(WompiEventOutcome.IGNORED,
                handler().apply(tx("transaction-1", "VOIDED", 10000, "COP"), WompiEventSource.WEBHOOK));
    }

    @Test
    void pendingIsIgnored() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));

        assertEquals(WompiEventOutcome.IGNORED,
                handler().apply(tx("transaction-1", "PENDING", 10000, "COP"), WompiEventSource.RECONCILIATION));
        assertEquals("PENDING", payment.getStatus());
    }

    @Test
    void missingSubscriptionRowIsAnInternalError() {
        SubscriptionPayment payment = pending(UUID.randomUUID(), 10000);
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));
        when(applicationRepository.lockById(payment.getStoreApplicationId())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> handler().apply(tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
    }

    @Test
    void legacyPaymentWithoutEnvironmentIsAccepted() {
        UUID applicationId = UUID.randomUUID();
        SubscriptionPayment payment = pending(applicationId, 10000);
        payment.setEnvironment(null);
        when(paymentRepository.lockByReference(REFERENCE)).thenReturn(Optional.of(payment));
        when(applicationRepository.lockById(applicationId))
                .thenReturn(Optional.of(StoreSubscription.builder().id(applicationId).build()));

        assertEquals(WompiEventOutcome.APPLIED,
                handler().apply(tx("transaction-1", "APPROVED", 10000, "COP"), WompiEventSource.WEBHOOK));
    }
}
