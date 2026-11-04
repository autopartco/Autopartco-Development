package com.autopartco.backend.core.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.shared.error.ConflictException;

import lombok.AllArgsConstructor;

/**
 * Fabricantes vistos por un almacén: los aprobados más los pendientes que él mismo sugirió. Las sugerencias nuevas
 * quedan {@code approved=false} hasta que operaciones las apruebe. Sin transacción envolvente a propósito: ante la
 * carrera con el índice único hay que reconsultar tras la violación, y eso no es posible dentro de la misma
 * transacción de PostgreSQL.
 */
@Service
@AllArgsConstructor
public class StoreManufacturers {
    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    /** Máximo de fabricantes pendientes de revisión que un almacén puede tener sugeridos a la vez. */
    static final int MAX_PENDING_PER_STORE = 20;
    static final double SIMILAR_THRESHOLD = 0.45;
    /** Umbrales de pg_trgm de la búsqueda (fijados por transacción): similitud global y por palabra. */
    static final double SEARCH_SIMILARITY = 0.3;
    static final double SEARCH_WORD_SIMILARITY = 0.5;

    private final ManufacturerRepository manufacturerRepository;

    public record Suggestion(StoreManufacturerResponse manufacturer, boolean created) {
    }

    public record SimilarManufacturer(UUID id, String name) {
    }

    public List<StoreManufacturerResponse> list(UUID storeId) {
        return manufacturerRepository.findVisibleToStore(storeId).stream().map(StoreManufacturers::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ManufacturerSearchResult> search(UUID storeId, String q) {
        String query = q == null ? "" : q.strip();
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        if (query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        manufacturerRepository.applySearchThresholds(SEARCH_SIMILARITY, SEARCH_WORD_SIMILARITY);
        return manufacturerRepository.search(storeId, query, escaped).stream()
                .map(row -> new ManufacturerSearchResult(row.getId(), row.getName(), row.getApproved(), row.getSimilarity()))
                .toList();
    }

    public Suggestion suggest(UUID storeId, StoreManufacturerRequest request) {
        String name = request.name();
        Suggestion existing = existing(storeId, name);
        if (existing != null) {
            return existing;
        }
        requireCanCreate(storeId, name, request.confirmNew());
        try {
            Manufacturer saved = manufacturerRepository.saveAndFlush(
                    Manufacturer.builder().name(name).approved(false).suggestedByStoreId(storeId).build());
            return new Suggestion(toResponse(saved), true);
        } catch (DataIntegrityViolationException race) {
            Suggestion winner = existing(storeId, name);
            if (winner != null) {
                return winner;
            }
            throw race;
        }
    }

    /**
     * Fabricante nuevo indicado por nombre dentro del alta/edición atómica de un producto. Fase de lectura (sin
     * escribir): si el nombre normalizado ya existe y es visible al almacén, {@code existing}; si no, verifica
     * parecidos ({@code MANUFACTURER_SIMILAR} sin {@code confirmNew}) y el tope de pendientes. Nombre de otro almacén
     * en revisión: {@code MANUFACTURER_NAME_UNAVAILABLE}.
     */
    NewManufacturer planNew(UUID storeId, String rawName, Boolean confirmNew) {
        String name = StoreManufacturerRequest.normalizeName(rawName);
        Manufacturer found = findExisting(storeId, name);
        if (found != null) {
            return new NewManufacturer(name, found);
        }
        requireCanCreate(storeId, name, confirmNew);
        return new NewManufacturer(name, null);
    }

    /**
     * Fase de escritura de {@link #planNew}: devuelve el existente o crea el pendiente ({@code approved=false},
     * sugerido por el almacén). Corre dentro de la transacción del producto: ante una carrera con el índice único no
     * se puede reconsultar, así que se responde {@code MANUFACTURER_CONCURRENT} y se revierte todo.
     */
    Manufacturer materialize(UUID storeId, NewManufacturer plan) {
        if (plan.existing() != null) {
            return plan.existing();
        }
        try {
            return manufacturerRepository.saveAndFlush(
                    Manufacturer.builder().name(plan.name()).approved(false).suggestedByStoreId(storeId).build());
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("MANUFACTURER_CONCURRENT",
                    "Otro usuario registró ese fabricante al mismo tiempo. Vuelve a intentarlo.");
        }
    }

    /** Resultado de {@link #planNew}: {@code existing} nulo = hay que crearlo con {@code name}. */
    record NewManufacturer(String name, Manufacturer existing) {
    }

    private void requireCanCreate(UUID storeId, String name, Boolean confirmNew) {
        if (!Boolean.TRUE.equals(confirmNew)) {
            List<SimilarManufacturer> similar = manufacturerRepository.findSimilar(storeId, name, SIMILAR_THRESHOLD)
                    .stream().map(row -> new SimilarManufacturer(row.getId(), row.getName())).toList();
            if (!similar.isEmpty()) {
                throw new ConflictException("MANUFACTURER_SIMILAR",
                        "Existen fabricantes con nombre parecido. Confirma si quieres crear uno nuevo.", similar);
            }
        }
        if (manufacturerRepository.countPendingByStore(storeId) >= MAX_PENDING_PER_STORE) {
            throw new ConflictException("MANUFACTURER_PENDING_LIMIT",
                    "Tienes " + MAX_PENDING_PER_STORE + " fabricantes pendientes de revisión. "
                            + "Espera a que sean revisados antes de sugerir más.");
        }
    }

    /** Coincidencia exacta normalizada: visible se devuelve; pendiente de otro almacén, conflicto sin revelar datos. */
    private Suggestion existing(UUID storeId, String name) {
        Manufacturer found = findExisting(storeId, name);
        return found == null ? null : new Suggestion(toResponse(found), false);
    }

    private Manufacturer findExisting(UUID storeId, String name) {
        for (Manufacturer found : manufacturerRepository.findByNormalizedName(name)) {
            if (found.isApproved() || storeId.equals(found.getSuggestedByStoreId())) {
                return found;
            }
            throw new ConflictException("MANUFACTURER_NAME_UNAVAILABLE",
                    "Ese nombre está en revisión. Inténtalo más tarde.");
        }
        return null;
    }

    private static StoreManufacturerResponse toResponse(Manufacturer manufacturer) {
        return new StoreManufacturerResponse(manufacturer.getId(), manufacturer.getName(), manufacturer.isApproved());
    }
}
