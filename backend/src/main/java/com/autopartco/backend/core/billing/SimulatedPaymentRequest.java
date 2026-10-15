package com.autopartco.backend.core.billing;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Solicitud de cobro simulado. {@code clientRequestId} hace idempotente la repetición. */
public record SimulatedPaymentRequest(
        @NotNull UUID planId,
        @Min(1) @Max(12) int months,
        @NotNull Outcome simulatedOutcome,
        @NotNull UUID clientRequestId
) {
    public enum Outcome {
        APPROVED,
        REJECTED
    }
}
