package com.autopartco.backend.marketplace.home;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.cache.TtlCache;

/** {@link HomeCache} en memoria con caducidad de {@value #TTL_SECONDS} s, por instancia. */
@Component
class InMemoryHomeCache implements HomeCache {
    static final long TTL_SECONDS = 60;

    private final TtlCache<HomeArea, HomeContent> cache = new TtlCache<>(TTL_SECONDS);

    @Override
    public HomeContent get(HomeArea area, Supplier<HomeContent> loader) {
        return cache.get(area, loader);
    }
}
