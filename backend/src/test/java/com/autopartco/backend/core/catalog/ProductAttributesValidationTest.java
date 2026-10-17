package com.autopartco.backend.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.shared.error.BadRequestException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ProductAttributesValidationTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static ProductCreateRequest create(String description) {
        return new ProductCreateRequest(UUID.randomUUID(), "Filtro", null, null, description, new BigDecimal("10.00"),
                1, UUID.randomUUID(), UUID.randomUUID(), null, null, List.of(), null, Map.of(), null, false);
    }

    private static ProductUpdateRequest update(String description) {
        return new ProductUpdateRequest(null, null, null, description, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static void assertInvalid(Map<String, Object> attributes) {
        assertEquals("INVALID_PRODUCT_ATTRIBUTE", assertThrows(BadRequestException.class,
                () -> ProductCatalog.normalizeAttributes(attributes)).getCode());
    }

    @Test
    void descripcionDeMasDe5000CaracteresEsInvalidaEnAltaYEdicion() {
        String larga = "x".repeat(5001);
        assertTrue(VALIDATOR.validate(create(larga)).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("description")));
        assertTrue(VALIDATOR.validate(update(larga)).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    void descripcionDe5000CaracteresEsValida() {
        String limite = "x".repeat(5000);
        assertTrue(VALIDATOR.validate(create(limite)).stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("description")));
        assertTrue(VALIDATOR.validate(update(limite)).stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    void rechazaValoresAnidados() {
        assertInvalid(Map.of("a", Map.of("b", 1)));
        assertInvalid(Map.of("a", List.of(1, 2)));
    }

    @Test
    void rechazaTextoDeMasDe500Caracteres() {
        assertInvalid(Map.of("a", "x".repeat(501)));
    }

    @Test
    void rechazaTamanoTotalSuperiorA8Kb() {
        Map<String, Object> grande = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            grande.put("k" + i, "y".repeat(500));
        }
        assertInvalid(grande);
    }

    @Test
    void rechazaClavesLargasOVacias() {
        assertInvalid(Map.of("k".repeat(101), "v"));
        assertInvalid(Map.of(" ", "v"));
    }

    @Test
    void aceptaEscalaresValidos() {
        Map<String, Object> ok = new HashMap<>();
        ok.put("color", "rojo");
        ok.put("largo", 12.5);
        ok.put("cantidad", 3);
        ok.put("nuevo", true);
        ok.put("nulo", null);
        Map<String, Object> result = ProductCatalog.normalizeAttributes(ok);
        assertEquals(4, result.size());
        assertEquals("x".repeat(500), ProductCatalog.normalizeAttributes(Map.of("a", "x".repeat(500))).get("a"));
    }
}
