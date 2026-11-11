package com.autopartco.backend.core.advertising;

import java.time.Instant;
import java.util.UUID;

/**
 * Tope de frecuencia ({@code ads:freq} de §4): el mismo visitante ve la misma campaña como mucho
 * {@value #MAX_PER_HOUR} veces por hora en una ubicación. Hoy en memoria ({@code InMemoryAdFrequencyCap}); T8 lo pasa a
 * Redis implementando esta interfaz.
 */
public interface AdFrequencyCap {
    int MAX_PER_HOUR = 3;

    /**
     * Cuenta una exhibición si no se superó el tope en la hora de {@code now}.
     *
     * @return {@code false} si el visitante ya vio la campaña {@value #MAX_PER_HOUR} veces en esa hora y ubicación
     */
    boolean tryAcquire(String visitorKey, UUID campaignId, AdPlacement placement, Instant now);
}
