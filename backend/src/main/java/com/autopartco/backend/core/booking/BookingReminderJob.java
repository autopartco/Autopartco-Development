package com.autopartco.backend.core.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Cada hora recuerda al comprador sus citas {@code CONFIRMED} que empiezan dentro de las próximas
 * {@value #WINDOW_HOURS} h (y no antes de {@value #MIN_HOURS} h, para no avisar de lo inminente). Una sola vez por cita:
 * el envío queda en auditoría ({@value #ACTION}) y una cita ya marcada no se vuelve a avisar, aunque la tarea se repita.
 */
@Component
@Slf4j
public class BookingReminderJob {
    static final String ACTION = "BOOKING_REMINDER_SENT";
    static final int WINDOW_HOURS = 24;
    static final int MIN_HOURS = 2;
    static final int BATCH = 200;

    private final ServiceBookingRepository repository;
    private final BookingNotifications notifications;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public BookingReminderJob(ServiceBookingRepository repository, BookingNotifications notifications,
            AuditService auditService) {
        this(repository, notifications, auditService, Clock.system(BookingRules.ZONE));
    }

    BookingReminderJob(ServiceBookingRepository repository, BookingNotifications notifications,
            AuditService auditService, Clock clock) {
        this.repository = repository;
        this.notifications = notifications;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "America/Bogota")
    public void run() {
        int sent = remindDue();
        if (sent > 0) {
            log.info("Sent {} service booking reminders", sent);
        }
    }

    int remindDue() {
        Instant now = clock.instant();
        List<UUID> due = repository.findConfirmedStartingBetween(now.plus(Duration.ofHours(MIN_HOURS)),
                now.plus(Duration.ofHours(WINDOW_HOURS)), PageRequest.of(0, BATCH));
        int sent = 0;
        for (UUID bookingId : due) {
            if (auditService.exists(ACTION, BookingService.ENTITY, bookingId)) {
                continue;
            }
            try {
                if (notifications.remind(bookingId)) {
                    sent++;
                }
            } catch (RuntimeException failure) {
                log.warn("Could not send service booking reminder ({})", failure.getClass().getSimpleName());
                continue;
            }
            auditService.record(null, ACTION, BookingService.ENTITY, bookingId, Map.of());
        }
        return sent;
    }
}
