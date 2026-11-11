package com.autopartco.backend.shared.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Aciertos y fallos por caché ({@code autopartco.cache.gets{cache,result=hit|miss}}) y errores de Redis
 * ({@code autopartco.cache.redis.errors}) en el {@link MeterRegistry} de Actuator. Sin registro, no hace nada.
 */
public class CacheMetrics {
    public static final CacheMetrics NONE = new CacheMetrics(null);
    public static final String GETS = "autopartco.cache.gets";
    public static final String REDIS_ERRORS = "autopartco.cache.redis.errors";

    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public CacheMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void hit(String cache) {
        increment(cache, "hit");
    }

    public void miss(String cache) {
        increment(cache, "miss");
    }

    public void redisError() {
        if (registry != null) {
            counters.computeIfAbsent(REDIS_ERRORS, name -> Counter.builder(name).register(registry)).increment();
        }
    }

    private void increment(String cache, String result) {
        if (registry == null) {
            return;
        }
        counters.computeIfAbsent(cache + '|' + result, key -> Counter.builder(GETS)
                .tag("cache", cache)
                .tag("result", result)
                .register(registry)).increment();
    }
}
