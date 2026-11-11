package com.autopartco.backend.core.identity.internal;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Hilos acotados para el correo de verificación, para no atarlo al tiempo de la petición. */
@Configuration
@EnableAsync
class IdentityAsyncConfig {
    static final String VERIFICATION_MAIL_EXECUTOR = "verificationMailExecutor";

    @Bean(VERIFICATION_MAIL_EXECUTOR)
    Executor verificationMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("verification-mail-");
        // Con la cola llena se descarta: el usuario puede pedir el reenvío y no se tumba la petición ya confirmada.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
