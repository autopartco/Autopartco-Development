package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;

import com.autopartco.backend.shared.error.BadRequestException;

/** Reglas puras de §2: número de productos, fechas y precio (tarifa diaria × días, por cada ubicación). */
public final class AdCampaignRules {
    public static final int MAX_PRODUCTS = 20;
    public static final int MAX_DAYS = 90;
    public static final int MAX_DAYS_AHEAD = 60;

    private AdCampaignRules() {
    }

    /**
     * Productos según las ubicaciones. Solo {@link AdPlacement#MAP_FEATURED}: ninguno (se anuncia el almacén; si
     * vienen productos, 400 {@code AD_PRODUCTS_NOT_ALLOWED}). Con otra ubicación: de 1 a {@value #MAX_PRODUCTS}
     * (0 → 400 {@code AD_PRODUCTS_REQUIRED}; más → 400 {@code AD_TOO_MANY_PRODUCTS}).
     */
    public static void requireProductCount(int distinctProducts, Collection<AdPlacement> placements) {
        if (storeOnly(placements)) {
            if (distinctProducts > 0) {
                throw new BadRequestException("AD_PRODUCTS_NOT_ALLOWED",
                        "Una campaña solo del mapa anuncia el almacén, no productos.");
            }
            return;
        }
        if (distinctProducts < 1) {
            throw new BadRequestException("AD_PRODUCTS_REQUIRED",
                    "Elige al menos un producto para anunciar en esas ubicaciones.");
        }
        requireProductCount(distinctProducts);
    }

    /** {@code true} si la única ubicación es {@link AdPlacement#MAP_FEATURED} (campaña del almacén, sin productos). */
    public static boolean storeOnly(Collection<AdPlacement> placements) {
        return placements != null && !placements.isEmpty()
                && placements.stream().allMatch(placement -> placement == AdPlacement.MAP_FEATURED);
    }

    /** De 1 a {@value #MAX_PRODUCTS} productos distintos; si no, 400 {@code AD_TOO_MANY_PRODUCTS}. */
    public static void requireProductCount(int distinctProducts) {
        if (distinctProducts < 1 || distinctProducts > MAX_PRODUCTS) {
            throw new BadRequestException("AD_TOO_MANY_PRODUCTS",
                    "Una campaña lleva de 1 a " + MAX_PRODUCTS + " productos.",
                    Map.of("max", MAX_PRODUCTS));
        }
    }

    /**
     * Inicio desde {@code today} y hasta {@value #MAX_DAYS_AHEAD} días adelante; fin no anterior al inicio; duración de
     * 1 a {@value #MAX_DAYS} días (ambos incluidos). Si no, 400 {@code AD_INVALID_DATES}.
     */
    public static void requireValidDates(LocalDate startsOn, LocalDate endsOn, LocalDate today) {
        if (startsOn == null || endsOn == null || startsOn.isBefore(today)
                || startsOn.isAfter(today.plusDays(MAX_DAYS_AHEAD)) || endsOn.isBefore(startsOn)
                || days(startsOn, endsOn) > MAX_DAYS) {
            throw new BadRequestException("AD_INVALID_DATES",
                    "La campaña debe empezar entre hoy y " + MAX_DAYS_AHEAD + " días adelante y durar de 1 a "
                            + MAX_DAYS + " días.",
                    Map.of("today", today.toString(), "maxDaysAhead", MAX_DAYS_AHEAD, "maxDays", MAX_DAYS));
        }
    }

    /** Días de la campaña, ambos extremos incluidos. */
    public static long days(LocalDate startsOn, LocalDate endsOn) {
        return ChronoUnit.DAYS.between(startsOn, endsOn) + 1;
    }

    /**
     * Total a pagar: suma, por cada ubicación, de su tarifa diaria × días. Con tarifas iguales equivale a
     * tarifa × días × ubicaciones. {@code dailyPrices} debe traer todas las ubicaciones pedidas.
     */
    public static BigDecimal totalPrice(Collection<AdPlacement> placements, Map<AdPlacement, BigDecimal> dailyPrices,
            LocalDate startsOn, LocalDate endsOn) {
        BigDecimal days = BigDecimal.valueOf(days(startsOn, endsOn));
        BigDecimal total = BigDecimal.ZERO;
        for (AdPlacement placement : placements) {
            BigDecimal daily = dailyPrices.get(placement);
            if (daily == null) {
                throw new IllegalArgumentException("Missing daily price for " + placement);
            }
            total = total.add(daily.multiply(days));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Monto en centavos enteros para Wompi. */
    public static long amountInCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }
}
