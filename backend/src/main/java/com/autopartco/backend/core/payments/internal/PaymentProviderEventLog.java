package com.autopartco.backend.core.payments.internal;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AllArgsConstructor;

/**
 * Registro de {@code payment_provider_events}. Solo inserción: esta clase no tiene UPDATE ni DELETE (la migración
 * lo impide además con un trigger). Deduplica por {@code (provider, checksum)} con {@code ON CONFLICT DO NOTHING}.
 * Los valores se recortan a los límites de las columnas y CHECK para que un evento raro no provoque un 500 eterno.
 */
@Repository
@AllArgsConstructor
public class PaymentProviderEventLog {
    static final String PROVIDER = "WOMPI";
    /** Margen bajo el CHECK {@code pg_column_size(payload) <= 32768}. */
    static final int MAX_PAYLOAD_BYTES = 30_000;
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern PROVIDER_STATUS = Pattern.compile("^[A-Z_]{1,30}$");

    static final String INSERT_SQL = """
            INSERT INTO payment_provider_events (
                provider, environment, event_type, checksum, reference, provider_transaction_id,
                provider_status, amount_in_cents, currency, source, outcome, payload)
            VALUES (
                :provider, :environment, :eventType, :checksum, :reference, :providerTransactionId,
                :providerStatus, :amountInCents, :currency, :source, :outcome, CAST(:payload AS jsonb))
            ON CONFLICT (provider, checksum) WHERE checksum IS NOT NULL DO NOTHING
            """;

    static final String EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM payment_provider_events WHERE provider = :provider AND checksum = :checksum)
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    /** Fila a insertar. {@code checksum} solo en WEBHOOK; los demás campos pueden ser {@code null}. */
    public record Entry(
            WompiEnvironment environment,
            String eventType,
            String checksum,
            String reference,
            String providerTransactionId,
            String providerStatus,
            Long amountInCents,
            String currency,
            WompiEventSource source,
            WompiEventOutcome outcome,
            JsonNode payload
    ) {
    }

    public boolean existsByChecksum(String checksum) {
        return Boolean.TRUE.equals(jdbcClient.sql(EXISTS_SQL)
                .param("provider", PROVIDER)
                .param("checksum", checksum)
                .query(Boolean.class)
                .single());
    }

    /** {@code false} si ya existía un evento con el mismo checksum (no se insertó nada). */
    public boolean insert(Entry entry) {
        int rows = jdbcClient.sql(INSERT_SQL)
                .param("provider", PROVIDER)
                .param("environment", entry.environment().name())
                .param("eventType", truncate(entry.eventType(), 60))
                .param("checksum", entry.checksum())
                .param("reference", truncate(entry.reference(), 100))
                .param("providerTransactionId", truncate(entry.providerTransactionId(), 100))
                .param("providerStatus", matches(PROVIDER_STATUS, entry.providerStatus()))
                .param("amountInCents", entry.amountInCents() != null && entry.amountInCents() >= 0
                        ? entry.amountInCents() : null)
                .param("currency", matches(CURRENCY, entry.currency()))
                .param("source", entry.source().name())
                .param("outcome", entry.outcome().name())
                .param("payload", payloadJson(entry))
                .update();
        return rows > 0;
    }

    String payloadJson(Entry entry) {
        try {
            JsonNode payload = entry.payload() != null && entry.payload().isObject()
                    ? entry.payload() : objectMapper.createObjectNode();
            String json = objectMapper.writeValueAsString(payload);
            if (json.getBytes(StandardCharsets.UTF_8).length <= MAX_PAYLOAD_BYTES) {
                return json;
            }
            ObjectNode reduced = objectMapper.createObjectNode().put("truncated", true);
            reduced.put("reference", truncate(entry.reference(), 100));
            reduced.put("transactionId", truncate(entry.providerTransactionId(), 100));
            reduced.put("status", truncate(entry.providerStatus(), 30));
            return objectMapper.writeValueAsString(reduced);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize payment provider event payload.", exception);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches() ? value : null;
    }
}
