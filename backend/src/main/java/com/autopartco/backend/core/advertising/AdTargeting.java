package com.autopartco.backend.core.advertising;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Segmentación de una campaña ({@code ad_campaigns.targeting}, objeto JSON ≤ 8 KB). Todas las listas son opcionales:
 * <ul>
 *   <li>{@code keywords}: frases normalizadas (sin tildes, minúsculas), máx. {@value #MAX_KEYWORDS} de 2 a
 *       {@value #MAX_KEYWORD_LENGTH} caracteres.</li>
 *   <li>{@code categoryIds}, {@code makeIds}, {@code modelIds}: máx. {@value #MAX_IDS} cada una.</li>
 *   <li>{@code cityCodes}: códigos DIVIPOLA de municipio (5 dígitos), máx. {@value #MAX_IDS}. Es un filtro: con
 *       ciudades, el anuncio solo sale cuando se conoce la ciudad del visitante y está en la lista.</li>
 * </ul>
 * Con esos topes el JSON nunca supera 8 KB.
 */
public record AdTargeting(
        List<String> keywords,
        List<UUID> categoryIds,
        List<UUID> makeIds,
        List<UUID> modelIds,
        List<String> cityCodes
) {
    public static final int MAX_KEYWORDS = 20;
    public static final int MAX_KEYWORD_LENGTH = 60;
    public static final int MAX_IDS = 20;
    public static final AdTargeting NONE = new AdTargeting(List.of(), List.of(), List.of(), List.of(), List.of());

    private static final Pattern CITY_CODE = Pattern.compile("\\d{5}");

    public AdTargeting {
        keywords = keywords == null ? List.of() : keywords.stream().filter(Objects::nonNull).toList();
        categoryIds = distinctIds(categoryIds);
        makeIds = distinctIds(makeIds);
        modelIds = distinctIds(modelIds);
        cityCodes = cityCodes == null ? List.of() : cityCodes.stream().filter(Objects::nonNull).toList();
    }

    /** Normaliza y valida lo que envía el cliente ({@code null} = sin segmentación). 400 {@code AD_TARGETING_INVALID}. */
    public static AdTargeting sanitize(AdTargeting raw) {
        if (raw == null) {
            return NONE;
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String keyword : raw.keywords()) {
            String normalized = AdText.normalize(keyword);
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.length() < 2 || normalized.length() > MAX_KEYWORD_LENGTH) {
                throw invalid("Cada palabra clave debe tener entre 2 y " + MAX_KEYWORD_LENGTH + " caracteres.");
            }
            keywords.add(normalized);
        }
        LinkedHashSet<String> cities = new LinkedHashSet<>();
        for (String code : raw.cityCodes()) {
            String trimmed = code == null ? "" : code.trim();
            if (!CITY_CODE.matcher(trimmed).matches()) {
                throw invalid("Los códigos de ciudad deben tener 5 dígitos (DIVIPOLA).");
            }
            cities.add(trimmed);
        }
        if (keywords.size() > MAX_KEYWORDS) {
            throw invalid("Máximo " + MAX_KEYWORDS + " palabras clave.");
        }
        if (raw.categoryIds().size() > MAX_IDS || raw.makeIds().size() > MAX_IDS || raw.modelIds().size() > MAX_IDS
                || cities.size() > MAX_IDS) {
            throw invalid("Máximo " + MAX_IDS + " valores por criterio de segmentación.");
        }
        return new AdTargeting(List.copyOf(keywords), raw.categoryIds(), raw.makeIds(), raw.modelIds(),
                List.copyOf(cities));
    }

    /** {@code true} si no restringe por consulta, categoría ni vehículo (las ciudades no cuentan). */
    public boolean hasNoRelevanceCriteria() {
        return keywords.isEmpty() && categoryIds.isEmpty() && makeIds.isEmpty() && modelIds.isEmpty();
    }

    /** Objeto JSON para la columna {@code targeting}. */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("keywords", keywords);
        json.put("categoryIds", categoryIds.stream().map(UUID::toString).toList());
        json.put("makeIds", makeIds.stream().map(UUID::toString).toList());
        json.put("modelIds", modelIds.stream().map(UUID::toString).toList());
        json.put("cityCodes", cityCodes);
        return json;
    }

    /** Lee la columna {@code targeting}; valores con forma inesperada se descartan. */
    public static AdTargeting fromJson(Map<String, ?> json) {
        if (json == null || json.isEmpty()) {
            return NONE;
        }
        return new AdTargeting(strings(json.get("keywords")), uuids(json.get("categoryIds")),
                uuids(json.get("makeIds")), uuids(json.get("modelIds")), strings(json.get("cityCodes")));
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static List<UUID> uuids(Object value) {
        List<UUID> ids = new ArrayList<>();
        for (String text : strings(value)) {
            try {
                ids.add(UUID.fromString(text));
            } catch (IllegalArgumentException ignored) {
                // Valor inválido en la columna: se ignora.
            }
        }
        return ids;
    }

    private static List<UUID> distinctIds(List<UUID> ids) {
        return ids == null ? List.of() : List.copyOf(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException("AD_TARGETING_INVALID", message);
    }
}
