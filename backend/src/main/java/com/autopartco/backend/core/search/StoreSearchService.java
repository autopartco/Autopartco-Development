package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.BusinessType;
import com.autopartco.backend.core.organization.StoreBrand;

/**
 * Búsqueda de almacenes publicados ({@link StoreSearchSql}): una sola consulta filtra, ordena (nombre exacto o
 * prefijo, similitud, calificación), pagina y trae los conteos de la página; los datos de marca salen de
 * {@link StoreBrand}.
 */
@Service
public class StoreSearchService {
    private final JdbcClient jdbcClient;

    public StoreSearchService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 400 {@code SEARCH_PAGE_INVALID}, {@code SEARCH_SIZE_INVALID} o {@code SEARCH_PLACE_INVALID}. */
    @Transactional(readOnly = true)
    public StoreSearchResult search(StoreSearchQuery query) {
        query.validate();
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        Map<String, Object> params = new LinkedHashMap<>(params(query.normalizedText(), query.city()));
        params.put("limit", size);
        params.put("offset", (long) page * size);
        long[] total = {0};
        List<StoreSearchItem> items = jdbcClient.sql(StoreSearchSql.PAGE_SQL)
                .params(params)
                .query((rs, row) -> {
                    total[0] = rs.getLong("total");
                    UUID id = rs.getObject("id", UUID.class);
                    BigDecimal average = rs.getBigDecimal("average");
                    long reviews = rs.getLong("rating_count");
                    return new StoreSearchItem(id, rs.getString("business_name"),
                            StoreBrand.url(id, StoreBrand.LOGO, rs.getString("logo_path")),
                            StoreSearchItem.shorten(rs.getString("description")), cities(rs.getString("cities")),
                            rs.getInt("branch_count"),
                            new StoreSearchItem.Rating(reviews == 0 || average == null ? null
                                    : average.setScale(1, RoundingMode.HALF_UP), reviews),
                            businessType(rs.getString("business_type")), rs.getLong("published_products"),
                            rs.getLong("active_services"));
                })
                .list();
        return new StoreSearchResult(items, page, size, items.isEmpty() ? 0 : total[0]);
    }

    /** Parámetros de coincidencia compartidos con las sugerencias; {@code text} ya normalizado. */
    static Map<String, Object> params(String text, String cityCode) {
        return Map.of("q", text, "contains", "%" + text + "%", "prefix", text + "%", "cityCode", cityCode);
    }

    static List<String> cities(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split("\\|"))
                .map(String::trim)
                .filter(city -> !city.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static BusinessType businessType(String value) {
        try {
            return value == null ? BusinessType.PARTS : BusinessType.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return BusinessType.PARTS;
        }
    }
}
