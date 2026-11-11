package com.autopartco.backend.core.search;

import java.util.UUID;

/**
 * Lo que se reconoció en {@code q} y se aplicó como filtro sugerido (el frontend lo muestra como chips que se pueden
 * quitar; quitarlos = repetir la búsqueda con {@code interpret=false} o con el filtro explícito). {@code text} es el
 * texto restante que se buscó. Los campos no reconocidos son nulos.
 */
public record SearchInterpretation(
        String text,
        UUID makeId,
        String makeName,
        UUID modelId,
        String modelName,
        Integer year,
        String cityCode,
        String cityName,
        String reference
) {
    public boolean recognizedSomething() {
        return makeId != null || modelId != null || year != null || cityCode != null || reference != null;
    }
}
