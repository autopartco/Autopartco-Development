package com.autopartco.backend.shared.web;

import java.util.List;

/** Página de resultados con la forma común de los listados paginados (páginas desde 1). */
public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(List.copyOf(items), page, size, total, totalPages);
    }
}
