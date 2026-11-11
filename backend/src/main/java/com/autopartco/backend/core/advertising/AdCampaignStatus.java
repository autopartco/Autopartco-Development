package com.autopartco.backend.core.advertising;

import java.util.Locale;
import java.util.Optional;

/**
 * Estado de una campaña ({@code ad_campaigns.status}). Transiciones:
 * <pre>
 * DRAFT ──checkout──▶ PENDING_PAYMENT ──pago aprobado──▶ SCHEDULED ──fecha──▶ ACTIVE ──fecha──▶ FINISHED
 *   ▲                     │  │                              │  ▲                 │  ▲
 *   └──pago rechazado─────┘  └──pago aprobado (hoy ≥ inicio)┼──┼────────────────▶┘  │
 *                                                           ▼  │                    │
 *                                                         PAUSED ──fecha────────────┘
 * Almacén: cancelar desde PENDING_PAYMENT, SCHEDULED, ACTIVE o PAUSED → CANCELLED (sin reembolso automático).
 * Admin: rechazar desde cualquier estado no final → REJECTED; pausar SCHEDULED o ACTIVE → PAUSED.
 * </pre>
 */
public enum AdCampaignStatus {
    DRAFT,
    PENDING_PAYMENT,
    SCHEDULED,
    ACTIVE,
    PAUSED,
    FINISHED,
    REJECTED,
    CANCELLED;

    /** FINISHED, REJECTED o CANCELLED: no admite más transiciones. */
    public boolean isFinal() {
        return this == FINISHED || this == REJECTED || this == CANCELLED;
    }

    /** Vacío si el valor no es un estado conocido. */
    public static Optional<AdCampaignStatus> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
