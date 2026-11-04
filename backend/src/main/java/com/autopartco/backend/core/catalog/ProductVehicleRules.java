package com.autopartco.backend.core.catalog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.internal.ProductVehicleRule;
import com.autopartco.backend.core.catalog.internal.ProductVehicleRuleRepository;
import com.autopartco.backend.core.vehicle.VehicleCatalogService;
import com.autopartco.backend.core.vehicle.VehicleRefs;
import com.autopartco.backend.shared.error.BadRequestException;

import lombok.AllArgsConstructor;

/**
 * Reglas de vehículo de los productos ({@code product_vehicle_rules}): validación de coherencia con el catálogo,
 * normalización (sin duplicados ni reglas contenidas en otra), reemplazo del conjunto y lectura con nombres. No
 * resuelve permisos ni pertenencia del producto: lo hace {@link ProductCatalog}.
 */
@Service
@AllArgsConstructor
public class ProductVehicleRules {
    /** Máximo de reglas por producto tras normalizar (y de reglas por petición). */
    public static final int MAX_RULES = 100;

    private static final Comparator<VehicleRuleResponse> ORDER = Comparator
            .comparing(VehicleRuleResponse::makeName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(VehicleRuleResponse::modelName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(VehicleRuleResponse::versionLabel, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));

    private final ProductVehicleRuleRepository repository;
    private final VehicleCatalogService vehicleCatalog;

    /** Error de una regla en {@code error.details} de {@code VEHICLE_RULE_INVALID}; {@code index} es su posición. */
    public record RuleError(int index, String field, String code, String message) {
    }

    /**
     * Valida cada regla contra el catálogo (marca existente, modelo de esa marca, versión de ese modelo, versión con
     * modelo) y la normaliza: quita duplicados y reglas cubiertas por otra más general (p. ej. «BMW, todos» absorbe
     * «BMW Serie 3»). Conserva el orden de llegada de las que quedan. Si alguna es incoherente, 400
     * {@code VEHICLE_RULE_INVALID} con un {@link RuleError} por regla incorrecta.
     */
    @Transactional(readOnly = true)
    public List<VehicleRuleRequest> normalize(List<VehicleRuleRequest> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        if (rules.size() > MAX_RULES) {
            throw new BadRequestException("VEHICLE_RULE_LIMIT",
                    "Un repuesto puede tener como máximo " + MAX_RULES + " reglas de vehículo.");
        }
        VehicleRefs refs = vehicleCatalog.resolve(
                ids(rules, VehicleRuleRequest::makeId),
                ids(rules, VehicleRuleRequest::modelId),
                ids(rules, VehicleRuleRequest::versionId));
        List<RuleError> errors = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            RuleError error = check(index, rules.get(index), refs);
            if (error != null) {
                errors.add(error);
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("VEHICLE_RULE_INVALID",
                    "Hay reglas de vehículo que no coinciden con el catálogo.", errors);
        }
        Set<VehicleRuleRequest> distinct = new LinkedHashSet<>(rules);
        return distinct.stream()
                .filter(rule -> distinct.stream().noneMatch(other -> !other.equals(rule) && other.covers(rule)))
                .toList();
    }

    /**
     * Reemplaza el conjunto de reglas del producto por {@code normalized} (ya normalizado): borra lógicamente las
     * que sobran y crea las que faltan; las que coinciden no se tocan.
     */
    @Transactional
    public void replace(UUID productId, List<VehicleRuleRequest> normalized) {
        List<ProductVehicleRule> current = repository.findAllOfProduct(productId);
        Set<VehicleRuleRequest> wanted = new LinkedHashSet<>(normalized);
        Set<VehicleRuleRequest> kept = new HashSet<>();
        List<UUID> toDelete = new ArrayList<>();
        for (ProductVehicleRule rule : current) {
            VehicleRuleRequest key = key(rule);
            if (wanted.contains(key) && kept.add(key)) {
                continue;
            }
            toDelete.add(rule.getId());
        }
        if (!toDelete.isEmpty()) {
            repository.softDelete(productId, toDelete, LocalDateTime.now());
        }
        List<ProductVehicleRule> created = wanted.stream()
                .filter(rule -> !kept.contains(rule))
                .<ProductVehicleRule>map(rule -> ProductVehicleRule.builder()
                        .productId(productId)
                        .makeId(rule.makeId())
                        .modelId(rule.modelId())
                        .versionId(rule.versionId())
                        .build())
                .toList();
        if (!created.isEmpty()) {
            repository.saveAll(created);
        }
    }

    /**
     * Reglas vigentes de los productos con nombres, ordenadas por marca, modelo y versión («todos» primero). Omite
     * las que apuntan a registros del catálogo borrados. Productos sin reglas no aparecen en el mapa.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<VehicleRuleResponse>> forProducts(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductVehicleRule> rules = repository.findAllOfProducts(Set.copyOf(productIds));
        if (rules.isEmpty()) {
            return Map.of();
        }
        VehicleRefs refs = vehicleCatalog.resolve(
                rules.stream().map(ProductVehicleRule::getMakeId).collect(Collectors.toSet()),
                rules.stream().map(ProductVehicleRule::getModelId).filter(Objects::nonNull).collect(Collectors.toSet()),
                rules.stream().map(ProductVehicleRule::getVersionId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, List<VehicleRuleResponse>> result = new LinkedHashMap<>();
        for (ProductVehicleRule rule : rules) {
            VehicleRuleResponse response = toResponse(rule, refs);
            if (response != null) {
                result.computeIfAbsent(rule.getProductId(), id -> new ArrayList<>()).add(response);
            }
        }
        result.values().forEach(list -> list.sort(ORDER));
        return result;
    }

    /** Reglas vigentes (con catálogo vigente) de un producto. */
    @Transactional(readOnly = true)
    public List<VehicleRuleResponse> forProduct(UUID productId) {
        return forProducts(Set.of(productId)).getOrDefault(productId, List.of());
    }

    /** Ids de marca, modelo y versión mencionados por las reglas (para promociones por atributo). */
    static Set<UUID> vehicleIds(List<VehicleRuleResponse> rules) {
        Set<UUID> ids = new HashSet<>();
        for (VehicleRuleResponse rule : rules) {
            ids.add(rule.makeId());
            if (rule.modelId() != null) ids.add(rule.modelId());
            if (rule.versionId() != null) ids.add(rule.versionId());
        }
        return ids;
    }

    private static RuleError check(int index, VehicleRuleRequest rule, VehicleRefs refs) {
        if (rule == null || rule.makeId() == null) {
            return new RuleError(index, "makeId", "MAKE_REQUIRED", "Indica la marca.");
        }
        if (rule.versionId() != null && rule.modelId() == null) {
            return new RuleError(index, "versionId", "VERSION_REQUIRES_MODEL",
                    "Para elegir una versión primero elige el modelo.");
        }
        if (!refs.makes().containsKey(rule.makeId())) {
            return new RuleError(index, "makeId", "MAKE_NOT_FOUND", "La marca no existe.");
        }
        if (rule.modelId() != null) {
            VehicleRefs.Ref model = refs.models().get(rule.modelId());
            if (model == null || !rule.makeId().equals(model.parentId())) {
                return new RuleError(index, "modelId", "MODEL_NOT_IN_MAKE", "El modelo no pertenece a la marca.");
            }
        }
        if (rule.versionId() != null) {
            VehicleRefs.Ref version = refs.versions().get(rule.versionId());
            if (version == null || !rule.modelId().equals(version.parentId())) {
                return new RuleError(index, "versionId", "VERSION_NOT_IN_MODEL",
                        "La versión no pertenece al modelo.");
            }
        }
        return null;
    }

    private static VehicleRuleResponse toResponse(ProductVehicleRule rule, VehicleRefs refs) {
        String makeName = refs.makes().get(rule.getMakeId());
        if (makeName == null) {
            return null;
        }
        String modelName = null;
        if (rule.getModelId() != null) {
            VehicleRefs.Ref model = refs.models().get(rule.getModelId());
            if (model == null) {
                return null;
            }
            modelName = model.name();
        }
        String versionLabel = null;
        if (rule.getVersionId() != null) {
            VehicleRefs.Ref version = refs.versions().get(rule.getVersionId());
            if (version == null) {
                return null;
            }
            versionLabel = version.name();
        }
        return new VehicleRuleResponse(rule.getMakeId(), makeName, rule.getModelId(), modelName,
                rule.getVersionId(), versionLabel);
    }

    private static VehicleRuleRequest key(ProductVehicleRule rule) {
        return new VehicleRuleRequest(rule.getMakeId(), rule.getModelId(), rule.getVersionId());
    }

    private static Set<UUID> ids(List<VehicleRuleRequest> rules,
            java.util.function.Function<VehicleRuleRequest, UUID> getter) {
        return rules.stream()
                .filter(Objects::nonNull)
                .map(getter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
