package com.autopartco.backend.core.advertising;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Entrega de anuncios (§2 «Entrega y eventos»). {@link #pick} toma los candidatos de la ubicación (caché de 60 s),
 * deja los que coinciden con el contexto, descarta los productos ya mostrados en la página y rota las campañas con
 * peso uniforme (orden aleatorio; sin subasta). Cada campaña cuenta una exhibición por respuesta en el tope de
 * frecuencia ({@value AdFrequencyCap#MAX_PER_HOUR} por visitante, hora y ubicación). Si hay más cupos que campañas,
 * una campaña puede aportar otro de sus productos en una segunda vuelta.
 * <p>
 * Coincidencia ({@link #matches}):
 * <ul>
 *   <li>{@code cityCodes} es un filtro: con ciudades, solo coincide si se conoce la ciudad del contexto y está.</li>
 *   <li>Sin consulta, categoría ni vehículo en el contexto: coinciden todas, salvo en {@code SEARCH_TOP}, que siempre
 *       exige coincidir con la consulta, la categoría o el vehículo.</li>
 *   <li>Con alguno: la categoría pedida está en {@code categoryIds} o es la del producto; la marca o el modelo están
 *       en {@code makeIds}/{@code modelIds}; una palabra clave aparece como frase en la consulta; o todas las
 *       palabras de la consulta son prefijo de alguna palabra del nombre del producto.</li>
 * </ul>
 * Nunca lanza: si falla la carga, responde sin anuncios.
 */
@Slf4j
@Service
public class AdServer {
    private final AdCandidateCache cache;
    private final AdCandidateSource source;
    private final AdFrequencyCap frequencyCap;
    private final AdTokenSigner signer;
    private final Clock clock;
    private final Supplier<Random> random;

    @Autowired
    public AdServer(AdCandidateCache cache, AdCandidateSource source, AdFrequencyCap frequencyCap,
            AdTokenSigner signer) {
        this(cache, source, frequencyCap, signer, Clock.systemUTC(), ThreadLocalRandom::current);
    }

    AdServer(AdCandidateCache cache, AdCandidateSource source, AdFrequencyCap frequencyCap, AdTokenSigner signer,
            Clock clock, Supplier<Random> random) {
        this.cache = cache;
        this.source = source;
        this.frequencyCap = frequencyCap;
        this.signer = signer;
        this.clock = clock;
        this.random = random;
    }

    /** Hasta {@code limit} anuncios (como mucho el cupo de la ubicación) para {@code context}. */
    public List<AdPick> pick(AdPlacement placement, AdContext context, int limit) {
        int wanted = Math.min(limit, placement.slots());
        if (wanted <= 0 || context == null) {
            return List.of();
        }
        List<AdCandidate> candidates;
        try {
            candidates = cache.get(placement, () -> source.load(placement, AdText.today(clock)));
        } catch (RuntimeException failure) {
            log.warn("Ad candidates unavailable for {}: {}", placement, failure.getClass().getSimpleName());
            return List.of();
        }
        Map<UUID, List<AdCandidate>> byCampaign = new LinkedHashMap<>();
        for (AdCandidate candidate : candidates) {
            if (!context.excludedProductIds().contains(candidate.productId())
                    && !(context.excludedStoreId() != null && context.excludedStoreId().equals(candidate.storeId()))
                    && matches(candidate, context, placement)) {
                byCampaign.computeIfAbsent(candidate.campaignId(), ignored -> new ArrayList<>()).add(candidate);
            }
        }
        if (byCampaign.isEmpty()) {
            return List.of();
        }
        Random rng = random.get();
        List<UUID> campaigns = new ArrayList<>(byCampaign.keySet());
        Collections.shuffle(campaigns, rng);
        byCampaign.values().forEach(products -> Collections.shuffle(products, rng));

        List<AdPick> picks = new ArrayList<>();
        Set<UUID> usedProducts = new HashSet<>();
        Set<UUID> allowed = new HashSet<>();
        Set<UUID> capped = new HashSet<>();
        boolean progress = true;
        while (picks.size() < wanted && progress) {
            progress = false;
            for (UUID campaignId : campaigns) {
                if (picks.size() >= wanted) {
                    break;
                }
                if (capped.contains(campaignId)) {
                    continue;
                }
                AdCandidate next = nextProduct(byCampaign.get(campaignId), usedProducts);
                if (next == null) {
                    continue;
                }
                if (!allowed.contains(campaignId)) {
                    if (context.visitorKey() != null && !frequencyCap.tryAcquire(context.visitorKey(), campaignId,
                            placement, clock.instant())) {
                        capped.add(campaignId);
                        continue;
                    }
                    allowed.add(campaignId);
                }
                usedProducts.add(next.productId());
                picks.add(new AdPick(campaignId, next.productId(), placement,
                        signer.sign(campaignId, next.productId(), placement)));
                progress = true;
            }
        }
        return picks;
    }

    private static AdCandidate nextProduct(List<AdCandidate> products, Set<UUID> usedProducts) {
        for (AdCandidate product : products) {
            if (!usedProducts.contains(product.productId())) {
                return product;
            }
        }
        return null;
    }

    static boolean matches(AdCandidate candidate, AdContext context, AdPlacement placement) {
        AdTargeting targeting = candidate.targeting();
        if (!targeting.cityCodes().isEmpty()
                && (context.cityCode() == null || !targeting.cityCodes().contains(context.cityCode()))) {
            return false;
        }
        if (!context.hasSignals()) {
            return placement != AdPlacement.SEARCH_TOP;
        }
        if ((candidate.productCategoryId() != null && context.categoryScope().contains(candidate.productCategoryId()))
                || intersects(targeting.categoryIds(), context.categoryScope())
                || intersects(targeting.makeIds(), context.makeScope())
                || intersects(targeting.modelIds(), context.modelScope())) {
            return true;
        }
        if (context.text().isEmpty()) {
            return false;
        }
        String padded = " " + context.text() + " ";
        for (String keyword : targeting.keywords()) {
            if (padded.contains(" " + keyword + " ")) {
                return true;
            }
        }
        return nameMatches(context.text(), candidate.productWords());
    }

    private static boolean intersects(List<UUID> targeted, Set<UUID> wanted) {
        for (UUID id : targeted) {
            if (wanted.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** Todas las palabras de la consulta son prefijo de alguna palabra del nombre. */
    private static boolean nameMatches(String text, List<String> productWords) {
        if (productWords.isEmpty()) {
            return false;
        }
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (String productWord : productWords) {
                if (productWord.startsWith(word)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
