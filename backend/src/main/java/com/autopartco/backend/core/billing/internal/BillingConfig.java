package com.autopartco.backend.core.billing.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.autopartco.backend.core.billing.internal.wompi.WompiProperties;

/**
 * Configuración del dominio de cobros: registra las propiedades de Wompi.
 */
@Configuration
@EnableConfigurationProperties(WompiProperties.class)
public class BillingConfig {
}
