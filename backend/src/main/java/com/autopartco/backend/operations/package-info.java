/**
 * Producto D: operaciones internas de la plataforma (verificación de almacenes, administración de catálogo,
 * promociones de plataforma y planes). Nadie importa este paquete; todo su contenido es interno.
 */
@ApplicationModule(
        displayName = "Operations",
        allowedDependencies = {"shared", "core.audit", "core.notification", "core.identity", "core.organization",
                "core.vehicle", "core.inventory", "core.pricing", "core.catalog", "core.billing", "core.order"})
package com.autopartco.backend.operations;

import org.springframework.modulith.ApplicationModule;
