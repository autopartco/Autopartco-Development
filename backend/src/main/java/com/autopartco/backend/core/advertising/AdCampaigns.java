package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.advertising.internal.AdCampaign;
import com.autopartco.backend.core.advertising.internal.AdCampaignRepository;
import com.autopartco.backend.core.advertising.internal.AdPlacementPrice;
import com.autopartco.backend.core.advertising.internal.AdPlacementPriceRepository;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

/**
 * Tarifas y campañas (§2). El precio se calcula siempre aquí ({@link AdCampaignRules#totalPrice}). Las campañas de
 * otro almacén responden {@code AD_CAMPAIGN_NOT_FOUND} (404), igual que las inexistentes. No comprueba permisos ni la
 * suscripción: lo hacen los llamadores ({@code storesuite.advertising}, {@code operations.ads}).
 * <p>
 * Errores: 400 {@code AD_TOO_MANY_PRODUCTS}, {@code AD_INVALID_DATES}, {@code AD_PRODUCT_NOT_PUBLISHED},
 * {@code AD_PLACEMENT_UNAVAILABLE}, {@code AD_TARGETING_INVALID}; 404 {@code AD_CAMPAIGN_NOT_FOUND}; 409
 * {@code AD_CAMPAIGN_INVALID_STATE} y {@code AD_CAMPAIGN_PAUSED_BY_ADMIN} (el almacén intenta reanudar una pausa del
 * admin).
 */
@Service
public class AdCampaigns {
    public static final int ADMIN_LIST_LIMIT = 200;

    private static final Set<AdCampaignStatus> STORE_PAUSABLE = EnumSet.of(AdCampaignStatus.SCHEDULED,
            AdCampaignStatus.ACTIVE);
    private static final Set<AdCampaignStatus> STORE_CANCELLABLE = EnumSet.of(AdCampaignStatus.PENDING_PAYMENT,
            AdCampaignStatus.SCHEDULED, AdCampaignStatus.ACTIVE, AdCampaignStatus.PAUSED);

    private final AdCampaignRepository campaignRepository;
    private final AdPlacementPriceRepository priceRepository;
    private final ListingQueries listingQueries;
    private final AdCandidateCache candidateCache;
    private final Clock clock;

    @Autowired
    public AdCampaigns(AdCampaignRepository campaignRepository, AdPlacementPriceRepository priceRepository,
            ListingQueries listingQueries, AdCandidateCache candidateCache) {
        this(campaignRepository, priceRepository, listingQueries, candidateCache, Clock.systemUTC());
    }

    AdCampaigns(AdCampaignRepository campaignRepository, AdPlacementPriceRepository priceRepository,
            ListingQueries listingQueries, AdCandidateCache candidateCache, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.priceRepository = priceRepository;
        this.listingQueries = listingQueries;
        this.candidateCache = candidateCache;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- tarifas

    /** Tarifas por ubicación, en el orden de {@link AdPlacement}. {@code onlyActive} para el portal. */
    @Transactional(readOnly = true)
    public List<AdPriceView> prices(boolean onlyActive) {
        return priceRepository.findAll().stream()
                .filter(price -> !onlyActive || price.isActive())
                .sorted(Comparator.comparing(AdPlacementPrice::getPlacement))
                .map(price -> new AdPriceView(price.getPlacement(), price.getDailyPrice(), price.isActive()))
                .toList();
    }

    /** Fija tarifas (admin). No cambia el precio de campañas ya creadas: se recalcula al editar o pagar. */
    @Transactional
    public List<AdPriceView> updatePrices(List<AdPriceUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new BadRequestException("AD_PRICE_INVALID", "Envía al menos una tarifa.");
        }
        Set<AdPlacement> seen = EnumSet.noneOf(AdPlacement.class);
        for (AdPriceUpdate update : updates) {
            if (update == null || update.placement() == null || update.dailyPrice() == null
                    || update.dailyPrice().signum() <= 0 || !seen.add(update.placement())) {
                throw new BadRequestException("AD_PRICE_INVALID", "Tarifas inválidas o repetidas.");
            }
        }
        for (AdPriceUpdate update : updates) {
            AdPlacementPrice price = priceRepository.findById(update.placement())
                    .orElseGet(() -> AdPlacementPrice.builder().placement(update.placement()).active(true).build());
            price.setDailyPrice(update.dailyPrice().setScale(2, java.math.RoundingMode.HALF_UP));
            if (update.active() != null) {
                price.setActive(update.active());
            }
            priceRepository.save(price);
        }
        return prices(false);
    }

    // ---------------------------------------------------------------- campañas del almacén

    /** Crea la campaña en {@code DRAFT} con su precio calculado. {@code accountId}: cuenta que la crea. */
    @Transactional
    public AdCampaignView create(UUID storeId, UUID accountId, AdCampaignRequest request) {
        Validated valid = validate(storeId, request);
        AdCampaign campaign = AdCampaign.builder()
                .storeId(storeId)
                .name(request.name().strip())
                .status(AdCampaignStatus.DRAFT)
                .startsOn(request.startsOn())
                .endsOn(request.endsOn())
                .targeting(valid.targeting().toJson())
                .totalPrice(valid.totalPrice())
                .createdBy(accountId)
                .productIds(new LinkedHashSet<>(valid.productIds()))
                .build();
        campaign.setPlacementList(valid.placements());
        return AdCampaignView.of(campaignRepository.save(campaign));
    }

    /** Reemplaza los datos de una campaña en {@code DRAFT} y recalcula el precio. */
    @Transactional
    public AdCampaignView update(UUID storeId, UUID campaignId, AdCampaignRequest request) {
        AdCampaign campaign = lockOwned(storeId, campaignId);
        requireStatus(campaign, EnumSet.of(AdCampaignStatus.DRAFT));
        Validated valid = validate(storeId, request);
        campaign.setName(request.name().strip());
        campaign.setPlacementList(valid.placements());
        campaign.setStartsOn(request.startsOn());
        campaign.setEndsOn(request.endsOn());
        campaign.setTargeting(valid.targeting().toJson());
        campaign.setTotalPrice(valid.totalPrice());
        campaign.getProductIds().clear();
        campaign.getProductIds().addAll(valid.productIds());
        return AdCampaignView.of(campaignRepository.save(campaign));
    }

    /** Borra una campaña en {@code DRAFT}. */
    @Transactional
    public void delete(UUID storeId, UUID campaignId) {
        AdCampaign campaign = lockOwned(storeId, campaignId);
        requireStatus(campaign, EnumSet.of(AdCampaignStatus.DRAFT));
        campaignRepository.delete(campaign);
    }

    @Transactional(readOnly = true)
    public List<AdCampaignView> list(UUID storeId, AdCampaignStatus status) {
        List<AdCampaign> campaigns = status == null
                ? campaignRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
                : campaignRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, status);
        return campaigns.stream().map(AdCampaignView::of).toList();
    }

    @Transactional(readOnly = true)
    public AdCampaignView get(UUID storeId, UUID campaignId) {
        return AdCampaignView.of(campaignRepository.findByIdAndStoreId(campaignId, storeId)
                .orElseThrow(AdCampaigns::notFound));
    }

    /** Pausa del almacén: {@code SCHEDULED} o {@code ACTIVE} → {@code PAUSED} ({@code paused_by = STORE}). */
    @Transactional
    public AdCampaignView pause(UUID storeId, UUID campaignId) {
        AdCampaign campaign = lockOwned(storeId, campaignId);
        requireStatus(campaign, STORE_PAUSABLE);
        campaign.pause(AdPausedBy.STORE);
        return changed(campaign);
    }

    /**
     * Reanuda una campaña {@code PAUSED} por el almacén según la fecha: {@code SCHEDULED} antes del inicio,
     * {@code ACTIVE} dentro del rango y {@code FINISHED} si ya terminó. No extiende fechas. Una pausa del admin
     * responde 409 {@code AD_CAMPAIGN_PAUSED_BY_ADMIN}.
     */
    @Transactional
    public AdCampaignView resume(UUID storeId, UUID campaignId) {
        AdCampaign campaign = lockOwned(storeId, campaignId);
        requireStatus(campaign, EnumSet.of(AdCampaignStatus.PAUSED));
        if (campaign.getPausedBy() == AdPausedBy.ADMIN) {
            throw new ConflictException("AD_CAMPAIGN_PAUSED_BY_ADMIN",
                    "La campaña fue pausada por Autopartco y solo el equipo de Autopartco puede reanudarla.");
        }
        campaign.moveTo(statusByDate(campaign, AdText.today(clock)));
        return changed(campaign);
    }

    /** Cancelación del almacén, sin reembolso automático (también desde una pausa del admin). */
    @Transactional
    public AdCampaignView cancel(UUID storeId, UUID campaignId) {
        AdCampaign campaign = lockOwned(storeId, campaignId);
        requireStatus(campaign, STORE_CANCELLABLE);
        campaign.moveTo(AdCampaignStatus.CANCELLED);
        return changed(campaign);
    }

    // ---------------------------------------------------------------- admin

    /** Campañas de todos los almacenes (las {@value #ADMIN_LIST_LIMIT} más recientes), con filtros opcionales. */
    @Transactional(readOnly = true)
    public List<AdCampaignView> adminList(AdCampaignStatus status, UUID storeId) {
        Set<AdCampaignStatus> statuses = status == null ? EnumSet.allOf(AdCampaignStatus.class) : EnumSet.of(status);
        PageRequest page = PageRequest.of(0, ADMIN_LIST_LIMIT);
        List<AdCampaign> campaigns = storeId == null
                ? campaignRepository.findByStatusInOrderByCreatedAtDesc(statuses, page)
                : campaignRepository.findByStoreIdAndStatusInOrderByCreatedAtDesc(storeId, statuses, page);
        return campaigns.stream().map(AdCampaignView::of).toList();
    }

    /**
     * Rechazo del admin desde cualquier estado no final. Si ya estaba pagada y no había empezado (hoy antes del
     * inicio), marca {@code requires_manual_refund}.
     */
    @Transactional
    public AdCampaignView reject(UUID campaignId, String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.isEmpty() || trimmed.length() > 300) {
            throw new BadRequestException("AD_REJECT_REASON_INVALID", "Indica el motivo (máximo 300 caracteres).");
        }
        AdCampaign campaign = campaignRepository.lockById(campaignId).orElseThrow(AdCampaigns::notFound);
        if (campaign.getStatus().isFinal()) {
            throw invalidState(campaign);
        }
        boolean notStarted = AdText.today(clock).isBefore(campaign.getStartsOn());
        if (campaign.getPaidAt() != null && notStarted) {
            campaign.setRequiresManualRefund(true);
        }
        campaign.moveTo(AdCampaignStatus.REJECTED);
        campaign.setRejectedReason(trimmed);
        return changed(campaign);
    }

    /**
     * Pausa del admin ({@code paused_by = ADMIN}): {@code SCHEDULED} o {@code ACTIVE} → {@code PAUSED}. Una pausa del
     * almacén pasa a ser del admin, para que el almacén no pueda levantarla; una pausa del admin ya hecha responde 409
     * {@code AD_CAMPAIGN_INVALID_STATE}.
     */
    @Transactional
    public AdCampaignView adminPause(UUID campaignId) {
        AdCampaign campaign = campaignRepository.lockById(campaignId).orElseThrow(AdCampaigns::notFound);
        boolean storePause = campaign.getStatus() == AdCampaignStatus.PAUSED
                && campaign.getPausedBy() != AdPausedBy.ADMIN;
        if (!storePause) {
            requireStatus(campaign, STORE_PAUSABLE);
        }
        campaign.pause(AdPausedBy.ADMIN);
        return changed(campaign);
    }

    /** Reanudación del admin de cualquier pausa (del almacén o del admin), según la fecha como {@link #resume}. */
    @Transactional
    public AdCampaignView adminResume(UUID campaignId) {
        AdCampaign campaign = campaignRepository.lockById(campaignId).orElseThrow(AdCampaigns::notFound);
        requireStatus(campaign, EnumSet.of(AdCampaignStatus.PAUSED));
        campaign.moveTo(statusByDate(campaign, AdText.today(clock)));
        return changed(campaign);
    }

    // ---------------------------------------------------------------- reglas

    /** Datos validados de una solicitud: productos y ubicaciones sin repetir, segmentación normalizada y precio. */
    record Validated(List<UUID> productIds, List<AdPlacement> placements, AdTargeting targeting,
            BigDecimal totalPrice) {
    }

    Validated validate(UUID storeId, AdCampaignRequest request) {
        List<UUID> productIds = List.copyOf(new LinkedHashSet<>(request.productIds()));
        List<AdPlacement> placements = List.copyOf(new LinkedHashSet<>(request.placements()));
        AdCampaignRules.requireProductCount(productIds.size(), placements);
        AdCampaignRules.requireValidDates(request.startsOn(), request.endsOn(), AdText.today(clock));
        AdTargeting targeting = AdTargeting.sanitize(request.targeting());
        BigDecimal total = AdCampaignRules.totalPrice(placements, activePrices(placements), request.startsOn(),
                request.endsOn());
        requirePublished(storeId, productIds);
        return new Validated(productIds, placements, targeting, total);
    }

    /** Tarifas activas de {@code placements}; si alguna falta o está inactiva, 400 {@code AD_PLACEMENT_UNAVAILABLE}. */
    Map<AdPlacement, BigDecimal> activePrices(List<AdPlacement> placements) {
        Map<AdPlacement, BigDecimal> prices = new EnumMap<>(AdPlacement.class);
        priceRepository.findAllById(placements).stream()
                .filter(AdPlacementPrice::isActive)
                .forEach(price -> prices.put(price.getPlacement(), price.getDailyPrice()));
        List<String> missing = placements.stream().filter(placement -> !prices.containsKey(placement))
                .map(AdPlacement::name).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("AD_PLACEMENT_UNAVAILABLE", "Alguna ubicación no está disponible.",
                    Map.of("placements", missing));
        }
        return prices;
    }

    /** Todos los productos son del almacén y están publicados; si no, 400 {@code AD_PRODUCT_NOT_PUBLISHED}. */
    void requirePublished(UUID storeId, List<UUID> productIds) {
        Set<UUID> published = listingQueries.publishedProductIdsAmong(storeId, productIds);
        List<String> notPublished = new ArrayList<>();
        for (UUID productId : productIds) {
            if (!published.contains(productId)) {
                notPublished.add(productId.toString());
            }
        }
        if (!notPublished.isEmpty()) {
            throw new BadRequestException("AD_PRODUCT_NOT_PUBLISHED",
                    "Solo puedes anunciar productos publicados de tu almacén.",
                    Map.of("productIds", notPublished));
        }
    }

    /** Estado que corresponde por fecha a una campaña pagada que vuelve a correr. */
    static AdCampaignStatus statusByDate(AdCampaign campaign, LocalDate today) {
        if (today.isAfter(campaign.getEndsOn())) {
            return AdCampaignStatus.FINISHED;
        }
        return today.isBefore(campaign.getStartsOn()) ? AdCampaignStatus.SCHEDULED : AdCampaignStatus.ACTIVE;
    }

    private AdCampaign lockOwned(UUID storeId, UUID campaignId) {
        return campaignRepository.lockByIdAndStoreId(campaignId, storeId).orElseThrow(AdCampaigns::notFound);
    }

    private AdCampaignView changed(AdCampaign campaign) {
        AdCampaign saved = campaignRepository.save(campaign);
        AdCacheInvalidation.afterCommit(candidateCache);
        return AdCampaignView.of(saved);
    }

    static void requireStatus(AdCampaign campaign, Set<AdCampaignStatus> allowed) {
        if (!allowed.contains(campaign.getStatus())) {
            throw invalidState(campaign);
        }
    }

    static ConflictException invalidState(AdCampaign campaign) {
        return new ConflictException("AD_CAMPAIGN_INVALID_STATE",
                "La campaña no permite esta acción en su estado actual.",
                Map.of("status", campaign.getStatus().name()));
    }

    public static NotFoundException notFound() {
        return new NotFoundException("AD_CAMPAIGN_NOT_FOUND", "No se encontró la campaña.");
    }
}
