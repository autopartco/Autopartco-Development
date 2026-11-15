package com.autopartco.backend.marketplace.services;

import static com.autopartco.backend.core.booking.BookingFixtures.BRANCH;
import static com.autopartco.backend.core.booking.BookingFixtures.CLOCK;
import static com.autopartco.backend.core.booking.BookingFixtures.SERVICE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.booking.AvailabilityService;
import com.autopartco.backend.core.booking.BookingFixtures;
import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class ServiceAvailabilityControllerTest {
    private static final String URL = "/api/v1/marketplace/services/" + SERVICE + "/availability";

    private final BookingCatalog catalog = mock(BookingCatalog.class);
    private final ServiceBookingRepository repository = mock(ServiceBookingRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new ServiceAvailabilityController(new AvailabilityService(catalog, repository, CLOCK), CLOCK))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service()));
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(BookingFixtures.branch()));
        when(repository.findConfirmedOverlapping(eq(SERVICE), eq(BRANCH), any(), any())).thenReturn(List.of());
    }

    @Test
    void returnsDaysWithSlotsInBogotaTime() throws Exception {
        mvc.perform(get(URL).param("branchId", BRANCH.toString()).param("from", "2026-11-17").param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotMinutes").value(60))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-17"))
                .andExpect(jsonPath("$.days[0].slots[0].startsAt").value("2026-11-17T08:00:00-05:00"))
                .andExpect(jsonPath("$.days[0].slots[0].busy").value(false));
    }

    @Test
    void errorsKeepTheirCodes() throws Exception {
        mvc.perform(get(URL).param("days", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BRANCH_NOT_IN_SERVICE"));
        mvc.perform(get(URL).param("branchId", BRANCH.toString()).param("days", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AVAILABILITY_DAYS_INVALID"));
        when(catalog.service(SERVICE)).thenReturn(Optional.empty());
        mvc.perform(get(URL).param("branchId", BRANCH.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SERVICE_NOT_FOUND"));
    }
}
