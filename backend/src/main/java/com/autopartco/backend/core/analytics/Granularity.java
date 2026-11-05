package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.util.Locale;

import com.autopartco.backend.shared.error.BadRequestException;

/** Agrupación de la serie del reporte de ventas. Las semanas empiezan el lunes. */
public enum Granularity {
    DAY,
    WEEK,
    MONTH;

    /** Sin valor: {@code DAY}. Valor desconocido: 400 {@code INVALID_GRANULARITY}. */
    public static Granularity parse(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("INVALID_GRANULARITY",
                    "La agrupación debe ser DAY, WEEK o MONTH.");
        }
    }

    /** Inicio del periodo que contiene {@code day}. */
    public LocalDate periodStart(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> Analytics.weekStart(day);
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    LocalDate next(LocalDate periodStart) {
        return switch (this) {
            case DAY -> periodStart.plusDays(1);
            case WEEK -> periodStart.plusWeeks(1);
            case MONTH -> periodStart.plusMonths(1);
        };
    }
}
