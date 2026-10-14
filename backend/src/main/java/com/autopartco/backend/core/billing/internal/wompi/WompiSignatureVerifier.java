package com.autopartco.backend.core.billing.internal.wompi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.security.TokenHashing;

import lombok.AllArgsConstructor;

/**
 * Verifica el checksum de los eventos de Wompi: SHA-256 de los valores de {@code signature.properties}, el
 * {@code timestamp} y el secreto de eventos, comparado en tiempo constante.
 */
@Component
@AllArgsConstructor
public class WompiSignatureVerifier {
    private final WompiProperties wompiProperties;

    public void verifyWebhookSignature(JsonNode payload) {
        if (wompiProperties.eventsSecret() == null || wompiProperties.eventsSecret().isBlank()) {
            throw new IllegalStateException("Wompi event-signature secret is not configured.");
        }
        JsonNode signature = payload.path("signature");
        JsonNode properties = signature.path("properties");
        String checksum = signature.path("checksum").asText("");
        JsonNode timestamp = payload.get("timestamp");
        if (!properties.isArray() || properties.isEmpty() || checksum.isBlank() || timestamp == null) {
            throw new BadRequestException("WOMPI_SIGNATURE_MISSING", "La firma del evento está incompleta.");
        }
        StringBuilder signedValues = new StringBuilder();
        for (JsonNode property : properties) {
            if (!property.isTextual()) {
                throw new BadRequestException("WOMPI_SIGNATURE_INVALID", "Las propiedades de firma no son válidas.");
            }
            JsonNode value = readDottedPath(payload, property.asText());
            if (value == null || value.isMissingNode() || value.isNull()) {
                throw new BadRequestException("WOMPI_SIGNATURE_INVALID", "Falta un campo requerido para verificar la firma.");
            }
            signedValues.append(value.isValueNode() ? value.asText() : value.toString());
        }
        signedValues.append(timestamp.isValueNode() ? timestamp.asText() : timestamp.toString())
                .append(wompiProperties.eventsSecret());
        byte[] expected = TokenHashing.sha256Hex(signedValues.toString()).getBytes(StandardCharsets.US_ASCII);
        byte[] received = checksum.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new BadRequestException("WOMPI_SIGNATURE_INVALID", "La firma del evento no es válida.");
        }
    }

    private JsonNode readDottedPath(JsonNode root, String dottedPath) {
        JsonNode value = root;
        for (String component : dottedPath.split("\\.")) {
            value = value.path(component);
        }
        return value;
    }
}
