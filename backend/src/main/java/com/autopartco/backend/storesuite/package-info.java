/**
 * Producto B: SaaS para almacenes (tienda, sucursales, productos, promociones, pedidos, equipo y suscripción).
 * Nadie importa este paquete; todo su contenido es interno.
 */
@ApplicationModule(
        displayName = "Store Suite",
        allowedDependencies = {"shared", "core.audit", "core.notification", "core.identity", "core.organization",
                "core.vehicle", "core.inventory", "core.pricing", "core.catalog", "core.billing", "core.order"})
package com.autopartco.backend.storesuite;

import org.springframework.modulith.ApplicationModule;
