package com.autopartco.backend.core.billing.internal.wompi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wompi")
public record WompiProperties(
        String publicKey,
        String integritySecret,
        String eventsSecret,
        String checkoutUrl,
        String redirectUrl
) {
}
