package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.payments.WompiCheckoutRequest;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.billing.internal.SubscriptionPayment;
import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionPaymentRepository;
import com.autopartco.backend.core.billing.internal.PlanRepository;

import lombok.AllArgsConstructor;

/**
 * Crea el pago pendiente de una suscripción y el enlace firmado al checkout de Wompi. Solo puede hacerlo el OWNER
 * del almacén, y el almacén debe estar {@code ACTIVE}.
 */
@Service
@AllArgsConstructor
public class SubscriptionCheckout {
    private final SubscriptionPaymentRepository paymentRepository;
    private final StoreSubscriptionRepository applicationRepository;
    private final PlanRepository planRepository;
    private final WompiProperties wompiProperties;
    private final WompiClient wompiClient;
    private final StoreAccess storeAccess;

    @Transactional
    public CheckoutResponse createCheckout(
            UUID storeId,
            String email,
            CheckoutRequest request
    ) {
        storeAccess.requireOwner(email, storeId);
        StoreSubscription application = applicationRepository.lockByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la solicitud de vendedor."
                ));
        if (application.getStatus() != StoreApplicationStatus.ACTIVE) {
            throw new ConflictException("SELLER_APPLICATION_NOT_ACTIVE", "El almacén todavía no está habilitado.");
        }
        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::isEnabled)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_PLAN_NOT_FOUND",
                        "El plan seleccionado no existe o no está disponible."
                ));
        if (plan.getMonthlyPriceCop() <= 0 || "TRY".equalsIgnoreCase(plan.getCode())) {
            throw new BadRequestException("SELLER_PLAN_NOT_PAYABLE", "El plan gratuito no requiere pago.");
        }
        requirePlanChangeAllowed(application, plan);
        if (request.months() < 1 || request.months() > 12) {
            throw new BadRequestException("INVALID_PREPAID_MONTHS", "Puedes pagar entre 1 y 12 meses.");
        }
        requireWompiConfiguration();

        long amountInCents;
        try {
            amountInCents = Math.multiplyExact(
                    Math.multiplyExact(plan.getMonthlyPriceCop(), request.months()),
                    100L
            );
        } catch (ArithmeticException exception) {
            throw new BadRequestException("SELLER_PAYMENT_AMOUNT_INVALID", "El valor del pago supera el límite permitido.");
        }

        String reference = "APC-" + UUID.randomUUID().toString().replace("-", "");
        String currency = plan.getCurrency();
        SubscriptionPayment payment = paymentRepository.save(SubscriptionPayment.builder()
                .storeApplicationId(application.getId())
                .plan(plan)
                .reference(reference)
                .amountInCents(amountInCents)
                .prepaidMonths(request.months())
                .currency(currency)
                .status("PENDING")
                .provider("WOMPI")
                .environment(wompiProperties.environment().name())
                .build());

        // Sin expiration-time: el pago de suscripción no reserva nada (ADR 0004).
        String checkoutUrl = wompiClient.checkoutUrl(new WompiCheckoutRequest(
                reference, amountInCents, currency, wompiProperties.redirectUrl(), null));
        return new CheckoutResponse(
                payment.getReference(),
                checkoutUrl,
                amountInCents,
                currency,
                request.months()
        );
    }

    // D3: con periodo pagado vigente no se cambia de plan; se paga el mismo plan o se espera al vencimiento.
    private void requirePlanChangeAllowed(StoreSubscription application, Plan plan) {
        LocalDateTime now = LocalDateTime.now();
        Plan current = application.getPlan();
        boolean paidNow = application.getPaidThrough() != null && application.getPaidThrough().isAfter(now);
        if (paidNow && current != null && !Objects.equals(current.getId(), plan.getId())) {
            throw new ConflictException(
                    "SUBSCRIPTION_PLAN_CHANGE_LOCKED",
                    "Tienes un periodo pagado vigente en el plan " + current.getCode() + ". Cambia de plan al vencer.",
                    Map.of("currentPlanCode", current.getCode(), "paidThrough", application.getPaidThrough().toString())
            );
        }
    }

    private void requireWompiConfiguration() {
        if (!wompiProperties.checkoutConfigured()) {
            throw new IllegalStateException("Wompi checkout configuration is incomplete.");
        }
    }
}
