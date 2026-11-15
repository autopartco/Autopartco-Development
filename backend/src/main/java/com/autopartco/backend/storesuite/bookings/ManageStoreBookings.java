package com.autopartco.backend.storesuite.bookings;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.booking.Booking;
import com.autopartco.backend.core.booking.BookingDecision;
import com.autopartco.backend.core.booking.BookingService;
import com.autopartco.backend.core.booking.BookingStatus;
import com.autopartco.backend.core.booking.StoreAction;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Bandeja de citas del almacén. Leer exige {@code BOOKING_READ}; actuar, {@code BOOKING_MANAGE} (sin permiso: 403). Una
 * cita de otro almacén es 404 {@code BOOKING_NOT_FOUND}.
 */
@Service
@AllArgsConstructor
public class ManageStoreBookings {
    private final StoreAccess storeAccess;
    private final BookingService bookingService;

    public PageResponse<Booking> list(UUID storeId, String email, BookingStatus status, UUID branchId,
            LocalDate from, LocalDate to, Integer page, Integer size) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.BOOKING_READ);
        return bookingService.listForStore(storeId, status, branchId, from, to, page, size);
    }

    public Booking get(UUID storeId, UUID bookingId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.BOOKING_READ);
        return bookingService.getForStore(storeId, bookingId);
    }

    public Booking decide(UUID storeId, UUID bookingId, String email, StoreAction action, BookingDecision decision) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.BOOKING_MANAGE);
        return bookingService.decide(storeId, bookingId, email, action, decision);
    }
}
