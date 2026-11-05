package com.autopartco.backend.core.payments.internal;

import java.util.Optional;

import com.autopartco.backend.core.payments.WompiTransaction;
import com.fasterxml.jackson.databind.JsonNode;

/** Lectura defensiva de la transacción de Wompi (evento o respuesta de la API). */
public final class WompiPayloads {
    private WompiPayloads() {
    }

    /**
     * Vacío si falta {@code id}, {@code reference}, {@code status} o {@code currency}, o si {@code amount_in_cents} no
     * es un entero no negativo representable en {@code long}.
     */
    public static Optional<WompiTransaction> transaction(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        String id = text(node, "id");
        String reference = text(node, "reference");
        String status = text(node, "status");
        String currency = text(node, "currency");
        JsonNode amount = node.path("amount_in_cents");
        if (id == null || reference == null || status == null || currency == null
                || !amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() < 0) {
            return Optional.empty();
        }
        return Optional.of(new WompiTransaction(id, reference, status, amount.longValue(), currency, node));
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isValueNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("");
        return text.isBlank() ? null : text;
    }
}
