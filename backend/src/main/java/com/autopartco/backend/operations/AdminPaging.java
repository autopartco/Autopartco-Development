package com.autopartco.backend.operations;

/** Página (desde 1) y tamaño (1..100) normalizados para los listados de la consola. */
public record AdminPaging(int page, int size) {
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public static AdminPaging of(int page, int size) {
        return new AdminPaging(Math.max(1, page), Math.min(MAX_SIZE, Math.max(1, size)));
    }

    public int offset() {
        return (page - 1) * size;
    }

    /** Texto de búsqueda para {@code ILIKE}: vacío → {@code null}; escapa {@code %}, {@code _} y {@code \}. */
    public static String like(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
