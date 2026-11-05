package com.autopartco.backend.core.payments.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiTransactionHandler;

/** Enrutamiento por prefijo y registro desde las fuentes de API (sync y conciliación). */
@ExtendWith(MockitoExtension.class)
class WompiDispatcherTest {
    private static final WompiProperties PROPERTIES = new WompiProperties(WompiEnvironment.PRODUCTION,
            "pub_prod_x", "prv_prod_x", "i", "e", "https://checkout.wompi.co/p/", "https://app.example/ok");

    @Mock
    private PaymentProviderEventLog eventLog;

    @Mock
    private WompiTransactionHandler subscriptions;

    @Mock
    private WompiTransactionHandler marketplace;

    private WompiEventRouter router() {
        return new WompiEventRouter(new WompiDispatcher(List.of(subscriptions, marketplace), eventLog, PROPERTIES,
                TransactionOperations.withoutTransaction()));
    }

    private static WompiTransaction tx(String reference) {
        return new WompiTransaction("tx-1", reference, "APPROVED", 23_000_000L, "COP", null);
    }

    @Test
    void routesByPrefixToTheHandlerThatSupportsIt() {
        when(subscriptions.supports("APM-1")).thenReturn(false);
        when(marketplace.supports("APM-1")).thenReturn(true);
        when(marketplace.apply(any(), any())).thenReturn(WompiEventOutcome.APPLIED);
        when(eventLog.insert(any())).thenReturn(true);

        WompiEventOutcome outcome = router().apply(tx("APM-1"), WompiEventSource.REDIRECT_SYNC);

        assertEquals(WompiEventOutcome.APPLIED, outcome);
        verify(marketplace).apply(tx("APM-1"), WompiEventSource.REDIRECT_SYNC);
        verify(subscriptions, never()).apply(any(), any());
        ArgumentCaptor<PaymentProviderEventLog.Entry> entry = ArgumentCaptor.forClass(PaymentProviderEventLog.Entry.class);
        verify(eventLog).insert(entry.capture());
        assertNull(entry.getValue().checksum());
        assertEquals(WompiEventSource.REDIRECT_SYNC, entry.getValue().source());
        assertEquals(WompiEnvironment.PRODUCTION, entry.getValue().environment());
        assertEquals(23_000_000L, entry.getValue().amountInCents());
    }

    @Test
    void noHandlerMeansUnknownReference() {
        when(eventLog.insert(any())).thenReturn(true);

        assertEquals(WompiEventOutcome.UNKNOWN_REFERENCE,
                router().apply(tx("OTHER-1"), WompiEventSource.RECONCILIATION));
    }

    @Test
    void webhookSourceIsNotAcceptedThroughThePublicRouter() {
        assertThrows(IllegalArgumentException.class, () -> router().apply(tx("APC-1"), WompiEventSource.WEBHOOK));
    }

    private static final String FULL_TRANSACTION = """
            {"id": "tx-1", "reference": "APM-1", "status": "APPROVED", "status_message": null,
             "amount_in_cents": 23000000, "currency": "COP", "payment_method_type": "CARD",
             "created_at": "2026-11-04T15:00:00.000Z", "finalized_at": "2026-11-04T15:01:00.000Z",
             "customer_email": "ana@example.com",
             "customer_data": {"full_name": "Ana Pérez", "phone_number": "+573001234567",
                               "legal_id": "1020304050", "legal_id_type": "CC"},
             "shipping_address": {"address_line_1": "Calle 10 # 20-30", "city": "Bogotá"},
             "payment_method": {"type": "CARD", "extra": {"bin": "424242", "last_four": "4242",
                                                          "name": "VISA-4242"}},
             "billing_data": {"legal_id": "1020304050"},
             "redirect_url": "https://front.example/pedido/1/resultado"}
            """;

    private static final List<String> FORBIDDEN = List.of("ana@example.com", "customer_email", "customer_data",
            "legal_id", "1020304050", "shipping_address", "Calle 10", "payment_method\"", "extra", "4242",
            "billing_data", "redirect_url", "phone_number");

    @Test
    void apiTransactionPayloadKeepsOnlyWhitelistedFields() throws Exception {
        com.fasterxml.jackson.databind.JsonNode raw = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(FULL_TRANSACTION);
        WompiTransaction transaction = new WompiTransaction("tx-1", "APM-1", "APPROVED", 23_000_000L, "COP", raw);
        when(marketplace.supports("APM-1")).thenReturn(true);
        when(marketplace.apply(any(), any())).thenReturn(WompiEventOutcome.APPLIED);
        when(eventLog.insert(any())).thenReturn(true);

        router().apply(transaction, WompiEventSource.RECONCILIATION);

        ArgumentCaptor<PaymentProviderEventLog.Entry> entry = ArgumentCaptor.forClass(PaymentProviderEventLog.Entry.class);
        verify(eventLog).insert(entry.capture());
        com.fasterxml.jackson.databind.JsonNode stored = entry.getValue().payload().path("transaction");
        assertEquals(java.util.Set.of("id", "reference", "status", "status_message", "amount_in_cents", "currency",
                "payment_method_type", "created_at", "finalized_at"), fieldNames(stored));
        String json = entry.getValue().payload().toString();
        FORBIDDEN.forEach(value -> assertEquals(false, json.contains(value), value));
    }

    @Test
    void webhookPayloadKeepsOnlyWhitelistedFields() throws Exception {
        com.fasterxml.jackson.databind.JsonNode event = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"event": "transaction.updated", "environment": "prod", "timestamp": 1530291411,
                 "sent_at": "2018-07-20T16:45:05.000Z",
                 "signature": {"properties": ["transaction.id"], "checksum": "abc", "other": "x"},
                 "data": {"transaction": %s, "extra_root": {"customer_email": "ana@example.com"}}}
                """.formatted(FULL_TRANSACTION));

        com.fasterxml.jackson.databind.JsonNode stored = WompiDispatcher.safePayload(new WompiDispatcher.Dispatch(
                null, WompiEventSource.WEBHOOK, "abc", "transaction.updated", event, true));

        assertEquals(java.util.Set.of("event", "environment", "timestamp", "signature", "data"), fieldNames(stored));
        assertEquals(java.util.Set.of("properties", "checksum"), fieldNames(stored.path("signature")));
        assertEquals(java.util.Set.of("transaction"), fieldNames(stored.path("data")));
        String json = stored.toString();
        FORBIDDEN.forEach(value -> assertEquals(false, json.contains(value), value));
    }

    @Test
    void ignoredRedirectSyncIsNotRecorded() {
        when(marketplace.supports("APM-1")).thenReturn(true);
        when(marketplace.apply(any(), any())).thenReturn(WompiEventOutcome.IGNORED);

        assertEquals(WompiEventOutcome.IGNORED, router().apply(tx("APM-1"), WompiEventSource.REDIRECT_SYNC));
        verify(eventLog, never()).insert(any());
    }

    @Test
    void ignoredReconciliationIsStillRecorded() {
        when(marketplace.supports("APM-1")).thenReturn(true);
        when(marketplace.apply(any(), any())).thenReturn(WompiEventOutcome.IGNORED);
        when(eventLog.insert(any())).thenReturn(true);

        router().apply(tx("APM-1"), WompiEventSource.RECONCILIATION);
        verify(eventLog).insert(any());
    }

    private static java.util.Set<String> fieldNames(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void handlerMustNotReturnDuplicate() {
        when(subscriptions.supports("APC-1")).thenReturn(true);
        when(subscriptions.apply(any(), any())).thenReturn(WompiEventOutcome.DUPLICATE);

        assertThrows(IllegalStateException.class, () -> router().apply(tx("APC-1"), WompiEventSource.REDIRECT_SYNC));
        verify(eventLog, never()).insert(any());
    }
}
