package com.autopartco.backend.core.search;

import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.ServiceCategory;

/**
 * Lo que se reconoció en {@code q} y se aplicó como filtro sugerido (el frontend lo muestra como chips que se pueden
 * quitar; quitarlos = repetir la búsqueda con {@code interpret=false} o con el filtro explícito). {@code text} es el
 * texto restante que se buscó. Los campos no reconocidos son nulos. {@code serviceCategory} es la categoría de
 * servicio que la consulta pide («alineación», «cambio de aceite»); no filtra los productos.
 * <p>
 * {@code corrections}: palabras de {@code q} reemplazadas por las del catálogo ({@code from} → {@code to}), en orden;
 * {@code text} ya va corregido. {@code relaxed}: {@link Relaxed#VEHICLE} cuando hay marca interpretada y ningún
 * resultado es compatible con ella ni la menciona (solo quedan universales y productos sin datos de vehículo).
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
        String reference,
        ServiceCategory serviceCategory,
        List<ParsedQuery.Correction> corrections,
        Relaxed relaxed
) {
    /** Qué se relajó de lo interpretado para poder responder. */
    public enum Relaxed { NONE, VEHICLE }

    public SearchInterpretation {
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
        relaxed = relaxed == null ? Relaxed.NONE : relaxed;
    }

    /** Sin correcciones ni relajación. */
    public SearchInterpretation(String text, UUID makeId, String makeName, UUID modelId, String modelName,
            Integer year, String cityCode, String cityName, String reference, ServiceCategory serviceCategory) {
        this(text, makeId, makeName, modelId, modelName, year, cityCode, cityName, reference, serviceCategory,
                List.of(), Relaxed.NONE);
    }

    /** Sin intención de servicio. */
    public SearchInterpretation(String text, UUID makeId, String makeName, UUID modelId, String modelName,
            Integer year, String cityCode, String cityName, String reference) {
        this(text, makeId, makeName, modelId, modelName, year, cityCode, cityName, reference, null);
    }

    /** La misma interpretación con {@code value} como relajación. */
    public SearchInterpretation withRelaxed(Relaxed value) {
        return new SearchInterpretation(text, makeId, makeName, modelId, modelName, year, cityCode, cityName,
                reference, serviceCategory, corrections, value);
    }

    public boolean recognizedSomething() {
        return makeId != null || modelId != null || year != null || cityCode != null || reference != null
                || serviceCategory != null || !corrections.isEmpty() || relaxed != Relaxed.NONE;
    }
}
