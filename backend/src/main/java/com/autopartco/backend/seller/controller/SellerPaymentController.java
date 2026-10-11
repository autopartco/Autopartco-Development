package com.autopartco.backend.seller.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.autopartco.backend.config.ApiConfig;
import com.autopartco.backend.seller.dto.SellerCheckoutRequest;
import com.autopartco.backend.seller.dto.SellerCheckoutResponse;
import com.autopartco.backend.seller.service.SellerPaymentService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiConfig.API_BASE_PATH + "/seller-payments")
@AllArgsConstructor
public class SellerPaymentController {
    private final SellerPaymentService paymentService;

    @PostMapping("/applications/{applicationId}/checkout")
    public SellerCheckoutResponse checkout(
            @PathVariable UUID applicationId,
            @Valid @RequestBody SellerCheckoutRequest request,
            Authentication authentication
    ) {
        return paymentService.createCheckout(applicationId, authentication.getName(), request);
    }

    @PostMapping("/wompi/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestBody JsonNode payload) {
        paymentService.processWebhook(payload);
    }
}
