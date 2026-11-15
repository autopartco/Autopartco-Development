package com.autopartco.backend.storesuite.bookings;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.booking.Booking;
import com.autopartco.backend.core.booking.BookingDecision;
import com.autopartco.backend.core.booking.BookingStatus;
import com.autopartco.backend.core.booking.StoreAction;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Citas del almacén (portal). Acciones: {@code confirm}, {@code propose} ({@code startsAt}), {@code reject} y
 * {@code cancel} ({@code note} obligatoria), {@code complete} y {@code no-show}; todas aceptan {@code version}.
 * Errores: 400 {@code BOOKING_SLOT_INVALID}, {@code BOOKING_NOTE_REQUIRED}; 403 sin permiso; 404
 * {@code BOOKING_NOT_FOUND}; 409 {@code BOOKING_INVALID_TRANSITION}, {@code BOOKING_VERSION_CONFLICT}.
 */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/bookings")
@AllArgsConstructor
public class StoreBookingController {
    private final ManageStoreBookings manageStoreBookings;

    /** {@code from}/{@code to}: días (hora de Bogotá) sobre la hora de la cita; {@code page} desde 1. */
    @GetMapping
    public PageResponse<Booking> list(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return manageStoreBookings.list(storeId, authentication.getName(), BookingStatus.parse(status), branchId,
                from, to, page, size);
    }

    @GetMapping("/{bookingId}")
    public Booking get(@PathVariable UUID storeId, @PathVariable UUID bookingId, Authentication authentication) {
        return manageStoreBookings.get(storeId, bookingId, authentication.getName());
    }

    @PostMapping("/{bookingId}/confirm")
    public Booking confirm(@PathVariable UUID storeId, @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDecision decision, Authentication authentication) {
        return decide(storeId, bookingId, StoreAction.CONFIRM, decision, authentication);
    }

    @PostMapping("/{bookingId}/propose")
    public Booking propose(@PathVariable UUID storeId, @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDecision decision, Authentication authentication) {
        return decide(storeId, bookingId, StoreAction.PROPOSE, decision, authentication);
    }

    @PostMapping("/{bookingId}/reject")
    public Booking reject(@PathVariable UUID storeId, @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDecision decision, Authentication authentication) {
        return decide(storeId, bookingId, StoreAction.REJECT, decision, authentication);
    }

    @PostMapping("/{bookingId}/cancel")
    public Booking cancel(@PathVariable UUID storeId, @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDecision decision, Authentication authentication) {
        return decide(storeId, bookingId, StoreAction.CANCEL, decision, authentication);
    }

    @PostMapping("/{bookingId}/complete")
    public Booking complete(@PathVariable UUID storeId, @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDecision decision, Authentication authentication) {
        return decide(storeId, bookingId, StoreAction.COMPLETE, decision, authentication);
    }

    @PostMapping("/{bookingId}/no-show")
    public Booking noShow(@PathVariable UUID storeId, @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDecision decision, Authentication authentication) {
        return decide(storeId, bookingId, StoreAction.NO_SHOW, decision, authentication);
    }

    private Booking decide(UUID storeId, UUID bookingId, StoreAction action, BookingDecision decision,
            Authentication authentication) {
        return manageStoreBookings.decide(storeId, bookingId, authentication.getName(), action, decision);
    }
}
