/**
 * Búsqueda del marketplace (v2): índice desnormalizado {@code search_listings} mantenido por eventos tras commit y una
 * reconstrucción nocturna, interpretación de la consulta, consulta SQL con relevancia, filtros, facetas y cercanía, y
 * sugerencias de autocompletado. Lo publicado lo define {@code core.catalog.ListingQueries}; aquí no se duplica.
 * También indexa y busca servicios de los almacenes ({@code search_services}, un resultado por servicio); de
 * {@code core.organization} solo usa tipos públicos ({@code ServiceCategory}, {@code OpeningHours}, {@code StoreBrand}).
 */
@ApplicationModule(
        displayName = "Core: Search",
        allowedDependencies = {"shared", "core.catalog", "core.pricing", "core.analytics", "core.advertising",
                "core.organization"})
package com.autopartco.backend.core.search;

import org.springframework.modulith.ApplicationModule;
