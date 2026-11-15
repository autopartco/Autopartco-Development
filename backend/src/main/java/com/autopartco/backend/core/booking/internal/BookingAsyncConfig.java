package com.autopartco.backend.core.booking.internal;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Hilo acotado para los avisos de citas (in-app y correo), para no atarlos a la petición que cambia la cita. */
@Configuration
@EnableAsync
public class BookingAsyncConfig {
    public static final String BOOKING_NOTIFICATION_EXECUTOR = "bookingNotificationExecutor";

    @Bean(BOOKING_NOTIFICATION_EXECUTOR)
    Executor bookingNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("booking-notify-");
        // Con la cola llena se descarta: el aviso no debe tumbar la cita ya guardada.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
