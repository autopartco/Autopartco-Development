/**
 * Producto A: marketplace para compradores (catálogo, almacenes publicados y, más adelante, compra).
 * Nadie importa este paquete; todo su contenido es interno.
 */
@ApplicationModule(
        displayName = "Marketplace",
        allowedDependencies = {"shared", "core.audit", "core.notification", "core.identity", "core.organization",
                "core.vehicle", "core.inventory", "core.pricing", "core.catalog", "core.billing", "core.order", "core.payments",
                "core.analytics", "core.settings", "core.search", "core.reviews", "core.questions", "core.advertising"})
package com.autopartco.backend.marketplace;

import org.springframework.modulith.ApplicationModule;
