/**
 * Producto, categoría, fabricante, imágenes y consultas de listado.
 */
@ApplicationModule(
        displayName = "Core: Catalog",
        allowedDependencies = {"shared", "core.organization", "core.vehicle", "core.inventory", "core.pricing"})
package com.autopartco.backend.core.catalog;

import org.springframework.modulith.ApplicationModule;
