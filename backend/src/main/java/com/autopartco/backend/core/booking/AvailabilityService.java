package com.autopartco.backend.core.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.BookingCatalog.BranchInfo;
import com.autopartco.backend.core.booking.internal.BookingCatalog.ServiceInfo;
import com.autopartco.backend.core.booking.internal.ServiceBooking;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;

/**
 * Franjas que se pueden pedir de un servicio visible en una de sus sedes activas: alineadas a {@code slot_minutes}
 * dentro de {@code opening_hours}, entre ahora + 2 h y ahora + 60 días (las demás no se listan). {@code busy} si ya hay
 * una cita {@code CONFIRMED} del mismo servicio y sede que se cruza.
 */
@Service
public class AvailabilityService {
    public static final int DEFAULT_DAYS = 7;
    public static final int MAX_DAYS = 14;

    private final BookingCatalog catalog;
    private final ServiceBookingRepository repository;
    private final Clock clock;

    @Autowired
    public AvailabilityService(BookingCatalog catalog, ServiceBookingRepository repository) {
        this(catalog, repository, Clock.system(BookingRules.ZONE));
    }

    /** {@code clock} fija «ahora» (pruebas). */
    public AvailabilityService(BookingCatalog catalog, ServiceBookingRepository repository, Clock clock) {
        this.catalog = catalog;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * @param from primer día (Bogotá); por defecto hoy, y nunca antes de hoy
     * @param days 1..14 (por defecto 7); fuera de rango 400 {@code AVAILABILITY_DAYS_INVALID}
     * @throws NotFoundException {@code SERVICE_NOT_FOUND} si el servicio no es visible
     * @throws BadRequestException {@code BRANCH_NOT_IN_SERVICE} o {@code BOOKING_DISABLED}
     */
    @Transactional(readOnly = true)
    public Availability availability(UUID serviceId, UUID branchId, LocalDate from, Integer days) {
        int count = days == null ? DEFAULT_DAYS : days;
        if (count < 1 || count > MAX_DAYS) {
            throw new BadRequestException("AVAILABILITY_DAYS_INVALID",
                    "Puedes consultar de 1 a " + MAX_DAYS + " días.");
        }
        ServiceInfo service = catalog.service(serviceId).filter(ServiceInfo::visible)
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND", "No se encontró el servicio."));
        if (branchId == null) {
            throw new BadRequestException("BRANCH_NOT_IN_SERVICE", "Elige la sede.");
        }
        BranchInfo branch = catalog.serviceBranch(serviceId, branchId).filter(BranchInfo::active)
                .orElseThrow(() -> new BadRequestException("BRANCH_NOT_IN_SERVICE", "La sede no presta este servicio."));
        if (!service.bookingEnabled() || !BookingRules.hasWeeklyHours(branch.openingHours())) {
            throw BookingService.bookingDisabled();
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, BookingRules.ZONE);
        LocalDate first = from == null || from.isBefore(today) ? today : from;
        LocalDate last = first.plusDays(count - 1L);
        Instant rangeStart = first.atStartOfDay(BookingRules.ZONE).toInstant();
        Instant rangeEnd = last.plusDays(1).atStartOfDay(BookingRules.ZONE).toInstant();
        List<ServiceBooking> confirmed = repository.findConfirmedOverlapping(serviceId, branchId, rangeStart,
                rangeEnd);

        Duration length = Duration.ofMinutes(service.slotMinutes());
        List<Availability.Day> result = new ArrayList<>(count);
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            List<Availability.Slot> slots = new ArrayList<>();
            for (ZonedDateTime start : BookingRules.slotStarts(branch.openingHours(), date, service.slotMinutes())) {
                Instant startsAt = start.toInstant();
                if (!BookingRules.withinWindow(startsAt, now)) {
                    continue;
                }
                Instant endsAt = startsAt.plus(length);
                boolean busy = confirmed.stream().anyMatch(booking -> booking.getStartsAt().isBefore(endsAt)
                        && booking.getEndsAt().isAfter(startsAt));
                slots.add(new Availability.Slot(start.toOffsetDateTime(), busy));
            }
            result.add(new Availability.Day(date, slots));
        }
        return new Availability(service.slotMinutes(), result);
    }
}
