package com.autopartco.backend.core.payments.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.shared.security.TokenHashing;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;

/**
 * Verifica el checksum de los eventos de Wompi: SHA-256 de los valores de {@code signature.properties} (rutas
 * relativas a {@code data}), el
 * {@code timestamp} y el secreto de eventos, comparado en tiempo constante ({@link MessageDigest#isEqual}).
 */
@Component
@AllArgsConstructor
public class WompiSignatureVerifier {
    private final WompiProperties wompiProperties;

    /**
     * Devuelve el checksum normalizado (minúsculas) si la firma es válida.
     *
     * @throws WompiSignatureException  si la firma falta, está incompleta o no coincide
     * @throws IllegalStateException    si el secreto de eventos no está configurado (error interno, 500)
     */
    public String verify(JsonNode payload) {
        String secret = wompiProperties.eventsSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Wompi event-signature secret is not configured.");
        }
        if (payload == null || !payload.isObject()) {
            throw new WompiSignatureException("Event body is not an object.");
        }
        JsonNode signature = payload.path("signature");
        JsonNode properties = signature.path("properties");
        JsonNode checksumNode = signature.path("checksum");
        JsonNode timestamp = payload.get("timestamp");
        if (!properties.isArray() || properties.isEmpty() || !checksumNode.isTextual()
                || checksumNode.asText().isBlank() || timestamp == null || !timestamp.isValueNode()) {
            throw new WompiSignatureException("Event signature is incomplete.");
        }
        // Las rutas de signature.properties (p. ej. "transaction.id") son relativas a payload.data (doc. de Wompi).
        JsonNode data = payload.path("data");
        StringBuilder signedValues = new StringBuilder();
        for (JsonNode property : properties) {
            if (!property.isTextual()) {
                throw new WompiSignatureException("Event signature properties are invalid.");
            }
            JsonNode value = readDottedPath(data, property.asText());
            if (value.isMissingNode() || value.isNull()) {
                throw new WompiSignatureException("A signed property is missing.");
            }
            signedValues.append(value.isValueNode() ? value.asText() : value.toString());
        }
        signedValues.append(timestamp.asText()).append(secret);
        String checksum = checksumNode.asText().toLowerCase(Locale.ROOT);
        byte[] expected = TokenHashing.sha256Hex(signedValues.toString()).getBytes(StandardCharsets.US_ASCII);
        byte[] received = checksum.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new WompiSignatureException("Event checksum does not match.");
        }
        return checksum;
    }

    private static JsonNode readDottedPath(JsonNode root, String dottedPath) {
        JsonNode value = root;
        for (String component : dottedPath.split("\\.")) {
            value = value.path(component);
        }
        return value;
    }
}
