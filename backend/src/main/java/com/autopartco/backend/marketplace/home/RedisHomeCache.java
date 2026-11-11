package com.autopartco.backend.marketplace.home;

import java.util.function.Supplier;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ConditionalOnRedis;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.TypedCache;

/**
 * {@link HomeCache} compartida en Redis (caché {@code home}, 60 s por área redondeada a 0,1°). Solo con
 * {@code REDIS_URL}; si Redis no responde, esa llamada usa la copia local. Sin Redis queda {@link InMemoryHomeCache}.
 * Los anuncios y sus tokens nunca pasan por aquí.
 */
@Component
@Primary
@ConditionalOnRedis
class RedisHomeCache implements HomeCache {
    private final TypedCache<HomeContent> cache;

    RedisHomeCache(ResilientCacheManager caches) {
        this.cache = caches.typed(AppCaches.HOME, HomeContent.class);
    }

    @Override
    public HomeContent get(HomeArea area, Supplier<HomeContent> loader) {
        return cache.get(key(area), loader);
    }

    static String key(HomeArea area) {
        return (area.lat() == null ? "-" : area.lat().toPlainString()) + '|'
                + (area.lng() == null ? "-" : area.lng().toPlainString()) + '|'
                + (area.cityCode() == null ? "-" : area.cityCode());
    }
}
