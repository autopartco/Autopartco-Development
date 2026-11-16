package com.autopartco.backend.operations.anomalies;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

/** Modelos predictivos registrados en {@code ml.*}: versión vigente, métricas frente a la línea base y último lote. */
@Service
@AllArgsConstructor
public class MlModelsAdmin {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcClient jdbc;

    public record ModelView(String code, String task, String targetEntity, String description, String version,
            String algorithm, String status, String role, String dataOrigin, OffsetDateTime trainedAt,
            OffsetDateTime deployedAt, String trainFrom, String trainTo, String validationFrom, String validationTo,
            Map<String, Object> metrics, Map<String, Object> baselineMetrics, Map<String, Object> hyperparameters,
            String knownLimitations, OffsetDateTime lastRunAt, String lastRunStatus, Long lastRunRows,
            long runsLast7Days, long failedRunsLast7Days) {
    }

    @Transactional(readOnly = true)
    public List<ModelView> models() {
        return jdbc.sql("""
                SELECT m.code, m.task, m.target_entity, m.description, mv.version, mv.algorithm, mv.status, mv.role,
                       mv.data_origin, mv.trained_at, mv.deployed_at, CAST(mv.train_from AS text) AS train_from,
                       CAST(mv.train_to AS text) AS train_to, CAST(mv.validation_from AS text) AS validation_from,
                       CAST(mv.validation_to AS text) AS validation_to, CAST(mv.metrics AS text) AS metrics,
                       CAST(mv.baseline_metrics AS text) AS baseline_metrics,
                       CAST(mv.hyperparameters AS text) AS hyperparameters, mv.known_limitations,
                       COALESCE(last.finished_at, risk.last_at) AS last_run_at,
                       COALESCE(last.status, CASE WHEN risk.last_at IS NOT NULL THEN 'SUCCEEDED' END) AS last_run_status,
                       COALESCE(last.rows_written, risk.scored) AS rows_written,
                       (SELECT count(*) FROM ml.batch_runs b JOIN ml.model_versions v ON v.id = b.model_version_id
                        WHERE v.model_id = m.id AND b.started_at >= now() - interval '7 days') AS runs_7d,
                       (SELECT count(*) FROM ml.batch_runs b JOIN ml.model_versions v ON v.id = b.model_version_id
                        WHERE v.model_id = m.id AND b.status = 'FAILED'
                          AND b.started_at >= now() - interval '7 days') AS failed_7d
                FROM ml.models m
                JOIN LATERAL (
                    SELECT * FROM ml.model_versions v WHERE v.model_id = m.id
                    ORDER BY (v.status = 'DEPLOYED') DESC, v.trained_at DESC NULLS LAST LIMIT 1
                ) mv ON TRUE
                LEFT JOIN LATERAL (
                    SELECT b.finished_at, b.status, b.rows_written FROM ml.batch_runs b
                    WHERE b.model_version_id = mv.id ORDER BY b.started_at DESC LIMIT 1
                ) last ON TRUE
                LEFT JOIN LATERAL (
                    -- payment_anomaly no usa lotes: puntúa pagos cada 15 min en payment_risk_assessments.
                    SELECT max(a.assessed_at) AS last_at, count(*) AS scored FROM payment_risk_assessments a
                    WHERE a.model_version_id = mv.id
                ) risk ON TRUE
                ORDER BY m.code
                """)
                .query((rs, n) -> new ModelView(rs.getString("code"), rs.getString("task"),
                        rs.getString("target_entity"), rs.getString("description"), rs.getString("version"),
                        rs.getString("algorithm"), rs.getString("status"), rs.getString("role"),
                        rs.getString("data_origin"), rs.getObject("trained_at", OffsetDateTime.class),
                        rs.getObject("deployed_at", OffsetDateTime.class), rs.getString("train_from"),
                        rs.getString("train_to"), rs.getString("validation_from"), rs.getString("validation_to"),
                        json(rs.getString("metrics")), json(rs.getString("baseline_metrics")),
                        json(rs.getString("hyperparameters")), rs.getString("known_limitations"),
                        rs.getObject("last_run_at", OffsetDateTime.class), rs.getString("last_run_status"),
                        (Long) rs.getObject("rows_written"), rs.getLong("runs_7d"), rs.getLong("failed_7d")))
                .list();
    }

    private static Map<String, Object> json(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (Exception failure) {
            return Map.of();
        }
    }
}
