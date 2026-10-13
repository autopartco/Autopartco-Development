package com.autopartco.backend.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void doesNotExposeUnauthorizedExceptionDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(
                new UnauthorizedException("ACCOUNT_LOCKED", "Sensitive internal account state")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().error().getCode());
        assertEquals("Las credenciales no son válidas.", response.getBody().error().getMessage());
    }
}
