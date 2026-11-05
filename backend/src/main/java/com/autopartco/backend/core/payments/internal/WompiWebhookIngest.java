package com.autopartco.backend.core.payments.internal;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recepción de un evento de Wompi: firma, deduplicación por checksum, entorno y despacho (ADR 0004 §2.7). No es
 * transaccional: {@link WompiDispatcher} abre la transacción.
 */
@Service
@AllArgsConstructor
@Slf4j
public class WompiWebhookIngest {
    private final WompiSignatureVerifier signatureVerifier;
    private final PaymentProviderEventLog eventLog;
    private final WompiDispatcher dispatcher;
    private final WompiProperties properties;

    /**
     * @throws WompiSignatureException firma ausente o inválida (401, no se guarda nada)
     */
    public WompiEventOutcome ingest(JsonNode payload) {
        String checksum = signatureVerifier.verify(payload);
        if (eventLog.existsByChecksum(checksum)) {
            log.info("Duplicate Wompi event ignored before dispatch");
            return WompiEventOutcome.DUPLICATE;
        }
        String eventType = WompiPayloads.text(payload, "event");
        String declaredEnvironment = WompiPayloads.text(payload, "environment");
        boolean environmentMatches = declaredEnvironment == null
                || declaredEnvironment.equals(properties.environment().eventValue());
        if (declaredEnvironment == null) {
            log.warn("Wompi event without environment; assuming {}", properties.environment());
        }
        WompiTransaction transaction = WompiPayloads.transaction(payload.path("data").path("transaction"))
                .orElse(null);
        if (transaction == null) {
            log.warn("Signed Wompi event without a readable transaction: event={}", eventType);
        }
        return dispatcher.dispatch(new WompiDispatcher.Dispatch(
                transaction, WompiEventSource.WEBHOOK, checksum, eventType, payload, environmentMatches));
    }
}
