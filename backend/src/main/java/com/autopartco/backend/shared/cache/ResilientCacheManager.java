package com.autopartco.backend.shared.cache;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link CacheManager} con las cachés fijas de {@link AppCaches}: Redis si está configurado y responde, Caffeine local
 * si no ({@link ResilientCache}). No crea cachés con otros nombres.
 */
public class ResilientCacheManager implements CacheManager {
    private final Map<String, ResilientCache> caches = new LinkedHashMap<>();
    private final JsonMapper json;
    private final boolean redisConfigured;

    public ResilientCacheManager(List<AppCaches.Spec> specs, RedisGuard redis, CacheMetrics metrics) {
        this.json = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.redisConfigured = redis != null;
        specs.forEach(spec -> caches.put(spec.name(), new ResilientCache(spec, redis, json, metrics)));
    }

    /** Solo cachés locales (sin Redis); útil en pruebas. */
    public static ResilientCacheManager local() {
        return new ResilientCacheManager(AppCaches.SPECS, null, CacheMetrics.NONE);
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.copyOf(caches.keySet());
    }

    /** {@code true} si hay {@code REDIS_URL} (aunque en este momento no responda). */
    public boolean redisConfigured() {
        return redisConfigured;
    }

    /**
     * Caché {@code name} con tipo de valor {@code valueType} (p. ej. {@code new TypeReference<List<X>>(){}.getType()}).
     * Con el tipo puede guardarse en Redis.
     */
    public <V> TypedCache<V> typed(String name, Type valueType) {
        ResilientCache cache = caches.get(name);
        if (cache == null) {
            throw new IllegalArgumentException("Caché desconocida: " + name);
        }
        cache.bind(json.constructType(valueType));
        return new TypedCache<>(cache);
    }
}
