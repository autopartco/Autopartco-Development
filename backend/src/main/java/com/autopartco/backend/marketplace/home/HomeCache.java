package com.autopartco.backend.marketplace.home;

import java.util.function.Supplier;

/**
 * Caché de la parte pública del inicio por área (TTL 60 s). Hoy en memoria por instancia ({@link InMemoryHomeCache});
 * T8 la reemplaza por Redis implementando esta misma interfaz. Los anuncios nunca pasan por aquí.
 */
interface HomeCache {
    /** Contenido de {@code area}; si no hay entrada vigente, lo carga con {@code loader} y lo guarda. */
    HomeContent get(HomeArea area, Supplier<HomeContent> loader);
}
