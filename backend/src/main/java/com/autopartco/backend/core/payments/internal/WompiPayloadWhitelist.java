package com.autopartco.backend.core.payments.internal;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Lista blanca de lo que se guarda en {@code payment_provider_events.payload} (Ley 1581: minimización). Del evento:
 * {@code event}, {@code environment}, {@code timestamp} y {@code signature} ({@code properties}, {@code checksum}); de
 * la transacción: {@code id}, {@code reference}, {@code status}, {@code status_message}, {@code amount_in_cents},
 * {@code currency}, {@code payment_method_type}, {@code created_at} y {@code finalized_at}. Todo lo demás (correo,
 * {@code customer_data}, documento, dirección de envío, {@code payment_method.extra}…) se descarta.
 */
public final class WompiPayloadWhitelist {
    static final List<String> EVENT_FIELDS = List.of("event", "environment", "timestamp");
    static final List<String> SIGNATURE_FIELDS = List.of("properties", "checksum");
    static final List<String> TRANSACTION_FIELDS = List.of("id", "reference", "status", "status_message",
            "amount_in_cents", "currency", "payment_method_type", "created_at", "finalized_at");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private WompiPayloadWhitelist() {
    }

    /** Evento del webhook: campos del evento, firma y {@code data.transaction} filtrados. */
    public static ObjectNode event(JsonNode payload) {
        ObjectNode result = NODES.objectNode();
        if (payload == null || !payload.isObject()) {
            return result;
        }
        copy(payload, result, EVENT_FIELDS);
        JsonNode signature = payload.path("signature");
        if (signature.isObject()) {
            copy(signature, result.putObject("signature"), SIGNATURE_FIELDS);
        }
        JsonNode transaction = payload.path("data").path("transaction");
        if (transaction.isObject()) {
            result.putObject("data").set("transaction", transaction(transaction));
        }
        return result;
    }

    /** Transacción consultada a la API (sync o conciliación), envuelta como {@code {"transaction": {...}}}. */
    public static ObjectNode apiTransaction(JsonNode transaction) {
        ObjectNode result = NODES.objectNode();
        if (transaction != null && transaction.isObject()) {
            result.set("transaction", transaction(transaction));
        }
        return result;
    }

    static ObjectNode transaction(JsonNode transaction) {
        ObjectNode result = NODES.objectNode();
        copy(transaction, result, TRANSACTION_FIELDS);
        return result;
    }

    private static void copy(JsonNode source, ObjectNode target, List<String> fields) {
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value != null && !value.isMissingNode()) {
                target.set(field, value.deepCopy());
            }
        }
    }
}
