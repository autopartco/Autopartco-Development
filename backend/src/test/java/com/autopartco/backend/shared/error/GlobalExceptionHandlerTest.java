package com.autopartco.backend.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void paymentRequiredReturns402WithDetails() {
        var response = handler.handlePaymentRequired(new PaymentRequiredException(
                "PAYMENT_REJECTED", "Pago rechazado.", Map.of("amountCop", 449700)
        ));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        ApiError error = response.getBody().error();
        assertEquals("PAYMENT_REJECTED", error.getCode());
        assertEquals("Pago rechazado.", error.getMessage());
        assertEquals(Map.of("amountCop", 449700), error.getDetails());
    }

    @Test
    void conflictReturns409WithDetails() {
        var response = handler.handleConflict(new ConflictException(
                "SELLER_PLAN_LIMIT_REACHED", "Límite alcanzado.", Map.of("limit", 50, "used", 50)
        ));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(Map.of("limit", 50, "used", 50), response.getBody().error().getDetails());
    }

    @Test
    void legacyConstructorsKeepNullDetails() {
        var notFound = handler.handleNotFound(new NotFoundException("STORE_NOT_FOUND", "No existe."));
        var badRequest = handler.handleBadRequest(new BadRequestException("BAD", "Mal."));

        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertNull(notFound.getBody().error().getDetails());
        assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
        assertNull(badRequest.getBody().error().getDetails());
    }
}
