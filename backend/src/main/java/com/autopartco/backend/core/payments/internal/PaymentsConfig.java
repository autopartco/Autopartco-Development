package com.autopartco.backend.core.payments.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.autopartco.backend.core.payments.WompiProperties;

/**
 * Registra las propiedades de Wompi ({@code wompi.*}) y es la única activación de {@code @Scheduled} del backend
 * (plan Wompi, N11): la usan la conciliación y el vencimiento del marketplace y la conciliación de suscripciones,
 * que dependen de este módulo.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(WompiProperties.class)
public class PaymentsConfig {
    static final int SCHEDULER_POOL_SIZE = 2;

    /**
     * Único {@code TaskScheduler}: dos hilos para que un trabajo lento (p. ej. Wompi sin responder) no frene al otro.
     * Se llama {@code taskScheduler} para que {@code @Scheduled} lo use.
     */
    @Bean(name = "taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("payments-jobs-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
