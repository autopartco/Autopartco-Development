package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.booking.BookingRules;

/** Horarios heredados ({@code "lunes": "08:00-18:00"}) se leen como el contrato oficial. */
class OpeningHoursLegacyTest {
    private static final Map<String, Object> LEGACY = Map.of("lunes", "08:00-18:00", "martes", "08:00-18:00",
            "miercoles", "08:00-18:00", "jueves", "08:00-18:00", "viernes", "08:00-18:00", "sabado", "08:00-14:00",
            "domingo", "cerrado");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void legacyHoursAreTranslatedToTheOfficialFormat() {
        Map<String, Object> hours = OpeningHours.readable(LEGACY);

        assertThat(hours).containsEntry("mon", List.of(List.of("08:00", "18:00")))
                .containsEntry("sat", List.of(List.of("08:00", "14:00")))
                .containsEntry("sun", List.of());
        assertThat(hours.keySet()).containsExactly("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    }

    @Test
    void officialHoursAndNullAreReturnedAsIs() {
        Map<String, Object> official = Map.of("mon", List.of(List.of("09:00", "12:00")));

        assertThat(OpeningHours.readable(official)).isSameAs(official);
        assertThat(OpeningHours.readable(null)).isNull();
    }

    @Test
    void legacyHoursWorkForOpenNowAndBookingSlots() {
        // Lunes 17 de noviembre de 2026, 10:00 en Bogotá.
        Clock monday = Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), BOGOTA);

        assertThat(OpeningHours.openNow(LEGACY, monday)).isTrue();
        Map<String, Object> hours = OpeningHours.readable(LEGACY);
        assertThat(BookingRules.hasWeeklyHours(hours)).isTrue();
        assertThat(BookingRules.ranges(hours, DayOfWeek.SUNDAY)).isEmpty();
        assertThat(BookingRules.ranges(hours, DayOfWeek.SATURDAY)).hasSize(1);
    }
}
