package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.autopartco.backend.core.catalog.VehicleFilter;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.location.ColombiaBounds;

import lombok.Builder;

/**
 * Parámetros de {@code GET /api/v1/marketplace/search} (§1 del plan v2.0.0). Todos opcionales. {@code page} desde 0.
 * {@code facets} nulo = solo en la primera página. {@code interpret=false} desactiva la interpretación de {@code q}
 * (año, vehículo, ciudad, referencia): todo el texto se busca como texto.
 */
@Builder(toBuilder = true)
public record SearchQuery(
        String q,
        UUID categoryId,
        List<UUID> manufacturerIds,
        UUID makeId,
        UUID modelId,
        UUID versionId,
        Integer year,
        BigDecimal priceMin,
        BigDecimal priceMax,
        Boolean inStock,
        Boolean onSale,
        UUID storeId,
        UUID branchId,
        String cityCode,
        String departmentCode,
        BigDecimal lat,
        BigDecimal lng,
        Integer radiusKm,
        Integer minRating,
        Integer minProductRating,
        Boolean universal,
        SearchSort sort,
        Integer page,
        Integer size,
        Boolean facets,
        Boolean interpret
) {
    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 48;
    public static final int MAX_PAGE = 1_000;
    public static final int MIN_RADIUS_KM = 1;
    public static final int MAX_RADIUS_KM = 200;
    public static final int MAX_MANUFACTURERS = 20;
    static final int MIN_YEAR = 1950;

    public SearchQuery {
        manufacturerIds = manufacturerIds == null ? List.of()
                : manufacturerIds.stream().filter(Objects::nonNull).distinct().toList();
        cityCode = blankToNull(cityCode);
        departmentCode = blankToNull(departmentCode);
    }

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    public SearchSort sortOrDefault() {
        return sort == null ? SearchSort.RELEVANCE : sort;
    }

    public boolean interpretOrDefault() {
        return interpret == null || interpret;
    }

    public boolean facetsOrDefault() {
        return facets == null ? pageOrDefault() == 0 : facets;
    }

    public boolean hasLocation() {
        return lat != null && lng != null;
    }

    /**
     * Reglas de §1; cada incumplimiento es 400 con código propio. {@code maxSize} lo fija el llamador ({@link #MAX_SIZE}
     * para {@code /search}; el catálogo compatible admite más).
     */
    public void validate(int maxSize, int maxPage) {
        int pageValue = pageOrDefault();
        if (pageValue < 0 || pageValue > maxPage) {
            throw new BadRequestException("SEARCH_PAGE_INVALID", "La página debe estar entre 0 y " + maxPage + ".");
        }
        int sizeValue = sizeOrDefault();
        if (sizeValue < 1 || sizeValue > maxSize) {
            throw new BadRequestException("SEARCH_SIZE_INVALID", "El tamaño de página debe estar entre 1 y " + maxSize + ".");
        }
        if ((lat == null) != (lng == null)) {
            throw new BadRequestException("SEARCH_POINT_INCOMPLETE", "Envía lat y lng juntos o ninguno.");
        }
        if (hasLocation() && !ColombiaBounds.contains(lat, lng)) {
            throw new BadRequestException("SEARCH_POINT_OUTSIDE_COVERAGE", "La ubicación está fuera de Colombia.");
        }
        if (radiusKm != null && (radiusKm < MIN_RADIUS_KM || radiusKm > MAX_RADIUS_KM)) {
            throw new BadRequestException("SEARCH_RADIUS_INVALID",
                    "El radio debe estar entre " + MIN_RADIUS_KM + " y " + MAX_RADIUS_KM + " km.");
        }
        if ((sortOrDefault() == SearchSort.NEAREST || radiusKm != null) && !hasLocation()) {
            throw new BadRequestException("LOCATION_REQUIRED",
                    "Para ordenar o filtrar por cercanía envía tu ubicación (lat y lng).");
        }
        if ((priceMin != null && priceMin.signum() < 0) || (priceMax != null && priceMax.signum() < 0)
                || (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0)) {
            throw new BadRequestException("SEARCH_PRICE_RANGE_INVALID",
                    "El rango de precios no es válido: priceMin debe ser menor o igual que priceMax.");
        }
        if (minRating != null && (minRating < 1 || minRating > 5)) {
            throw new BadRequestException("SEARCH_RATING_INVALID", "La calificación mínima debe estar entre 1 y 5.");
        }
        if (minProductRating != null && (minProductRating < 1 || minProductRating > 5)) {
            throw new BadRequestException("SEARCH_RATING_INVALID",
                    "La calificación mínima del producto debe estar entre 1 y 5.");
        }
        if (year != null && (year < MIN_YEAR || year > Year.now().getValue() + 1)) {
            throw new BadRequestException("SEARCH_YEAR_INVALID", "El año del vehículo no es válido.");
        }
        if ((cityCode != null && !cityCode.matches("\\d{5}"))
                || (departmentCode != null && !departmentCode.matches("\\d{2}"))) {
            throw new BadRequestException("SEARCH_PLACE_INVALID",
                    "La ciudad (5 dígitos) o el departamento (2 dígitos) no tienen el formato DIVIPOLA.");
        }
        if (manufacturerIds.size() > MAX_MANUFACTURERS) {
            throw new BadRequestException("SEARCH_TOO_MANY_MANUFACTURERS",
                    "Puedes filtrar por máximo " + MAX_MANUFACTURERS + " fabricantes.");
        }
        // Mismas reglas que el catálogo: modelo exige marca y versión exige modelo (400 VEHICLE_FILTER_INVALID).
        new VehicleFilter(makeId, modelId, versionId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
