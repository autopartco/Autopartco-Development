package com.autopartco.backend.marketplace.home;

import java.util.List;

import com.autopartco.backend.core.search.HomeFacts;
import com.autopartco.backend.core.search.SearchResult;

/**
 * Parte cacheable del inicio (precios generales, sin anuncios ni tokens). {@code organicPool} son los productos
 * mejor calificados del área, de los que se rellenan los cupos de «Destacados» que dejen libres los anuncios.
 */
record HomeContent(
        HomeFacts.Stats stats,
        List<String> popularSearches,
        List<HomeFacts.CategoryCount> categories,
        List<SearchResult.Hit> nearby,
        List<SearchResult.Hit> organicPool
) {
    HomeContent {
        popularSearches = List.copyOf(popularSearches);
        categories = List.copyOf(categories);
        nearby = List.copyOf(nearby);
        organicPool = List.copyOf(organicPool);
    }
}
