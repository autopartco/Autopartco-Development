package com.autopartco.backend.marketplace.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.organization.PublicBranches;
import com.autopartco.backend.core.organization.PublicBranches.PublicBranch;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.search.ServiceHit;
import com.autopartco.backend.core.search.ServiceSearchQuery;
import com.autopartco.backend.core.search.ServiceSearchResult;
import com.autopartco.backend.core.search.ServiceSearchService;
import com.autopartco.backend.core.search.ServiceTerms;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.TypedCache;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.AreaCookie;
import com.autopartco.backend.shared.web.ClientIp;
import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.type.TypeReference;

/**
 * Servicios de los almacenes para compradores (público): búsqueda, categorías y detalle. Un resultado por servicio, con
 * la sucursal más cercana. Límite por IP: 120 peticiones por minuto entre los tres endpoints.
 */
@RestController
@RequestMapping(ServiceBrowseController.BASE)
public class ServiceBrowseController {
    static final String BASE = ApiPaths.MARKETPLACE + "/services";
    static final int REQUESTS_PER_MINUTE = 120;
    static final int RELATED_LIMIT = 6;
    /** Mensaje de WhatsApp de servicios si el almacén aún no tiene la clave (misma plantilla por defecto del almacén). */
    static final String DEFAULT_SERVICE_MESSAGE =
            "Hola, vi el servicio {servicio} en Autopartco y quiero información para agendar: {enlace}";

    /** Categoría con su ícono Lucide y el número de servicios publicados. */
    public record CategoryItem(ServiceCategory category, String label, String icon, long count) {
    }

    /** {@code facets} es nulo si no se pidieron ({@code facets=false} o página distinta de la primera). */
    public record SearchResponse(List<ServiceCard> items, int page, int size, long total, Facets facets) {
        public record Facets(List<ServiceSearchResult.CategoryCount> categories,
                List<com.autopartco.backend.core.search.SearchResult.CityCount> cities) {
        }

        static SearchResponse of(ServiceSearchResult result) {
            Facets facets = result.facets() == null ? null
                    : new Facets(result.facets().categories(), result.facets().cities());
            return new SearchResponse(result.items().stream().map(ServiceCard::of).toList(), result.page(),
                    result.size(), result.total(), facets);
        }
    }

    private final ServiceSearchService searchService;
    private final PublicBranches publicBranches;
    private final PublishedStores publishedStores;
    private final SettingsQueries settingsQueries;
    private final SlidingWindowRateLimiter limiter;
    private final TypedCache<List<CategoryItem>> categoriesCache;

    /** Límite en Redis si está configurado ({@link RateLimiters}); si no, en memoria. */
    @Autowired
    public ServiceBrowseController(ServiceSearchService searchService, PublicBranches publicBranches,
            PublishedStores publishedStores, SettingsQueries settingsQueries, RateLimiters rateLimiters,
            ResilientCacheManager caches) {
        this(searchService, publicBranches, publishedStores, settingsQueries, rateLimiters,
                caches == null ? null : caches.<List<CategoryItem>>typed(AppCaches.SERVICE_CATEGORIES,
                        new TypeReference<List<CategoryItem>>() {
                        }.getType()));
    }

    /** Sin caché de categorías (pruebas). */
    ServiceBrowseController(ServiceSearchService searchService, PublicBranches publicBranches,
            PublishedStores publishedStores, SettingsQueries settingsQueries, Clock clock) {
        this(searchService, publicBranches, publishedStores, settingsQueries, RateLimiters.inMemory(clock),
                (TypedCache<List<CategoryItem>>) null);
    }

    private ServiceBrowseController(ServiceSearchService searchService, PublicBranches publicBranches,
            PublishedStores publishedStores, SettingsQueries settingsQueries, RateLimiters rateLimiters,
            TypedCache<List<CategoryItem>> categoriesCache) {
        this.searchService = searchService;
        this.publicBranches = publicBranches;
        this.publishedStores = publishedStores;
        this.settingsQueries = settingsQueries;
        this.limiter = rateLimiters.create("service-search", "SEARCH_RATE_LIMITED", REQUESTS_PER_MINUTE,
                Duration.ofMinutes(1));
        this.categoriesCache = categoriesCache;
    }

    /**
     * Búsqueda de servicios. Errores 400: {@code SEARCH_SIZE_INVALID} (1..48), {@code SEARCH_RADIUS_INVALID} (1..200),
     * {@code SEARCH_POINT_INCOMPLETE}, {@code SEARCH_POINT_OUTSIDE_COVERAGE}, {@code LOCATION_REQUIRED}
     * ({@code sort=nearest} o radio sin ubicación), {@code SEARCH_SORT_INVALID}, {@code SERVICE_CATEGORY_INVALID},
     * {@code SEARCH_PRICE_RANGE_INVALID} y {@code SEARCH_PLACE_INVALID}. Sin lat/lng y con {@code sort=nearest} o radio
     * usa el área de la cookie {@code ap_area} si es válida.
     */
    @GetMapping("/search")
    public SearchResponse search(
            HttpServletRequest http,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Integer radiusKm,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) Boolean bookable,
            @RequestParam(required = false) Boolean openNow,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean facets
    ) {
        limiter.acquire(ClientIp.of(http));
        ServiceSearchQuery.Sort requestedSort = ServiceSearchQuery.Sort.fromParameter(sort);
        BigDecimal pointLat = lat;
        BigDecimal pointLng = lng;
        if (lat == null && lng == null && (requestedSort == ServiceSearchQuery.Sort.NEAREST || radiusKm != null)) {
            Optional<AreaCookie.Area> area = AreaCookie.read(http);
            if (area.isPresent()) {
                pointLat = area.get().lat();
                pointLng = area.get().lng();
            }
        }
        ServiceSearchQuery query = ServiceSearchQuery.builder()
                .q(q).category(parseCategory(category)).cityCode(cityCode).departmentCode(departmentCode)
                .lat(pointLat).lng(pointLng).radiusKm(radiusKm).storeId(storeId).priceMax(priceMax)
                .onSale(onSale).bookable(bookable).openNow(openNow)
                .sort(requestedSort).page(page).size(size).facets(facets)
                .build();
        return SearchResponse.of(searchService.search(query));
    }

    /** Categorías con servicios publicados (opcionalmente solo de una ciudad). Caché del servidor de 5 minutos. */
    @GetMapping("/categories")
    public List<CategoryItem> categories(HttpServletRequest http, @RequestParam(required = false) String cityCode) {
        limiter.acquire(ClientIp.of(http));
        String city = cityCode == null || cityCode.isBlank() ? null : cityCode.strip();
        if (categoriesCache == null) {
            return loadCategories(city);
        }
        return categoriesCache.get(city == null ? "all" : city, () -> loadCategories(city));
    }

    /**
     * Detalle de un servicio visible (activo, almacén publicado, sucursal activa). 404 {@code SERVICE_NOT_FOUND} si no
     * lo es. {@code lat}/{@code lng} (o la cookie {@code ap_area}) eligen la sucursal más cercana y los relacionados.
     */
    @GetMapping("/{serviceId}")
    public ServiceDetail detail(
            HttpServletRequest http,
            @PathVariable UUID serviceId,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng
    ) {
        limiter.acquire(ClientIp.of(http));
        if ((lat == null) != (lng == null)) {
            throw new BadRequestException("SEARCH_POINT_INCOMPLETE", "Envía lat y lng juntos o ninguno.");
        }
        BigDecimal pointLat = lat;
        BigDecimal pointLng = lng;
        if (lat == null) {
            Optional<AreaCookie.Area> area = AreaCookie.read(http);
            if (area.isPresent()) {
                pointLat = area.get().lat();
                pointLng = area.get().lng();
            }
        }
        ServiceHit hit = searchService.find(serviceId, pointLat, pointLng)
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND", "No se encontró el servicio."));
        UUID storeId = hit.store().id();
        StoreSettings.Contact contact = settingsQueries.storeSettings(storeId).contact();
        List<ServiceCard> related = searchService.related(hit, pointLat, pointLng, RELATED_LIMIT).stream()
                .map(ServiceCard::of).toList();
        String message = contact.whatsappServiceMessage() == null || contact.whatsappServiceMessage().isBlank()
                ? DEFAULT_SERVICE_MESSAGE : contact.whatsappServiceMessage();
        return new ServiceDetail(ServiceCard.of(hit), hit.description(),
                branchesOf(serviceId, storeId, contact.whatsappEnabled()), contact.whatsappEnabled(), message,
                related);
    }

    /** Sucursales activas que prestan el servicio, con la resolución de WhatsApp de {@code /stores/{id}/branches}. */
    private List<PublicBranch> branchesOf(UUID serviceId, UUID storeId, boolean whatsappEnabled) {
        List<PublicBranch> branches = publicBranches.list(storeId).stream()
                .filter(branch -> branch.serviceIds().contains(serviceId))
                .toList();
        if (!whatsappEnabled) {
            return branches.stream().map(branch -> branch.withWhatsapp(null)).toList();
        }
        String storeWhatsapp = publishedStores.publicWhatsapp(storeId);
        return branches.stream()
                .map(branch -> branch.whatsapp() == null ? branch.withWhatsapp(storeWhatsapp) : branch)
                .toList();
    }

    private List<CategoryItem> loadCategories(String cityCode) {
        return searchService.categories(cityCode).stream()
                .map(count -> new CategoryItem(count.category(), ServiceTerms.label(count.category()),
                        ServiceTerms.of(count.category()).icon(), count.count()))
                .toList();
    }

    private static ServiceCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ServiceCategory.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("SERVICE_CATEGORY_INVALID", "La categoría de servicio no es válida.");
        }
    }
}
