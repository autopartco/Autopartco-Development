/**
 * Infraestructura de cobros con Wompi (ADR 0004): configuración y validación por entorno, cliente de la API y del
 * Web Checkout, webhook único y enrutamiento de transacciones por prefijo de referencia.
 * <p>
 * API pública: {@link com.autopartco.backend.core.payments.WompiProperties},
 * {@link com.autopartco.backend.core.payments.WompiClient},
 * {@link com.autopartco.backend.core.payments.WompiEventRouter} y el SPI
 * {@link com.autopartco.backend.core.payments.WompiTransactionHandler}, que implementan {@code core.billing}
 * ({@code APC-}) y {@code marketplace.checkout} ({@code APM-}). Este módulo no depende de ninguno de ellos.
 */
@ApplicationModule(displayName = "Core: Payments", allowedDependencies = {"shared"})
package com.autopartco.backend.core.payments;

import org.springframework.modulith.ApplicationModule;
