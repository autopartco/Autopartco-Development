package com.autopartco.backend.shared.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.shared.web.RateLimitStore;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.core.type.TypeReference;

/** Sin {@code REDIS_URL} arranca con Caffeine; con Redis caído o lento, la caché responde desde memoria. */
class CacheConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class, RedisConfig.class)
            .withBean(SimpleMeterRegistry.class);

    @Test
    void withoutRedisUrlStartsWithLocalCachesAndNoRedisBeans() {
        runner.withPropertyValues("spring.data.redis.url=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
            assertThat(context).doesNotHaveBean(RedisGuard.class);
            assertThat(context).doesNotHaveBean(RateLimitStore.class);
            ResilientCacheManager manager = context.getBean(ResilientCacheManager.class);
            assertThat(context.getBean(CacheManager.class)).isSameAs(manager);
            assertThat(manager.redisConfigured()).isFalse();
            assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("catalog:options", "search:results",
                    "search:suggest", "store:profile", "store:branches", "settings:store", "home", "ads:active");
            assertThat(manager.getCache(AppCaches.SEARCH_RESULTS).getNativeCache())
                    .isInstanceOf(com.github.benmanes.caffeine.cache.Cache.class);

            TypedCache<String> cache = manager.typed(AppCaches.HOME, String.class);
            AtomicInteger loads = new AtomicInteger();
            assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
            assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
            cache.evict("k");
            assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v2");

            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            assertThat(registry.get(CacheMetrics.GETS).tag("cache", "home").tag("result", "hit").counter().count())
                    .isEqualTo(1.0);
            assertThat(registry.get(CacheMetrics.GETS).tag("cache", "home").tag("result", "miss").counter().count())
                    .isEqualTo(2.0);
        });
    }

    @Test
    void withUnreachableRedisStartsAndFallsBackToMemory() {
        // Puerto 1 en localhost: conexión rechazada al primer uso; la conexión es perezosa, así que arranca igual.
        runner.withPropertyValues("spring.data.redis.url=redis://127.0.0.1:1", "spring.data.redis.timeout=200ms",
                "spring.data.redis.connect-timeout=200ms").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisGuard.class);
                    ResilientCacheManager manager = context.getBean(ResilientCacheManager.class);
                    assertThat(manager.redisConfigured()).isTrue();
                    TypedCache<String> cache = manager.typed(AppCaches.STORE_PROFILE, String.class);
                    AtomicInteger loads = new AtomicInteger();
                    assertThat(cache.get("s", () -> "p" + loads.incrementAndGet())).isEqualTo("p1");
                    // Circuito abierto: la segunda lectura ya no intenta Redis y acierta en Caffeine.
                    assertThat(context.getBean(RedisGuard.class).available()).isFalse();
                    assertThat(cache.get("s", () -> "p" + loads.incrementAndGet())).isEqualTo("p1");
                });
    }

    @Test
    void redisTierStoresJsonAndReadsItBackTyped() {
        Map<String, String> store = new HashMap<>();
        StringRedisTemplate template = fakeRedis(store);
        RedisGuard guard = new RedisGuard(template, Duration.ofSeconds(30), Clock.systemUTC(), CacheMetrics.NONE);
        ResilientCacheManager manager = new ResilientCacheManager(AppCaches.SPECS, guard, CacheMetrics.NONE);
        TypedCache<List<SearchResult.Hit>> cache = manager.typed(AppCaches.SEARCH_RESULTS,
                new TypeReference<List<SearchResult.Hit>>() {
                }.getType());

        List<SearchResult.Hit> hits = List.of(new SearchResult.Hit(listing(), new BigDecimal("1.5")));
        assertThat(cache.get("k", () -> hits)).isEqualTo(hits);
        assertThat(store).containsKey("ap:c:search:results:k");

        List<SearchResult.Hit> read = cache.get("k", () -> {
            throw new AssertionError("debió leerse de Redis");
        });
        assertThat(read).hasSize(1);
        assertThat(read.getFirst().listing().id()).isEqualTo(hits.getFirst().listing().id());
        assertThat(read.getFirst().listing().effectivePrice()).isEqualByComparingTo("80000.00");
        assertThat(read.getFirst().distanceKm()).isEqualByComparingTo("1.5");
    }

    @Test
    void redisFailureUsesLocalCopyWithoutThrowing() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"));
        RedisGuard guard = new RedisGuard(template, Duration.ofSeconds(30), Clock.systemUTC(), CacheMetrics.NONE);
        TypedCache<String> cache = new ResilientCacheManager(AppCaches.SPECS, guard, CacheMetrics.NONE)
                .typed(AppCaches.HOME, String.class);
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.get("a", () -> "x" + loads.incrementAndGet())).isEqualTo("x1");
        assertThat(cache.get("a", () -> "x" + loads.incrementAndGet())).isEqualTo("x1");
        assertThat(guard.available()).isFalse();
    }

    @SuppressWarnings("unchecked")
    static StringRedisTemplate fakeRedis(Map<String, String> store) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> store.get(invocation.<String>getArgument(0)));
        doAnswer(invocation -> store.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        return template;
    }

    private static ProductResponse listing() {
        return new ProductResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Centro", "Pastillas",
                "SKU-1", null, null, new BigDecimal("85000.00"), new BigDecimal("80000.00"),
                new BigDecimal("5000.00"), null, 3, null, "Bosch", true, null, "ACTIVE", false,
                Map.of("posicion", "delantera"), List.of(), List.of());
    }
}
