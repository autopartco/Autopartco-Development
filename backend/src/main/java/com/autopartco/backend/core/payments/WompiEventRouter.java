package com.autopartco.backend.core.payments;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.payments.internal.WompiDispatcher;

import lombok.AllArgsConstructor;

/**
 * Punto de entrada para aplicar una transacción consultada a la API de Wompi (sync tras la redirección y
 * conciliación). Usa el mismo enrutamiento por prefijo y el mismo registro en {@code payment_provider_events} que el
 * webhook, que entra por {@code core.payments.web.WompiWebhookController}.
 */
@Service
@AllArgsConstructor
public class WompiEventRouter {
    private final WompiDispatcher dispatcher;

    /**
     * Aplica {@code transaction} con {@link WompiEventSource#REDIRECT_SYNC} o {@link WompiEventSource#RECONCILIATION}
     * en una transacción propia. Llamar fuera de cualquier transacción y después de la llamada de red. Solo lanza ante
     * errores internos.
     */
    public WompiEventOutcome apply(WompiTransaction transaction, WompiEventSource source) {
        if (source == WompiEventSource.WEBHOOK) {
            throw new IllegalArgumentException("Webhook events are dispatched by the webhook controller.");
        }
        return dispatcher.dispatch(new WompiDispatcher.Dispatch(
                transaction, source, null, null, transaction.raw(), true));
    }
}
