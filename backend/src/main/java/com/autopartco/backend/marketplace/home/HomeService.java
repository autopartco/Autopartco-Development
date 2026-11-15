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
import com.autopartco.backend.core.search.ServiceHit;
import com.autopartco.backend.core.search.ServiceSearchQuery;
import com.autopartco.backend.core.search.ServiceSearchService;
import com.autopartco.backend.core.search.ServiceTerms;
import com.autopartco.backend.marketplace.services.ServiceBrowseController;
import com.autopartco.backend.marketplace.services.ServiceCard;
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

    static final int SERVICES_LIMIT = 8;

    private final HomeFacts facts;
    private final SearchService searchService;
    private final HomeCache cache;
    /** Nulo en pruebas sin servicios. */
    private final ServiceSearchService serviceSearch;

    @org.springframework.beans.factory.annotation.Autowired
    HomeService(HomeFacts facts, SearchService searchService, HomeCache cache, ServiceSearchService serviceSearch) {
        this.facts = facts;
        this.searchService = searchService;
        this.cache = cache;
        this.serviceSearch = serviceSearch;
    }

    HomeService(HomeFacts facts, SearchService searchService, HomeCache cache) {
        this(facts, searchService, cache, null);
    }

    HomeResponse home(HomeArea area, String visitorKey) {
        HomeContent content = cache.get(area, () -> load(area));
        List<SearchResult.Sponsored> ads = searchService.featuredAds(area.cityCode(), visitorKey,
                BuyerContext.anonymous());
        HomeContent.Services services = content.services();
        return new HomeResponse(content.stats(), content.popularSearches(), content.categories(),
                content.nearby().stream().map(HomeService::organic).toList(), featured(ads, content),
                new HomeResponse.Services(
                        services.categories().stream()
                                .map(count -> new ServiceBrowseController.CategoryItem(count.category(),
                                        ServiceTerms.label(count.category()), ServiceTerms.of(count.category()).icon(),
                                        count.count()))
                                .toList(),
                        services.nearby().stream().map(ServiceCard::of).toList(),
                        services.promoted().stream().map(ServiceCard::of).toList()));
    }

    private HomeContent load(HomeArea area) {
        return new HomeContent(facts.stats(), facts.popularSearches(), facts.categories(), nearby(area), pool(area),
                services(area));
    }

    /**
     * Servicios del área. Con ubicación: los más cercanos (50 km y, si no hay, sin radio). Sin ubicación: la ciudad
     * del área o, sin ella, la ciudad con más servicios. «En promoción» sigue el mismo criterio y cae a todo el país si
     * el área no tiene ninguno. Un fallo deja el bloque vacío sin romper el inicio.
     */
    private HomeContent.Services services(HomeArea area) {
        if (serviceSearch == null) {
            return HomeContent.Services.EMPTY;
        }
        try {
            String city = area.cityCode();
            if (city == null && !area.located()) {
                city = serviceSearch.topCity().orElse(null);
            }
            ServiceSearchQuery.ServiceSearchQueryBuilder base = ServiceSearchQuery.builder().cityCode(city)
                    .lat(area.lat()).lng(area.lng()).page(0).size(SERVICES_LIMIT).facets(false)
                    .sort(area.located() ? ServiceSearchQuery.Sort.NEAREST : ServiceSearchQuery.Sort.RATING);
            List<ServiceHit> nearby = area.located()
                    ? serviceSearch.search(base.radiusKm(NEARBY_RADIUS_KM).build()).items()
                    : serviceSearch.search(base.build()).items();
            if (nearby.isEmpty() && area.located()) {
                nearby = serviceSearch.search(base.radiusKm(null).build()).items();
            }
            List<ServiceHit> promoted = serviceSearch.search(base.radiusKm(null).onSale(true).build()).items();
            if (promoted.isEmpty()) {
                promoted = serviceSearch.search(ServiceSearchQuery.builder().onSale(true).page(0)
                        .size(SERVICES_LIMIT).facets(false).sort(ServiceSearchQuery.Sort.RATING).build()).items();
            }
            return new HomeContent.Services(serviceSearch.categories(city), nearby, promoted);
        } catch (RuntimeException failure) {
            return HomeContent.Services.EMPTY;
        }
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
