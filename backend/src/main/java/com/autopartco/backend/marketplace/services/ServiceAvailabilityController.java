package com.autopartco.backend.marketplace.services;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.booking.Availability;
import com.autopartco.backend.core.booking.AvailabilityService;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.ClientIp;
import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Franjas para agendar un servicio en una sede (público). Errores: 404 {@code SERVICE_NOT_FOUND}; 400
 * {@code BRANCH_NOT_IN_SERVICE}, {@code BOOKING_DISABLED}, {@code AVAILABILITY_DAYS_INVALID}; 429
 * {@code SEARCH_RATE_LIMITED} ({@value #REQUESTS_PER_MINUTE} por minuto e IP).
 */
@RestController
@RequestMapping(ServiceBrowseController.BASE)
public class ServiceAvailabilityController {
    static final int REQUESTS_PER_MINUTE = 60;

    private final AvailabilityService availabilityService;
    private final SlidingWindowRateLimiter limiter;

    /** Límite en Redis si está configurado ({@link RateLimiters}); si no, en memoria. */
    @Autowired
    public ServiceAvailabilityController(AvailabilityService availabilityService, RateLimiters rateLimiters) {
        this.availabilityService = availabilityService;
        this.limiter = rateLimiters.create("service-availability", "SEARCH_RATE_LIMITED", REQUESTS_PER_MINUTE,
                Duration.ofMinutes(1));
    }

    /** Solo memoria (pruebas). */
    ServiceAvailabilityController(AvailabilityService availabilityService, Clock clock) {
        this(availabilityService, RateLimiters.inMemory(clock));
    }

    @GetMapping("/{serviceId}/availability")
    public Availability availability(
            HttpServletRequest http,
            @PathVariable UUID serviceId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) Integer days
    ) {
        limiter.acquire(ClientIp.of(http));
        return availabilityService.availability(serviceId, branchId, from, days);
    }
}
