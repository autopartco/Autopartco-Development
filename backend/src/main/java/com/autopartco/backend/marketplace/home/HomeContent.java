package com.autopartco.backend.marketplace.home;

import java.util.List;

import com.autopartco.backend.core.search.HomeFacts;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.ServiceHit;
import com.autopartco.backend.core.search.ServiceSearchResult;

/**
 * Parte cacheable del inicio (precios generales, sin anuncios ni tokens). {@code organicPool} son los productos
 * mejor calificados del área, de los que se rellenan los cupos de «Destacados» que dejen libres los anuncios.
 */
record HomeContent(
        HomeFacts.Stats stats,
        List<String> popularSearches,
        List<HomeFacts.CategoryCount> categories,
        List<SearchResult.Hit> nearby,
        List<SearchResult.Hit> organicPool,
        Services services
) {
    /** Bloque de servicios del inicio: conteo por categoría, cercanos y en promoción. */
    record Services(List<ServiceSearchResult.CategoryCount> categories, List<ServiceHit> nearby,
            List<ServiceHit> promoted) {
        static final Services EMPTY = new Services(List.of(), List.of(), List.of());

        Services {
            categories = categories == null ? List.of() : List.copyOf(categories);
            nearby = nearby == null ? List.of() : List.copyOf(nearby);
            promoted = promoted == null ? List.of() : List.copyOf(promoted);
        }
    }

    HomeContent(HomeFacts.Stats stats, List<String> popularSearches, List<HomeFacts.CategoryCount> categories,
            List<SearchResult.Hit> nearby, List<SearchResult.Hit> organicPool) {
        this(stats, popularSearches, categories, nearby, organicPool, Services.EMPTY);
    }

    HomeContent {
        popularSearches = List.copyOf(popularSearches);
        categories = List.copyOf(categories);
        nearby = List.copyOf(nearby);
        organicPool = List.copyOf(organicPool);
        services = services == null ? Services.EMPTY : services;
    }
}
