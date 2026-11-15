package com.autopartco.backend.marketplace.bookings;

import static com.autopartco.backend.core.booking.BookingFixtures.BOOKING;
import static com.autopartco.backend.core.booking.BookingFixtures.BRANCH;
import static com.autopartco.backend.core.booking.BookingFixtures.BUYER;
import static com.autopartco.backend.core.booking.BookingFixtures.BUYER_EMAIL;
import static com.autopartco.backend.core.booking.BookingFixtures.CLOCK;
import static com.autopartco.backend.core.booking.BookingFixtures.OTHER_BUYER;
import static com.autopartco.backend.core.booking.BookingFixtures.PHONE;
import static com.autopartco.backend.core.booking.BookingFixtures.SERVICE;
import static com.autopartco.backend.core.booking.BookingFixtures.STORE;
import static com.autopartco.backend.core.booking.BookingFixtures.booking;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import org.springframework.http.MediaType;
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
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class BuyerBookingControllerTest {
    private static final String URL = "/api/v1/bookings";
    private static final Authentication BUYER_AUTH =
            new UsernamePasswordAuthenticationToken(BUYER_EMAIL, null, List.of());

    private final ServiceBookingRepository repository = mock(ServiceBookingRepository.class);
    private final BookingCatalog catalog = mock(BookingCatalog.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BookingService bookingService = new BookingService(repository, catalog, accountLookup,
                mock(AuditService.class), events, CLOCK);
        mvc = MockMvcBuilders.standaloneSetup(new BuyerBookingController(bookingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(accountLookup.requireByEmail(BUYER_EMAIL)).thenReturn(BookingFixtures.buyer());
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service()));
        when(catalog.services(anyCollection())).thenReturn(Map.of(SERVICE, BookingFixtures.service()));
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(BookingFixtures.branch()));
        when(catalog.branches(anyCollection())).thenReturn(Map.of(BRANCH, BookingFixtures.branch()));
        when(catalog.profilePhone(BUYER)).thenReturn(Optional.of("3001112233"));
        when(repository.saveAndFlush(any(ServiceBooking.class))).thenAnswer(invocation -> {
            ServiceBooking saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
                saved.setVersion(0L);
            }
            return saved;
        });
    }

    private static MockHttpServletRequestBuilder create(String startsAt, String extra) {
        return post(URL).principal(BUYER_AUTH).contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceId":"%s","branchId":"%s","startsAt":"%s"%s}
                """.formatted(SERVICE, BRANCH, startsAt, extra == null ? "" : "," + extra));
    }

    @Test
    void requestReturns201WithTheBooking() throws Exception {
        mvc.perform(create("2026-11-17T09:00:00-05:00",
                        "\"vehicle\":{\"makeId\":\"" + UUID.randomUUID() + "\",\"modelId\":\"" + UUID.randomUUID()
                                + "\",\"year\":2019,\"plate\":\"abc12d\"},\"note\":\"Vibra a 80\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(
                        "^CT-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$")))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.startsAt").value("2026-11-17T09:00:00-05:00"))
                .andExpect(jsonPath("$.endsAt").value("2026-11-17T10:00:00-05:00"))
                .andExpect(jsonPath("$.service.name").value("Alineación y balanceo"))
                .andExpect(jsonPath("$.store.id").value(STORE.toString()))
                .andExpect(jsonPath("$.branch.id").value(BRANCH.toString()))
                .andExpect(jsonPath("$.quotedPrice").value(60000.0))
                .andExpect(jsonPath("$.contactPhone").value(PHONE))
                .andExpect(jsonPath("$.vehicle.plate").value("ABC12D"))
                .andExpect(jsonPath("$.buyerNote").value("Vibra a 80"))
                .andExpect(jsonPath("$.buyer").doesNotExist());
    }

    @Test
    void slotOutsideHoursMisalignedOrTooSoonIs400() throws Exception {
        for (String startsAt : List.of("2026-11-17T12:30:00-05:00", "2026-11-17T09:15:00-05:00",
                "2026-11-16T09:00:00-05:00", "2026-11-22T09:00:00-05:00")) {
            mvc.perform(create(startsAt, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BOOKING_SLOT_INVALID"));
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void fourthActiveBookingInTheSameStoreIs429() throws Exception {
        when(repository.countByAccountAndStore(BUYER, STORE, BookingStatus.ACTIVE)).thenReturn(3L);

        mvc.perform(create("2026-11-17T09:00:00-05:00", null))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("BOOKING_LIMIT_REACHED"));
    }

    @Test
    void missingPhoneWithoutProfilePhoneIs400() throws Exception {
        when(catalog.profilePhone(BUYER)).thenReturn(Optional.empty());

        mvc.perform(create("2026-11-17T09:00:00-05:00", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CONTACT_PHONE_REQUIRED"));
    }

    @Test
    void missingRequiredFieldsFailValidation() throws Exception {
        mvc.perform(post(URL).principal(BUYER_AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + BRANCH + "\"}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void someoneElsesBookingIs404() throws Exception {
        ServiceBooking foreign = booking(BookingStatus.REQUESTED, "2026-11-17T09:00");
        foreign.setAccountId(OTHER_BUYER);
        when(repository.findById(BOOKING)).thenReturn(Optional.of(foreign));
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(foreign));

        mvc.perform(get(URL + "/" + BOOKING).principal(BUYER_AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));
        mvc.perform(post(URL + "/" + BOOKING + "/cancel").principal(BUYER_AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));
        mvc.perform(post(URL + "/" + BOOKING + "/accept-proposal").principal(BUYER_AUTH))
                .andExpect(status().isNotFound());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void ownBookingShowsTheContactPhone() throws Exception {
        when(repository.findById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.CANCELLED_BY_BUYER,
                "2026-11-17T09:00")));

        mvc.perform(get(URL + "/" + BOOKING).principal(BUYER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_BUYER"))
                .andExpect(jsonPath("$.contactPhone").value(PHONE))
                .andExpect(jsonPath("$.buyer").doesNotExist());
    }

    @Test
    void acceptingWithoutAProposalIs409() throws Exception {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        mvc.perform(post(URL + "/" + BOOKING + "/accept-proposal").principal(BUYER_AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_INVALID_TRANSITION"));
    }

    @Test
    void cancelWithReason() throws Exception {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        mvc.perform(post(URL + "/" + BOOKING + "/cancel").principal(BUYER_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ya lo arreglé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_BUYER"));
    }

    @Test
    void invalidStatusFilterIs400() throws Exception {
        mvc.perform(get(URL).param("status", "nope").principal(BUYER_AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BOOKING_STATUS_INVALID"));
    }
}
