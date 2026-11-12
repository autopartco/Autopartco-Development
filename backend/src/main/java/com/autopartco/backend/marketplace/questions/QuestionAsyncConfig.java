package com.autopartco.backend.marketplace.questions;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Hilo acotado para los avisos de preguntas (in-app y correo), para no atarlos a la petición que pregunta o responde. */
@Configuration
@EnableAsync
class QuestionAsyncConfig {
    static final String QUESTION_NOTIFICATION_EXECUTOR = "questionNotificationExecutor";

    @Bean(QUESTION_NOTIFICATION_EXECUTOR)
    Executor questionNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("question-notify-");
        // Con la cola llena se descarta: el aviso no debe tumbar la pregunta o la respuesta ya guardadas.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
