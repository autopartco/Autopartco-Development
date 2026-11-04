package com.autopartco.backend.core.catalog;

/**
 * Requisito de publicación incumplido; se devuelve en {@code error.details} de {@code PRODUCT_NOT_PUBLISHABLE}.
 * {@code code}: {@code NO_IMAGES}, {@code NO_VEHICLES}, {@code MANUFACTURER_INVALID} o {@code SUBSCRIPTION_INACTIVE}.
 */
public record PublicationIssue(String code, String message) {
    public static final String NO_IMAGES = "NO_IMAGES";
    public static final String NO_VEHICLES = "NO_VEHICLES";
    public static final String MANUFACTURER_INVALID = "MANUFACTURER_INVALID";
    public static final String SUBSCRIPTION_INACTIVE = "SUBSCRIPTION_INACTIVE";

    static PublicationIssue noImages() {
        return new PublicationIssue(NO_IMAGES, "Agrega al menos una imagen del repuesto.");
    }

    static PublicationIssue noVehicles() {
        return new PublicationIssue(NO_VEHICLES, "Indica los vehículos compatibles o marca el producto como universal.");
    }

    static PublicationIssue manufacturerInvalid() {
        return new PublicationIssue(MANUFACTURER_INVALID,
                "El fabricante no es válido. Elige uno aprobado o uno sugerido por tu almacén.");
    }

    static PublicationIssue subscriptionInactive() {
        return new PublicationIssue(SUBSCRIPTION_INACTIVE,
                "Tu suscripción no está activa. Renueva un plan para publicar.");
    }
}
