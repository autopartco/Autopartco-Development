package com.autopartco.backend.core.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.payments.PaymentSyncRateLimiter;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentStatusTest {
    private static final String EMAIL = "owner@example.com";
    private static final String REFERENCE = "APC-1";

    @Mock
    private StoreAccess storeAccess;
    @Mock
    private Subscriptions subscriptions;
    @Mock
    private WompiClient wompiClient;
    @Mock
    private WompiEventRouter eventRouter;
    @Mock
    private PaymentSyncRateLimiter rateLimiter;

    @InjectMocks
    private SubscriptionPaymentStatus status;

    private final UUID storeId = UUID.randomUUID();

    private static SubscriptionPaymentView view(String provider, String paymentStatus) {
        return new SubscriptionPaymentView(UUID.randomUUID(), REFERENCE, provider, "PRO", "Pro", 3, 270000, "COP",
                paymentStatus, LocalDateTime.now(), null, null, null);
    }

    private static WompiTransaction tx(String reference) {
        return new WompiTransaction("tx-1", reference, "APPROVED", 27_000_000L, "COP", null);
    }

    @Test
    void getRequiresOwner() {
        doThrow(new AccessDeniedException("owner")).when(storeAccess).requireOwner(EMAIL, storeId);

        assertThrows(AccessDeniedException.class, () -> status.get(storeId, EMAIL, REFERENCE));
        verifyNoInteractions(subscriptions);
    }

    @Test
    void syncAppliesTransactionOfThisReference() {
        SubscriptionPaymentView pending = view("WOMPI", "PENDING");
        SubscriptionPaymentView approved = view("WOMPI", "APPROVED");
        when(subscriptions.paymentView(storeId, REFERENCE)).thenReturn(pending, approved);
        when(wompiClient.findTransaction("tx-1")).thenReturn(Optional.of(tx(REFERENCE)));
        when(eventRouter.apply(any(), any())).thenReturn(WompiEventOutcome.APPLIED);

        SubscriptionPaymentView result = status.sync(storeId, EMAIL, REFERENCE, "tx-1");

        assertSame(approved, result);
        verify(storeAccess).requireOwner(EMAIL, storeId);
        verify(rateLimiter).acquire(EMAIL);
        verify(eventRouter).apply(tx(REFERENCE), WompiEventSource.REDIRECT_SYNC);
    }

    @Test
    void syncRejectsTransactionOfAnotherReference() {
        when(subscriptions.paymentView(storeId, REFERENCE)).thenReturn(view("WOMPI", "PENDING"));
        when(wompiClient.findTransaction("tx-1")).thenReturn(Optional.of(tx("APC-other")));

        ConflictException error = assertThrows(ConflictException.class,
                () -> status.sync(storeId, EMAIL, REFERENCE, "tx-1"));
        assertEquals("PAYMENT_REFERENCE_MISMATCH", error.getCode());
        verify(eventRouter, never()).apply(any(), any());
    }

    @Test
    void syncReportsUnknownTransaction() {
        when(subscriptions.paymentView(storeId, REFERENCE)).thenReturn(view("WOMPI", "PENDING"));
        when(wompiClient.findTransaction("tx-1")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> status.sync(storeId, EMAIL, REFERENCE, "tx-1"));
        assertEquals("PAYMENT_TRANSACTION_NOT_FOUND", error.getCode());
    }

    @Test
    void syncReturnsCurrentStateWhenWompiIsUnavailable() {
        SubscriptionPaymentView pending = view("WOMPI", "PENDING");
        when(subscriptions.paymentView(storeId, REFERENCE)).thenReturn(pending);
        when(wompiClient.findTransaction("tx-1")).thenThrow(new WompiUnavailableException("down"));

        assertSame(pending, status.sync(storeId, EMAIL, REFERENCE, "tx-1"));
        verify(eventRouter, never()).apply(any(), any());
    }

    @Test
    void syncSkipsWompiForApprovedOrSimulatedPayments() {
        when(subscriptions.paymentView(storeId, REFERENCE))
                .thenReturn(view("WOMPI", "APPROVED"), view("SIMULATED", "APPROVED"));

        status.sync(storeId, EMAIL, REFERENCE, "tx-1");
        status.sync(storeId, EMAIL, REFERENCE, "tx-1");

        verifyNoInteractions(wompiClient, eventRouter);
    }

    @Test
    void syncIsRateLimitedBeforeTouchingWompi() {
        doThrow(new TooManyRequestsException("PAYMENT_SYNC_RATE_LIMITED", "slow down"))
                .when(rateLimiter).acquire(EMAIL);

        assertThrows(TooManyRequestsException.class, () -> status.sync(storeId, EMAIL, REFERENCE, "tx-1"));
        verifyNoInteractions(wompiClient, subscriptions);
    }
}
