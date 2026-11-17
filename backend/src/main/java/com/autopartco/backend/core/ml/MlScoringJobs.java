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
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

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
 *
 * <p>{@link #rescore(String)} recalcula un modelo a demanda (consola de administración) con una clave de lote propia,
 * así no choca con el lote nocturno del día. Un cerrojo impide que dos puntuaciones corran a la vez en la instancia:
 * los trabajos programados esperan su turno y el recálculo manual se rechaza si hay otra en curso.</p>
 */
@Slf4j
@Service
public class MlScoringJobs {
    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    static final int PAYMENT_WINDOW_HOURS = 2;
    /** Demanda estimada por el modelo. */
    public static final String METHOD_MODEL = "MODEL";
    /** Demanda estimada con el ritmo reciente porque la fila supera el volumen con el que se entrenó el modelo. */
    public static final String METHOD_RECENT_RATE = "RECENT_RATE";

    private final ModelRegistry registry;
    private final MlQueries queries;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReentrantLock running = new ReentrantLock();

    /** Resultado de un recálculo manual: {@code SUCCEEDED} o {@code FAILED} y las filas escritas. */
    public record Rescore(String model, String status, long rows) {
    }

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
        exclusively(() -> {
            safely(ModelRegistry.DEMAND, () -> scoreDemand(cutoff));
            safely(ModelRegistry.STOCKOUT, () -> scoreStockout(cutoff));
        });
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "America/Bogota")
    public void scorePaymentsRecent() {
        exclusively(() -> safely(ModelRegistry.PAYMENT_ANOMALY, this::scorePayments));
    }

    /**
     * Recalcula ya el modelo {@code key} con fecha de corte hoy (pagos: los de las últimas 2 horas sin evaluar). Las
     * pantallas pasan a leer este lote en cuanto termina.
     *
     * @throws NotFoundException si el modelo no existe
     * @throws ConflictException si el modelo no está cargado o hay otra puntuación en curso
     */
    public Rescore rescore(String key) {
        if (!ModelRegistry.KEYS.contains(key)) {
            throw new NotFoundException("ML_MODEL_NOT_FOUND", "El modelo no existe");
        }
        if (registry.loaded(key).isEmpty()) {
            throw new ConflictException("ML_MODEL_NOT_LOADED", "El modelo no está cargado en el backend");
        }
        if (!running.tryLock()) {
            throw new ConflictException("ML_SCORING_RUNNING", "Hay otra puntuación en curso; inténtalo en unos minutos");
        }
        try {
            LocalDate cutoff = LocalDate.now(clock.withZone(ZONE));
            String suffix = ":manual:" + clock.millis();
            long rows = switch (key) {
                case ModelRegistry.DEMAND -> scoreDemand(cutoff, suffix);
                case ModelRegistry.STOCKOUT -> scoreStockout(cutoff, suffix);
                default -> scorePayments();
            };
            log.info("Recálculo manual del modelo {}: {} filas", key, rows);
            return new Rescore(key, "SUCCEEDED", rows);
        } catch (RuntimeException failure) {
            log.warn("Falló el recálculo manual del modelo {}: {}", key, failure.getClass().getSimpleName());
            return new Rescore(key, "FAILED", 0);
        } finally {
            running.unlock();
        }
    }

    // ---- Demanda ------------------------------------------------------------------------------------------------

    long scoreDemand(LocalDate cutoff) {
        return scoreDemand(cutoff, "");
    }

    long scoreDemand(LocalDate cutoff, String runSuffix) {
        return runDaily(ModelRegistry.DEMAND, cutoff, runSuffix, (loaded, rows) -> {
            TreeEnsemble model = loaded.model();
            int horizon = model.metadata().path("horizonDays").asInt(28);
            double sum4Max = model.splitMax("sum4");
            double sum13Max = model.splitMax("sum13");
            int sum4At = model.features().indexOf("sum4");
            int sum13At = model.features().indexOf("sum13");
            List<MlQueries.Prediction> out = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                double[] x = model.vector(row);
                double sum4 = x[sum4At];
                double sum13 = x[sum13At];
                // Los árboles no extrapolan: más allá del mayor corte aprendido su respuesta es plana y subestima a
                // los almacenes con mucho más volumen que los datos de entrenamiento.
                boolean outside = sum4 > sum4Max || sum13 > sum13Max;
                double expected = outside ? recentRate(sum4, sum13, horizon) : model.predict(x);
                ObjectNode explanation = mapper.createObjectNode()
                        .put("categoryId", row.get("category_id").toString())
                        .put("method", outside ? METHOD_RECENT_RATE : METHOD_MODEL);
                out.add(new MlQueries.Prediction("BRANCH", (UUID) row.get("store_id"), (UUID) row.get("branch_id"),
                        null, cutoff, cutoff.plusDays(horizon - 1L), horizon, round(expected),
                        (double) Poisson.quantile(expected, 0.1), (double) Poisson.quantile(expected, 0.9), null,
                        null, explanation.toString()));
            }
            return out;
        });
    }

    /**
     * Unidades esperadas en {@code horizonDays} con el ritmo reciente: media entre las últimas 4 semanas y el promedio
     * semanal de las últimas 13, escalada al horizonte.
     */
    static double recentRate(double sum4, double sum13, int horizonDays) {
        double weekly = 0.5 * (sum4 / 4.0) + 0.5 * (sum13 / 13.0);
        return weekly * horizonDays / 7.0;
    }

    // ---- Agotamiento --------------------------------------------------------------------------------------------

    long scoreStockout(LocalDate cutoff) {
        return scoreStockout(cutoff, "");
    }

    long scoreStockout(LocalDate cutoff, String runSuffix) {
        return runDaily(ModelRegistry.STOCKOUT, cutoff, runSuffix, (loaded, rows) -> {
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

    private long runDaily(String key, LocalDate cutoff, String runSuffix, Scorer scorer) {
        Optional<ModelRegistry.Loaded> loaded = registry.loaded(key);
        if (loaded.isEmpty()) {
            return 0;
        }
        TreeEnsemble model = loaded.get().model();
        UUID versionId = queries.ensureVersion(model);
        String params = mapper.createObjectNode().put("cutoff", cutoff.toString()).toString();
        Optional<UUID> run = queries.startRun(versionId, key + ":" + model.version() + ":" + cutoff + runSuffix,
                params);
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

    private void exclusively(Runnable job) {
        running.lock();
        try {
            job.run();
        } finally {
            running.unlock();
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
