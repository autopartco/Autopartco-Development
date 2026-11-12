package com.autopartco.backend.marketplace.reviews;

import java.util.Locale;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Filtros y orden de un listado público de reseñas. Páginas desde 1.
 *
 * @param stars       solo reseñas con esas estrellas (1–5) o {@code null}
 * @param withComment solo con comentario
 */
record ReviewListQuery(int page, int size, Sort sort, Integer stars, boolean withComment) {
    static final int DEFAULT_SIZE = 10;
    static final int MAX_SIZE = 50;
    /** Más allá no hay reseñas reales; evita desbordar el desplazamiento con páginas absurdas. */
    static final int MAX_PAGE = 10_000;

    enum Sort {
        RECENT("r.created_at DESC, r.id"),
        HIGHEST("r.rating DESC, r.created_at DESC, r.id"),
        LOWEST("r.rating ASC, r.created_at DESC, r.id");

        private final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }

        String orderBy() {
            return orderBy;
        }
    }

    /**
     * Normaliza la página (1..{@value #MAX_PAGE}) y el tamaño (1..{@value #MAX_SIZE}). 400 {@code REVIEW_SORT_INVALID}
     * con un orden distinto de {@code recent|highest|lowest} y {@code REVIEW_STARS_INVALID} fuera de 1–5.
     */
    static ReviewListQuery of(Integer page, Integer size, String sort, Integer stars, Boolean withComment) {
        int p = page == null ? 1 : Math.min(MAX_PAGE, Math.max(1, page));
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        Sort order = Sort.RECENT;
        if (sort != null && !sort.isBlank()) {
            try {
                order = Sort.valueOf(sort.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new BadRequestException("REVIEW_SORT_INVALID", "El orden debe ser recent, highest o lowest.");
            }
        }
        if (stars != null && (stars < 1 || stars > 5)) {
            throw new BadRequestException("REVIEW_STARS_INVALID", "Las estrellas deben estar entre 1 y 5.");
        }
        return new ReviewListQuery(p, s, order, stars, Boolean.TRUE.equals(withComment));
    }

    int offset() {
        return (page - 1) * size;
    }
}
