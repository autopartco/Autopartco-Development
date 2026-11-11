package com.autopartco.backend.shared.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import com.autopartco.backend.shared.web.RateLimitStore;

/**
 * Ventana deslizante en un {@code ZSET} de Redis por clave ({@value #PREFIX}{@code <limitador>:<ip>}), atómica con un
 * script Lua: descarta las llamadas fuera de la ventana, cuenta y, si cabe, agrega la actual. La clave caduca con la
 * ventana. Si Redis falla, devuelve vacío y el limitador usa su ventana en memoria.
 */
public class RedisRateLimitStore implements RateLimitStore {
    static final String PREFIX = "ap:rl:";
    static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local max = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            if redis.call('ZCARD', key) >= max then
              redis.call('PEXPIRE', key, window)
              return 0
            end
            redis.call('ZADD', key, now, ARGV[4])
            redis.call('PEXPIRE', key, window)
            return 1
            """, Long.class);

    private final RedisGuard redis;

    public RedisRateLimitStore(RedisGuard redis) {
        this.redis = redis;
    }

    @Override
    public Optional<Boolean> tryAcquire(String key, int max, Duration window, Instant now) {
        String member = now.toEpochMilli() + "-" + UUID.randomUUID();
        return redis.call("ratelimit", r -> r.execute(SCRIPT, List.of(PREFIX + key),
                        String.valueOf(now.toEpochMilli()), String.valueOf(window.toMillis()), String.valueOf(max),
                        member))
                .map(result -> result == 1L);
    }
}
