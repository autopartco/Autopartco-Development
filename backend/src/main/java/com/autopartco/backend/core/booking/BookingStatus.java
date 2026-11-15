package com.autopartco.backend.core.booking;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.autopartco.backend.shared.error.BadRequestException;

/** Estado de una cita ({@code service_bookings.status}). Las transiciones válidas están en {@link BookingRules}. */
public enum BookingStatus {
    REQUESTED,
    PROPOSED,
    CONFIRMED,
    CANCELLED_BY_BUYER,
    REJECTED,
    CANCELLED_BY_STORE,
    COMPLETED,
    NO_SHOW,
    EXPIRED;

    /** Cuentan para el máximo de citas activas por comprador y almacén. */
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(REQUESTED, PROPOSED, CONFIRMED);

    /**
     * Cerradas sin prestarse el servicio: el almacén ya no ve el teléfono ni la placa del comprador (Ley 1581, datos
     * mínimos).
     */
    public static final Set<BookingStatus> CANCELLED =
            EnumSet.of(CANCELLED_BY_BUYER, REJECTED, CANCELLED_BY_STORE, EXPIRED);

    public boolean active() {
        return ACTIVE.contains(this);
    }

    public boolean cancelled() {
        return CANCELLED.contains(this);
    }

    /** {@code null} si viene vacío; 400 {@code BOOKING_STATUS_INVALID} si no es un estado. */
    public static BookingStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("BOOKING_STATUS_INVALID", "El estado de la cita no es válido.");
        }
    }
}
