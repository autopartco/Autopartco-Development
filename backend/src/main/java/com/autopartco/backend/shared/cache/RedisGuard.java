package com.autopartco.backend.shared.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Acceso a Redis que nunca rompe la petición: cualquier fallo (sin conexión, tiempo de espera de 200 ms, error del
 * servidor) devuelve vacío y abre el circuito durante {@code retryAfter}; mientras está abierto ni siquiera se intenta
 * y los llamadores usan su respaldo en memoria. Se registra un solo aviso por apertura, sin claves ni valores.
 */
@Slf4j
public class RedisGuard {
    private final StringRedisTemplate template;
    private final Duration retryAfter;
    private final Clock clock;
    private final AtomicLong openUntilMillis = new AtomicLong();
    private final CacheMetrics metrics;

    public RedisGuard(StringRedisTemplate template, Duration retryAfter, Clock clock, CacheMetrics metrics) {
        this.template = template;
        this.retryAfter = retryAfter;
        this.clock = clock;
        this.metrics = metrics == null ? CacheMetrics.NONE : metrics;
    }

    /** {@code false} mientras el circuito está abierto tras un fallo. */
    public boolean available() {
        return clock.millis() >= openUntilMillis.get();
    }

    /**
     * Ejecuta {@code operation}. Vacío si el circuito está abierto, si falla o si devuelve {@code null}.
     *
     * @param name nombre corto de la operación (solo para el registro)
     */
    public <T> Optional<T> call(String name, Function<StringRedisTemplate, T> operation) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(operation.apply(template));
        } catch (RuntimeException failure) {
            trip(name, failure);
            return Optional.empty();
        }
    }

    /** Como {@link #call} sin resultado; {@code true} si se ejecutó sin fallo. */
    public boolean run(String name, java.util.function.Consumer<StringRedisTemplate> operation) {
        return call(name, redis -> {
            operation.accept(redis);
            return Boolean.TRUE;
        }).isPresent();
    }

    private void trip(String name, RuntimeException failure) {
        metrics.redisError();
        long now = clock.millis();
        long previous = openUntilMillis.getAndSet(now + retryAfter.toMillis());
        if (previous <= now) {
            log.warn("Redis no disponible ({}: {}); se usa la caché en memoria durante {} s.", name,
                    failure.getClass().getSimpleName(), retryAfter.toSeconds());
        }
    }
}
