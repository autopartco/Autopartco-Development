package com.autopartco.backend.core.booking.internal;

import java.util.UUID;

import com.autopartco.backend.core.booking.BookingStatus;

/**
 * Una cita se creó ({@code previous} nulo) o cambió de estado. Se publica dentro de la transacción; los avisos se envían
 * después del commit. {@code buyerReason} solo viaja al aviso del almacén cuando el comprador cancela; no se guarda.
 */
public record BookingChanged(UUID bookingId, BookingStatus previous, BookingStatus status, String buyerReason) {
}
