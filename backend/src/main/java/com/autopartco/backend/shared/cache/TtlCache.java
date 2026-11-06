package com.autopartco.backend.shared.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Caché en memoria con caducidad, sin dependencias. Los valores deben ser inmutables. Una sola carga por clave a la
 * vez (las demás peticiones esperan el resultado en vez de repetir la consulta). No cachea excepciones.
 */
public final class TtlCache<K, V> {
    private static final List<TtlCache<?, ?>> ALL = new CopyOnWriteArrayList<>();
    private static final int MAX_ENTRIES = 500;

    private record Entry<V>(V value, long expiresAtNanos) {
    }

    private final long ttlNanos;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Map<K, Object> locks = new ConcurrentHashMap<>();

    public TtlCache(long ttlSeconds) {
        this.ttlNanos = ttlSeconds * 1_000_000_000L;
        ALL.add(this);
    }

    public V get(K key, Supplier<V> loader) {
        Entry<V> hit = entries.get(key);
        if (hit != null && hit.expiresAtNanos() > System.nanoTime()) {
            return hit.value();
        }
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            hit = entries.get(key);
            if (hit != null && hit.expiresAtNanos() > System.nanoTime()) {
                return hit.value();
            }
            V value = loader.get();
            if (entries.size() >= MAX_ENTRIES) {
                entries.clear();
            }
            entries.put(key, new Entry<>(value, System.nanoTime() + ttlNanos));
            return value;
        }
    }

    public void clear() {
        entries.clear();
    }

    /** Vacía todas las cachés; si hay una transacción en curso, lo hace al confirmarla (evita recachear datos viejos). */
    public static void invalidateAll() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    clearAll();
                }
            });
        } else {
            clearAll();
        }
    }

    private static void clearAll() {
        ALL.forEach(TtlCache::clear);
    }
}
