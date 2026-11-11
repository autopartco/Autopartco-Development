package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.advertising.internal.AdCampaign;
import com.autopartco.backend.core.advertising.internal.AdCampaignRepository;
import com.autopartco.backend.core.advertising.internal.AdPayment;
import com.autopartco.backend.core.advertising.internal.AdPaymentRepository;
import com.autopartco.backend.core.payments.PaymentSyncRateLimiter;
import com.autopartco.backend.core.payments.WompiCheckoutRequest;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Cobro de una campaña por Wompi (referencia {@code ADS-…}), con el mismo flujo que {@code SubscriptionCheckout}: pago
 * {@code PENDING} en {@code ad_payments} y enlace firmado al Web Checkout. El monto sale del precio recalculado aquí
 * con las tarifas vigentes. La campaña pasa a {@code PENDING_PAYMENT}; el webhook ({@code AdWompiHandler}) la activa
 * o la devuelve a {@code DRAFT}.
 * <p>
 * Un checkout repetido desde {@code PENDING_PAYMENT} reemplaza el pago pendiente (queda {@code FAILED}); si ese pago
 * se aprueba igual más tarde, el handler lo aplica una sola vez y el sobrante queda para reembolso manual.
 */
@Slf4j
@Service
public class AdCheckout {
    public static final String REFERENCE_PREFIX = "ADS-";
    static final String CURRENCY = "COP";

    private final AdCampaignRepository campaignRepository;
    private final AdPaymentRepository paymentRepository;
    private final AdCampaigns campaigns;
    private final WompiProperties wompiProperties;
    private final WompiClient wompiClient;
    private final WompiEventRouter eventRouter;
    private final PaymentSyncRateLimiter syncRateLimiter;
    private final Clock clock;

    @Autowired
    public AdCheckout(AdCampaignRepository campaignRepository, AdPaymentRepository paymentRepository,
            AdCampaigns campaigns, WompiProperties wompiProperties, WompiClient wompiClient,
            WompiEventRouter eventRouter, PaymentSyncRateLimiter syncRateLimiter) {
        this(campaignRepository, paymentRepository, campaigns, wompiProperties, wompiClient, eventRouter,
                syncRateLimiter, Clock.systemUTC());
    }

    AdCheckout(AdCampaignRepository campaignRepository, AdPaymentRepository paymentRepository,
            AdCampaigns campaigns, WompiProperties wompiProperties, WompiClient wompiClient,
            WompiEventRouter eventRouter, PaymentSyncRateLimiter syncRateLimiter, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.paymentRepository = paymentRepository;
        this.campaigns = campaigns;
        this.wompiProperties = wompiProperties;
        this.wompiClient = wompiClient;
        this.eventRouter = eventRouter;
        this.syncRateLimiter = syncRateLimiter;
        this.clock = clock;
    }

    /**
     * Crea el pago pendiente y el enlace. Revalida fechas (400 {@code AD_INVALID_DATES} si el inicio ya pasó),
     * productos publicados y tarifas activas. 409 {@code AD_PAYMENT_UNAVAILABLE} si Wompi no está configurado y
     * {@code AD_CAMPAIGN_INVALID_STATE} fuera de {@code DRAFT}/{@code PENDING_PAYMENT}.
     */
    @Transactional
    public AdCheckoutResponse checkout(UUID storeId, UUID campaignId, UUID accountId) {
        if (!wompiProperties.checkoutConfigured()) {
            throw new ConflictException("AD_PAYMENT_UNAVAILABLE", "El pago en línea no está disponible.");
        }
        AdCampaign campaign = campaignRepository.lockByIdAndStoreId(campaignId, storeId)
                .orElseThrow(AdCampaigns::notFound);
        AdCampaigns.requireStatus(campaign, EnumSet.of(AdCampaignStatus.DRAFT, AdCampaignStatus.PENDING_PAYMENT));
        AdCampaignRules.requireValidDates(campaign.getStartsOn(), campaign.getEndsOn(), AdText.today(clock));
        List<UUID> productIds = List.copyOf(campaign.getProductIds());
        AdCampaignRules.requireProductCount(productIds.size());
        campaigns.requirePublished(storeId, productIds);
        List<AdPlacement> placements = campaign.placementList();
        BigDecimal total = AdCampaignRules.totalPrice(placements, campaigns.activePrices(placements),
                campaign.getStartsOn(), campaign.getEndsOn());
        long amountInCents = AdCampaignRules.amountInCents(total);

        List<AdPayment> pending = paymentRepository.findByCampaignIdAndStatus(campaign.getId(), AdPayment.PENDING);
        for (AdPayment previous : pending) {
            previous.setStatus(AdPayment.FAILED);
            paymentRepository.saveAndFlush(previous);
        }
        String reference = REFERENCE_PREFIX + UUID.randomUUID().toString().replace("-", "");
        AdPayment payment = paymentRepository.save(AdPayment.builder()
                .campaignId(campaign.getId())
                .reference(reference)
                .amountInCents(amountInCents)
                .currency(CURRENCY)
                .status(AdPayment.PENDING)
                .provider(AdPayment.WOMPI)
                .environment(wompiProperties.environment().name())
                .requestedByAccountId(accountId)
                .build());
        campaign.setTotalPrice(total);
        campaign.setStatus(AdCampaignStatus.PENDING_PAYMENT);
        campaignRepository.save(campaign);

        // Sin expiration-time: el pago de una campaña no reserva nada (igual que la suscripción).
        String checkoutUrl = wompiClient.checkoutUrl(new WompiCheckoutRequest(
                payment.getReference(), amountInCents, CURRENCY, wompiProperties.redirectUrl(), null));
        return new AdCheckoutResponse(payment.getReference(), checkoutUrl, amountInCents, CURRENCY,
                campaign.getId());
    }

    /**
     * Sincroniza el pago tras la redirección de Wompi: consulta la transacción con la llave privada y la aplica con
     * {@link WompiEventSource#REDIRECT_SYNC} si su referencia es un pago de esta campaña. La redirección nunca es
     * prueba de pago. Sin transacción: la llamada a Wompi va fuera. Si Wompi no responde, devuelve el estado actual.
     * 404 {@code AD_PAYMENT_NOT_FOUND} si la transacción no existe o no es de la campaña.
     */
    public AdCampaignView sync(UUID storeId, UUID campaignId, String transactionId, String rateLimitKey) {
        syncRateLimiter.acquire(rateLimitKey);
        AdCampaignView current = campaigns.get(storeId, campaignId);
        if (current.status() != AdCampaignStatus.PENDING_PAYMENT && current.status() != AdCampaignStatus.DRAFT) {
            return current;
        }
        Optional<WompiTransaction> transaction;
        try {
            transaction = wompiClient.findTransaction(transactionId);
        } catch (WompiUnavailableException unavailable) {
            log.warn("Wompi lookup failed while syncing ad campaign {}: {}", campaignId, unavailable.getMessage());
            return current;
        }
        boolean ours = transaction
                .flatMap(found -> paymentRepository.findByReference(found.reference()))
                .filter(payment -> payment.getCampaignId().equals(campaignId))
                .isPresent();
        if (!ours) {
            throw new NotFoundException("AD_PAYMENT_NOT_FOUND", "No se encontró el pago de la campaña.");
        }
        eventRouter.apply(transaction.get(), WompiEventSource.REDIRECT_SYNC);
        return campaigns.get(storeId, campaignId);
    }
}
