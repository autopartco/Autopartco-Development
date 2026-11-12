package com.autopartco.backend.core.settings;

/**
 * Configuración de la cuenta ({@code accounts.configuration}) con valores por defecto. {@code security.twoFactor}
 * es de solo lectura aquí: se activa/desactiva con {@code /account/settings/two-factor/*}.
 */
public record AccountSettings(Security security, Notifications notifications, Preferences preferences) {

    public record Security(TwoFactor twoFactor, boolean loginAlerts) {
    }

    public record TwoFactor(boolean enabled, String method) {
    }

    /** {@code reviewReminders}: correos que invitan y recuerdan calificar lo recibido. */
    public record Notifications(boolean orderUpdates, boolean priceAlerts, boolean storeReplies, boolean marketing,
            boolean reviewReminders) {
    }

    /** {@code theme}: {@code system} | {@code light} | {@code dark}; {@code defaultCityCode}: DIVIPOLA (5 dígitos). */
    public record Preferences(String theme, String defaultCityCode) {
    }
}
