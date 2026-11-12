package com.autopartco.backend.marketplace.questions;

import java.util.regex.Pattern;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Reglas de texto de preguntas y respuestas: longitudes y sin datos de contacto (enlaces, correos ni teléfonos), para
 * que la conversación siga en la plataforma.
 */
final class QuestionRules {
    static final int MIN_QUESTION = 10;
    static final int MAX_QUESTION = 300;
    static final int MAX_ANSWER = 1000;
    static final int MAX_QUERY = 80;

    /** El mismo criterio de enlaces que las reseñas: esquema, {@code www.} o dominio con un TLD común. */
    static final Pattern LINK = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://|\\bwww\\.|\\b[a-z0-9][a-z0-9-]*\\.(com|co|net|org|info|biz|io|me|ly|app|"
                    + "xyz|online|store|shop|site|top|link|click|gl|es|us|tk|ru|cn)(/|\\b))");

    /** Correo, aunque lleve espacios alrededor de la arroba. */
    static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+\\s*@\\s*[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}");

    /** Indicativo de Colombia ({@code +57}). */
    static final Pattern COUNTRY_CODE = Pattern.compile("\\+\\s*57");

    /** Siete o más dígitos seguidos, que pueden ir separados por un espacio o un guion ({@code 300 123 4567}). */
    static final Pattern PHONE = Pattern.compile("(?<!\\d)\\d(?:[ \\t-]?\\d){6,}");

    private QuestionRules() {
    }

    /** Pregunta recortada de 10 a 300 caracteres. 400 {@code QUESTION_INVALID} o {@code QUESTION_CONTACT_NOT_ALLOWED}. */
    static String question(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.length() < MIN_QUESTION || trimmed.length() > MAX_QUESTION) {
            throw new BadRequestException("QUESTION_INVALID",
                    "La pregunta debe tener entre " + MIN_QUESTION + " y " + MAX_QUESTION + " caracteres.");
        }
        requireNoContact(trimmed, "La pregunta");
        return trimmed;
    }

    /**
     * Respuesta del almacén: recortada, obligatoria y hasta 1000 caracteres (400 {@code VALIDATION_ERROR}), sin datos
     * de contacto (400 {@code QUESTION_CONTACT_NOT_ALLOWED}).
     */
    static String answer(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "Escribe la respuesta.");
        }
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_ANSWER) {
            throw new BadRequestException("VALIDATION_ERROR", "La respuesta no puede superar 1000 caracteres.");
        }
        requireNoContact(trimmed, "La respuesta");
        return trimmed;
    }

    /**
     * Patrón ILIKE ({@code %texto%}, con {@code %}, {@code _} y {@code \} escapados) o {@code null} sin búsqueda.
     * 400 {@code QUESTION_QUERY_INVALID} con más de 80 caracteres.
     */
    static String searchPattern(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_QUERY) {
            throw new BadRequestException("QUESTION_QUERY_INVALID",
                    "La búsqueda no puede superar " + MAX_QUERY + " caracteres.");
        }
        return "%" + trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    static boolean hasContact(String text) {
        return LINK.matcher(text).find() || EMAIL.matcher(text).find() || COUNTRY_CODE.matcher(text).find()
                || PHONE.matcher(text).find();
    }

    private static void requireNoContact(String text, String subject) {
        if (hasContact(text)) {
            throw new BadRequestException("QUESTION_CONTACT_NOT_ALLOWED",
                    subject + " no puede incluir enlaces, correos ni teléfonos.");
        }
    }
}
