package com.autopartco.backend.storesuite.inventory;

import java.util.ArrayList;
import java.util.List;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * CSV con {@code ;} como separador y comillas dobles ({@code ""} escapa una comilla; admite saltos de línea dentro de
 * comillas). Sin dependencias externas. La escritura neutraliza celdas que un programa de hojas de cálculo
 * interpretaría como fórmula.
 */
final class CsvCodec {
    static final char SEPARATOR = ';';
    static final String BOM = "﻿";

    private CsvCodec() {
    }

    /** Registro leído; {@code line} es la línea física (desde 1) donde empieza. */
    record Record(int line, List<String> cells) {
    }

    /**
     * Lee todos los registros no vacíos. Acepta BOM inicial y saltos CRLF, LF o CR. Más de {@code maxRecords}
     * registros: 400 {@code TOO_MANY_ROWS}; comillas sin cerrar: 400 {@code INVALID_CSV}.
     */
    static List<Record> parse(String text, int maxRecords) {
        String input = text.startsWith(BOM) ? text.substring(1) : text;
        List<Record> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean touched = false;
        int line = 1;
        int recordLine = 1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && cell.isEmpty()) {
                quoted = true;
                touched = true;
            } else if (c == SEPARATOR) {
                cells.add(cell.toString());
                cell.setLength(0);
                touched = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++;
                }
                finish(records, cells, cell, touched, recordLine, maxRecords);
                touched = false;
                line++;
                recordLine = line;
            } else {
                cell.append(c);
                touched = true;
            }
        }
        if (quoted) {
            throw new BadRequestException("INVALID_CSV", "El archivo tiene comillas sin cerrar.");
        }
        finish(records, cells, cell, touched, recordLine, maxRecords);
        return records;
    }

    private static void finish(List<Record> records, List<String> cells, StringBuilder cell, boolean touched,
            int recordLine, int maxRecords) {
        if (touched) {
            cells.add(cell.toString());
            boolean blank = cells.stream().allMatch(value -> value.isBlank());
            if (!blank) {
                if (records.size() >= maxRecords) {
                    throw new BadRequestException("TOO_MANY_ROWS", "El archivo supera el máximo de filas permitido.");
                }
                records.add(new Record(recordLine, List.copyOf(cells)));
            }
        }
        cells.clear();
        cell.setLength(0);
    }

    /**
     * Celda lista para escribir: si empieza por {@code = + - @} (o tabulación/retorno) se antepone {@code '} para que
     * no se evalúe como fórmula, y se entrecomilla si contiene separador, comillas o saltos de línea.
     */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.indexOf(SEPARATOR) >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
