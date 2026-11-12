package com.autopartco.backend.marketplace.questions;

import java.util.regex.Matcher;
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

    /** Correo, aunque lleve espacios alrededor de la arroba o no tenga TLD ({@code pepe@gmail}). */
    static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+\\s*@\\s*[a-z0-9-]{2,}");

    /** Correo escrito con la palabra «arroba» ({@code pepe arroba gmail}, {@code pepe (arroba) gmail}). */
    static final Pattern EMAIL_WORD = Pattern.compile(
            "(?iu)[a-z0-9._%+-]+\\s*[(\\[]?\\s*\\barroba\\b\\s*[)\\]]?\\s*[a-z0-9-]{2,}");

    /** Indicativo de Colombia ({@code +57}). */
    static final Pattern COUNTRY_CODE = Pattern.compile("\\+\\s*57");

    /** «WhatsApp» o «wsp» seguidos de dígitos ({@code wsp 300...}, {@code WhatsApp: 3...}). */
    static final Pattern MESSAGING = Pattern.compile("(?iu)\\b(?:whats\\s?app|wsp)\\b[\\s:.#()/-]*\\d");

    /** Racha de dígitos separados solo por espacios, puntos, guiones, paréntesis o barras (sin letras de por medio). */
    static final Pattern DIGIT_RUN = Pattern.compile("\\d(?:[\\s.()/-]*\\d)*");

    /**
     * Número colombiano de 10 dígitos, celular ({@code 3xx}) o fijo ({@code 60x}), opcionalmente con {@code 57}
     * delante, que ocupa la racha completa una vez quitados los separadores: {@code 300 123 45 67},
     * {@code (300) 123 4567}, {@code 300/123/4567} y {@code 3 0 0 1 2 3 4 5 6 7} se rechazan. Exigir la racha
     * completa evita rechazar referencias OEM ({@code 04465-33450}, {@code 8200 123 456}), precios
     * ({@code 1.250.000}) y años («Mazda 3 2015 2016»). Falso positivo aceptado: una referencia de 10 dígitos que
     * empiece por 3 o 60 ({@code ref 3101234567}) se rechaza como teléfono.
     */
    static final Pattern PHONE = Pattern.compile("(?:57)?(?:3\\d{2}|60\\d)\\d{7}");

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
        return LINK.matcher(text).find() || EMAIL.matcher(text).find() || EMAIL_WORD.matcher(text).find()
                || COUNTRY_CODE.matcher(text).find() || MESSAGING.matcher(text).find() || hasPhone(text);
    }

    /** Alguna racha de dígitos, sin separadores, es exactamente un teléfono ({@link #PHONE}). */
    static boolean hasPhone(String text) {
        Matcher run = DIGIT_RUN.matcher(text);
        while (run.find()) {
            if (PHONE.matcher(run.group().replaceAll("\\D", "")).matches()) {
                return true;
            }
        }
        return false;
    }

    private static void requireNoContact(String text, String subject) {
        if (hasContact(text)) {
            throw new BadRequestException("QUESTION_CONTACT_NOT_ALLOWED",
                    subject + " no puede incluir enlaces, correos ni teléfonos.");
        }
    }
}
