package com.autopartco.backend.core.payments.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiTransactionHandler;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Núcleo del enrutamiento (lo usan {@code WompiEventRouter} y el webhook). Despacha una transacción de Wompi al
 * {@link WompiTransactionHandler} que reclama su referencia y registra el resultado en
 * {@code payment_provider_events}, todo en una transacción. Si el registro choca con un checksum ya guardado (carrera
 * entre dos entregas del mismo evento), revierte los efectos del handler y devuelve
 * {@link WompiEventOutcome#DUPLICATE}.
 */
@Service
@Slf4j
public class WompiDispatcher {
    private final List<WompiTransactionHandler> handlers;
    private final PaymentProviderEventLog eventLog;
    private final WompiProperties properties;
    private final TransactionOperations transactions;

    @Autowired
    public WompiDispatcher(
            List<WompiTransactionHandler> handlers,
            PaymentProviderEventLog eventLog,
            WompiProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this(handlers, eventLog, properties, new TransactionTemplate(transactionManager));
    }

    /** Para pruebas: {@code TransactionOperations.withoutTransaction()}. */
    public WompiDispatcher(
            List<WompiTransactionHandler> handlers,
            PaymentProviderEventLog eventLog,
            WompiProperties properties,
            TransactionOperations transactions
    ) {
        this.handlers = List.copyOf(handlers);
        this.eventLog = eventLog;
        this.properties = properties;
        this.transactions = transactions;
    }

    /**
     * {@code transaction} {@code null}: evento firmado pero ilegible o de otro tipo; se registra como
     * {@link WompiEventOutcome#IGNORED}. Llamar fuera de cualquier transacción (abre la suya).
     */
    public WompiEventOutcome dispatch(Dispatch dispatch) {
        WompiEventOutcome outcome = transactions.execute(status -> {
            WompiEventOutcome result = resolve(dispatch);
            WompiTransaction tx = dispatch.transaction();
            if (dispatch.source() == WompiEventSource.REDIRECT_SYNC && result == WompiEventOutcome.IGNORED) {
                // El comprador puede repetir el sync muchas veces: un IGNORED no aporta nada al registro.
                return result;
            }
            boolean inserted = eventLog.insert(new PaymentProviderEventLog.Entry(
                    properties.environment(),
                    dispatch.eventType(),
                    dispatch.checksum(),
                    tx == null ? null : tx.reference(),
                    tx == null ? null : tx.id(),
                    tx == null ? null : tx.status(),
                    tx == null ? null : tx.amountInCents(),
                    tx == null ? null : tx.currency(),
                    dispatch.source(),
                    result,
                    safePayload(dispatch)
            ));
            if (!inserted) {
                status.setRollbackOnly();
                return WompiEventOutcome.DUPLICATE;
            }
            return result;
        });
        logOutcome(dispatch, outcome);
        return outcome;
    }

    /**
     * Lista blanca del payload ({@link WompiPayloadWhitelist}): el evento completo del webhook o la transacción de la
     * API. Nunca se guardan datos del comprador.
     */
    static JsonNode safePayload(Dispatch dispatch) {
        return dispatch.source() == WompiEventSource.WEBHOOK
                ? WompiPayloadWhitelist.event(dispatch.payload())
                : WompiPayloadWhitelist.apiTransaction(dispatch.payload());
    }

    private WompiEventOutcome resolve(Dispatch dispatch) {
        WompiTransaction tx = dispatch.transaction();
        if (tx == null) {
            return WompiEventOutcome.IGNORED;
        }
        if (!dispatch.environmentMatches()) {
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        Optional<WompiTransactionHandler> handler = handlers.stream()
                .filter(candidate -> candidate.supports(tx.reference()))
                .findFirst();
        if (handler.isEmpty()) {
            return WompiEventOutcome.UNKNOWN_REFERENCE;
        }
        WompiEventOutcome result = handler.get().apply(tx, dispatch.source());
        if (result == null || result == WompiEventOutcome.DUPLICATE) {
            throw new IllegalStateException("Wompi handler returned an invalid outcome: " + result);
        }
        return result;
    }

    private void logOutcome(Dispatch dispatch, WompiEventOutcome outcome) {
        WompiTransaction tx = dispatch.transaction();
        String reference = tx == null ? null : tx.reference();
        String transactionId = tx == null ? null : tx.id();
        String status = tx == null ? null : tx.status();
        switch (outcome) {
            case UNKNOWN_REFERENCE, REJECTED_MISMATCH -> log.error(
                    "Wompi transaction not applied: outcome={} source={} reference={} transactionId={} status={} "
                            + "environmentMatches={}",
                    outcome, dispatch.source(), reference, transactionId, status, dispatch.environmentMatches());
            case DUPLICATE -> log.info("Duplicate Wompi event ignored: source={} reference={} transactionId={}",
                    dispatch.source(), reference, transactionId);
            default -> log.info("Wompi transaction processed: outcome={} source={} reference={} transactionId={} "
                    + "status={}", outcome, dispatch.source(), reference, transactionId, status);
        }
    }

    /**
     * Datos de un despacho.
     *
     * @param transaction        transacción leída o {@code null} si el evento no trae una legible
     * @param source             origen
     * @param checksum           checksum del evento (solo WEBHOOK)
     * @param eventType          {@code event} del webhook (p. ej. {@code transaction.updated})
     * @param payload            JSON que se guarda
     * @param environmentMatches {@code false} si el evento declara otro entorno: no se aplica
     */
    public record Dispatch(
            WompiTransaction transaction,
            WompiEventSource source,
            String checksum,
            String eventType,
            JsonNode payload,
            boolean environmentMatches
    ) {
    }
}
