package com.autopartco.backend.storesuite.bookings;

import static com.autopartco.backend.core.booking.BookingFixtures.BOOKING;
import static com.autopartco.backend.core.booking.BookingFixtures.BRANCH;
import static com.autopartco.backend.core.booking.BookingFixtures.BUYER;
import static com.autopartco.backend.core.booking.BookingFixtures.CLOCK;
import static com.autopartco.backend.core.booking.BookingFixtures.OTHER_STORE;
import static com.autopartco.backend.core.booking.BookingFixtures.PHONE;
import static com.autopartco.backend.core.booking.BookingFixtures.SERVICE;
import static com.autopartco.backend.core.booking.BookingFixtures.STAFF_EMAIL;
import static com.autopartco.backend.core.booking.BookingFixtures.STORE;
import static com.autopartco.backend.core.booking.BookingFixtures.booking;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.booking.BookingFixtures;
import com.autopartco.backend.core.booking.BookingService;
import com.autopartco.backend.core.booking.BookingStatus;
import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.ServiceBooking;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class StoreBookingControllerTest {
    private static final String URL = "/api/v1/stores/" + STORE + "/bookings";
    private static final Authentication STAFF =
            new UsernamePasswordAuthenticationToken(STAFF_EMAIL, null, List.of());

    private final ServiceBookingRepository repository = mock(ServiceBookingRepository.class);
    private final BookingCatalog catalog = mock(BookingCatalog.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BookingService bookingService = new BookingService(repository, catalog, accountLookup,
                mock(AuditService.class), events, CLOCK);
        mvc = MockMvcBuilders.standaloneSetup(
                        new StoreBookingController(new ManageStoreBookings(storeAccess, bookingService)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(accountLookup.requireByEmail(STAFF_EMAIL)).thenReturn(BookingFixtures.staff());
        when(accountLookup.findAllByIds(anyCollection())).thenReturn(Map.of(BUYER, BookingFixtures.buyer()));
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service()));
        when(catalog.services(anyCollection())).thenReturn(Map.of(SERVICE, BookingFixtures.service()));
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(BookingFixtures.branch()));
        when(catalog.branches(anyCollection())).thenReturn(Map.of(BRANCH, BookingFixtures.branch()));
        when(repository.saveAndFlush(any(ServiceBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stored(ServiceBooking booking) {
        when(repository.findById(BOOKING)).thenReturn(Optional.of(booking));
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking));
    }

    private static MockHttpServletRequestBuilder action(String name, String body) {
        MockHttpServletRequestBuilder request = post(URL + "/" + BOOKING + "/" + name).principal(STAFF);
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void listShowsBuyerPhoneOnlyOnNonCancelledBookings() throws Exception {
        ServiceBooking confirmed = booking(BookingStatus.CONFIRMED, "2026-11-17T09:00");
        ServiceBooking cancelled = booking(BookingStatus.CANCELLED_BY_STORE, "2026-11-17T10:00");
        cancelled.setId(UUID.randomUUID());
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(confirmed, cancelled)));

        mvc.perform(get(URL).param("status", "confirmed").param("from", "2026-11-17").principal(STAFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("CT-7K3P9Q"))
                .andExpect(jsonPath("$.items[0].startsAt").value("2026-11-17T09:00:00-05:00"))
                .andExpect(jsonPath("$.items[0].buyer.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.items[0].buyer.phone").value(PHONE))
                .andExpect(jsonPath("$.items[0].contactPhone").value(PHONE))
                .andExpect(jsonPath("$.items[0].vehicle.plate").value("ABC123"))
                .andExpect(jsonPath("$.items[0].branch.name").value("Sede Chapinero"))
                .andExpect(jsonPath("$.items[1].buyer.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.items[1].buyer.phone").doesNotExist())
                .andExpect(jsonPath("$.items[1].contactPhone").doesNotExist())
                .andExpect(jsonPath("$.items[1].vehicle.plate").doesNotExist())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.total").value(2));
        verify(storeAccess).requirePermission(STAFF_EMAIL, STORE, StorePermissionCodes.BOOKING_READ);
    }

    @Test
    void withoutBookingReadTheInboxIsForbidden() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(storeAccess)
                .requirePermission(STAFF_EMAIL, STORE, StorePermissionCodes.BOOKING_READ);

        mvc.perform(get(URL).principal(STAFF)).andExpect(status().isForbidden());
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void withoutBookingManageActionsAreForbidden() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(storeAccess)
                .requirePermission(STAFF_EMAIL, STORE, StorePermissionCodes.BOOKING_MANAGE);
        stored(booking(BookingStatus.REQUESTED, "2026-11-17T09:00"));

        mvc.perform(action("confirm", null)).andExpect(status().isForbidden());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void bookingOfAnotherStoreIsNotFound() throws Exception {
        ServiceBooking foreign = booking(BookingStatus.REQUESTED, "2026-11-17T09:00");
        foreign.setStoreId(OTHER_STORE);
        stored(foreign);

        mvc.perform(get(URL + "/" + BOOKING).principal(STAFF))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));
        mvc.perform(action("confirm", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void confirmRequestedBooking() throws Exception {
        stored(booking(BookingStatus.REQUESTED, "2026-11-17T09:00"));

        mvc.perform(action("confirm", "{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.buyer.phone").value(PHONE));
        verify(storeAccess).requirePermission(STAFF_EMAIL, STORE, StorePermissionCodes.BOOKING_MANAGE);
    }

    @Test
    void invalidTransitionIsAConflict() throws Exception {
        stored(booking(BookingStatus.EXPIRED, "2026-11-13T09:00"));

        mvc.perform(action("confirm", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_INVALID_TRANSITION"));
    }

    @Test
    void completingBeforeTheStartIsAConflict() throws Exception {
        stored(booking(BookingStatus.CONFIRMED, "2026-11-17T09:00"));

        mvc.perform(action("complete", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_INVALID_TRANSITION"));
        mvc.perform(action("no-show", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_INVALID_TRANSITION"));
    }

    @Test
    void staleVersionIsAConflict() throws Exception {
        stored(booking(BookingStatus.REQUESTED, "2026-11-17T09:00"));

        mvc.perform(action("confirm", "{\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_VERSION_CONFLICT"));
    }

    @Test
    void rejectAndCancelRequireANote() throws Exception {
        stored(booking(BookingStatus.REQUESTED, "2026-11-17T09:00"));

        mvc.perform(action("reject", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOTE_REQUIRED"));
        mvc.perform(action("cancel", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOTE_REQUIRED"));

        mvc.perform(action("reject", "{\"note\":\"No hacemos ese modelo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.storeNote").value("No hacemos ese modelo"))
                .andExpect(jsonPath("$.buyer.phone").doesNotExist());
    }

    @Test
    void proposeFollowsTheSlotRules() throws Exception {
        stored(booking(BookingStatus.REQUESTED, "2026-11-17T09:00"));

        mvc.perform(action("propose", "{\"startsAt\":\"2026-11-18T15:20:00-05:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BOOKING_SLOT_INVALID"));
        mvc.perform(action("propose", "{\"startsAt\":\"2026-11-18T15:00:00-05:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.proposedStartsAt").value("2026-11-18T15:00:00-05:00"));
        verify(events).publishEvent(any(com.autopartco.backend.core.booking.internal.BookingChanged.class));
    }

    @Test
    void invalidStatusFilterIsRejected() throws Exception {
        mvc.perform(get(URL).param("status", "PAID").principal(STAFF))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BOOKING_STATUS_INVALID"));
        verify(repository, never()).findAll(any(Specification.class), eq(Pageable.unpaged()));
    }
}
