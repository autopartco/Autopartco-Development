package com.autopartco.backend.core.settings.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Esquema fijo de una configuración jsonb: secciones anidadas y hojas con valor por defecto y validación.
 * {@link #effective} fusiona lo guardado con los valores por defecto (lo inválido o desconocido se ignora) y
 * {@link #apply} aplica un PATCH parcial: lo no enviado se conserva, una clave desconocida da {@code UNKNOWN_SETTING} y
 * un valor inválido {@code INVALID_SETTING} (ambos con {@code details.key} = ruta con puntos).
 */
public final class SettingsSchema {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** Normaliza y valida un valor recibido; lanza {@link Invalid} con el mensaje para el cliente. */
    @FunctionalInterface
    public interface Normalizer {
        Object normalize(Object value);
    }

    /** Valor inválido; {@link SettingsSchema} le añade la ruta. */
    public static final class Invalid extends RuntimeException {
        public Invalid(String message) {
            super(message, null, false, false);
        }
    }

    private sealed interface Node permits Section, Leaf {
    }

    private record Section(Map<String, Node> children) implements Node {
    }

    private record Leaf(Object defaultValue, Normalizer normalizer) implements Node {
    }

    private final Section root;

    private SettingsSchema(Section root) {
        this.root = root;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Claves de primer nivel (secciones) que gestiona este esquema. */
    public Set<String> rootKeys() {
        return Collections.unmodifiableSet(root.children().keySet());
    }

    /** Objeto completo: lo guardado y válido, y el valor por defecto en lo demás. */
    public Map<String, Object> effective(Map<String, Object> stored) {
        return effective(root, stored);
    }

    /** Aplica {@code patch} sobre {@code effective} y devuelve un objeto nuevo; no modifica los argumentos. */
    public Map<String, Object> apply(Map<String, Object> effective, Map<String, Object> patch) {
        if (patch == null) {
            throw new BadRequestException("INVALID_SETTING", "Envía un objeto con las configuraciones a cambiar.");
        }
        return apply(root, effective, patch, "");
    }

    private static Map<String, Object> effective(Section section, Object stored) {
        Map<?, ?> storedMap = stored instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        section.children().forEach((key, node) -> {
            Object value = storedMap.get(key);
            if (node instanceof Section child) {
                result.put(key, effective(child, value));
            } else {
                Leaf leaf = (Leaf) node;
                result.put(key, storedMap.containsKey(key) ? normalizeOrDefault(leaf, value) : leaf.defaultValue());
            }
        });
        return result;
    }

    private static Object normalizeOrDefault(Leaf leaf, Object value) {
        try {
            return leaf.normalizer().normalize(value);
        } catch (Invalid | ClassCastException ignored) {
            return leaf.defaultValue();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> apply(Section section, Map<String, Object> current, Map<?, ?> patch, String prefix) {
        Map<String, Object> result = new LinkedHashMap<>(current);
        for (Map.Entry<?, ?> entry : patch.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix + key;
            Node node = section.children().get(key);
            if (node == null) {
                throw new BadRequestException("UNKNOWN_SETTING", "La configuración «" + path + "» no existe.",
                        Map.of("key", path));
            }
            if (node instanceof Section child) {
                if (!(entry.getValue() instanceof Map<?, ?> nestedPatch)) {
                    throw invalid(path, "«" + path + "» debe ser un objeto.");
                }
                Map<String, Object> nestedCurrent = result.get(key) instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : effective(child, null);
                result.put(key, apply(child, nestedCurrent, nestedPatch, path + "."));
            } else {
                try {
                    result.put(key, ((Leaf) node).normalizer().normalize(entry.getValue()));
                } catch (Invalid exception) {
                    throw invalid(path, exception.getMessage());
                }
            }
        }
        return result;
    }

    private static BadRequestException invalid(String path, String message) {
        return new BadRequestException("INVALID_SETTING", message, Map.of("key", path));
    }

    /** Construye el esquema con rutas con puntos, p. ej. {@code leaf("security.loginAlerts", true, bool())}. */
    public static final class Builder {
        private final Map<String, Node> children = new LinkedHashMap<>();

        public Builder leaf(String path, Object defaultValue, Normalizer normalizer) {
            String[] parts = path.split("\\.");
            Map<String, Node> level = children;
            for (int i = 0; i < parts.length - 1; i++) {
                Node existing = level.computeIfAbsent(parts[i], k -> new Section(new LinkedHashMap<>()));
                level = ((Section) existing).children();
            }
            level.put(parts[parts.length - 1], new Leaf(defaultValue, normalizer));
            return this;
        }

        public SettingsSchema build() {
            return new SettingsSchema(new Section(children));
        }
    }

    // ---- Normalizadores ----

    public static Normalizer bool() {
        return value -> {
            if (value instanceof Boolean flag) {
                return flag;
            }
            throw new Invalid("El valor debe ser verdadero o falso.");
        };
    }

    public static Normalizer oneOf(String... allowed) {
        List<String> options = List.of(allowed);
        return value -> {
            if (value instanceof String text && options.contains(text)) {
                return text;
            }
            throw new Invalid("El valor debe ser uno de: " + String.join(", ", options) + ".");
        };
    }

    /** Texto opcional: vacío o {@code null} → {@code null}; sin espacios extremos; máx. {@code max} caracteres. */
    public static Normalizer optionalText(int max) {
        return value -> {
            if (value == null) {
                return null;
            }
            if (!(value instanceof String text)) {
                throw new Invalid("El valor debe ser un texto.");
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            requireLength(trimmed, max);
            requireNoControlChars(trimmed, false);
            return trimmed;
        };
    }

    /** Texto obligatorio con máx. {@code max} caracteres; admite saltos de línea si {@code multiline}. */
    public static Normalizer requiredText(int max, boolean multiline) {
        return value -> {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new Invalid("El texto es obligatorio.");
            }
            String trimmed = text.trim();
            requireLength(trimmed, max);
            requireNoControlChars(trimmed, multiline);
            return trimmed;
        };
    }

    /** Texto opcional que, si viene, cumple {@code pattern}. */
    public static Normalizer optionalMatching(Pattern pattern, String message) {
        return value -> {
            if (value == null) {
                return null;
            }
            if (value instanceof String text && pattern.matcher(text.trim()).matches()) {
                return text.trim();
            }
            throw new Invalid(message);
        };
    }

    /** Lista de UUID sin repetir, máx. {@code max}; se guarda como textos. */
    public static Normalizer uuidList(int max) {
        return value -> {
            List<?> items = list(value);
            Set<String> ids = new LinkedHashSet<>();
            for (Object item : items) {
                if (!(item instanceof String text)) {
                    throw new Invalid("Cada elemento debe ser un identificador válido.");
                }
                try {
                    ids.add(UUID.fromString(text.trim()).toString());
                } catch (IllegalArgumentException exception) {
                    throw new Invalid("Cada elemento debe ser un identificador válido.");
                }
            }
            if (ids.size() > max) {
                throw new Invalid("Puedes elegir máximo " + max + " elementos.");
            }
            return List.copyOf(ids);
        };
    }

    /** Lista de correos válidos sin repetir (en minúsculas), máx. {@code max}. */
    public static Normalizer emailList(int max) {
        return value -> {
            List<?> items = list(value);
            Set<String> emails = new LinkedHashSet<>();
            for (Object item : items) {
                if (!(item instanceof String text) || text.trim().length() > 100
                        || !EMAIL.matcher(text.trim()).matches()) {
                    throw new Invalid("Cada destinatario debe ser un correo válido.");
                }
                emails.add(text.trim().toLowerCase(Locale.ROOT));
            }
            if (emails.size() > max) {
                throw new Invalid("Puedes agregar máximo " + max + " destinatarios.");
            }
            return List.copyOf(emails);
        };
    }

    private static List<?> list(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> items)) {
            throw new Invalid("El valor debe ser una lista.");
        }
        return new ArrayList<>(items);
    }

    private static void requireLength(String text, int max) {
        if (text.codePointCount(0, text.length()) > max) {
            throw new Invalid("El texto no puede superar " + max + " caracteres.");
        }
    }

    private static void requireNoControlChars(String text, boolean allowNewLines) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean newLine = c == '\n' || c == '\r';
            if (Character.isISOControl(c) && !(allowNewLines && newLine)) {
                throw new Invalid("El texto contiene caracteres no permitidos.");
            }
        }
    }
}
