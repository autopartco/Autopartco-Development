package com.autopartco.backend.core.search;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.autopartco.backend.core.organization.ServiceCategory;

/**
 * Vocabulario de las categorías de servicio: etiqueta en español, ícono Lucide y los términos (sin tildes ni
 * mayúsculas) que activan la intención de servicio en una consulta. Es estático (no depende de la BD): también alimenta
 * el {@code document} de {@code search_services} (categoría + sinónimos con peso B). «llanta» sola no activa
 * {@link ServiceCategory#TIRES}: sigue priorizando productos.
 */
public final class ServiceTerms {

    /** {@code terms} normalizados con {@link QueryParser#normalizeName}: solo {@code [a-z0-9]} y espacios. */
    public record Entry(ServiceCategory category, String label, String icon, List<String> terms) {
    }

    private static final Map<ServiceCategory, Entry> ENTRIES = new EnumMap<>(ServiceCategory.class);

    static {
        add(ServiceCategory.MECHANICS, "Mecánica general", "wrench", "mecanica", "mecanico", "taller");
        add(ServiceCategory.ELECTRICAL, "Electricidad automotriz", "zap", "electricista", "electrico automotriz");
        add(ServiceCategory.ALIGNMENT_BALANCING, "Alineación y balanceo", "gauge", "alineacion", "balanceo",
                "alinear");
        add(ServiceCategory.TIRES, "Llantas y montallantas", "circle-dot", "montallantas", "despinche");
        add(ServiceCategory.LUBRICATION, "Lubricación y cambio de aceite", "droplets", "cambio de aceite",
                "lubricacion", "aceite y filtro");
        add(ServiceCategory.BODY_PAINT, "Latonería y pintura", "paint-bucket", "latoneria", "pintura",
                "latoneria y pintura");
        add(ServiceCategory.INSTALLATION, "Instalación y montaje", "cog", "instalacion", "montaje");
        add(ServiceCategory.DIAGNOSTICS, "Diagnóstico y escáner", "scan-search", "escaner", "diagnostico",
                "revision");
        add(ServiceCategory.AC, "Aire acondicionado", "snowflake", "aire acondicionado", "carga de gas");
        add(ServiceCategory.OTHER, "Otros servicios", "ellipsis");
    }

    private ServiceTerms() {
    }

    private static void add(ServiceCategory category, String label, String icon, String... terms) {
        ENTRIES.put(category, new Entry(category, label, icon, List.of(terms)));
    }

    /** Entrada de la categoría (siempre existe: todas las categorías están declaradas). */
    public static Entry of(ServiceCategory category) {
        return ENTRIES.get(category);
    }

    public static List<Entry> all() {
        return List.copyOf(ENTRIES.values());
    }

    /** Etiqueta, o el nombre del enum si no tuviera. */
    public static String label(ServiceCategory category) {
        Entry entry = ENTRIES.get(category);
        return entry == null ? category.name() : entry.label();
    }

    /** Texto indexable de la categoría: etiqueta y todos sus términos (el índice los normaliza sin tildes). */
    public static String indexText(ServiceCategory category) {
        Entry entry = ENTRIES.get(category);
        return entry == null ? "" : (entry.label() + " " + String.join(" ", entry.terms())).strip();
    }

    /**
     * Categoría de servicio que {@code normalizedText} pide, o {@code null}: el término más largo que aparece como
     * palabras completas (el empate lo gana el orden de {@link ServiceCategory}). {@code normalizedText} debe venir de
     * {@link QueryParser#normalizeName}.
     */
    public static ServiceCategory intent(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return null;
        }
        String padded = " " + normalizedText + " ";
        ServiceCategory best = null;
        int bestLength = 0;
        for (ServiceCategory category : ServiceCategory.values()) {
            for (String term : ENTRIES.get(category).terms()) {
                if (term.length() > bestLength && padded.contains(" " + term + " ")) {
                    best = category;
                    bestLength = term.length();
                }
            }
        }
        return best;
    }

    /**
     * Hasta {@code max} categorías para el autocompletado: las que la consulta pide ({@link #intent}) y las que tienen
     * un término o la etiqueta que empieza con ella (mínimo 3 caracteres).
     */
    public static List<ServiceCategory> suggest(String normalizedText, int max) {
        List<ServiceCategory> found = new ArrayList<>();
        if (normalizedText == null || normalizedText.length() < 3 || max < 1) {
            return found;
        }
        ServiceCategory intent = intent(normalizedText);
        if (intent != null && intent != ServiceCategory.OTHER) {
            found.add(intent);
        }
        for (ServiceCategory category : ServiceCategory.values()) {
            if (found.size() >= max) {
                break;
            }
            Entry entry = ENTRIES.get(category);
            boolean startsWith = entry.terms().stream().anyMatch(term -> startsWithWord(term, normalizedText))
                    || startsWithWord(QueryParser.normalizeName(entry.label()), normalizedText);
            if (startsWith && !found.contains(category)) {
                found.add(category);
            }
        }
        return found.size() > max ? found.subList(0, max) : found;
    }

    private static boolean startsWithWord(String text, String prefix) {
        return text != null && (text.startsWith(prefix) || text.contains(" " + prefix));
    }
}
