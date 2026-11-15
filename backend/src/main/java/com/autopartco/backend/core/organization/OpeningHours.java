package com.autopartco.backend.core.organization;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Horario semanal de una sucursal: {@code {"mon": [["08:00","18:00"]], ..., "sun": [], "notes": "Festivos 9-13"}}.
 * Días {@code mon..sun}, de 0 a 2 franjas {@code HH:mm} ascendentes y sin solaparse (una franja no cruza la
 * medianoche); {@code notes} hasta {@value #NOTES_MAX} caracteres sin HTML. Un día ausente o sin franjas está cerrado.
 */
public final class OpeningHours {
    public static final String INVALID = "INVALID_OPENING_HOURS";
    public static final int NOTES_MAX = 120;
    static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final String NOTES = "notes";
    private static final int MAX_SLOTS = 2;
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private OpeningHours() {
    }

    /**
     * Valida y normaliza. Devuelve {@code null} si el horario queda vacío (objeto {@code {}}), lo que borra el horario.
     *
     * @throws BadRequestException {@code INVALID_OPENING_HOURS}
     */
    public static Map<String, Object> validate(Map<String, Object> hours) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : hours.entrySet()) {
            String key = entry.getKey();
            if (NOTES.equals(key)) {
                String notes = notes(entry.getValue());
                if (notes != null) {
                    normalized.put(NOTES, notes);
                }
            } else if (DAYS.contains(key)) {
                normalized.put(key, slots(key, entry.getValue()));
            } else {
                throw invalid("Día no válido en el horario: use mon, tue, wed, thu, fri, sat, sun o notes.");
            }
        }
        // Orden estable de lectura: lunes a domingo y las notas al final.
        Map<String, Object> ordered = new LinkedHashMap<>();
        DAYS.stream().filter(normalized::containsKey).forEach(day -> ordered.put(day, normalized.get(day)));
        if (normalized.containsKey(NOTES)) {
            ordered.put(NOTES, normalized.get(NOTES));
        }
        return ordered.isEmpty() ? null : ordered;
    }

    /** Días del formato heredado ({@code "lunes": "08:00-18:00"}, {@code "domingo": "cerrado"}). */
    private static final Map<String, String> LEGACY_DAYS = Map.of("lunes", "mon", "martes", "tue", "miercoles", "wed",
            "jueves", "thu", "viernes", "fri", "sabado", "sat", "domingo", "sun");
    private static final Pattern LEGACY_RANGE = Pattern.compile("^(\\d{2}:\\d{2})-(\\d{2}:\\d{2})$");

    /**
     * Horario en el formato del contrato para leerlo. Un horario heredado con días en español y franjas en texto
     * ({@code {"lunes": "08:00-18:00", "domingo": "cerrado"}}) se traduce a {@code {"mon": [["08:00","18:00"]],
     * "sun": []}}. Uno ya oficial (o nulo) se devuelve igual. Al guardar desde el portal queda en el formato oficial.
     */
    public static Map<String, Object> readable(Map<String, Object> hours) {
        if (hours == null || DAYS.stream().anyMatch(hours::containsKey)
                || LEGACY_DAYS.keySet().stream().noneMatch(hours::containsKey)) {
            return hours;
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (String day : DAYS) {
            LEGACY_DAYS.entrySet().stream().filter(entry -> entry.getValue().equals(day)).findFirst()
                    .map(entry -> hours.get(entry.getKey()))
                    .ifPresent(value -> converted.put(day, legacySlots(value)));
        }
        if (hours.get(NOTES) instanceof String notes) {
            converted.put(NOTES, notes);
        }
        return converted;
    }

    private static List<List<String>> legacySlots(Object value) {
        List<List<String>> slots = new ArrayList<>();
        if (value instanceof String text) {
            for (String part : text.split(",")) {
                java.util.regex.Matcher matcher = LEGACY_RANGE.matcher(part.strip());
                if (matcher.matches() && TIME.matcher(matcher.group(1)).matches()
                        && TIME.matcher(matcher.group(2)).matches()) {
                    slots.add(List.of(matcher.group(1), matcher.group(2)));
                }
            }
        }
        return slots;
    }

    /**
     * {@code true} si ahora (hora de {@code clock}) cae dentro de una franja de hoy; {@code false} si hoy no tiene
     * franjas o ya pasaron; {@code null} si no hay horario semanal (vacío, solo notas o ilegible).
     */
    public static Boolean openNow(Map<String, Object> rawHours, Clock clock) {
        Map<String, Object> hours = readable(rawHours);
        if (hours == null || DAYS.stream().noneMatch(hours::containsKey)) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        Object today = hours.get(DAYS.get(now.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue()));
        LocalTime time = now.toLocalTime();
        try {
            if (today instanceof List<?> slots) {
                for (Object slot : slots) {
                    if (slot instanceof List<?> range && range.size() == 2
                            && !time.isBefore(LocalTime.parse(String.valueOf(range.get(0))))
                            && time.isBefore(LocalTime.parse(String.valueOf(range.get(1))))) {
                        return true;
                    }
                }
            }
        } catch (DateTimeParseException unreadable) {
            return null;
        }
        return false;
    }

    private static List<List<String>> slots(String day, Object value) {
        if (!(value instanceof List<?> raw)) {
            throw invalid("Las franjas de «" + day + "» deben ser una lista.");
        }
        if (raw.size() > MAX_SLOTS) {
            throw invalid("«" + day + "» admite hasta " + MAX_SLOTS + " franjas.");
        }
        List<List<String>> slots = new ArrayList<>();
        LocalTime previousEnd = null;
        for (Object item : raw) {
            if (!(item instanceof List<?> range) || range.size() != 2
                    || !(range.get(0) instanceof String from) || !(range.get(1) instanceof String to)
                    || !TIME.matcher(from).matches() || !TIME.matcher(to).matches()) {
                throw invalid("Cada franja de «" + day + "» debe ser [\"HH:mm\", \"HH:mm\"].");
            }
            LocalTime start = LocalTime.parse(from);
            LocalTime end = LocalTime.parse(to);
            if (!start.isBefore(end)) {
                throw invalid("En «" + day + "» la hora de cierre debe ser posterior a la de apertura.");
            }
            if (previousEnd != null && start.isBefore(previousEnd)) {
                throw invalid("Las franjas de «" + day + "» deben ir en orden y sin solaparse.");
            }
            previousEnd = end;
            slots.add(List.of(from, to));
        }
        return slots;
    }

    private static String notes(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw invalid("Las notas del horario deben ser texto.");
        }
        String trimmed = text.trim();
        if (trimmed.length() > NOTES_MAX) {
            throw invalid("Las notas del horario no pueden superar " + NOTES_MAX + " caracteres.");
        }
        if (trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0) {
            throw invalid("Las notas del horario no pueden contener HTML.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException(INVALID, message);
    }
}
