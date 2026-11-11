package com.autopartco.backend.core.advertising;

import java.util.List;
import java.util.function.Supplier;

/**
 * Caché de candidatos por ubicación ({@code ads:active} de §4, TTL 60 s). Hoy en memoria por instancia
 * ({@code InMemoryAdCandidateCache}); T8 la reemplaza por Redis implementando esta misma interfaz.
 */
public interface AdCandidateCache {
    /** Candidatos de {@code placement}; si no hay entrada vigente, los carga con {@code loader} y los guarda. */
    List<AdCandidate> get(AdPlacement placement, Supplier<List<AdCandidate>> loader);

    /** Descarta todo (al cambiar el estado de una campaña). */
    void invalidate();
}
