package com.autopartco.backend.shared.error;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.autopartco.backend.shared.storage.StorageException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException exception) {

        ApiError error = new ApiError(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {

        ApiError error = new ApiError(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {

        ApiError error = new ApiError(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler(PaymentRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePaymentRequired(PaymentRequiredException exception) {

        ApiError error = new ApiError(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );

        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException exception) {

        ApiError error = new ApiError(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException exception) {

        ApiError error = new ApiError(
                "UNAUTHORIZED",
                "Las credenciales no son válidas.",
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(new ApiError(
                        "FORBIDDEN",
                        "No tiene permisos para realizar esta operación.",
                        null
                )));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        Map<String, String> details = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        details.put(error.getField(), error.getDefaultMessage())
                );

        ApiError error = new ApiError(
                "VALIDATION_ERROR",
                "Los datos enviados no son válidos.",
                details
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        ApiError error = new ApiError(
                "INVALID_REQUEST_BODY",
                "El cuerpo de la solicitud contiene datos inválidos o campos no permitidos.",
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(error));
    }

    @ExceptionHandler({PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(RuntimeException exception) {
        String code = exception instanceof PayloadTooLargeException tooLarge
                ? tooLarge.getCode()
                : "PAYLOAD_TOO_LARGE";
        String message = exception instanceof PayloadTooLargeException
                ? exception.getMessage()
                : "El archivo supera el tamaño máximo permitido.";
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(new ApiError(code, message, null)));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageFailure(StorageException exception) {
        log.error("Product image storage operation failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(new ApiError(
                        "PRODUCT_IMAGE_STORAGE_ERROR",
                        "No se pudo procesar la imagen.",
                        null
                )));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {

        log.error("Unexpected error", exception);

        ApiError error = new ApiError(
                "INTERNAL_SERVER_ERROR",
                "Ha ocurrido un error interno en el servidor.",
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(error));
    }
}
