package com.autopartco.backend.core.pricing;

/**
 * Compradores a los que aplica una promoción. {@code ALL} aplica a cualquiera (también anónimos); el resto exige que
 * el comprador autenticado pertenezca al segmento: {@code PLATFORM_FREQUENT} en toda la plataforma y {@code STORE_*}
 * en el almacén del producto. Los segmentos los calcula {@code core.analytics} y llegan como texto en
 * {@link BuyerContext} ({@code FREQUENT}, {@code AT_RISK}, {@code NEW}, {@code DORMANT}).
 */
public enum PromotionAudience {
    ALL(null),
    PLATFORM_FREQUENT("FREQUENT"),
    STORE_FREQUENT("FREQUENT"),
    STORE_AT_RISK("AT_RISK"),
    STORE_NEW("NEW"),
    STORE_DORMANT("DORMANT");

    private final String segment;

    PromotionAudience(String segment) {
        this.segment = segment;
    }

    /** Código del segmento exigido ({@code null} en {@code ALL}). */
    public String segment() {
        return segment;
    }

    /** {@code true} si el segmento se evalúa por almacén ({@code STORE_*}). */
    public boolean isStoreAudience() {
        return name().startsWith("STORE_");
    }
}
