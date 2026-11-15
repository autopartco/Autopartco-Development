package com.autopartco.backend.core.booking;

import static com.autopartco.backend.core.booking.BookingFixtures.BRANCH;
import static com.autopartco.backend.core.booking.BookingFixtures.CLOCK;
import static com.autopartco.backend.core.booking.BookingFixtures.SERVICE;
import static com.autopartco.backend.core.booking.BookingFixtures.at;
import static com.autopartco.backend.core.booking.BookingFixtures.booking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;

class AvailabilityServiceTest {
    private final BookingCatalog catalog = mock(BookingCatalog.class);
    private final ServiceBookingRepository repository = mock(ServiceBookingRepository.class);
    private final AvailabilityService service = new AvailabilityService(catalog, repository, CLOCK);

    @BeforeEach
    void setUp() {
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service()));
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(BookingFixtures.branch()));
        when(repository.findConfirmedOverlapping(eq(SERVICE), eq(BRANCH), any(), any())).thenReturn(List.of());
    }

    private static List<OffsetDateTime> starts(Availability.Day day) {
        return day.slots().stream().map(Availability.Slot::startsAt).toList();
    }

    @Test
    void todayOnlyListsSlotsAtLeastTwoHoursAhead() {
        Availability availability = service.availability(SERVICE, BRANCH, null, 2);

        assertThat(availability.slotMinutes()).isEqualTo(60);
        assertThat(availability.days()).extracting(Availability.Day::date)
                .containsExactly(LocalDate.parse("2026-11-16"), LocalDate.parse("2026-11-17"));
        // «Ahora» son las 08:00: la primera franja posible es la de las 10:00.
        assertThat(starts(availability.days().get(0))).containsExactly(at("2026-11-16T10:00"),
                at("2026-11-16T11:00"), at("2026-11-16T14:00"), at("2026-11-16T15:00"), at("2026-11-16T16:00"),
                at("2026-11-16T17:00"));
        assertThat(availability.days().get(1).slots()).hasSize(8);
    }

    @Test
    void slotsFollowSlotMinutesAndClosedDaysAreEmpty() {
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service(true, 120)));

        Availability availability = service.availability(SERVICE, BRANCH, LocalDate.parse("2026-11-21"), 2);

        assertThat(starts(availability.days().get(0))).containsExactly(at("2026-11-21T08:00"),
                at("2026-11-21T10:00"));
        assertThat(availability.days().get(1).slots()).isEmpty();
    }

    @Test
    void confirmedOverlappingBookingMarksSlotsBusy() {
        var confirmed = booking(BookingStatus.CONFIRMED, "2026-11-17T09:30");
        when(repository.findConfirmedOverlapping(eq(SERVICE), eq(BRANCH), any(), any()))
                .thenReturn(List.of(confirmed));

        Availability availability = service.availability(SERVICE, BRANCH, LocalDate.parse("2026-11-17"), 1);

        List<Availability.Slot> slots = availability.days().get(0).slots();
        assertThat(slots).filteredOn(Availability.Slot::busy).extracting(Availability.Slot::startsAt)
                .containsExactly(at("2026-11-17T09:00"), at("2026-11-17T10:00"));
        verify(repository).findConfirmedOverlapping(SERVICE, BRANCH, Instant.parse("2026-11-17T05:00:00Z"),
                Instant.parse("2026-11-18T05:00:00Z"));
    }

    @Test
    void pastStartDateIsClampedToTodayAndTheWindowEndsAtSixtyDays() {
        Availability past = service.availability(SERVICE, BRANCH, LocalDate.parse("2026-11-01"), 1);
        assertThat(past.days().get(0).date()).isEqualTo(LocalDate.parse("2026-11-16"));

        Availability far = service.availability(SERVICE, BRANCH, LocalDate.parse("2027-01-15"), 5);
        // 15-ene (viernes) está dentro de los 60 días; desde el 18-ene ya no se ofrece nada.
        assertThat(far.days().get(0).slots()).isNotEmpty();
        assertThat(far.days().get(3).slots()).isEmpty();
    }

    @Test
    void daysOutOfRangeAreRejected() {
        for (int days : new int[] {0, 15}) {
            assertThatThrownBy(() -> service.availability(SERVICE, BRANCH, null, days))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("AVAILABILITY_DAYS_INVALID");
        }
    }

    @Test
    void branchMustOfferTheServiceAndBeActive() {
        UUID other = UUID.randomUUID();
        when(catalog.serviceBranch(SERVICE, other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.availability(SERVICE, other, null, 1))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BRANCH_NOT_IN_SERVICE");
        assertThatThrownBy(() -> service.availability(SERVICE, null, null, 1))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BRANCH_NOT_IN_SERVICE");

        BookingCatalog.BranchInfo inactive = new BookingCatalog.BranchInfo(BRANCH, "Sede", false,
                BookingFixtures.hours(), null, null, null, null, null, null, null);
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.availability(SERVICE, BRANCH, null, 1))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BRANCH_NOT_IN_SERVICE");
    }

    @Test
    void disabledBookingOrMissingHoursIsBookingDisabled() {
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service(false, 60)));
        assertThatThrownBy(() -> service.availability(SERVICE, BRANCH, null, 1))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BOOKING_DISABLED");

        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service()));
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(BookingFixtures.branch(null)));
        assertThatThrownBy(() -> service.availability(SERVICE, BRANCH, null, 1))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BOOKING_DISABLED");
    }

    @Test
    void invisibleServiceIsNotFound() {
        when(catalog.service(SERVICE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.availability(SERVICE, BRANCH, null, 1))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("SERVICE_NOT_FOUND");
    }
}
