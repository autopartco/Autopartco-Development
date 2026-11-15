package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.location.ColombiaBounds;

import lombok.Builder;

/**
 * Parámetros de {@code GET /api/v1/marketplace/services/search}. Todos opcionales; {@code page} desde 0. {@code facets}
 * nulo = solo en la primera página. {@code excludeServiceId} es interno (relacionados del detalle). Mismas reglas y
 * códigos de error que {@link SearchQuery}.
 */
@Builder(toBuilder = true)
public record ServiceSearchQuery(
        String q,
        ServiceCategory category,
        String cityCode,
        String departmentCode,
        BigDecimal lat,
        BigDecimal lng,
        Integer radiusKm,
        UUID storeId,
        BigDecimal priceMax,
        Boolean onSale,
        Boolean bookable,
        Boolean openNow,
        Sort sort,
        Integer page,
        Integer size,
        Boolean facets,
        UUID excludeServiceId
) {
    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = SearchQuery.MAX_SIZE;

    /** Orden público ({@code relevance|nearest|price_asc|rating}). */
    public enum Sort {
        RELEVANCE, NEAREST, PRICE_ASC, RATING;

        /** Valor del parámetro {@code sort} ({@code relevance} por defecto); otro valor: 400 {@code SEARCH_SORT_INVALID}. */
        public static Sort fromParameter(String value) {
            if (value == null || value.isBlank()) {
                return RELEVANCE;
            }
            String normalized = value.strip().toUpperCase(Locale.ROOT);
            for (Sort sort : values()) {
                if (sort.name().equals(normalized)) {
                    return sort;
                }
            }
            throw new BadRequestException("SEARCH_SORT_INVALID",
                    "El orden debe ser relevance, nearest, price_asc o rating.");
        }
    }

    public ServiceSearchQuery {
        q = blankToNull(q);
        cityCode = blankToNull(cityCode);
        departmentCode = blankToNull(departmentCode);
    }

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    public Sort sortOrDefault() {
        return sort == null ? Sort.RELEVANCE : sort;
    }

    public boolean facetsOrDefault() {
        return facets == null ? pageOrDefault() == 0 : facets;
    }

    public boolean hasLocation() {
        return lat != null && lng != null;
    }

    /** Reglas de {@code /marketplace/search}; cada incumplimiento es 400 con código propio. */
    public void validate() {
        int pageValue = pageOrDefault();
        if (pageValue < 0 || pageValue > SearchQuery.MAX_PAGE) {
            throw new BadRequestException("SEARCH_PAGE_INVALID",
                    "La página debe estar entre 0 y " + SearchQuery.MAX_PAGE + ".");
        }
        int sizeValue = sizeOrDefault();
        if (sizeValue < 1 || sizeValue > MAX_SIZE) {
            throw new BadRequestException("SEARCH_SIZE_INVALID",
                    "El tamaño de página debe estar entre 1 y " + MAX_SIZE + ".");
        }
        if ((lat == null) != (lng == null)) {
            throw new BadRequestException("SEARCH_POINT_INCOMPLETE", "Envía lat y lng juntos o ninguno.");
        }
        if (hasLocation() && !ColombiaBounds.contains(lat, lng)) {
            throw new BadRequestException("SEARCH_POINT_OUTSIDE_COVERAGE", "La ubicación está fuera de Colombia.");
        }
        if (radiusKm != null && (radiusKm < SearchQuery.MIN_RADIUS_KM || radiusKm > SearchQuery.MAX_RADIUS_KM)) {
            throw new BadRequestException("SEARCH_RADIUS_INVALID", "El radio debe estar entre "
                    + SearchQuery.MIN_RADIUS_KM + " y " + SearchQuery.MAX_RADIUS_KM + " km.");
        }
        if ((sortOrDefault() == Sort.NEAREST || radiusKm != null) && !hasLocation()) {
            throw new BadRequestException("LOCATION_REQUIRED",
                    "Para ordenar o filtrar por cercanía envía tu ubicación (lat y lng).");
        }
        if (priceMax != null && priceMax.signum() < 0) {
            throw new BadRequestException("SEARCH_PRICE_RANGE_INVALID", "El precio máximo no puede ser negativo.");
        }
        if ((cityCode != null && !cityCode.matches("\\d{5}"))
                || (departmentCode != null && !departmentCode.matches("\\d{2}"))) {
            throw new BadRequestException("SEARCH_PLACE_INVALID",
                    "La ciudad (5 dígitos) o el departamento (2 dígitos) no tienen el formato DIVIPOLA.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
