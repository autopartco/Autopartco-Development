package com.autopartco.backend.core.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** El evaluador Java reproduce las predicciones de LightGBM y scikit-learn generadas al entrenar (parity.json). */
class TreeEnsembleParityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ModelRegistry REGISTRY = new ModelRegistry();

    @ParameterizedTest
    @ValueSource(strings = {ModelRegistry.DEMAND, ModelRegistry.STOCKOUT, ModelRegistry.PAYMENT_ANOMALY})
    void reproducesTrainingLibrary(String key) throws IOException {
        TreeEnsemble model = REGISTRY.model(key).orElseThrow();
        JsonNode parity = read("ml/" + key + "/parity.json");
        double tolerance = parity.path("tolerance").asDouble();
        JsonNode rows = parity.path("rows");
        assertThat(rows.size()).isGreaterThanOrEqualTo(40);
        for (int i = 0; i < rows.size(); i++) {
            double[] x = new double[rows.get(i).size()];
            for (int j = 0; j < x.length; j++) {
                x[j] = rows.get(i).get(j).asDouble();
            }
            double expected = parity.path("expected").get(i).asDouble();
            assertThat(model.predict(x)).as("%s fila %d", key, i).isCloseTo(expected,
                    org.assertj.core.data.Offset.offset(Math.max(tolerance, Math.abs(expected) * 1e-9)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {ModelRegistry.DEMAND, ModelRegistry.STOCKOUT, ModelRegistry.PAYMENT_ANOMALY})
    void scoresThousandRowsFast(String key) {
        TreeEnsemble model = REGISTRY.model(key).orElseThrow();
        double[] x = new double[model.features().size()];
        for (int warm = 0; warm < 2_000; warm++) {
            x[warm % x.length] = warm % 7;
            model.predict(x);
        }
        long start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            x[i % x.length] = i % 11;
            model.predict(x);
        }
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(50);
    }

    @Test
    void vectorFollowsModelOrderAndRejectsMissingValues() {
        TreeEnsemble model = REGISTRY.model(ModelRegistry.STOCKOUT).orElseThrow();
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < model.features().size(); i++) {
            row.put(model.features().get(i), i);
        }
        assertThat(model.vector(row)[3]).isEqualTo(3.0);
        row.put(model.features().getFirst(), null);
        assertThatThrownBy(() -> model.vector(row)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingArtifactLeavesModelUnloaded() {
        ModelRegistry registry = new ModelRegistry(java.util.List.of("does_not_exist"));
        assertThat(registry.model("does_not_exist")).isEmpty();
    }

    @Test
    void rejectsMalformedTrees() throws IOException {
        JsonNode bad = MAPPER.readTree("""
                {"key":"x","version":"1","features":["a"],"output":"PROBABILITY_MEAN",
                 "trees":[{"feature":[0],"threshold":[1.0],"left":[0],"right":[0],"value":[0.0]}]}
                """);
        assertThatThrownBy(() -> TreeEnsemble.of(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    private static JsonNode read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return MAPPER.readTree(in);
        }
    }
}
