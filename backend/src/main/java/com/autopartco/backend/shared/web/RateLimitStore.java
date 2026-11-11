package com.autopartco.backend.shared.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Ventana deslizante compartida entre instancias (Redis). {@link SlidingWindowRateLimiter} la consulta primero y, si
 * responde vacío (no disponible), aplica su ventana en memoria.
 */
public interface RateLimitStore {
    /**
     * Registra una llamada de {@code key} si no se superó {@code max} en {@code window}.
     *
     * @return {@code true} admitida, {@code false} rechazada; vacío si el almacén no está disponible
     */
    Optional<Boolean> tryAcquire(String key, int max, Duration window, Instant now);
}
