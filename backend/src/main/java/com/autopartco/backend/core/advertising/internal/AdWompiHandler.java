package com.autopartco.backend.core.advertising.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.advertising.AdCacheInvalidation;
import com.autopartco.backend.core.advertising.AdCampaignRules;
import com.autopartco.backend.core.advertising.AdCampaignStatus;
import com.autopartco.backend.core.advertising.AdCandidateCache;
import com.autopartco.backend.core.advertising.AdText;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiTransactionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Aplica las transacciones de Wompi de las campañas ({@code ADS-…}) sobre {@code ad_payments} y {@code ad_campaigns},
 * dentro de la transacción del router de {@code core.payments} (sin red). Como {@code SubscriptionWompiHandler}:
 * <ul>
 *   <li>Bloquea el pago y luego la campaña; valida proveedor, entorno, monto en centavos y moneda.</li>
 *   <li>APPROVED: fija {@code paid_at} y pasa la campaña a {@code SCHEDULED} (antes del inicio) o {@code ACTIVE} una
 *       sola vez. Si la campaña ya no se puede activar (rechazada, cancelada, vencida, ya pagada o con otro precio),
 *       el pago queda APPROVED y la campaña con {@code requires_manual_refund}; una vencida sin pagar pasa a
 *       {@code CANCELLED}.</li>
 *   <li>DECLINED/ERROR/VOIDED: el pago guarda el estado y la campaña {@code PENDING_PAYMENT} vuelve a {@code DRAFT}
 *       si no le queda otro pago pendiente.</li>
 *   <li>Idempotente por {@code provider_transaction_id}: repetir la misma transacción da IGNORED.</li>
 * </ul>
 */
@Slf4j
@Component
public class AdWompiHandler implements WompiTransactionHandler {
    public static final String REFERENCE_PREFIX = "ADS-";
    private static final Set<AdCampaignStatus> AWAITING = EnumSet.of(AdCampaignStatus.PENDING_PAYMENT,
            AdCampaignStatus.DRAFT);

    private final AdPaymentRepository paymentRepository;
    private final AdCampaignRepository campaignRepository;
    private final WompiProperties wompiProperties;
    private final AdCandidateCache candidateCache;
    private final Clock clock;

    @Autowired
    public AdWompiHandler(AdPaymentRepository paymentRepository, AdCampaignRepository campaignRepository,
            WompiProperties wompiProperties, AdCandidateCache candidateCache) {
        this(paymentRepository, campaignRepository, wompiProperties, candidateCache, Clock.systemUTC());
    }

    public AdWompiHandler(AdPaymentRepository paymentRepository, AdCampaignRepository campaignRepository,
            WompiProperties wompiProperties, AdCandidateCache candidateCache, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.campaignRepository = campaignRepository;
        this.wompiProperties = wompiProperties;
        this.candidateCache = candidateCache;
        this.clock = clock;
    }

    @Override
    public boolean supports(String reference) {
        return reference != null && reference.startsWith(REFERENCE_PREFIX);
    }

    @Override
    public WompiEventOutcome apply(WompiTransaction transaction, WompiEventSource source) {
        Optional<AdPayment> found = paymentRepository.lockByReference(transaction.reference());
        if (found.isEmpty()) {
            return WompiEventOutcome.UNKNOWN_REFERENCE;
        }
        AdPayment payment = found.get();
        if (!AdPayment.WOMPI.equals(payment.getProvider())
                || !wompiProperties.environment().name().equals(payment.getEnvironment())) {
            log.error("Wompi transaction {} targets ad payment {} of another provider or environment",
                    transaction.id(), payment.getReference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (transaction.amountInCents() != payment.getAmountInCents()
                || !payment.getCurrency().equals(transaction.currency())) {
            log.error("Wompi transaction {} amount or currency does not match ad payment {}",
                    transaction.id(), payment.getReference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (transaction.approved()) {
            return approve(payment, transaction);
        }
        if (transaction.failed()) {
            return fail(payment, transaction);
        }
        if (!transaction.pending()) {
            log.warn("Unsupported Wompi status {} for ad payment {}", transaction.status(), payment.getReference());
        }
        return WompiEventOutcome.IGNORED;
    }

    private WompiEventOutcome approve(AdPayment payment, WompiTransaction transaction) {
        if (AdPayment.APPROVED.equals(payment.getStatus())) {
            if (transaction.id().equals(payment.getProviderTransactionId())) {
                return WompiEventOutcome.IGNORED;
            }
            // Segundo cobro aprobado sobre una referencia ya pagada: reembolso manual.
            campaignRepository.lockById(payment.getCampaignId())
                    .ifPresent(campaign -> campaign.setRequiresManualRefund(true));
            log.error("Second approved Wompi transaction {} for already paid ad payment {}; manual refund required",
                    transaction.id(), payment.getReference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (linkedElsewhere(payment, transaction)) {
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        AdCampaign campaign = campaignRepository.lockById(payment.getCampaignId())
                .orElseThrow(() -> new IllegalStateException("Campaign missing for ad payment " + payment.getId()));
        LocalDateTime now = LocalDateTime.now(clock.withZone(AdText.ZONE));
        LocalDate today = AdText.today(clock);
        payment.setStatus(AdPayment.APPROVED);
        payment.setProviderTransactionId(transaction.id());
        payment.setPaidAt(now);

        boolean activatable = campaign.getPaidAt() == null
                && AWAITING.contains(campaign.getStatus())
                && !today.isAfter(campaign.getEndsOn())
                && campaign.getTotalPrice() != null
                && AdCampaignRules.amountInCents(campaign.getTotalPrice()) == payment.getAmountInCents();
        if (activatable) {
            campaign.setPaidAt(now);
            campaign.setStatus(today.isBefore(campaign.getStartsOn())
                    ? AdCampaignStatus.SCHEDULED
                    : AdCampaignStatus.ACTIVE);
            AdCacheInvalidation.afterCommit(candidateCache);
            log.info("Ad campaign {} paid by Wompi transaction {}; status {}",
                    campaign.getId(), transaction.id(), campaign.getStatus());
            return WompiEventOutcome.APPLIED;
        }
        campaign.setRequiresManualRefund(true);
        if (campaign.getPaidAt() == null) {
            campaign.setPaidAt(now);
        }
        if (AWAITING.contains(campaign.getStatus())) {
            // Vencida o con otro precio: no se puede activar.
            campaign.setStatus(AdCampaignStatus.CANCELLED);
        }
        log.error("Approved Wompi transaction {} for ad campaign {} that cannot run (status {}); manual refund "
                + "required", transaction.id(), campaign.getId(), campaign.getStatus());
        return WompiEventOutcome.APPLIED;
    }

    private WompiEventOutcome fail(AdPayment payment, WompiTransaction transaction) {
        if (AdPayment.APPROVED.equals(payment.getStatus())) {
            if (WompiTransaction.VOIDED.equals(transaction.status())
                    && transaction.id().equals(payment.getProviderTransactionId())) {
                // Wompi anuló un cobro ya aplicado: la campaña no cambia; queda para revisión manual.
                Optional<AdCampaign> campaign = campaignRepository.lockById(payment.getCampaignId());
                if (campaign.isPresent() && !campaign.get().isRequiresManualRefund()) {
                    campaign.get().setRequiresManualRefund(true);
                    log.error("Approved Wompi transaction {} of ad payment {} was VOIDED; manual review required",
                            transaction.id(), payment.getReference());
                    return WompiEventOutcome.APPLIED;
                }
            }
            return WompiEventOutcome.IGNORED;
        }
        if (transaction.status().equals(payment.getStatus())
                && transaction.id().equals(payment.getProviderTransactionId())) {
            return WompiEventOutcome.IGNORED;
        }
        if (linkedElsewhere(payment, transaction)) {
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        payment.setStatus(transaction.status());
        payment.setProviderTransactionId(transaction.id());
        campaignRepository.lockById(payment.getCampaignId()).ifPresent(campaign -> {
            boolean otherPending = paymentRepository.findByCampaignIdAndStatus(campaign.getId(), AdPayment.PENDING)
                    .stream().anyMatch(other -> !other.getId().equals(payment.getId()));
            if (campaign.getStatus() == AdCampaignStatus.PENDING_PAYMENT && !otherPending) {
                campaign.setStatus(AdCampaignStatus.DRAFT);
            }
        });
        return WompiEventOutcome.APPLIED;
    }

    private boolean linkedElsewhere(AdPayment payment, WompiTransaction transaction) {
        if (transaction.id().equals(payment.getProviderTransactionId())) {
            return false;
        }
        if (paymentRepository.existsByProviderTransactionId(transaction.id())) {
            log.error("Wompi transaction {} is already linked to another ad payment", transaction.id());
            return true;
        }
        return false;
    }
}
