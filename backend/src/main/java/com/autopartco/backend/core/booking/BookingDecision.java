package com.autopartco.backend.core.booking;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de las acciones del almacén sobre una cita. {@code startsAt}: solo {@code propose}; {@code note}: obligatoria
 * en {@code reject} y {@code cancel}; {@code version}: la que el portal leyó (si no coincide, 409
 * {@code BOOKING_VERSION_CONFLICT}).
 */
public record BookingDecision(
        OffsetDateTime startsAt,
        @Size(max = BookingRules.NOTE_MAX) String note,
        Long version
) {
    public static BookingDecision empty() {
        return new BookingDecision(null, null, null);
    }
}
