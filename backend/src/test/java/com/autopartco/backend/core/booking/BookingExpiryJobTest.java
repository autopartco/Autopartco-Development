package com.autopartco.backend.core.booking;

import static com.autopartco.backend.core.booking.BookingFixtures.CLOCK;
import static com.autopartco.backend.core.booking.BookingFixtures.NOW;
import static com.autopartco.backend.core.booking.BookingFixtures.at;
import static com.autopartco.backend.core.booking.BookingFixtures.booking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.BookingChanged;
import com.autopartco.backend.core.booking.internal.ServiceBooking;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.core.identity.AccountLookup;

class BookingExpiryJobTest {
    private final ServiceBookingRepository repository = mock(ServiceBookingRepository.class);
    private final BookingCatalog catalog = mock(BookingCatalog.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BookingService bookingService =
            new BookingService(repository, catalog, accountLookup, auditService, events, CLOCK);
    private final BookingExpiryJob job = new BookingExpiryJob(repository, bookingService, CLOCK);

    private final UUID requestedId = UUID.randomUUID();
    private final UUID proposedId = UUID.randomUUID();
    private final UUID confirmedMeanwhileId = UUID.randomUUID();
    private ServiceBooking requested;
    private ServiceBooking proposed;
    private ServiceBooking confirmedMeanwhile;

    @BeforeEach
    void setUp() {
        requested = booking(BookingStatus.REQUESTED, "2026-11-16T07:00");
        requested.setId(requestedId);
        proposed = booking(BookingStatus.PROPOSED, "2026-11-15T09:00");
        proposed.setId(proposedId);
        proposed.setProposedStartsAt(NOW.minus(Duration.ofMinutes(30)));
        // Lo confirmaron entre la consulta y el bloqueo: ya no se vence.
        confirmedMeanwhile = booking(BookingStatus.CONFIRMED, "2026-11-16T07:00");
        confirmedMeanwhile.setId(confirmedMeanwhileId);
        when(repository.findDueToExpire(NOW, PageRequest.of(0, BookingExpiryJob.BATCH)))
                .thenReturn(List.of(requestedId, proposedId, confirmedMeanwhileId));
        when(repository.lockById(requestedId)).thenReturn(Optional.of(requested));
        when(repository.lockById(proposedId)).thenReturn(Optional.of(proposed));
        when(repository.lockById(confirmedMeanwhileId)).thenReturn(Optional.of(confirmedMeanwhile));
        when(repository.saveAndFlush(any(ServiceBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dueRequestsAndProposalsBecomeExpired() {
        int expired = job.expireDue();

        assertThat(expired).isEqualTo(2);
        assertThat(requested.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(proposed.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(confirmedMeanwhile.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(events).publishEvent(new BookingChanged(requestedId, BookingStatus.REQUESTED, BookingStatus.EXPIRED,
                null));
        verify(events).publishEvent(new BookingChanged(proposedId, BookingStatus.PROPOSED, BookingStatus.EXPIRED,
                null));
        verify(auditService).record(isNull(), eq("BOOKING_EXPIRED"), eq(BookingService.ENTITY), eq(requestedId),
                anyMap());
        verify(repository, never()).saveAndFlush(confirmedMeanwhile);
    }

    @Test
    void proposalWithAFutureProposedTimeIsNotExpired() {
        proposed.setProposedStartsAt(at("2026-11-18T15:00").toInstant());

        assertThat(job.expireDue()).isEqualTo(1);
        assertThat(proposed.getStatus()).isEqualTo(BookingStatus.PROPOSED);
    }

    @Test
    void aFailingBookingDoesNotStopTheBatch() {
        when(repository.lockById(requestedId)).thenThrow(new IllegalStateException("db"));

        assertThat(job.expireDue()).isEqualTo(1);
        assertThat(proposed.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void runsEveryFifteenMinutesInBogota() throws Exception {
        Method run = BookingExpiryJob.class.getMethod("run");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 */15 * * * *");
        assertThat(scheduled.zone()).isEqualTo("America/Bogota");
    }

    // --- Recordatorio 24 h antes (misma familia de tareas programadas) ------------------------------------------------

    @Test
    void reminderIsSentOnceAndMarkedInTheAuditLog() {
        BookingNotifications notifications = mock(BookingNotifications.class);
        BookingReminderJob reminders = new BookingReminderJob(repository, notifications, auditService, CLOCK);
        UUID alreadySent = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        when(repository.findConfirmedStartingBetween(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(24)),
                PageRequest.of(0, BookingReminderJob.BATCH))).thenReturn(List.of(alreadySent, pending));
        when(auditService.exists(BookingReminderJob.ACTION, BookingService.ENTITY, alreadySent)).thenReturn(true);
        when(notifications.remind(pending)).thenReturn(true);

        assertThat(reminders.remindDue()).isEqualTo(1);

        verify(notifications, never()).remind(alreadySent);
        verify(auditService).record(null, BookingReminderJob.ACTION, BookingService.ENTITY, pending, Map.of());
        verify(auditService, never()).record(isNull(), eq(BookingReminderJob.ACTION), eq(BookingService.ENTITY),
                eq(alreadySent), anyMap());
    }

    @Test
    void failedReminderIsNotMarkedSoItIsRetried() {
        BookingNotifications notifications = mock(BookingNotifications.class);
        BookingReminderJob reminders = new BookingReminderJob(repository, notifications, auditService, CLOCK);
        UUID pending = UUID.randomUUID();
        when(repository.findConfirmedStartingBetween(any(), any(), any())).thenReturn(List.of(pending));
        when(notifications.remind(pending)).thenThrow(new IllegalStateException("smtp"));

        assertThat(reminders.remindDue()).isZero();
        verify(auditService, never()).record(any(), eq(BookingReminderJob.ACTION), any(), any(), anyMap());
    }
}
