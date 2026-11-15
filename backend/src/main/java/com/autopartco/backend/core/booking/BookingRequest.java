package com.autopartco.backend.core.booking;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de cita del comprador. {@code startsAt} con zona (ISO-8601, p. ej. {@code 2026-11-20T09:00:00-05:00});
 * {@code contactPhone} opcional: por defecto el teléfono del perfil.
 */
public record BookingRequest(
        @NotNull UUID serviceId,
        @NotNull UUID branchId,
        @NotNull OffsetDateTime startsAt,
        @Size(max = 20) String contactPhone,
        @Valid BookingVehicle vehicle,
        @Size(max = BookingRules.NOTE_MAX) String note
) {
}
