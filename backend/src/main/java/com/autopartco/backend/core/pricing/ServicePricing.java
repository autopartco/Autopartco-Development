package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Precio «desde» de servicios con las promociones vigentes ({@link PriceCalculator#servicePrice}). {@link #cached}
 * reutiliza las reglas {@value #CACHE_SECONDS} s (tarjetas del marketplace); {@link #current} las lee al momento
 * (índice y precio comprometido en una cita).
 */
@Service
public class ServicePricing {
    static final long CACHE_SECONDS = 30;

    private final PriceCalculator calculator;
    private final Clock clock;
    private volatile Snapshot snapshot;

    @Autowired
    public ServicePricing(PriceCalculator calculator) {
        this(calculator, Clock.systemUTC());
    }

    ServicePricing(PriceCalculator calculator, Clock clock) {
        this.calculator = calculator;
        this.clock = clock;
    }

    /** Reglas de servicios vigentes ahora, leídas de la base. */
    public Rules current() {
        Instant now = clock.instant();
        List<PromotionRule> rules = calculator.activeRulesAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).stream()
                .filter(rule -> rule.targetType().isService())
                .toList();
        Rules current = new Rules(calculator, rules);
        snapshot = new Snapshot(now, current);
        return current;
    }

    /** Reglas de servicios con hasta {@value #CACHE_SECONDS} s de antigüedad. */
    public Rules cached() {
        Snapshot last = snapshot;
        if (last != null && Duration.between(last.at(), clock.instant()).getSeconds() < CACHE_SECONDS) {
            return last.rules();
        }
        return current();
    }

    private record Snapshot(Instant at, Rules rules) {
    }

    /** Reglas vigentes de un momento; {@link #price} no consulta la base. */
    public static final class Rules {
        private final PriceCalculator calculator;
        private final List<PromotionRule> rules;

        public Rules(PriceCalculator calculator, List<PromotionRule> rules) {
            this.calculator = calculator;
            this.rules = List.copyOf(rules);
        }

        public boolean isEmpty() {
            return rules.isEmpty();
        }

        /** @param category nombre de la categoría de servicio */
        public ServicePrice price(UUID serviceId, UUID storeId, String category, BigDecimal priceFrom) {
            return calculator.servicePrice(serviceId, storeId, category, priceFrom, rules);
        }
    }
}
