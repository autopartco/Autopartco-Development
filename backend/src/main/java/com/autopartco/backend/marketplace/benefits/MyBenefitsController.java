package com.autopartco.backend.marketplace.benefits;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionScope;
import com.autopartco.backend.core.pricing.PromotionService;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/**
 * «Tus beneficios»: promociones vigentes con audiencia distinta de {@code ALL} que aplican a los segmentos del
 * comprador autenticado. Solo responde al propio comprador; al almacén nunca se le revela quién pertenece a sus
 * segmentos ni a los de la plataforma. Las promociones de almacenes no publicados se omiten.
 */
@RestController
@AllArgsConstructor
public class MyBenefitsController {
    static final String BENEFITS = ApiPaths.MARKETPLACE + "/me/benefits";

    private final AccountLookup accountLookup;
    private final BuyerContexts buyerContexts;
    private final PromotionService promotionService;
    private final StoreService storeService;

    @GetMapping(BENEFITS)
    public List<BuyerBenefit> list(Authentication authentication) {
        UUID personId = accountLookup.requireByEmail(authentication.getName()).personId();
        BuyerContext buyer = buyerContexts.forPerson(personId);
        List<PromotionResponse> candidates = promotionService.activeAudiencePromotions().stream()
                .filter(promotion -> buyer.qualifiesFor(promotion.audience(), owningStore(promotion)))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> storeNames = storeService.published().stream()
                .collect(Collectors.toMap(StoreResponse::id, StoreResponse::businessName, (first, second) -> first));
        return candidates.stream()
                .filter(promotion -> promotion.storeId() == null || storeNames.containsKey(promotion.storeId()))
                .map(promotion -> new BuyerBenefit(
                        promotion.id(),
                        promotion.name(),
                        promotion.audience(),
                        promotion.storeId(),
                        promotion.storeId() == null ? null : storeNames.get(promotion.storeId()),
                        promotion.discountType(),
                        promotion.discountValue(),
                        promotion.targetType(),
                        promotion.targetId(),
                        promotion.endsAt()))
                .toList();
    }

    /** Almacén cuyos segmentos cuentan: el dueño en promociones de almacén; ninguno en las de plataforma. */
    private static UUID owningStore(PromotionResponse promotion) {
        return promotion.scope() == PromotionScope.STORE ? promotion.storeId() : null;
    }
}
