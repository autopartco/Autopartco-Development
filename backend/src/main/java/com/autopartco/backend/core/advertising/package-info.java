/**
 * Publicidad prepagada por día (§2 del plan v2.0.0): tarifas por ubicación, campañas del almacén y su máquina de
 * estados, cobro por Wompi (handler {@code ADS-} del SPI de {@code core.payments}), entrega de anuncios
 * ({@link com.autopartco.backend.core.advertising.AdServer}) con token firmado, registro de eventos, atribución de
 * pedidos pagados y estadísticas por campaña.
 * <p>
 * No comprueba permisos de almacén ni la suscripción: lo hacen {@code storesuite.advertising} y
 * {@code operations.ads}. Lo publicado lo define {@code core.catalog}; aquí solo se valida que los productos lo estén.
 * Fechas en la zona {@code America/Bogota}.
 */
@ApplicationModule(
        displayName = "Core: Advertising",
        allowedDependencies = {"shared", "core.catalog", "core.payments"})
package com.autopartco.backend.core.advertising;

import org.springframework.modulith.ApplicationModule;
