package com.autopartco.backend.core.ml;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Puntuación en lote. Demanda y agotamiento: cada noche a las 02:30 (America/Bogota; configurable con
 * {@code ml.scoring.nightly-cron}) con fecha de corte hoy, un lote por modelo y día
 * ({@code ml.batch_runs.idempotency_key} evita repetirlo y que dos instancias lo corran a la vez).
 * Pagos: cada 15 minutos puntúa los pagos de las últimas 2 horas aún sin evaluar por la versión vigente y guarda la
 * evaluación en {@code payment_risk_assessments} ({@code REVIEW} si supera el umbral). Un fallo se registra en el lote
 * y no afecta a los demás; las pantallas siguen leyendo el último lote correcto.
 */
@Slf4j
@Service
public class MlScoringJobs {
    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    static final int PAYMENT_WINDOW_HOURS = 2;

    private final ModelRegistry registry;
    private final MlQueries queries;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public MlScoringJobs(ModelRegistry registry, MlQueries queries) {
        this(registry, queries, Clock.systemUTC());
    }

    MlScoringJobs(ModelRegistry registry, MlQueries queries, Clock clock) {
        this.registry = registry;
        this.queries = queries;
        this.clock = clock;
    }

    @Scheduled(cron = "${ml.scoring.nightly-cron:0 30 2 * * *}", zone = "America/Bogota")
    public void scoreNightly() {
        LocalDate cutoff = LocalDate.now(clock.withZone(ZONE));
        safely(ModelRegistry.DEMAND, () -> scoreDemand(cutoff));
        safely(ModelRegistry.STOCKOUT, () -> scoreStockout(cutoff));
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "America/Bogota")
    public void scorePaymentsRecent() {
        safely(ModelRegistry.PAYMENT_ANOMALY, this::scorePayments);
    }

    // ---- Demanda ------------------------------------------------------------------------------------------------

    long scoreDemand(LocalDate cutoff) {
        return runDaily(ModelRegistry.DEMAND, cutoff, (loaded, rows) -> {
            TreeEnsemble model = loaded.model();
            int horizon = model.metadata().path("horizonDays").asInt(28);
            List<MlQueries.Prediction> out = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                double expected = model.predict(model.vector(row));
                ObjectNode explanation = mapper.createObjectNode().put("categoryId", row.get("category_id").toString());
                out.add(new MlQueries.Prediction("BRANCH", (UUID) row.get("store_id"), (UUID) row.get("branch_id"),
                        null, cutoff, cutoff.plusDays(horizon - 1L), horizon, round(expected),
                        (double) Poisson.quantile(expected, 0.1), (double) Poisson.quantile(expected, 0.9), null,
                        null, explanation.toString()));
            }
            return out;
        });
    }

    // ---- Agotamiento --------------------------------------------------------------------------------------------

    long scoreStockout(LocalDate cutoff) {
        return runDaily(ModelRegistry.STOCKOUT, cutoff, (loaded, rows) -> {
            TreeEnsemble model = loaded.model();
            int horizon = model.metadata().path("horizonDays").asInt(14);
            double high = model.metadata().path("thresholds").path("high").asDouble(0.10);
            double medium = model.metadata().path("thresholds").path("medium").asDouble(0.05);
            List<MlQueries.Prediction> out = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                double p = model.predict(model.vector(row));
                String level = p >= high ? "HIGH" : p >= medium ? "MEDIUM" : "LOW";
                ObjectNode explanation = mapper.createObjectNode()
                        .put("inventoryId", row.get("inventory_id").toString())
                        .put("stock", ((Number) row.get("stock")).intValue())
                        .put("coverDays", round(((Number) row.get("cover_days")).doubleValue()));
                out.add(new MlQueries.Prediction("PRODUCT_BRANCH", (UUID) row.get("store_id"),
                        (UUID) row.get("branch_id"), (UUID) row.get("product_id"), cutoff,
                        cutoff.plusDays(horizon - 1L), horizon, null, null, null, level, round(p),
                        explanation.toString()));
            }
            return out;
        });
    }

    // ---- Pagos --------------------------------------------------------------------------------------------------

    long scorePayments() {
        Optional<ModelRegistry.Loaded> loaded = registry.loaded(ModelRegistry.PAYMENT_ANOMALY);
        if (loaded.isEmpty()) {
            return 0;
        }
        TreeEnsemble model = loaded.get().model();
        UUID versionId = queries.ensureVersion(model);
        OffsetDateTime until = OffsetDateTime.now(clock);
        OffsetDateTime since = until.minusHours(PAYMENT_WINDOW_HOURS);
        Set<UUID> done = queries.assessedPayments(versionId, since);
        double threshold = model.metadata().path("thresholds").path("anomaly").asDouble(0.6);
        List<MlQueries.Assessment> out = new ArrayList<>();
        for (Map<String, Object> row : queries.features(loaded.get().featuresSql(),
                Map.of("since", since, "until", until))) {
            UUID paymentId = (UUID) row.get("payment_id");
            if (done.contains(paymentId)) {
                continue;
            }
            double score = model.predict(model.vector(row));
            ObjectNode signals = mapper.createObjectNode()
                    .put("model", model.key())
                    .put("version", model.version())
                    .put("threshold", round(threshold));
            out.add(new MlQueries.Assessment(paymentId, (UUID) row.get("order_id"), Math.min(1, Math.max(0, score)),
                    score > threshold ? "REVIEW" : "ALLOW", signals.toString()));
        }
        if (!out.isEmpty()) {
            queries.insertAssessments(versionId, out);
            log.info("Pagos evaluados por {}: {} ({} para revisión)", model.key(), out.size(),
                    out.stream().filter(a -> a.decision().equals("REVIEW")).count());
        }
        return out.size();
    }

    // ---- Común --------------------------------------------------------------------------------------------------

    @FunctionalInterface
    interface Scorer {
        List<MlQueries.Prediction> score(ModelRegistry.Loaded loaded, List<Map<String, Object>> rows);
    }

    private long runDaily(String key, LocalDate cutoff, Scorer scorer) {
        Optional<ModelRegistry.Loaded> loaded = registry.loaded(key);
        if (loaded.isEmpty()) {
            return 0;
        }
        TreeEnsemble model = loaded.get().model();
        UUID versionId = queries.ensureVersion(model);
        String params = mapper.createObjectNode().put("cutoff", cutoff.toString()).toString();
        Optional<UUID> run = queries.startRun(versionId, key + ":" + model.version() + ":" + cutoff, params);
        if (run.isEmpty()) {
            return 0;
        }
        long started = System.nanoTime();
        try {
            List<Map<String, Object>> rows = queries.features(loaded.get().featuresSql(), Map.of("cutoff", cutoff));
            List<MlQueries.Prediction> predictions = scorer.score(loaded.get(), rows);
            queries.insertPredictions(versionId, run.get(), predictions);
            queries.finishRun(run.get(), true, predictions.size(), null);
            log.info("Modelo {} puntuó {} filas en {} ms", key, predictions.size(),
                    (System.nanoTime() - started) / 1_000_000);
            return predictions.size();
        } catch (RuntimeException failure) {
            queries.finishRun(run.get(), false, 0, failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private static void safely(String key, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException failure) {
            log.warn("Falló la puntuación del modelo {}: {}", key, failure.getClass().getSimpleName());
        }
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
