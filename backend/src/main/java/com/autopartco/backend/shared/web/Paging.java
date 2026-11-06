package com.autopartco.backend.shared.web;

import java.util.List;

import org.springframework.http.ResponseEntity;

/**
 * Paginado compatible hacia atrás: el cuerpo sigue siendo la lista y el total va en cabeceras
 * ({@code X-Total-Count}, {@code X-Total-Pages}, {@code X-Page}, {@code X-Size}). Sin {@code page} ni {@code size}
 * se devuelve la lista completa, como antes. {@code page} empieza en 0; {@code size} por defecto 24, máximo 100.
 */
public final class Paging {
    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static boolean requested(Integer page, Integer size) {
        return page != null || size != null;
    }

    public static int page(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    public static int size(Integer size) {
        return size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
    }

    /** Pagina en memoria una lista ya completa (p. ej. la del catálogo cacheado). */
    public static <T> ResponseEntity<List<T>> slice(List<T> all, Integer page, Integer size) {
        if (!requested(page, size)) {
            return ResponseEntity.ok(all);
        }
        int p = page(page);
        int s = size(size);
        int from = (int) Math.min((long) p * s, all.size());
        return of(all.subList(from, Math.min(from + s, all.size())), all.size(), p, s);
    }

    /** Respuesta con la página ya cargada y el total de elementos. */
    public static <T> ResponseEntity<List<T>> of(List<T> items, long total, int page, int size) {
        long pages = (total + size - 1) / size;
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total))
                .header("X-Total-Pages", String.valueOf(pages))
                .header("X-Page", String.valueOf(page))
                .header("X-Size", String.valueOf(size))
                .body(List.copyOf(items));
    }
}
