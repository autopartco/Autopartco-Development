package com.autopartco.backend.core.billing.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.autopartco.backend.core.billing.internal.wompi.WompiWebhookProcessor;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/**
 * Webhook de eventos de Wompi. Público: la autenticidad se comprueba con la firma del evento.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/webhooks/wompi")
@AllArgsConstructor
public class WompiWebhookController {
    private final WompiWebhookProcessor webhookProcessor;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestBody JsonNode payload) {
        webhookProcessor.processWebhook(payload);
    }
}
