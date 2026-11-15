package com.autopartco.backend.core.search;

import java.util.List;

/** Página de almacenes publicados. {@code page} desde 0. */
public record StoreSearchResult(List<StoreSearchItem> items, int page, int size, long total) {
    public StoreSearchResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static StoreSearchResult empty(int page, int size) {
        return new StoreSearchResult(List.of(), page, size, 0);
    }
}
