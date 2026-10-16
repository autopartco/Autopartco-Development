package com.autopartco.backend.storesuite.onboarding;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Hilo dedicado al correo del enlace de configuración, para no atarlo al tiempo de la petición. */
@Configuration
@EnableAsync
class OnboardingAsyncConfig {
    static final String SETUP_MAIL_EXECUTOR = "setupMailExecutor";

    @Bean(SETUP_MAIL_EXECUTOR)
    Executor setupMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("setup-mail-");
        return executor;
    }
}
