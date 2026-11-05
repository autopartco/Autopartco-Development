package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/** Reporte de búsquedas del marketplace (read model sobre {@code marketplace_searches}, {@code make},
 * {@code model} y {@code geo_municipalities}). */
@Service
@AllArgsConstructor
public class SearchInsights {
    static final int LIMIT = 20;

    private static final String RANGE = """
            WHERE m.occurred_at >= :fromTs AND m.occurred_at < :toTs
            """;

    static final String TOTALS_SQL = """
            SELECT count(*) AS searches,
                   count(*) FILTER (WHERE m.results_count = 0) AS unmet
            FROM marketplace_searches m
            """ + RANGE;

    static final String TOP_QUERIES_SQL = """
            SELECT m.query_normalized AS query,
                   count(*) AS searches,
                   count(*) FILTER (WHERE m.results_count = 0) AS unmet
            FROM marketplace_searches m
            """ + RANGE + """
              AND m.query_normalized IS NOT NULL
            GROUP BY m.query_normalized
            ORDER BY searches DESC, query
            LIMIT :limit
            """;

    static final String TOP_UNMET_QUERIES_SQL = """
            SELECT m.query_normalized AS query,
                   count(*) AS searches
            FROM marketplace_searches m
            """ + RANGE + """
              AND m.query_normalized IS NOT NULL
              AND m.results_count = 0
            GROUP BY m.query_normalized
            ORDER BY searches DESC, query
            LIMIT :limit
            """;

    static final String TOP_VEHICLES_SQL = """
            SELECT m.make_id,
                   mk.name AS make_name,
                   m.model_id,
                   md.name AS model_name,
                   m.vehicle_year AS year,
                   count(*) AS searches,
                   count(*) FILTER (WHERE m.results_count = 0) AS unmet
            FROM marketplace_searches m
            LEFT JOIN make mk ON mk.id = m.make_id
            LEFT JOIN model md ON md.id = m.model_id
            """ + RANGE + """
              AND m.make_id IS NOT NULL
            GROUP BY m.make_id, mk.name, m.model_id, md.name, m.vehicle_year
            ORDER BY searches DESC, make_name, model_name
            LIMIT :limit
            """;

    static final String BY_MUNICIPALITY_SQL = """
            SELECT m.municipality_code,
                   g.name AS municipality_name,
                   count(*) AS searches,
                   count(*) FILTER (WHERE m.results_count = 0) AS unmet
            FROM marketplace_searches m
            LEFT JOIN geo_municipalities g ON g.code = m.municipality_code
            """ + RANGE + """
              AND m.municipality_code IS NOT NULL
            GROUP BY m.municipality_code, g.name
            ORDER BY searches DESC, m.municipality_code
            LIMIT :limit
            """;

    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public SearchReport report(DateRange range) {
        long[] totals = scoped(TOTALS_SQL, range)
                .query((result, row) -> new long[] {result.getLong("searches"), result.getLong("unmet")})
                .optional()
                .orElse(new long[] {0, 0});
        BigDecimal unmetRate = totals[0] == 0
                ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(totals[1]).divide(BigDecimal.valueOf(totals[0]), 4, RoundingMode.HALF_UP);
        List<SearchReport.QueryCount> topQueries = scoped(TOP_QUERIES_SQL, range)
                .param("limit", LIMIT)
                .query((result, row) -> new SearchReport.QueryCount(
                        result.getString("query"), result.getLong("searches"), result.getLong("unmet")))
                .list();
        List<SearchReport.UnmetQuery> topUnmet = scoped(TOP_UNMET_QUERIES_SQL, range)
                .param("limit", LIMIT)
                .query((result, row) -> new SearchReport.UnmetQuery(
                        result.getString("query"), result.getLong("searches")))
                .list();
        List<SearchReport.VehicleCount> vehicles = scoped(TOP_VEHICLES_SQL, range)
                .param("limit", LIMIT)
                .query((result, row) -> new SearchReport.VehicleCount(
                        result.getObject("make_id", UUID.class),
                        result.getString("make_name"),
                        result.getObject("model_id", UUID.class),
                        result.getString("model_name"),
                        result.getObject("year", Integer.class),
                        result.getLong("searches"),
                        result.getLong("unmet")))
                .list();
        List<SearchReport.MunicipalityCount> municipalities = scoped(BY_MUNICIPALITY_SQL, range)
                .param("limit", LIMIT)
                .query((result, row) -> new SearchReport.MunicipalityCount(
                        result.getString("municipality_code"),
                        result.getString("municipality_name"),
                        result.getLong("searches"),
                        result.getLong("unmet")))
                .list();
        return new SearchReport(range.from(), range.to(), totals[0], totals[1], unmetRate, topQueries, topUnmet,
                vehicles, municipalities);
    }

    private JdbcClient.StatementSpec scoped(String sql, DateRange range) {
        return jdbcClient.sql(sql)
                .param("fromTs", range.startInstant())
                .param("toTs", range.endInstant());
    }
}
