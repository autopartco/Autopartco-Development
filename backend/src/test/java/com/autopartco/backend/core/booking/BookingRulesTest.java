package com.autopartco.backend.core.booking;

import static com.autopartco.backend.core.booking.BookingFixtures.NOW;
import static com.autopartco.backend.core.booking.BookingFixtures.at;
import static com.autopartco.backend.core.booking.BookingFixtures.hours;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;

class BookingRulesTest {

    @Test
    void alignedSlotInsideOpeningHoursIsAccepted() {
        Instant start = BookingRules.requireSlot(at("2026-11-17T09:00"), 60, hours(), NOW);

        assertThat(start).isEqualTo(Instant.parse("2026-11-17T14:00:00Z"));
    }

    @ParameterizedTest(name = "{0} → BOOKING_SLOT_INVALID")
    @CsvSource({
            // No alineada a 60 minutos desde la apertura de las 08:00.
            "2026-11-17T09:30",
            // Hora de almuerzo (entre franjas) y fuera del cierre.
            "2026-11-17T12:00",
            "2026-11-17T18:00",
            // La franja de 60 minutos no cabe antes del cierre.
            "2026-11-17T17:30",
            // Domingo cerrado.
            "2026-11-22T09:00",
            // Menos de 2 h desde «ahora» (lunes 08:00).
            "2026-11-16T09:00",
            // Más de 60 días.
            "2027-01-18T09:00",
            // En el pasado.
            "2026-11-13T09:00"
    })
    void invalidSlotsAreRejected(String localStart) {
        assertThatThrownBy(() -> BookingRules.requireSlot(at(localStart), 60, hours(), NOW))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo(BookingRules.SLOT_INVALID);
    }

    @Test
    void secondsOrMinutesOffTheGridAreRejected() {
        assertThatThrownBy(() -> BookingRules.requireSlot(at("2026-11-17T09:00:30"), 60, hours(), NOW))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo(BookingRules.SLOT_INVALID);
    }

    @Test
    void exactlyTwoHoursAheadIsAccepted() {
        assertThat(BookingRules.requireSlot(at("2026-11-16T10:00"), 60, hours(), NOW))
                .isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void missingStartIsASlotError() {
        assertThatThrownBy(() -> BookingRules.requireSlot(null, 60, hours(), NOW))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo(BookingRules.SLOT_INVALID);
    }

    @Test
    void slotsAlignFromEachOpeningAndFitBeforeClosing() {
        List<LocalTime> starts = BookingRules.slotStarts(hours(), LocalDate.parse("2026-11-17"), 90).stream()
                .map(ZonedDateTime::toLocalTime).toList();

        assertThat(starts).containsExactly(LocalTime.parse("08:00"), LocalTime.parse("09:30"),
                LocalTime.parse("14:00"), LocalTime.parse("15:30"));
        assertThat(BookingRules.slotStarts(hours(), LocalDate.parse("2026-11-22"), 60)).isEmpty();
    }

    @Test
    void weeklyHoursAreRequiredForBooking() {
        assertThat(BookingRules.hasWeeklyHours(hours())).isTrue();
        assertThat(BookingRules.hasWeeklyHours(null)).isFalse();
        assertThat(BookingRules.hasWeeklyHours(java.util.Map.of("notes", "Solo con cita"))).isFalse();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "REQUESTED, CONFIRMED", "REQUESTED, PROPOSED", "REQUESTED, REJECTED", "REQUESTED, CANCELLED_BY_BUYER",
            "REQUESTED, EXPIRED", "PROPOSED, CONFIRMED", "PROPOSED, CANCELLED_BY_BUYER", "PROPOSED, EXPIRED",
            "CONFIRMED, COMPLETED", "CONFIRMED, NO_SHOW", "CONFIRMED, CANCELLED_BY_STORE",
            "CONFIRMED, CANCELLED_BY_BUYER"
    })
    void allowedTransitions(BookingStatus from, BookingStatus to) {
        assertThat(BookingRules.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} es 409")
    @CsvSource({
            "REQUESTED, COMPLETED", "REQUESTED, CANCELLED_BY_STORE", "PROPOSED, REJECTED", "PROPOSED, PROPOSED",
            "CONFIRMED, REJECTED", "CONFIRMED, EXPIRED", "CONFIRMED, PROPOSED", "COMPLETED, NO_SHOW",
            "EXPIRED, CONFIRMED", "REJECTED, CONFIRMED", "CANCELLED_BY_BUYER, CONFIRMED", "NO_SHOW, COMPLETED"
    })
    void otherTransitionsAreConflicts(BookingStatus from, BookingStatus to) {
        assertThatThrownBy(() -> BookingRules.requireTransition(from, to, NOW.minus(Duration.ofDays(1)), NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(BookingRules.INVALID_TRANSITION);
    }

    @Test
    void completedAndNoShowOnlyAfterTheStart() {
        Instant start = NOW.plus(Duration.ofHours(1));
        assertThatThrownBy(() -> BookingRules.requireTransition(BookingStatus.CONFIRMED, BookingStatus.COMPLETED,
                start, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(BookingRules.INVALID_TRANSITION);
        assertThatThrownBy(() -> BookingRules.requireTransition(BookingStatus.CONFIRMED, BookingStatus.NO_SHOW,
                start, NOW))
                .isInstanceOf(ConflictException.class);

        BookingRules.requireTransition(BookingStatus.CONFIRMED, BookingStatus.COMPLETED, start, start);
        BookingRules.requireTransition(BookingStatus.CONFIRMED, BookingStatus.NO_SHOW, start, start.plusSeconds(60));
    }

    @Test
    void codesUseThePrefixAndAnUnambiguousAlphabet() {
        Set<String> codes = new HashSet<>();
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            String code = BookingRules.newCode(random);
            assertThat(code).matches("^CT-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$").hasSizeLessThanOrEqualTo(12);
            codes.add(code);
        }
        assertThat(codes).hasSizeGreaterThan(1990);
        assertThat(BookingRules.newCode()).doesNotContainPattern("[01OIL]");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(value = {
            "3001112233, +573001112233", "+573001112233, +573001112233", "300 111 2233, +573001112233",
            "573001112233, +573001112233", "12345, NULL", "+13001112233, NULL", "abc, NULL"
    }, nullValues = "NULL")
    void phonesAreNormalizedToColombianE164(String input, String expected) {
        assertThat(BookingRules.normalizePhone(input)).isEqualTo(expected);
    }

    @Test
    void notesAreTrimmedAndLimited() {
        assertThat(BookingRules.note("  hola  ")).isEqualTo("hola");
        assertThat(BookingRules.note("   ")).isNull();
        assertThatThrownBy(() -> BookingRules.note("x".repeat(BookingRules.NOTE_MAX + 1)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BOOKING_NOTE_INVALID");
    }
}
