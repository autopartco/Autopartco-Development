package com.autopartco.backend.shared.cache;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contadores de versión compartidos entre instancias (p. ej. {@code catalogVersion} de {@code search:results}). Con
 * Redis viven en {@value #PREFIX}{@code <nombre>} y cada instancia relee el valor como mucho una vez por segundo; sin
 * Redis (o si no responde) se usa el contador local, que siempre avanza también.
 */
public class SharedCounters {
    static final String PREFIX = "ap:ver:";
    static final long REFRESH_MILLIS = 1_000;

    private record Memo(long value, long readAtMillis) {
    }

    private final RedisGuard redis;
    private final Clock clock;
    private final Map<String, AtomicLong> local = new ConcurrentHashMap<>();
    private final Map<String, Memo> remote = new ConcurrentHashMap<>();

    public SharedCounters(RedisGuard redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /** Solo local (sin Redis). */
    public static SharedCounters local() {
        return new SharedCounters(null, Clock.systemUTC());
    }

    /** Valor actual; el prefijo {@code r} o {@code l} distingue el origen para no mezclar claves de ambos. */
    public String current(String name) {
        if (redis != null && redis.available()) {
            long now = clock.millis();
            Memo memo = remote.get(name);
            if (memo != null && now - memo.readAtMillis() < REFRESH_MILLIS) {
                return "r" + memo.value();
            }
            Optional<String> raw = redis.call("version", r -> {
                String value = r.opsForValue().get(PREFIX + name);
                return value == null ? "0" : value;
            });
            if (raw.isPresent()) {
                long value = parse(raw.get());
                remote.put(name, new Memo(value, now));
                return "r" + value;
            }
        }
        return "l" + counter(name).get();
    }

    /** Sube la versión (local y, si responde, en Redis). */
    public void increment(String name) {
        counter(name).incrementAndGet();
        remote.remove(name);
        if (redis != null) {
            redis.run("incr", r -> r.opsForValue().increment(PREFIX + name));
        }
    }

    private AtomicLong counter(String name) {
        return local.computeIfAbsent(name, ignored -> new AtomicLong());
    }

    private static long parse(String raw) {
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }
}
