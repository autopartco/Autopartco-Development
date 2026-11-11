package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.shared.error.BadRequestException;

class OpeningHoursTest {

    private static Map<String, Object> hours(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static void assertInvalid(Map<String, Object> value) {
        assertThatThrownBy(() -> OpeningHours.validate(value))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_OPENING_HOURS"));
    }

    @Test
    void acceptsTwoAscendingSlotsClosedDaysAndNotes() {
        Map<String, Object> normalized = OpeningHours.validate(hours(
                "sun", List.of(),
                "mon", List.of(List.of("08:00", "12:00"), List.of("14:00", "18:00")),
                "notes", "  Festivos 9-13  "));

        assertThat(normalized).containsExactly(
                Map.entry("mon", List.of(List.of("08:00", "12:00"), List.of("14:00", "18:00"))),
                Map.entry("sun", List.of()),
                Map.entry("notes", "Festivos 9-13"));
    }

    @Test
    void emptyObjectClearsTheSchedule() {
        assertThat(OpeningHours.validate(Map.of())).isNull();
        assertThat(OpeningHours.validate(hours("notes", "   "))).isNull();
    }

    @Test
    void rejectsUnknownDaysAndMalformedSlots() {
        assertInvalid(hours("lunes", List.of()));
        assertInvalid(hours("mon", "08:00-18:00"));
        assertInvalid(hours("mon", List.of("08:00", "18:00")));
        assertInvalid(hours("mon", List.of(List.of("8:00", "18:00"))));
        assertInvalid(hours("mon", List.of(List.of("08:00", "24:00"))));
        assertInvalid(hours("mon", List.of(List.of("08:00", "08:60"))));
        assertInvalid(hours("mon", List.of(List.of("08:00"))));
    }

    @Test
    void rejectsMoreThanTwoSlotsReversedOrOverlappingSlots() {
        assertInvalid(hours("mon", List.of(List.of("06:00", "08:00"), List.of("09:00", "12:00"),
                List.of("14:00", "18:00"))));
        assertInvalid(hours("tue", List.of(List.of("18:00", "08:00"))));
        assertInvalid(hours("tue", List.of(List.of("08:00", "08:00"))));
        assertInvalid(hours("wed", List.of(List.of("08:00", "12:00"), List.of("11:00", "18:00"))));
        assertInvalid(hours("wed", List.of(List.of("14:00", "18:00"), List.of("08:00", "12:00"))));
    }

    @Test
    void contiguousSlotsAreAllowed() {
        assertThat(OpeningHours.validate(hours("fri", List.of(List.of("08:00", "12:00"), List.of("12:00", "18:00")))))
                .isNotNull();
    }

    @Test
    void notesAreLimitedAndCannotContainHtml() {
        assertInvalid(hours("notes", "x".repeat(121)));
        assertInvalid(hours("notes", "<b>abierto</b>"));
        assertInvalid(hours("notes", 5));
        assertThat(OpeningHours.validate(hours("notes", "x".repeat(120)))).isNotNull();
    }

    @Test
    void openNowUsesTheClockForSlotsThatDoNotCrossMidnight() {
        Map<String, Object> schedule = hours("wed", List.of(List.of("08:00", "12:00"), List.of("14:00", "18:00")),
                "thu", List.of());
        // 2026-11-11 es miércoles.
        assertThat(OpeningHours.openNow(schedule, at("2026-11-11T07:59:00"))).isFalse();
        assertThat(OpeningHours.openNow(schedule, at("2026-11-11T08:00:00"))).isTrue();
        assertThat(OpeningHours.openNow(schedule, at("2026-11-11T11:59:00"))).isTrue();
        assertThat(OpeningHours.openNow(schedule, at("2026-11-11T12:00:00"))).isFalse();
        assertThat(OpeningHours.openNow(schedule, at("2026-11-11T15:00:00"))).isTrue();
        assertThat(OpeningHours.openNow(schedule, at("2026-11-11T18:00:00"))).isFalse();
    }

    @Test
    void openNowIsFalseOnDaysWithoutSlotsAndNullWithoutAWeeklySchedule() {
        Map<String, Object> schedule = hours("wed", List.of(List.of("08:00", "12:00")), "thu", List.of());

        assertThat(OpeningHours.openNow(schedule, at("2026-11-12T10:00:00"))).isFalse();
        assertThat(OpeningHours.openNow(schedule, at("2026-11-14T10:00:00"))).isFalse();
        assertThat(OpeningHours.openNow(null, at("2026-11-11T10:00:00"))).isNull();
        assertThat(OpeningHours.openNow(Map.of(), at("2026-11-11T10:00:00"))).isNull();
        assertThat(OpeningHours.openNow(hours("notes", "Solo notas"), at("2026-11-11T10:00:00"))).isNull();
    }

    @Test
    void unreadableStoredScheduleDoesNotBreakTheListing() {
        assertThat(OpeningHours.openNow(hours("wed", List.of(List.of("mañana", "tarde"))),
                at("2026-11-11T10:00:00"))).isNull();
    }

    /** Hora local de pared en la zona del reloj (UTC aquí, igual que la hora de Bogotá del llamador). */
    private static Clock at(String localDateTime) {
        return Clock.fixed(Instant.parse(localDateTime + "Z"), ZoneOffset.UTC);
    }
}
