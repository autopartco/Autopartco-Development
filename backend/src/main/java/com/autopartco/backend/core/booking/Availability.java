package com.autopartco.backend.core.booking;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Franjas que se pueden pedir de un servicio en una sede. {@code busy}: ya hay una cita confirmada que se cruza
 * (orientativo: el almacén decide).
 */
public record Availability(int slotMinutes, List<Day> days) {
    public record Day(LocalDate date, List<Slot> slots) {
    }

    public record Slot(OffsetDateTime startsAt, boolean busy) {
    }
}
