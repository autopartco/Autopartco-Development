package com.autopartco.backend.core.payments;

import static com.autopartco.backend.shared.security.TokenHashing.sha256Hex;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.payments.internal.WompiPayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente de Wompi: firma de integridad y URL del Web Checkout (sin red) y consulta de transacciones con la llave
 * privada ({@code GET /v1/transactions/{id}} y {@code GET /v1/transactions?reference=}). La URL base sale del entorno.
 * Nunca registra llaves ni secretos.
 */
@Service
@Slf4j
public class WompiClient {
    /** Formato del {@code id} de transacción que llega en la redirección. */
    public static final Pattern TRANSACTION_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    // Formato de Wompi para expiration-time: ISO 8601 en UTC con milisegundos.
    private static final DateTimeFormatter EXPIRATION_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final WompiProperties properties;
    private final ObjectMapper objectMapper;
    // Perezoso: el HttpClient solo se crea en la primera consulta (el checkout firmado no usa red).
    private final Supplier<RestClient.Builder> builderSupplier;
    private volatile RestClient restClient;

    @Autowired
    public WompiClient(WompiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, WompiClient::defaultBuilder);
    }

    /** Para pruebas: permite enlazar un {@code MockRestServiceServer} al builder. */
    public WompiClient(WompiProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this(properties, objectMapper, () -> builder);
    }

    private WompiClient(
            WompiProperties properties, ObjectMapper objectMapper, Supplier<RestClient.Builder> builderSupplier) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.builderSupplier = builderSupplier;
    }

    private static RestClient.Builder defaultBuilder() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory);
    }

    private RestClient restClient() {
        RestClient client = restClient;
        if (client == null) {
            synchronized (this) {
                client = restClient;
                if (client == null) {
                    client = builderSupplier.get().build();
                    restClient = client;
                }
            }
        }
        return client;
    }

    public WompiEnvironment environment() {
        return properties.environment();
    }

    /**
     * Firma de integridad: SHA-256 de {@code referencia + monto + moneda [+ expiration-time] + secreto}.
     * {@code expiresAt} {@code null} omite el vencimiento.
     */
    public String integritySignature(String reference, long amountInCents, String currency, Instant expiresAt) {
        requireCheckoutConfiguration();
        String expiration = expiresAt == null ? "" : formatExpiration(expiresAt);
        return sha256Hex(reference + amountInCents + currency + expiration + properties.integritySecret());
    }

    /** URL firmada del Web Checkout. El frontend la usa tal cual: cualquier cambio invalida la firma. */
    public String checkoutUrl(WompiCheckoutRequest request) {
        requireCheckoutConfiguration();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.checkoutUrl())
                .queryParam("public-key", properties.publicKey())
                .queryParam("currency", request.currency())
                .queryParam("amount-in-cents", request.amountInCents())
                .queryParam("reference", request.reference())
                .queryParam("signature:integrity", integritySignature(
                        request.reference(), request.amountInCents(), request.currency(), request.expiresAt()))
                .queryParam("redirect-url", request.redirectUrl());
        if (request.expiresAt() != null) {
            builder.queryParam("expiration-time", formatExpiration(request.expiresAt()));
        }
        return builder.build().encode().toUriString();
    }

    /**
     * {@code GET /v1/transactions/{id}}. Vacío si Wompi responde 404 o el id no tiene el formato esperado.
     *
     * @throws WompiUnavailableException si Wompi no responde, responde otro error o falta la llave privada
     */
    public Optional<WompiTransaction> findTransaction(String transactionId) {
        if (transactionId == null || !TRANSACTION_ID.matcher(transactionId).matches()) {
            return Optional.empty();
        }
        JsonNode body = get(UriComponentsBuilder.fromUriString(properties.apiUrl())
                .path("/transactions/{id}")
                .buildAndExpand(transactionId)
                .encode()
                .toUriString());
        if (body == null) {
            return Optional.empty();
        }
        Optional<WompiTransaction> transaction = WompiPayloads.transaction(body.path("data"));
        if (transaction.isEmpty()) {
            throw new WompiUnavailableException("Wompi returned an unreadable transaction.");
        }
        return transaction;
    }

    /**
     * {@code GET /v1/transactions?reference=}: transacciones de una referencia (puede haber varias tras un
     * rechazo). Las filas ilegibles se descartan con log WARN.
     *
     * @throws WompiUnavailableException si Wompi no responde, responde un error o falta la llave privada
     */
    public List<WompiTransaction> findTransactionsByReference(String reference) {
        JsonNode body = get(UriComponentsBuilder.fromUriString(properties.apiUrl())
                .path("/transactions")
                .queryParam("reference", reference)
                .encode()
                .toUriString());
        List<WompiTransaction> transactions = new ArrayList<>();
        if (body == null) {
            return transactions;
        }
        JsonNode data = body.path("data");
        if (!data.isArray()) {
            throw new WompiUnavailableException("Wompi returned an unreadable transaction list.");
        }
        for (JsonNode node : data) {
            WompiPayloads.transaction(node).ifPresentOrElse(
                    transactions::add,
                    () -> log.warn("Ignoring unreadable Wompi transaction for reference {}", reference));
        }
        return transactions;
    }

    /** Cuerpo JSON de un 2xx; {@code null} si es 404. */
    private JsonNode get(String uri) {
        if (WompiProperties.isBlank(properties.privateKey())) {
            throw new WompiUnavailableException("Wompi private key is not configured.");
        }
        try {
            return restClient().get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.privateKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return null;
                        }
                        if (status < 200 || status >= 300) {
                            throw new WompiUnavailableException("Wompi API answered HTTP " + status + ".");
                        }
                        try (InputStream stream = response.getBody()) {
                            return objectMapper.readTree(stream);
                        }
                    });
        } catch (RestClientException exception) {
            throw new WompiUnavailableException("Wompi API is unreachable.", exception);
        } catch (IllegalArgumentException exception) {
            throw new WompiUnavailableException("Wompi API request could not be built.", exception);
        }
    }

    private void requireCheckoutConfiguration() {
        if (!properties.checkoutConfigured()) {
            throw new IllegalStateException("Wompi checkout configuration is incomplete.");
        }
    }

    static String formatExpiration(Instant expiresAt) {
        return EXPIRATION_FORMAT.format(expiresAt.truncatedTo(ChronoUnit.MILLIS));
    }
}
