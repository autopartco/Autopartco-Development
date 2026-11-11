package com.autopartco.backend.shared.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caché de dos niveles con respaldo: Redis compartido (si hay {@code REDIS_URL}, la caché tiene tipo de valor y Redis
 * responde) o, si no, Caffeine local de la instancia. Nunca falla por Redis: un error o un tiempo de espera hace que esa
 * llamada use la copia local. Los valores van a Redis en JSON con el tipo registrado en
 * {@link ResilientCacheManager#typed}; una caché sin tipo es solo local. Claves {@value #PREFIX}{@code <caché>:<clave>}.
 * <p>
 * Con Redis activo la copia local no se llena (evita servir datos que otra instancia ya invalidó). Las invalidaciones
 * se aplican en ambos niveles.
 */
@Slf4j
public final class ResilientCache implements Cache {
    public static final String PREFIX = "ap:c:";
    private static final int DELETE_BATCH = 500;

    private record Fetched(String raw) {
    }

    private final AppCaches.Spec spec;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> local;
    private final RedisGuard redis;
    private final JsonMapper json;
    private final CacheMetrics metrics;
    private volatile JavaType valueType;

    ResilientCache(AppCaches.Spec spec, RedisGuard redis, JsonMapper json, CacheMetrics metrics) {
        this.spec = spec;
        this.local = Caffeine.newBuilder()
                .expireAfterWrite(spec.ttl())
                .maximumSize(spec.maxLocalEntries())
                .build();
        this.redis = redis;
        this.json = json;
        this.metrics = metrics == null ? CacheMetrics.NONE : metrics;
    }

    void bind(JavaType type) {
        JavaType current = valueType;
        if (current != null && !current.equals(type)) {
            throw new IllegalStateException("La caché " + spec.name() + " ya tiene otro tipo de valor.");
        }
        valueType = type;
    }

    /** {@code true} si hay Redis configurado y la caché tiene tipo (puede usarlo cuando responda). */
    boolean distributed() {
        return redis != null && valueType != null;
    }

    @Override
    public String getName() {
        return spec.name();
    }

    @Override
    public Object getNativeCache() {
        return local;
    }

    /**
     * Valor de {@code key} o, si no hay, el de {@code loader} (que se guarda salvo que sea {@code null}). Las excepciones
     * del cargador se propagan tal cual y no se cachean.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        if (distributed()) {
            Optional<Fetched> fetched = redis.call("get", r -> new Fetched(r.opsForValue().get(redisKey(key))));
            if (fetched.isPresent()) {
                Object cached = decode(fetched.get().raw());
                if (cached != null) {
                    metrics.hit(spec.name());
                    return (T) cached;
                }
                metrics.miss(spec.name());
                T loaded = loader.get();
                writeRedis(key, loaded);
                return loaded;
            }
        }
        Object present = local.getIfPresent(key);
        if (present != null) {
            metrics.hit(spec.name());
            return (T) present;
        }
        return (T) local.get(key, ignored -> {
            metrics.miss(spec.name());
            return loader.get();
        });
    }

    @Override
    public ValueWrapper get(Object key) {
        String k = String.valueOf(key);
        if (distributed()) {
            Optional<Fetched> fetched = redis.call("get", r -> new Fetched(r.opsForValue().get(redisKey(k))));
            if (fetched.isPresent()) {
                Object cached = decode(fetched.get().raw());
                if (cached == null) {
                    metrics.miss(spec.name());
                    return null;
                }
                metrics.hit(spec.name());
                return new SimpleValueWrapper(cached);
            }
        }
        Object present = local.getIfPresent(k);
        if (present == null) {
            metrics.miss(spec.name());
            return null;
        }
        metrics.hit(spec.name());
        return new SimpleValueWrapper(present);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        Object value = wrapper == null ? null : wrapper.get();
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Valor en caché de tipo inesperado en " + spec.name());
        }
        return (T) value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return get(String.valueOf(key), () -> {
            try {
                return valueLoader.call();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new ValueRetrievalException(key, valueLoader, failure);
            }
        });
    }

    @Override
    public void put(Object key, Object value) {
        String k = String.valueOf(key);
        if (value == null) {
            evict(k);
            return;
        }
        if (distributed() && writeRedis(k, value)) {
            return;
        }
        local.put(k, value);
    }

    @Override
    public void evict(Object key) {
        String k = String.valueOf(key);
        local.invalidate(k);
        if (redis != null) {
            redis.run("del", r -> r.delete(redisKey(k)));
        }
    }

    @Override
    public void clear() {
        local.invalidateAll();
        if (redis != null) {
            redis.run("clear", r -> {
                List<String> batch = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions().match(PREFIX + spec.name() + ":*").count(DELETE_BATCH)
                        .build();
                try (Cursor<String> cursor = r.scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= DELETE_BATCH) {
                            r.delete(batch);
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    r.delete(batch);
                }
            });
        }
    }

    private boolean writeRedis(String key, Object value) {
        if (value == null) {
            return false;
        }
        String raw;
        try {
            raw = json.writeValueAsString(value);
        } catch (RuntimeException failure) {
            log.debug("No se pudo serializar un valor de la caché {}: {}", spec.name(),
                    failure.getClass().getSimpleName());
            return false;
        }
        return redis.run("set", r -> r.opsForValue().set(redisKey(key), raw, spec.ttl()));
    }

    private Object decode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return json.readValue(raw, valueType);
        } catch (RuntimeException failure) {
            // Formato viejo (despliegue con otro esquema) o dañado: se trata como fallo y se sobrescribe.
            log.debug("Valor ilegible en la caché {}: {}", spec.name(), failure.getClass().getSimpleName());
            return null;
        }
    }

    private String redisKey(String key) {
        return PREFIX + spec.name() + ':' + key;
    }
}
