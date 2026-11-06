package com.autopartco.backend.core.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;

/** Arma el enlace al centro de alertas del portal ({@code /portal/{storeId}/alertas}) y envía el aviso. */
@Service
@AllArgsConstructor
public class StoreAlertMailer {
    private final EmailService emailService;
    private final AppProperties appProperties;

    public void send(String to, UUID storeId, String storeName, List<String> alertLines) {
        String link = UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .pathSegment("portal", storeId.toString(), "alertas")
                .build()
                .toUriString();
        emailService.sendStoreAlerts(to, storeName, alertLines, link);
    }
}
