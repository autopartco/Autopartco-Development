package com.autopartco.backend.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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

    @Test
    void paymentRequiredReturns402WithCodeMessageAndDetails() {
        Map<String, Object> details = Map.of(
                "paymentMethod", "SIMULATED_CARD",
                "amount", new BigDecimal("230000.00"),
                "currency", "COP");

        ResponseEntity<ErrorResponse> response = handler.handlePaymentRequired(
                new PaymentRequiredException("PAYMENT_REJECTED", "El pago simulado fue rechazado.", details));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals(402, response.getStatusCode().value());
        assertEquals("PAYMENT_REJECTED", response.getBody().error().getCode());
        assertEquals("El pago simulado fue rechazado.", response.getBody().error().getMessage());
        assertSame(details, response.getBody().error().getDetails());
    }

    @Test
    void conflictPassesDetails() {
        Map<String, Object> details = Map.of("items", List.of(Map.of(
                "productId", "p", "branchId", "b", "requested", 3, "available", 1)));

        ResponseEntity<ErrorResponse> response = handler.handleConflict(
                new ConflictException("INSUFFICIENT_STOCK", "Sin stock.", details));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INSUFFICIENT_STOCK", response.getBody().error().getCode());
        assertNotNull(response.getBody().error().getDetails());
        assertSame(details, response.getBody().error().getDetails());
    }

    @Test
    void notFoundAndBadRequestPassDetailsWhenGiven() {
        Map<String, Object> details = Map.of("items", List.of(Map.of("productId", "p", "branchId", "b")));

        assertSame(details, handler.handleNotFound(
                new NotFoundException("PRODUCT_NOT_FOUND", "No disponible.", details)).getBody().error().getDetails());
        assertSame(details, handler.handleBadRequest(
                new BadRequestException("CHECKOUT_DUPLICATE_LINE", "Repetido.", details)).getBody().error().getDetails());
    }

    @Test
    void legacyConstructorsKeepNullDetailsWithCodes() {
        ResponseEntity<ErrorResponse> notFound = handler.handleNotFound(
                new NotFoundException("ADDRESS_NOT_FOUND", "No se encontró la dirección."));
        ResponseEntity<ErrorResponse> badRequest = handler.handleBadRequest(
                new BadRequestException("PAYMENT_METHOD_NOT_SUPPORTED", "Método no soportado."));
        ResponseEntity<ErrorResponse> conflict = handler.handleConflict(
                new ConflictException("CHECKOUT_DUPLICATE_REQUEST", "Repetida."));

        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("ADDRESS_NOT_FOUND", notFound.getBody().error().getCode());
        assertNull(notFound.getBody().error().getDetails());
        assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
        assertEquals("PAYMENT_METHOD_NOT_SUPPORTED", badRequest.getBody().error().getCode());
        assertNull(badRequest.getBody().error().getDetails());
        assertNull(conflict.getBody().error().getDetails());
    }
}
