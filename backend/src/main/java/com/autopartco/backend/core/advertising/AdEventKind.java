package com.autopartco.backend.core.advertising;

import java.util.Locale;
import java.util.Optional;

/** Tipo de evento de un anuncio ({@code ad_events.kind}). {@link #ORDER} solo lo registra el backend. */
public enum AdEventKind {
    IMPRESSION,
    CLICK,
    WHATSAPP_CLICK,
    ADD_TO_CART,
    ORDER;

    /** {@code true} si el cliente puede enviarlo a {@code POST /events/ads}. */
    public boolean clientReportable() {
        return this != ORDER;
    }

    /** Vacío si el valor no es un tipo conocido. */
    public static Optional<AdEventKind> parse(String value) {
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
