package com.autopartco.backend.marketplace.benefits;

import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.analytics.SegmentMembership;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.pricing.BuyerContext;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Arma el {@link BuyerContext} del comprador con los segmentos de {@link SegmentMembership} (caché de 10 minutos).
 * Lo usan el catálogo, el checkout y «Tus beneficios», para que el precio mostrado y el cobrado salgan de la misma
 * evaluación en el servidor.
 */
@Slf4j
@Component
@AllArgsConstructor
public class BuyerContexts {
    private final AccountLookup accountLookup;
    private final SegmentMembership segmentMembership;

    /**
     * Catálogo público: sin sesión, anónimo. Best-effort: si la cuenta no existe o los segmentos no se pueden
     * calcular, el comprador queda sin segmentos (solo promociones {@code ALL}) y el catálogo sigue respondiendo.
     */
    public BuyerContext forBrowsing(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return BuyerContext.anonymous();
        }
        try {
            return accountLookup.findByEmail(authentication.getName())
                    .map(account -> forPerson(account.personId()))
                    .orElse(BuyerContext.anonymous());
        } catch (RuntimeException failure) {
            log.warn("No se pudieron calcular los segmentos del comprador: {}", failure.getClass().getSimpleName());
            return BuyerContext.anonymous();
        }
    }

    /** Comprador autenticado conocido (checkout y beneficios). Los errores se propagan. */
    public BuyerContext forPerson(UUID personId) {
        if (personId == null) {
            return BuyerContext.anonymous();
        }
        SegmentMembership.Memberships memberships = segmentMembership.memberships(personId);
        return new BuyerContext(personId, memberships.platformSegments(), memberships.storeSegments());
    }

    static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
