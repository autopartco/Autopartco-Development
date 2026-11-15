/**
 * Agenda de citas de servicio ({@code service_bookings}): reglas de franja y de estados ({@link
 * com.autopartco.backend.core.booking.BookingRules}), solicitud y gestión por comprador y almacén ({@link
 * com.autopartco.backend.core.booking.BookingService}), disponibilidad orientativa ({@link
 * com.autopartco.backend.core.booking.AvailabilityService}), vencimiento y recordatorio programados y avisos tras el
 * commit ({@link com.autopartco.backend.core.booking.BookingNotifications}). Hora de Bogotá. Los permisos del portal los
 * resuelve {@code storesuite}; de {@code core.organization} solo usa tipos públicos ({@code OpeningHours},
 * {@code ServiceCategory}, {@code StoreBrand}) y lee servicios y sucursales por SQL.
 */
@ApplicationModule(
        displayName = "Core: Booking",
        allowedDependencies = {"shared", "core.identity", "core.organization", "core.notification", "core.settings",
                "core.audit", "core.pricing"})
package com.autopartco.backend.core.booking;

import org.springframework.modulith.ApplicationModule;
