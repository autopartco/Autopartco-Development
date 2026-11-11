package com.autopartco.backend.shared.cache;

import java.util.function.Supplier;

/**
 * Vista tipada de una {@link ResilientCache}: el tipo permite guardarla en Redis como JSON. Las claves son texto; quien
 * las arma decide qué entra (nunca datos personales ni tokens).
 */
public final class TypedCache<V> {
    private final ResilientCache cache;

    TypedCache(ResilientCache cache) {
        this.cache = cache;
    }

    /** Valor vigente de {@code key} o el de {@code loader} (que se guarda si no es {@code null}). */
    public V get(String key, Supplier<V> loader) {
        return cache.get(key, loader);
    }

    public void evict(String key) {
        cache.evict(key);
    }

    public void clear() {
        cache.clear();
    }

    public String name() {
        return cache.getName();
    }
}
