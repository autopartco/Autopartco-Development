package com.autopartco.backend.core.ml;

import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;

/** SQL directo sobre el esquema {@code ml} y {@code payment_risk_assessments}. No depende de otros módulos. */
@Repository
@AllArgsConstructor
class MlQueries {
    static final int BATCH_SIZE = 1000;
    static final String OWNER_MODULE = "CORE_ML";
    static final String ARTIFACT_URI = "classpath:ml/%s/model.json";

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    /** Fila de predicción para {@code ml.predictions}; los campos nulos se guardan como NULL. */
    record Prediction(String entityType, UUID storeId, UUID branchId, UUID productId, LocalDate featuresAsOf,
            LocalDate targetDate, int horizonDays, Double value, Double lowerBound, Double upperBound,
            String predictedClass, Double probability, String explanationJson) {
    }

    record Assessment(UUID paymentId, UUID orderId, double riskScore, String decision, String signalsJson) {
    }

    List<Map<String, Object>> features(String sql, Map<String, ?> params) {
        return jdbc.sql(sql).params(params).query().listOfRows();
    }

    // ---- Registro ---------------------------------------------------------------------------------------------

    /** Id de la versión del modelo en {@code ml.model_versions}; la registra (DEPLOYED, SHADOW) si no existe. */
    UUID ensureVersion(TreeEnsemble model) {
        JsonNode m = model.metadata();
        jdbc.sql("""
                INSERT INTO ml.models (code, task, target_entity, description, owner_module)
                VALUES (:code, :task, :entity, :description, :owner)
                ON CONFLICT (code) DO NOTHING
                """)
                .param("code", model.key())
                .param("task", m.path("task").asText())
                .param("entity", m.path("targetEntity").asText())
                .param("description", m.path("name").asText() + " · " + m.path("algorithm").asText())
                .param("owner", OWNER_MODULE)
                .update();
        jdbc.sql("""
                INSERT INTO ml.model_versions (model_id, version, algorithm, is_baseline, feature_set_version,
                    hyperparameters, metrics, baseline_metrics, known_limitations, train_from, train_to,
                    validation_from, validation_to, artifact_uri, code_commit, data_origin, status, role,
                    trained_at, deployed_at)
                SELECT id, :version, :algorithm, false, 1, CAST(:hyper AS jsonb), CAST(:metrics AS jsonb),
                    CAST(:baseline AS jsonb), :limitations, CAST(:trainFrom AS date), CAST(:trainTo AS date),
                    CAST(:validationFrom AS date), CAST(:validationTo AS date), :uri, :commit, :origin,
                    'DEPLOYED', 'SHADOW', CAST(:trainedAt AS timestamptz), now()
                FROM ml.models WHERE code = :code
                ON CONFLICT (model_id, version) DO NOTHING
                """)
                .param("code", model.key())
                .param("version", model.version())
                .param("algorithm", m.path("algorithm").asText())
                .param("hyper", m.path("hyperparameters").toString())
                .param("metrics", m.path("metrics").toString())
                .param("baseline", m.path("baselineMetrics").toString())
                .param("limitations", "Entrenado con datos " + m.path("dataOrigin").asText("SYNTHETIC")
                        + "; métricas en partición temporal de prueba.")
                .param("trainFrom", m.path("trainFrom").asText(null))
                .param("trainTo", m.path("trainTo").asText(null))
                .param("validationFrom", m.path("validationFrom").asText(null))
                .param("validationTo", m.path("validationTo").asText(null))
                .param("uri", ARTIFACT_URI.formatted(model.key()))
                .param("commit", m.path("codeCommit").asText())
                .param("origin", m.path("dataOrigin").asText("SYNTHETIC"))
                .param("trainedAt", m.path("trainedAt").asText())
                .update();
        return jdbc.sql("""
                SELECT mv.id FROM ml.model_versions mv JOIN ml.models m ON m.id = mv.model_id
                WHERE m.code = :code AND mv.version = :version
                """)
                .param("code", model.key())
                .param("version", model.version())
                .query(UUID.class)
                .single();
    }

    // ---- Lotes ------------------------------------------------------------------------------------------------

    /** Abre un lote; vacío si ya existe uno con esa clave (otra instancia o el mismo día ya puntuado). */
    Optional<UUID> startRun(UUID versionId, String idempotencyKey, String parametersJson) {
        return jdbc.sql("""
                INSERT INTO ml.batch_runs (run_type, model_version_id, idempotency_key, parameters, status)
                VALUES ('SCORING', :version, :key, CAST(:params AS jsonb), 'RUNNING')
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id
                """)
                .param("version", versionId)
                .param("key", idempotencyKey)
                .param("params", parametersJson)
                .query(UUID.class)
                .optional();
    }

    void finishRun(UUID runId, boolean succeeded, long rows, String error) {
        jdbc.sql("""
                UPDATE ml.batch_runs SET status = :status, rows_written = :rows, error_message = :error,
                    finished_at = now()
                WHERE id = :id
                """)
                .param("status", succeeded ? "SUCCEEDED" : "FAILED")
                .param("rows", rows)
                .param("error", error)
                .param("id", runId)
                .update();
    }

    void insertPredictions(UUID versionId, UUID runId, List<Prediction> rows) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO ml.predictions (model_version_id, batch_run_id, entity_type, store_id, branch_id,
                    product_id, features_as_of, target_date, horizon_days, value, lower_bound, upper_bound,
                    predicted_class, probability, explanation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, versionId);
            ps.setObject(2, runId);
            ps.setString(3, row.entityType());
            ps.setObject(4, row.storeId());
            ps.setObject(5, row.branchId());
            ps.setObject(6, row.productId());
            ps.setObject(7, row.featuresAsOf());
            ps.setObject(8, row.targetDate());
            ps.setInt(9, row.horizonDays());
            setDouble(ps, 10, row.value());
            setDouble(ps, 11, row.lowerBound());
            setDouble(ps, 12, row.upperBound());
            ps.setString(13, row.predictedClass());
            setDouble(ps, 14, row.probability());
            ps.setString(15, row.explanationJson());
        });
    }

    // ---- Pagos ------------------------------------------------------------------------------------------------

    Set<UUID> assessedPayments(UUID versionId, OffsetDateTime since) {
        return new HashSet<>(jdbc.sql("""
                SELECT a.payment_id FROM payment_risk_assessments a JOIN payments p ON p.id = a.payment_id
                WHERE a.model_version_id = :version AND p.created_at >= :since
                """)
                .param("version", versionId)
                .param("since", since)
                .query(UUID.class)
                .list());
    }

    void insertAssessments(UUID versionId, List<Assessment> rows) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO payment_risk_assessments (payment_id, order_id, stage, risk_score, decision, signals,
                    model_version_id)
                VALUES (?, ?, 'POST_AUTHORIZATION', ?, ?, CAST(? AS jsonb), ?)
                """, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, row.paymentId());
            ps.setObject(2, row.orderId());
            ps.setBigDecimal(3, java.math.BigDecimal.valueOf(row.riskScore()).setScale(6,
                    java.math.RoundingMode.HALF_UP));
            ps.setString(4, row.decision());
            ps.setString(5, row.signalsJson());
            ps.setObject(6, versionId);
        });
    }

    // ---- Lecturas ---------------------------------------------------------------------------------------------

    record LatestRun(UUID runId, String version, String algorithm, OffsetDateTime finishedAt) {
    }

    Optional<LatestRun> latestRun(String modelCode) {
        return jdbc.sql("""
                SELECT br.id, mv.version, mv.algorithm, br.finished_at
                FROM ml.batch_runs br
                JOIN ml.model_versions mv ON mv.id = br.model_version_id
                JOIN ml.models m ON m.id = mv.model_id
                WHERE m.code = :code AND br.run_type = 'SCORING' AND br.status = 'SUCCEEDED'
                ORDER BY br.finished_at DESC
                LIMIT 1
                """)
                .param("code", modelCode)
                .query((rs, n) -> new LatestRun(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class)))
                .optional();
    }

    record DemandRow(UUID branchId, String branchName, UUID categoryId, String categoryCode, double expectedUnits,
            double low, double high, String method) {
    }

    List<DemandRow> demand(UUID runId, UUID storeId, UUID branchId) {
        return jdbc.sql("""
                SELECT p.branch_id, b.name, c.id, c.code, p.value, p.lower_bound, p.upper_bound,
                    COALESCE(p.explanation ->> 'method', 'MODEL')
                FROM ml.predictions p
                JOIN branches b ON b.id = p.branch_id
                JOIN categories c ON c.id = CAST(p.explanation ->> 'categoryId' AS uuid)
                WHERE p.batch_run_id = :run AND p.store_id = :store
                  AND (CAST(:branch AS uuid) IS NULL OR p.branch_id = CAST(:branch AS uuid))
                ORDER BY p.value DESC
                """)
                .param("run", runId)
                .param("store", storeId)
                .param("branch", branchId, Types.OTHER)
                .query((rs, n) -> new DemandRow(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getDouble(5), rs.getDouble(6),
                        rs.getDouble(7), rs.getString(8)))
                .list();
    }

    record StockoutRow(UUID inventoryId, UUID productId, String productName, String sku, UUID branchId,
            String branchName, double probability, String level, int stock, double coverDays) {
    }

    List<StockoutRow> stockout(UUID runId, UUID storeId, double minProbability, int limit) {
        return jdbc.sql("""
                SELECT CAST(p.explanation ->> 'inventoryId' AS uuid), p.product_id, pr.name, pr.sku, p.branch_id,
                    b.name, p.probability, p.predicted_class, CAST(p.explanation ->> 'stock' AS integer),
                    CAST(p.explanation ->> 'coverDays' AS double precision)
                FROM ml.predictions p
                JOIN products pr ON pr.id = p.product_id
                JOIN branches b ON b.id = p.branch_id
                WHERE p.batch_run_id = :run AND p.store_id = :store AND p.probability >= :min
                ORDER BY p.probability DESC
                LIMIT :limit
                """)
                .param("run", runId)
                .param("store", storeId)
                .param("min", minProbability)
                .param("limit", limit)
                .query((rs, n) -> new StockoutRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6),
                        rs.getDouble(7), rs.getString(8), rs.getInt(9), rs.getDouble(10)))
                .list();
    }

    private static void setDouble(java.sql.PreparedStatement ps, int index, Double value)
            throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setDouble(index, value);
        }
    }
}
