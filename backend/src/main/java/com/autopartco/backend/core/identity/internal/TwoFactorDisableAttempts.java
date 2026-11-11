package com.autopartco.backend.core.identity.internal;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

/**
 * Fallos de contraseña al desactivar la 2FA, por cuenta: con {@value #MAX_FAILURES} en {@link #WINDOW} todo intento
 * responde 429 {@value #ERROR_CODE}, aunque la contraseña sea correcta, hasta que el más antiguo salga de la ventana.
 * Ventana deslizante de {@link RateLimiters} (Redis si hay, si no memoria): no toca el esquema ni el bloqueo del login
 * ({@link LoginAttemptService}). La clave es el id de la cuenta (con HMAC en Redis).
 */
@Component
public class TwoFactorDisableAttempts {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofHours(1);
    static final String ERROR_CODE = "TWO_FACTOR_DISABLE_LOCKED";

    private final SlidingWindowRateLimiter failures;

    public TwoFactorDisableAttempts(RateLimiters rateLimiters) {
        this.failures = rateLimiters.create("2fa-disable", ERROR_CODE, MAX_FAILURES, WINDOW);
    }

    /** 429 si la cuenta agotó los fallos de la ventana. */
    void requireNotLocked(UUID accountId) {
        if (failures.exhausted(accountId.toString())) {
            throw new TooManyRequestsException(ERROR_CODE,
                    "Demasiados intentos fallidos. Vuelve a intentarlo en una hora.");
        }
    }

    void recordFailure(UUID accountId) {
        try {
            failures.acquire(accountId.toString());
        } catch (TooManyRequestsException alreadyLocked) {
            // Ya estaba en el máximo: la ventana sigue llena.
        }
    }
}
