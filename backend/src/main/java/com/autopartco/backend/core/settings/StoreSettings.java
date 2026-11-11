package com.autopartco.backend.core.settings;

import java.util.List;
import java.util.UUID;

/**
 * Configuración del almacén ({@code store.configuration}) con valores por defecto. No incluye {@code description} ni
 * {@code publicProfile} (perfil del almacén), que siguen en la misma columna y no se tocan desde aquí.
 */
public record StoreSettings(
        Contact contact,
        Profile profile,
        Catalog catalog,
        Notifications notifications,
        Security security
) {
    /** {@code whatsappMessage} admite {@code {producto}} y debe contener {@code {enlace}}. */
    public record Contact(boolean whatsappEnabled, String whatsappMessage) {
    }

    public record Profile(boolean showBranchesMap, List<UUID> featuredProductIds, String tagline) {
        public Profile {
            featuredProductIds = featuredProductIds == null ? List.of() : List.copyOf(featuredProductIds);
        }
    }

    public record Catalog(boolean showExactStock) {
    }

    public record Notifications(
            boolean newOrder,
            boolean lowStock,
            boolean newReview,
            boolean weeklyReport,
            List<String> recipients
    ) {
        public Notifications {
            recipients = recipients == null ? List.of() : List.copyOf(recipients);
        }
    }

    public record Security(boolean requireStaffTwoFactor) {
    }
}
