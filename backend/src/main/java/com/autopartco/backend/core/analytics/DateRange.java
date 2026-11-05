package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Rango de días calendario, ambos inclusive, en la zona {@code America/Bogota}. Como máximo
 * {@value #MAX_DAYS} días.
 */
public record DateRange(LocalDate from, LocalDate to) {
    public static final int MAX_DAYS = 366;
    public static final int DEFAULT_DAYS = 30;

    public DateRange {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw invalid();
        }
    }

    /**
     * Rango a partir de los parámetros {@code YYYY-MM-DD} (opcionales). Sin {@code to}: hoy; sin {@code from}: los
     * {@value #DEFAULT_DAYS} días que terminan en {@code to}. Fecha mal escrita, {@code from > to} o más de
     * {@value #MAX_DAYS} días: 400 {@code INVALID_RANGE}.
     */
    public static DateRange resolve(String from, String to, LocalDate today) {
        LocalDate end = isBlank(to) ? today : parse(to);
        LocalDate start = isBlank(from) ? end.minusDays(DEFAULT_DAYS - 1L) : parse(from);
        return new DateRange(start, end);
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Periodo inmediatamente anterior de igual duración. */
    public DateRange previous() {
        LocalDate previousTo = from.minusDays(1);
        return new DateRange(previousTo.minusDays(days() - 1), previousTo);
    }

    /** Primer instante del rango. */
    public OffsetDateTime startInstant() {
        return Analytics.startOf(from);
    }

    /** Primer instante posterior al rango (exclusivo). */
    public OffsetDateTime endInstant() {
        return Analytics.startOf(to.plusDays(1));
    }

    private static LocalDate parse(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException invalidDate) {
            throw invalid();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BadRequestException invalid() {
        return new BadRequestException("INVALID_RANGE",
                "El rango de fechas no es válido: usa fechas AAAA-MM-DD, la inicial no puede ser posterior a la final "
                        + "y el rango no puede superar " + MAX_DAYS + " días.");
    }
}
