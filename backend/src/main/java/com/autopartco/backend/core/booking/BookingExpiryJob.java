package com.autopartco.backend.core.booking;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Cada 15 minutos vence las solicitudes sin respuesta cuya hora llegó ({@code REQUESTED}/{@code PROPOSED} →
 * {@code EXPIRED}), hasta {@value #BATCH} por ejecución. Cada cita va en su propia transacción
 * ({@link BookingService#expire}, que bloquea la fila y revalida): dos instancias a la vez no la vencen dos veces y el
 * aviso al comprador sale tras el commit. {@code @EnableScheduling} lo activa {@code core.payments.internal.PaymentsConfig}.
 */
@Component
@Slf4j
public class BookingExpiryJob {
    static final int BATCH = 200;

    private final ServiceBookingRepository repository;
    private final BookingService bookingService;
    private final Clock clock;

    @Autowired
    public BookingExpiryJob(ServiceBookingRepository repository, BookingService bookingService) {
        this(repository, bookingService, Clock.system(BookingRules.ZONE));
    }

    BookingExpiryJob(ServiceBookingRepository repository, BookingService bookingService, Clock clock) {
        this.repository = repository;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "America/Bogota")
    public void run() {
        int expired = expireDue();
        if (expired > 0) {
            log.info("Expired {} service bookings", expired);
        }
    }

    /** Vence lo pendiente; devuelve cuántas citas cambiaron. */
    int expireDue() {
        List<UUID> due = repository.findDueToExpire(clock.instant(), PageRequest.of(0, BATCH));
        int expired = 0;
        for (UUID bookingId : due) {
            try {
                if (bookingService.expire(bookingId)) {
                    expired++;
                }
            } catch (RuntimeException failure) {
                log.warn("Could not expire service booking ({})", failure.getClass().getSimpleName());
            }
        }
        return expired;
    }
}
