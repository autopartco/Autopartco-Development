package com.autopartco.backend.shared.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Aplica {@code app.client-ip.trusted-proxies} (CIDR separados por comas) a {@link ClientIp}. Sin la propiedad se
 * mantienen los rangos de Cloudflare; con un valor vacío nunca se acepta {@code CF-Connecting-IP}.
 */
@Configuration(proxyBeanMethods = false)
class ClientIpConfig {

    ClientIpConfig(@Value("${app.client-ip.trusted-proxies:#{null}}") List<String> trustedProxies) {
        if (trustedProxies != null) {
            ClientIp.trustProxies(trustedProxies);
        }
    }
}
