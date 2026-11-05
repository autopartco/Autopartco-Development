package com.autopartco.backend.core.payments.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.payments.internal.WompiSignatureException;
import com.autopartco.backend.core.payments.internal.WompiWebhookIngest;
import com.autopartco.backend.shared.web.ApiPaths;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook único de eventos de Wompi (una URL por entorno). Público: la autenticidad se comprueba con la firma.
 * <ul>
 *   <li>401 sin cuerpo: cuerpo vacío o JSON inválido, o firma ausente o inválida; no se guarda nada.</li>
 *   <li>200 sin cuerpo: aplicado, duplicado o inaplicable (registrado para revisión).</li>
 *   <li>500 sin cuerpo: error interno; la transacción se revierte y Wompi reintenta.</li>
 * </ul>
 * El cuerpo llega como texto y se lee con el {@link ObjectMapper} de Jackson 2 ({@code shared.config.JacksonConfig}):
 * los convertidores HTTP de Spring Boot 4 usan Jackson 3 y no saben construir un {@link JsonNode} de Jackson 2.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/webhooks/wompi")
@AllArgsConstructor
@Slf4j
public class WompiWebhookController {
    private final WompiWebhookIngest ingest;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> webhook(@RequestBody(required = false) String body) {
        try {
            ingest.ingest(parse(body));
            return ResponseEntity.ok().build();
        } catch (WompiSignatureException exception) {
            log.warn("Rejected Wompi event: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (RuntimeException exception) {
            log.error("Wompi event could not be processed; Wompi will retry", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Objeto JSON del evento; vacío, ilegible o no objeto se trata como firma ausente (401). */
    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            throw new WompiSignatureException("Event body is empty.");
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new WompiSignatureException("Event body is not valid JSON.");
        }
        if (payload == null || !payload.isObject()) {
            throw new WompiSignatureException("Event body is not an object.");
        }
        return payload;
    }
}
