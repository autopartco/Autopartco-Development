package com.autopartco.backend.marketplace.reviews;

import java.util.Arrays;
import java.util.List;

/**
 * Tipo de reseña: tablas, columna del objeto calificado y categorías válidas. Los nombres de tabla y columna son
 * constantes de código (nunca valores del cliente), así que pueden ir en el texto del SQL.
 */
enum ReviewKind {
    STORE("store_reviews", "store_review_aspects", "store_id", names(StoreAspect.values())),
    PRODUCT("product_reviews", "product_review_aspects", "product_id", names(ProductAspect.values()));

    private final String table;
    private final String aspectTable;
    private final String scopeColumn;
    private final List<String> aspects;

    ReviewKind(String table, String aspectTable, String scopeColumn, List<String> aspects) {
        this.table = table;
        this.aspectTable = aspectTable;
        this.scopeColumn = scopeColumn;
        this.aspects = aspects;
    }

    String table() {
        return table;
    }

    String aspectTable() {
        return aspectTable;
    }

    String scopeColumn() {
        return scopeColumn;
    }

    /** Códigos de categoría en el orden de declaración del enum. */
    List<String> aspects() {
        return aspects;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
