package com.autopartco.backend.shared.cache;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activa el bean solo si hay Redis configurado: {@value #URL_PROPERTY} con valor ({@code REDIS_URL} en el {@code .env}
 * raíz). Sin ella la app usa las versiones en memoria y nunca intenta conectarse a Redis.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnRedis.RedisUrlPresent.class)
public @interface ConditionalOnRedis {
    String URL_PROPERTY = "spring.data.redis.url";

    final class RedisUrlPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String url = context.getEnvironment().getProperty(URL_PROPERTY);
            return url != null && !url.isBlank();
        }
    }
}
