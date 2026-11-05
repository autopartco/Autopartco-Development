package com.autopartco.backend.core.payments.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransactionHandler;
import com.autopartco.backend.core.payments.internal.PaymentProviderEventLog;
import com.autopartco.backend.core.payments.internal.WompiDispatcher;
import com.autopartco.backend.core.payments.internal.WompiSignatureVerifier;
import com.autopartco.backend.core.payments.internal.WompiWebhookIngest;
import com.autopartco.backend.shared.security.TokenHashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Webhook por HTTP con los convertidores por defecto de Spring (Jackson 3 en Spring Boot 4). Cubre el fallo de
 * producción v1.0.2: {@code @RequestBody JsonNode} de Jackson 2 respondía 500 ({@code HttpMessageConversionException}).
 */
class WompiWebhookMvcTest {
    private static final String URL = "/api/v1/webhooks/wompi";
    private static final String SECRET = "test-events-secret";

    private final PaymentProviderEventLog eventLog = mock(PaymentProviderEventLog.class);
    private final WompiTransactionHandler handler = mock(WompiTransactionHandler.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        WompiProperties properties = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_x", "prv_test_x",
                "integrity", SECRET, "https://checkout.wompi.co/p/", "https://app.example/ok");
        WompiDispatcher dispatcher = new WompiDispatcher(List.of(handler), eventLog, properties,
                TransactionOperations.withoutTransaction());
        WompiWebhookController controller = new WompiWebhookController(new WompiWebhookIngest(
                new WompiSignatureVerifier(properties), eventLog, dispatcher, properties), new ObjectMapper());
        // Sin convertidores propios: los mismos que Spring elige por defecto con Jackson 3 en el classpath.
        mvc = MockMvcBuilders.standaloneSetup(controller, new Jackson2JsonNodeController()).build();
    }

    /** Evento en el formato de la documentación de Wompi, firmado con el secreto de eventos. */
    private static String signedEvent() {
        String checksum = TokenHashing.sha256Hex(
                "1234-1610641025-49201" + "APPROVED" + "4490000" + "1530291411" + SECRET);
        return """
                {
                  "event": "transaction.updated",
                  "data": {
                    "transaction": {
                      "id": "1234-1610641025-49201",
                      "amount_in_cents": 4490000,
                      "reference": "APC-1",
                      "customer_email": "juan.perez@gmail.com",
                      "currency": "COP",
                      "payment_method_type": "NEQUI",
                      "status": "APPROVED"
                    }
                  },
                  "environment": "test",
                  "signature": {
                    "properties": ["transaction.id", "transaction.status", "transaction.amount_in_cents"],
                    "checksum": "%s"
                  },
                  "timestamp": 1530291411,
                  "sent_at": "2018-07-20T16:45:05.000Z"
                }
                """.formatted(checksum);
    }

    @Test
    void realJsonEventIsAcceptedWith200() throws Exception {
        when(handler.supports("APC-1")).thenReturn(true);
        when(handler.apply(any(), any())).thenReturn(WompiEventOutcome.APPLIED);
        when(eventLog.insert(any())).thenReturn(true);

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(signedEvent()))
                .andExpect(status().isOk());

        verify(handler).apply(any(), any(WompiEventSource.class));
        verify(eventLog).insert(any());
    }

    @Test
    void invalidSignatureIs401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(signedEvent().replace("\"APPROVED\"", "\"DECLINED\"")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(handler);
    }

    @Test
    void emptyOrInvalidJsonBodyIs401WithoutRecording() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(eventLog, handler);
    }

    /** Control: confirma que esta configuración reproduce el fallo con un {@code JsonNode} de Jackson 2. */
    @Test
    void defaultConvertersCannotBindJackson2JsonNode() {
        Throwable failure;
        try {
            failure = mvc.perform(post("/test/jackson2").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResolvedException();
        } catch (Exception exception) {
            failure = exception;
        }
        assertThat(failure).as("binding a Jackson 2 JsonNode must fail as in production").isNotNull();
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertThat(failure.toString() + " / " + root)
                .containsAnyOf("HttpMessageConversionException", "HttpMessageNotReadableException",
                        "HttpMediaTypeNotSupportedException", "JsonNode");
    }

    @RestController
    static class Jackson2JsonNodeController {
        @PostMapping("/test/jackson2")
        ResponseEntity<Void> bind(@RequestBody JsonNode body) {
            return ResponseEntity.ok().build();
        }
    }
}
