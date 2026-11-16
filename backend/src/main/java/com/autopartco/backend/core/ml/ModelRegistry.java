package com.autopartco.backend.core.ml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Carga una sola vez, al arrancar, los modelos y su SQL de variables desde {@code classpath:ml/<clave>/}. Si un
 * artefacto falta o está dañado, ese modelo queda sin cargar (se registra) y el backend arranca igual.
 */
@Slf4j
@Component
public class ModelRegistry {
    public static final String DEMAND = "demand_category_28d";
    public static final String STOCKOUT = "stockout_14d";
    public static final String PAYMENT_ANOMALY = "payment_anomaly";
    static final List<String> KEYS = List.of(DEMAND, STOCKOUT, PAYMENT_ANOMALY);

    record Loaded(TreeEnsemble model, String featuresSql) {
    }

    private final Map<String, Loaded> models = new LinkedHashMap<>();

    public ModelRegistry() {
        this(KEYS);
    }

    ModelRegistry(List<String> keys) {
        ObjectMapper mapper = new ObjectMapper();
        for (String key : keys) {
            try {
                TreeEnsemble model = TreeEnsemble.of(mapper.readTree(read(key, "model.json")));
                models.put(key, new Loaded(model, read(key, "features.sql")));
                log.info("Modelo {} {} cargado", key, model.version());
            } catch (IOException | RuntimeException failure) {
                log.warn("Modelo {} no cargado: {}", key, failure.getClass().getSimpleName());
            }
        }
    }

    Optional<Loaded> loaded(String key) {
        return Optional.ofNullable(models.get(key));
    }

    public Optional<TreeEnsemble> model(String key) {
        return loaded(key).map(Loaded::model);
    }

    private static String read(String key, String file) throws IOException {
        try (InputStream in = new ClassPathResource("ml/" + key + "/" + file).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
