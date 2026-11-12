package com.autopartco.backend.marketplace.reviews;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Hilo acotado para la invitación a calificar, para no atarla a la petición que entrega el pedido. */
@Configuration
@EnableAsync
class ReviewMailAsyncConfig {
    static final String REVIEW_MAIL_EXECUTOR = "reviewMailExecutor";

    @Bean(REVIEW_MAIL_EXECUTOR)
    Executor reviewMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("review-mail-");
        // Con la cola llena se descarta: la invitación es opcional y no debe tumbar la entrega ya confirmada.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
