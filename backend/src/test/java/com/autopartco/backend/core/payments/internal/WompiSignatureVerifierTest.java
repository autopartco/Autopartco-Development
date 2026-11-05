package com.autopartco.backend.core.payments.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.shared.security.TokenHashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class WompiSignatureVerifierTest {
    static final String EVENTS_SECRET = "test-events-secret";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WompiSignatureVerifier verifier = new WompiSignatureVerifier(properties(EVENTS_SECRET));

    static WompiProperties properties(String eventsSecret) {
        return new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_x", "prv_test_x", "integrity", eventsSecret,
                "https://checkout.wompi.co/p/", "https://app.example/ok");
    }

    /** Evento firmado con las propiedades id, status y amount_in_cents (como envía Wompi). */
    static JsonNode signedEvent(String reference, String status, long amount, String environment, String secret)
            throws Exception {
        String checksum = TokenHashing.sha256Hex("transaction-1" + status + amount + "1730733600" + secret);
        return MAPPER.readTree("""
                {
                  "event": "transaction.updated",
                  "environment": "%s",
                  "data": {"transaction": {
                    "id": "transaction-1",
                    "reference": "%s",
                    "status": "%s",
                    "amount_in_cents": %d,
                    "currency": "COP"
                  }},
                  "timestamp": 1730733600,
                  "signature": {
                    "properties": ["transaction.id", "transaction.status", "transaction.amount_in_cents"],
                    "checksum": "%s"
                  }
                }
                """.formatted(environment, reference, status, amount, checksum.toUpperCase()));
    }

    @Test
    void acceptsValidChecksumCaseInsensitivelyAndReturnsItNormalized() throws Exception {
        JsonNode event = signedEvent("APC-1", "APPROVED", 10000, "test", EVENTS_SECRET);

        String checksum = verifier.verify(event);

        assertEquals(TokenHashing.sha256Hex("transaction-1APPROVED10000" + "1730733600" + EVENTS_SECRET), checksum);
    }

    @Test
    void acceptsEventInTheLiteralFormatOfWompiDocumentation() throws Exception {
        // Ejemplo de "Eventos" de la documentación de Wompi: las propiedades firmadas son relativas a "data".
        String checksum = TokenHashing.sha256Hex(
                "1234-1610641025-49201" + "APPROVED" + "4490000" + "1530291411" + EVENTS_SECRET);
        JsonNode event = MAPPER.readTree("""
                {
                  "event": "transaction.updated",
                  "data": {
                    "transaction": {
                      "id": "1234-1610641025-49201",
                      "amount_in_cents": 4490000,
                      "reference": "MZQ3X2DE2SMX",
                      "customer_email": "juan.perez@gmail.com",
                      "currency": "COP",
                      "payment_method_type": "NEQUI",
                      "redirect_url": "https://mitienda.com.co/pagos/redireccion",
                      "status": "APPROVED",
                      "shipping_address": null,
                      "payment_link_id": null,
                      "payment_source_id": null
                    }
                  },
                  "environment": "prod",
                  "signature": {
                    "properties": [
                      "transaction.id",
                      "transaction.status",
                      "transaction.amount_in_cents"
                    ],
                    "checksum": "%s"
                  },
                  "timestamp": 1530291411,
                  "sent_at": "2018-07-20T16:45:05.000Z"
                }
                """.formatted(checksum));

        assertEquals(checksum, verifier.verify(event));
    }

    @Test
    void propertiesResolvedFromRootAreNotAccepted() throws Exception {
        // Una ruta con prefijo "data." no existe dentro de data: la firma se considera incompleta.
        String checksum = TokenHashing.sha256Hex("t-1" + "1" + EVENTS_SECRET);
        JsonNode event = MAPPER.readTree("""
                {"data": {"transaction": {"id": "t-1"}}, "timestamp": 1,
                 "signature": {"properties": ["data.transaction.id"], "checksum": "%s"}}
                """.formatted(checksum));

        assertThrows(WompiSignatureException.class, () -> verifier.verify(event));
    }

    @Test
    void rejectsChecksumSignedWithAnotherSecret() throws Exception {
        JsonNode event = signedEvent("APC-1", "APPROVED", 10000, "test", "other-secret");

        assertThrows(WompiSignatureException.class, () -> verifier.verify(event));
    }

    @Test
    void rejectsTamperedSignedField() throws Exception {
        JsonNode event = signedEvent("APC-1", "DECLINED", 10000, "test", EVENTS_SECRET);
        ((com.fasterxml.jackson.databind.node.ObjectNode) event.path("data").path("transaction"))
                .put("status", "APPROVED");

        assertThrows(WompiSignatureException.class, () -> verifier.verify(event));
    }

    @Test
    void rejectsIncompleteSignature() throws Exception {
        JsonNode noSignature = MAPPER.readTree("{\"data\":{},\"timestamp\":1}");
        JsonNode noTimestamp = MAPPER.readTree(
                "{\"data\":{\"a\":\"1\"},\"signature\":{\"properties\":[\"a\"],\"checksum\":\"abc\"}}");
        JsonNode missingProperty = MAPPER.readTree(
                "{\"timestamp\":1,\"signature\":{\"properties\":[\"missing\"],\"checksum\":\"abc\"}}");
        JsonNode nonTextProperty = MAPPER.readTree(
                "{\"timestamp\":1,\"signature\":{\"properties\":[1],\"checksum\":\"abc\"}}");

        assertThrows(WompiSignatureException.class, () -> verifier.verify(noSignature));
        assertThrows(WompiSignatureException.class, () -> verifier.verify(noTimestamp));
        assertThrows(WompiSignatureException.class, () -> verifier.verify(missingProperty));
        assertThrows(WompiSignatureException.class, () -> verifier.verify(nonTextProperty));
        assertThrows(WompiSignatureException.class, () -> verifier.verify(MAPPER.readTree("[]")));
    }

    @Test
    void missingEventsSecretIsAnInternalError() throws Exception {
        WompiSignatureVerifier unconfigured = new WompiSignatureVerifier(properties(""));
        JsonNode event = signedEvent("APC-1", "APPROVED", 10000, "test", EVENTS_SECRET);

        assertThrows(IllegalStateException.class, () -> unconfigured.verify(event));
    }
}
