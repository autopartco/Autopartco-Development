package com.autopartco.backend.shared.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

/**
 * Limitador sobre Redis con un {@code StringRedisTemplate} falso que reproduce el script Lua (ZSET por clave) sin
 * Redis real, y respaldo en memoria cuando Redis falla.
 */
class RedisRateLimiterTest {
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-11T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    /** Clave → marcas de tiempo (ms) de las llamadas admitidas: lo que haría el ZSET. */
    private final Map<String, List<Long>> zsets = new HashMap<>();
    private final List<String> keys = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private StringRedisTemplate fakeRedis() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(invocation -> {
            assertThat(invocation.<RedisScript<Long>>getArgument(0)).isSameAs(RedisRateLimitStore.SCRIPT);
            String key = invocation.<List<String>>getArgument(1).getFirst();
            Object[] args = (Object[]) invocation.getRawArguments()[2];
            long now = Long.parseLong((String) args[0]);
            long window = Long.parseLong((String) args[1]);
            int max = Integer.parseInt((String) args[2]);
            keys.add(key);
            List<Long> calls = zsets.computeIfAbsent(key, ignored -> new ArrayList<>());
            calls.removeIf(at -> at <= now - window);
            if (calls.size() >= max) {
                return 0L;
            }
            calls.add(now);
            return 1L;
        });
        return template;
    }

    private SlidingWindowRateLimiter limiter(StringRedisTemplate template, int max) {
        RedisGuard guard = new RedisGuard(template, Duration.ofSeconds(30), clock, CacheMetrics.NONE);
        return new RateLimiters(new RedisRateLimitStore(guard, new KeyHmac("sal-de-prueba")), clock)
                .create("auth", "AUTH_RATE_LIMITED", max, Duration.ofMinutes(1));
    }

    @Test
    void sharedWindowAppliesAcrossInstances() {
        StringRedisTemplate redis = fakeRedis();
        SlidingWindowRateLimiter first = limiter(redis, 3);
        SlidingWindowRateLimiter second = limiter(redis, 3);
        first.acquire("1.1.1.1");
        second.acquire("1.1.1.1");
        first.acquire("1.1.1.1");
        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class, () -> second.acquire("1.1.1.1"));
        assertThat(ex.getCode()).isEqualTo("AUTH_RATE_LIMITED");
        assertDoesNotThrow(() -> second.acquire("2.2.2.2"));
        assertThat(keys).allMatch(key -> key.startsWith("ap:rl:auth:"));
        assertThat(keys).noneMatch(key -> key.contains("1.1.1.1") || key.contains("2.2.2.2"));
    }

    @Test
    void ipPartOfTheKeyIsHmacWithTheSalt() {
        RedisGuard guard = new RedisGuard(mock(StringRedisTemplate.class), Duration.ofSeconds(30), clock,
                CacheMetrics.NONE);
        RedisRateLimitStore store = new RedisRateLimitStore(guard, new KeyHmac("sal-a"));
        RedisRateLimitStore otherSalt = new RedisRateLimitStore(guard, new KeyHmac("sal-b"));

        String key = store.redisKey("auth:login:10.0.0.1");
        assertThat(key).startsWith("ap:rl:auth:").doesNotContain("10.0.0.1").hasSize("ap:rl:auth:".length() + 32);
        assertThat(store.redisKey("auth:login:10.0.0.1")).isEqualTo(key);
        assertThat(otherSalt.redisKey("auth:login:10.0.0.1")).isNotEqualTo(key);
        assertThat(store.redisKey("auth:login:10.0.0.2")).isNotEqualTo(key);
    }

    @Test
    void windowSlidesInRedis() {
        SlidingWindowRateLimiter limiter = limiter(fakeRedis(), 2);
        limiter.acquire("k");
        limiter.acquire("k");
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("k"));
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.acquire("k"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisDownFallsBackToInMemoryWindow() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        SlidingWindowRateLimiter limiter = limiter(broken, 2);
        limiter.acquire("k");
        limiter.acquire("k");
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("k"));
    }

    @Test
    void withoutStoreUsesMemoryOnly() {
        SlidingWindowRateLimiter limiter = RateLimiters.inMemory(clock)
                .create("x", "X_LIMITED", 1, Duration.ofMinutes(1));
        limiter.acquire("k");
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("k"));
    }
}
