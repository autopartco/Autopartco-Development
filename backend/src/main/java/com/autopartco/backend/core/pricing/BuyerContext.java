package com.autopartco.backend.core.pricing;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comprador para el que se calcula el precio. Lo arma {@code marketplace} con los segmentos de {@code core.analytics}
 * (como texto, para que {@code pricing} no dependa de ese módulo). Un comprador anónimo ({@code personId} nulo) solo
 * recibe promociones con audiencia {@link PromotionAudience#ALL}.
 *
 * @param personId persona autenticada; {@code null} si es anónimo
 * @param platformSegments segmentos de la persona en toda la plataforma (p. ej. {@code FREQUENT})
 * @param storeSegments segmentos de la persona por almacén
 */
public record BuyerContext(UUID personId, Set<String> platformSegments, Map<UUID, Set<String>> storeSegments) {
    private static final BuyerContext ANONYMOUS = new BuyerContext(null, Set.of(), Map.of());

    public BuyerContext {
        platformSegments = platformSegments == null ? Set.of() : Set.copyOf(platformSegments);
        storeSegments = storeSegments == null ? Map.of() : storeSegments.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    public static BuyerContext anonymous() {
        return ANONYMOUS;
    }

    public boolean isAnonymous() {
        return personId == null;
    }

    /**
     * {@code true} si el comprador pertenece a algún segmento y por tanto puede recibir un precio distinto del general.
     * Sin segmentos su precio es el mismo de un anónimo y se puede servir desde la caché compartida.
     */
    public boolean hasSegments() {
        return !isAnonymous() && (!platformSegments.isEmpty() || !storeSegments.isEmpty());
    }

    /**
     * {@code true} si el comprador puede recibir una promoción con esa audiencia sobre un producto del almacén
     * {@code storeId}. {@code ALL} (o nula) siempre; un anónimo nunca recibe las demás.
     */
    public boolean qualifiesFor(PromotionAudience audience, UUID storeId) {
        if (audience == null || audience == PromotionAudience.ALL) {
            return true;
        }
        if (isAnonymous()) {
            return false;
        }
        if (audience.isStoreAudience()) {
            return storeId != null && storeSegments.getOrDefault(storeId, Set.of()).contains(audience.segment());
        }
        return platformSegments.contains(audience.segment());
    }
}
