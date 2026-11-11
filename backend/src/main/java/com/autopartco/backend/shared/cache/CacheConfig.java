package com.autopartco.backend.shared.cache;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Cachés de §4 ({@link AppCaches}) con respaldo: Redis si hay {@code REDIS_URL} y responde ({@link RedisConfig}),
 * Caffeine local si no. Métricas de aciertos y fallos en el registro de Actuator ({@link CacheMetrics}).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    @Bean
    CacheMetrics cacheMetrics(ObjectProvider<MeterRegistry> registry) {
        return new CacheMetrics(registry.getIfAvailable());
    }

    @Bean
    @Primary
    ResilientCacheManager cacheManager(ObjectProvider<RedisGuard> redisGuard, CacheMetrics cacheMetrics) {
        return new ResilientCacheManager(AppCaches.SPECS, redisGuard.getIfAvailable(), cacheMetrics);
    }

    @Bean
    SharedCounters sharedCounters(ObjectProvider<RedisGuard> redisGuard) {
        return new SharedCounters(redisGuard.getIfAvailable(), Clock.systemUTC());
    }
}
