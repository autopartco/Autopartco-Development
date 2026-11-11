package com.autopartco.backend.marketplace.home;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.search.HomeFacts;
import com.autopartco.backend.core.search.SearchQuery;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.SearchSort;
import com.autopartco.backend.marketplace.catalog.SearchResponse;

/**
 * Arma el inicio. La parte pública (cifras, búsquedas populares, categorías, «Cerca de ti» y el relleno orgánico) se
 * cachea 60 s por área con precios generales. Los anuncios {@code HOME_FEATURED} y sus tokens se piden en cada llamada.
 */
@Service
class HomeService {
    static final int NEARBY_LIMIT = 12;
    static final int FEATURED_LIMIT = SearchService.FEATURED_SLOTS;
    static final int NEARBY_RADIUS_KM = 50;
    static final int POOL_SIZE = 24;

    private final HomeFacts facts;
    private final SearchService searchService;
    private final HomeCache cache;

    HomeService(HomeFacts facts, SearchService searchService, HomeCache cache) {
        this.facts = facts;
        this.searchService = searchService;
        this.cache = cache;
    }

    HomeResponse home(HomeArea area, String visitorKey) {
        HomeContent content = cache.get(area, () -> load(area));
        List<SearchResult.Sponsored> ads = searchService.featuredAds(area.cityCode(), visitorKey,
                BuyerContext.anonymous());
        return new HomeResponse(content.stats(), content.popularSearches(), content.categories(),
                content.nearby().stream().map(HomeService::organic).toList(), featured(ads, content));
    }

    private HomeContent load(HomeArea area) {
        return new HomeContent(facts.stats(), facts.popularSearches(), facts.categories(), nearby(area), pool(area));
    }

    /**
     * Con ubicación: los más cercanos en 50 km y, si no hay ninguno, los más cercanos sin radio. Sin ella: los más
     * recientes. La ciudad filtra en ambos casos.
     */
    private List<SearchResult.Hit> nearby(HomeArea area) {
        if (!area.located()) {
            return catalog(base(area).sort(SearchSort.NEWEST).size(NEARBY_LIMIT).build());
        }
        List<SearchResult.Hit> within = catalog(base(area).sort(SearchSort.NEAREST).size(NEARBY_LIMIT)
                .radiusKm(NEARBY_RADIUS_KM).build());
        return within.isEmpty() ? catalog(base(area).sort(SearchSort.NEAREST).size(NEARBY_LIMIT).build()) : within;
    }

    /** Mejor calificados del área (con distancia si hay ubicación); sin ciudad ni ubicación, de todo el país. */
    private List<SearchResult.Hit> pool(HomeArea area) {
        return catalog(base(area).sort(SearchSort.RATING).size(POOL_SIZE).build());
    }

    private static SearchQuery.SearchQueryBuilder base(HomeArea area) {
        return SearchQuery.builder().cityCode(area.cityCode()).lat(area.lat()).lng(area.lng()).page(0)
                .facets(false).interpret(false);
    }

    private List<SearchResult.Hit> catalog(SearchQuery query) {
        return searchService.catalog(query, BuyerContext.anonymous()).items();
    }

    /** Anuncios primero; los cupos que sobran se rellenan con orgánicos (los que no están en «Cerca de ti» primero). */
    private List<SearchResponse.Item> featured(List<SearchResult.Sponsored> ads, HomeContent content) {
        List<SearchResponse.Item> featured = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        for (SearchResult.Sponsored ad : ads) {
            if (featured.size() < FEATURED_LIMIT && used.add(ad.hit().listing().id())) {
                featured.add(new SearchResponse.Item(ad.hit().listing(), ad.hit().distanceKm(), true,
                        ad.adImpressionToken()));
            }
        }
        Set<UUID> nearbyIds = new HashSet<>();
        content.nearby().forEach(hit -> nearbyIds.add(hit.listing().id()));
        fill(featured, used, content.organicPool(), hit -> !nearbyIds.contains(hit.listing().id()));
        fill(featured, used, content.organicPool(), hit -> true);
        return featured;
    }

    private static void fill(List<SearchResponse.Item> featured, Set<UUID> used, List<SearchResult.Hit> pool,
            Predicate<SearchResult.Hit> accept) {
        for (SearchResult.Hit hit : pool) {
            if (featured.size() >= FEATURED_LIMIT) {
                return;
            }
            if (accept.test(hit) && used.add(hit.listing().id())) {
                featured.add(organic(hit));
            }
        }
    }

    private static SearchResponse.Item organic(SearchResult.Hit hit) {
        return new SearchResponse.Item(hit.listing(), hit.distanceKm(), false, null);
    }
}
