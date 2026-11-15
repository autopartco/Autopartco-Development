package com.autopartco.backend.core.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Aprendizaje nocturno de los clics: cada día a las 04:30 (America/Bogota) recalcula desde cero
 * {@code search_query_category} con los últimos 90 días, en una transacción (borrado + insert … select, sin bucles por
 * fila), y recarga {@link QueryLearning}. {@code impressions} son las búsquedas con resultados de cada consulta y
 * {@code clicks} los clics por consulta y categoría del producto. {@code @EnableScheduling} lo activa
 * {@code core.payments.internal.PaymentsConfig} para todo el backend.
 */
@Slf4j
@Component
public class QueryLearningJob {
    static final String DELETE_SQL = "DELETE FROM search_query_category";

    static final String INSERT_SQL = """
            INSERT INTO search_query_category (normalized_query, category_id, clicks, impressions, updated_at)
            SELECT k.query_normalized, k.category_id, k.clicks, coalesce(q.impressions, 0), now()
            FROM (
                -- Un voto por sesión (consulta × categoría): muchos clics de una misma sesión no envenenan el orden.
                SELECT s.query_normalized, p.category_id, count(DISTINCT s.session_hash) AS clicks
                FROM search_clicks c
                JOIN marketplace_searches s ON s.id = c.search_id
                JOIN products p ON p.id = c.product_id
                WHERE c.created_at >= now() - interval '90 days'
                  AND s.query_normalized IS NOT NULL AND p.category_id IS NOT NULL
                  AND s.session_hash IS NOT NULL
                GROUP BY s.query_normalized, p.category_id
            ) k
            LEFT JOIN (
                SELECT query_normalized, count(*) AS impressions
                FROM marketplace_searches
                WHERE occurred_at >= now() - interval '90 days'
                  AND results_count > 0 AND query_normalized IS NOT NULL
                GROUP BY query_normalized
            ) q ON q.query_normalized = k.query_normalized
            """;

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transaction;
    private final QueryLearning learning;

    public QueryLearningJob(JdbcClient jdbcClient, PlatformTransactionManager transactionManager,
            QueryLearning learning) {
        this.jdbcClient = jdbcClient;
        this.transaction = new TransactionTemplate(transactionManager);
        this.learning = learning;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "America/Bogota")
    public void learn() {
        try {
            Integer rows = transaction.execute(status -> {
                jdbcClient.sql(DELETE_SQL).update();
                return jdbcClient.sql(INSERT_SQL).update();
            });
            learning.reload();
            log.info("Aprendizaje de consultas: {} pares consulta-categoría recalculados.", rows);
        } catch (RuntimeException failure) {
            log.warn("Falló el aprendizaje de consultas: {}", failure.getClass().getSimpleName());
        }
    }
}
