package com.autopartco.backend.marketplace.bookings;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.booking.Booking;
import com.autopartco.backend.core.booking.BookingRequest;
import com.autopartco.backend.core.booking.BookingRules;
import com.autopartco.backend.core.booking.BookingService;
import com.autopartco.backend.core.booking.BookingStatus;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * Citas de servicio del comprador (con sesión). Solo las propias: una ajena es 404 {@code BOOKING_NOT_FOUND}.
 * Errores de {@link BookingService}: 400 {@code BOOKING_SLOT_INVALID}, {@code BRANCH_NOT_IN_SERVICE},
 * {@code BOOKING_DISABLED}, {@code CONTACT_PHONE_REQUIRED}; 409 {@code BOOKING_INVALID_TRANSITION}; 429
 * {@code BOOKING_LIMIT_REACHED}.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/bookings")
@AllArgsConstructor
public class BuyerBookingController {
    private final BookingService bookingService;

    /** Cuerpo opcional de la cancelación; el motivo solo se envía al almacén. */
    public record CancelRequest(@Size(max = BookingRules.NOTE_MAX) String reason) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking request(@Valid @RequestBody BookingRequest request, Authentication authentication) {
        return bookingService.request(authentication.getName(), request);
    }

    /** Propias, de la más reciente a la más antigua; {@code page} desde 1. */
    @GetMapping
    public PageResponse<Booking> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return bookingService.listMine(authentication.getName(), BookingStatus.parse(status), page, size);
    }

    @GetMapping("/{bookingId}")
    public Booking get(@PathVariable UUID bookingId, Authentication authentication) {
        return bookingService.getMine(authentication.getName(), bookingId);
    }

    @PostMapping("/{bookingId}/accept-proposal")
    public Booking acceptProposal(@PathVariable UUID bookingId, Authentication authentication) {
        return bookingService.acceptProposal(authentication.getName(), bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public Booking cancel(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CancelRequest request,
            Authentication authentication
    ) {
        return bookingService.cancelByBuyer(authentication.getName(), bookingId,
                request == null ? null : request.reason());
    }
}
