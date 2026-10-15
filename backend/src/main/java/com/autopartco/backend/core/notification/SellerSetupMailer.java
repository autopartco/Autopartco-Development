package com.autopartco.backend.core.notification;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;

/** Arma y envía el enlace de configuración del almacén ({@code /seller/setup?token=...}). */
@Service
@AllArgsConstructor
public class SellerSetupMailer {
    private final EmailService emailService;
    private final AppProperties appProperties;

    public void send(String to, String givenName, String onboardingToken) {
        String setupLink = UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/seller/setup")
                .queryParam("token", onboardingToken)
                .build()
                .toUriString();
        emailService.sendSellerOnboarding(to, givenName, setupLink);
    }
}
