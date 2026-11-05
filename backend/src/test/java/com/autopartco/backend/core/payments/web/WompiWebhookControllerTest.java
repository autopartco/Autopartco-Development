package com.autopartco.backend.core.payments.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiTransactionHandler;
import com.autopartco.backend.core.payments.internal.PaymentProviderEventLog;
import com.autopartco.backend.core.payments.internal.WompiDispatcher;
import com.autopartco.backend.core.payments.internal.WompiSignatureVerifier;
import com.autopartco.backend.core.payments.internal.WompiWebhookIngest;
import com.autopartco.backend.shared.security.TokenHashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Política de respuesta del webhook (ADR 0004 §2.7) con firma, ingesta y despacho reales; el registro de eventos y el
 * handler son simulados.
 */
@ExtendWith(MockitoExtension.class)
class WompiWebhookControllerTest {
    private static final String SECRET = "test-events-secret";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private PaymentProviderEventLog eventLog;

    @Mock
    private WompiTransactionHandler subscriptionHandler;

    private final AtomicBoolean rolledBack = new AtomicBoolean(false);
    private WompiWebhookController controller;

    @BeforeEach
    void setUp() {
        WompiProperties properties = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_x", "prv_test_x",
                "integrity", SECRET, "https://checkout.wompi.co/p/", "https://app.example/ok");
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                TransactionStatus status = new SimpleTransactionStatus();
                T result = action.doInTransaction(status);
                rolledBack.set(status.isRollbackOnly());
                return result;
            }
        };
        WompiDispatcher dispatcher = new WompiDispatcher(List.of(subscriptionHandler), eventLog, properties,
                transactions);
        controller = new WompiWebhookController(new WompiWebhookIngest(
                new WompiSignatureVerifier(properties), eventLog, dispatcher, properties));
    }

    private static JsonNode event(String reference, String status, String environment, String secret)
            throws Exception {
        String checksum = TokenHashing.sha256Hex("transaction-1" + status + 10000 + "1730733600" + secret);
        return MAPPER.readTree("""
                {
                  "event": "transaction.updated",
                  "environment": "%s",
                  "data": {"transaction": {
                    "id": "transaction-1", "reference": "%s", "status": "%s",
                    "amount_in_cents": 10000, "currency": "COP", "customer_email": "buyer@example.com"
                  }},
                  "timestamp": 1730733600,
                  "signature": {
                    "properties": ["transaction.id", "transaction.status", "transaction.amount_in_cents"],
                    "checksum": "%s"
                  }
                }
                """.formatted(environment, reference, status, checksum));
    }

    private PaymentProviderEventLog.Entry insertedEntry() {
        ArgumentCaptor<PaymentProviderEventLog.Entry> entry = ArgumentCaptor.forClass(PaymentProviderEventLog.Entry.class);
        verify(eventLog).insert(entry.capture());
        return entry.getValue();
    }

    @Test
    void invalidSignatureAnswers401AndStoresNothing() throws Exception {
        ResponseEntity<Void> response = controller.webhook(event("APC-1", "APPROVED", "test", "wrong-secret"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(eventLog, subscriptionHandler);
    }

    @Test
    void appliedEventAnswers200AndIsRecorded() throws Exception {
        when(subscriptionHandler.supports("APC-1")).thenReturn(true);
        when(subscriptionHandler.apply(any(), any())).thenReturn(WompiEventOutcome.APPLIED);
        when(eventLog.insert(any())).thenReturn(true);

        ResponseEntity<Void> response = controller.webhook(event("APC-1", "APPROVED", "test", SECRET));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<WompiTransaction> tx = ArgumentCaptor.forClass(WompiTransaction.class);
        verify(subscriptionHandler).apply(tx.capture(), org.mockito.ArgumentMatchers.eq(WompiEventSource.WEBHOOK));
        assertEquals("transaction-1", tx.getValue().id());
        assertEquals(10000, tx.getValue().amountInCents());
        PaymentProviderEventLog.Entry entry = insertedEntry();
        assertEquals(WompiEventOutcome.APPLIED, entry.outcome());
        assertEquals(WompiEventSource.WEBHOOK, entry.source());
        assertEquals(WompiEnvironment.SANDBOX, entry.environment());
        assertEquals("transaction.updated", entry.eventType());
        assertEquals(TokenHashing.sha256Hex("transaction-1APPROVED10000" + "1730733600" + SECRET), entry.checksum());
        assertEquals("APC-1", entry.reference());
        assertEquals("APPROVED", entry.providerStatus());
        assertTrue(!rolledBack.get());
    }

    @Test
    void alreadyRecordedChecksumAnswers200WithoutEffects() throws Exception {
        when(eventLog.existsByChecksum(anyString())).thenReturn(true);

        ResponseEntity<Void> response = controller.webhook(event("APC-1", "APPROVED", "test", SECRET));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verifyNoInteractions(subscriptionHandler);
        verify(eventLog, never()).insert(any());
    }

    @Test
    void concurrentDuplicateRollsBackHandlerEffects() throws Exception {
        when(subscriptionHandler.supports("APC-1")).thenReturn(true);
        when(subscriptionHandler.apply(any(), any())).thenReturn(WompiEventOutcome.APPLIED);
        when(eventLog.insert(any())).thenReturn(false);

        ResponseEntity<Void> response = controller.webhook(event("APC-1", "APPROVED", "test", SECRET));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(rolledBack.get());
    }

    @Test
    void unknownPrefixAnswers200AndRecordsUnknownReference() throws Exception {
        when(subscriptionHandler.supports("XYZ-1")).thenReturn(false);
        when(eventLog.insert(any())).thenReturn(true);

        ResponseEntity<Void> response = controller.webhook(event("XYZ-1", "APPROVED", "test", SECRET));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(WompiEventOutcome.UNKNOWN_REFERENCE, insertedEntry().outcome());
        verify(subscriptionHandler, never()).apply(any(), any());
    }

    @Test
    void handlerUnknownReferenceAnswers200AndIsRecorded() throws Exception {
        when(subscriptionHandler.supports("APC-404")).thenReturn(true);
        when(subscriptionHandler.apply(any(), any())).thenReturn(WompiEventOutcome.UNKNOWN_REFERENCE);
        when(eventLog.insert(any())).thenReturn(true);

        assertEquals(HttpStatus.OK, controller.webhook(event("APC-404", "APPROVED", "test", SECRET)).getStatusCode());
        assertEquals(WompiEventOutcome.UNKNOWN_REFERENCE, insertedEntry().outcome());
    }

    @Test
    void eventFromAnotherEnvironmentIsRecordedAsMismatchAndNotApplied() throws Exception {
        when(eventLog.insert(any())).thenReturn(true);

        ResponseEntity<Void> response = controller.webhook(event("APC-1", "APPROVED", "prod", SECRET));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(WompiEventOutcome.REJECTED_MISMATCH, insertedEntry().outcome());
        verifyNoInteractions(subscriptionHandler);
    }

    @Test
    void signedEventWithoutTransactionIsRecordedAsIgnored() throws Exception {
        String checksum = TokenHashing.sha256Hex("tok_1" + "1730733600" + SECRET);
        JsonNode event = MAPPER.readTree("""
                {"event": "nequi_token.updated", "environment": "test",
                 "data": {"nequi_token": {"id": "tok_1"}}, "timestamp": 1730733600,
                 "signature": {"properties": ["nequi_token.id"], "checksum": "%s"}}
                """.formatted(checksum));
        when(eventLog.insert(any())).thenReturn(true);

        assertEquals(HttpStatus.OK, controller.webhook(event).getStatusCode());
        PaymentProviderEventLog.Entry entry = insertedEntry();
        assertEquals(WompiEventOutcome.IGNORED, entry.outcome());
        assertNull(entry.reference());
        verifyNoInteractions(subscriptionHandler);
    }

    @Test
    void internalErrorAnswers500AndRecordsNothing() throws Exception {
        when(subscriptionHandler.supports("APC-1")).thenReturn(true);
        when(subscriptionHandler.apply(any(), any())).thenThrow(new IllegalStateException("db down"));

        ResponseEntity<Void> response = controller.webhook(event("APC-1", "APPROVED", "test", SECRET));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(eventLog, never()).insert(any());
    }
}
