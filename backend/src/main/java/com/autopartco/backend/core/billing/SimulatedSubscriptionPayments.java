package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.PlanRepository;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionEvents;
import com.autopartco.backend.core.billing.internal.SubscriptionPayment;
import com.autopartco.backend.core.billing.internal.SubscriptionPaymentRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionPeriods;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.PaymentRequiredException;

import lombok.AllArgsConstructor;

/**
 * Cobro simulado de la suscripción (sin pasarela). Tiene el mismo efecto que un webhook aprobado de Wompi: cambia el
 * plan, extiende {@code paid_through}, registra el pago y los eventos. Solo el OWNER puede cobrar.
 */
@Service
@AllArgsConstructor
public class SimulatedSubscriptionPayments {
    private static final String PROVIDER = "SIMULATED";

    private final SubscriptionPaymentRepository paymentRepository;
    private final StoreSubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final SubscriptionEvents subscriptionEvents;
    private final StoreAccess storeAccess;
    private final AccountLookup accountLookup;
    private final BillingSettings billingSettings;

    @Transactional
    public SimulatedPaymentResult pay(UUID storeId, String email, SimulatedPaymentRequest request) {
        storeAccess.requireOwner(email, storeId);
        if (!billingSettings.simulatedPaymentsEnabled()) {
            throw new BadRequestException(
                    "PAYMENT_METHOD_NOT_SUPPORTED",
                    "El cobro simulado está deshabilitado en este entorno."
            );
        }
        StoreSubscription application = subscriptionRepository.lockByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la solicitud de vendedor."
                ));
        if (application.getStatus() != StoreApplicationStatus.ACTIVE) {
            throw new ConflictException("SELLER_APPLICATION_NOT_ACTIVE", "El almacén todavía no está habilitado.");
        }

        // Repetición idempotente: la misma clave en la misma tienda devuelve el pago original.
        String reference = "SIM-" + request.clientRequestId();
        var existing = paymentRepository.findByReference(reference);
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getStoreApplicationId(), application.getId())) {
                throw duplicateRequest();
            }
            return new SimulatedPaymentResult(SubscriptionPaymentView.from(existing.get()), false);
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
        if (request.months() < 1 || request.months() > 12) {
            throw new BadRequestException("INVALID_PREPAID_MONTHS", "Puedes pagar entre 1 y 12 meses.");
        }
        LocalDateTime now = LocalDateTime.now();
        requirePlanChangeAllowed(application, plan, now);

        long amountInCents;
        try {
            amountInCents = Math.multiplyExact(Math.multiplyExact(plan.getMonthlyPriceCop(), request.months()), 100L);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("SELLER_PAYMENT_AMOUNT_INVALID", "El valor del pago supera el límite permitido.");
        }
        if (request.simulatedOutcome() == SimulatedPaymentRequest.Outcome.REJECTED) {
            throw new PaymentRequiredException(
                    "PAYMENT_REJECTED",
                    "El pago simulado fue rechazado.",
                    Map.of("amountCop", amountInCents / 100, "currency", plan.getCurrency())
            );
        }

        LocalDateTime periodStart = SubscriptionPeriods.periodStart(now, application.getTrialEndsAt(), application.getPaidThrough());
        LocalDateTime periodEnd = periodStart.plusMonths(request.months());
        UUID previousPlanId = application.getPlan() == null ? null : application.getPlan().getId();
        UUID accountId = accountLookup.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(AccountRef::accountId)
                .orElse(null);

        SubscriptionPayment payment;
        try {
            payment = paymentRepository.saveAndFlush(SubscriptionPayment.builder()
                .storeApplicationId(application.getId())
                .plan(plan)
                .reference(reference)
                .amountInCents(amountInCents)
                .prepaidMonths(request.months())
                .currency(plan.getCurrency())
                .status("APPROVED")
                .provider(PROVIDER)
                .paidAt(now)
                .periodStartsAt(periodStart)
                .periodEndsAt(periodEnd)
                .planSnapshot(snapshot(plan))
                .requestedByAccountId(accountId)
                .build());
        } catch (DataIntegrityViolationException exception) {
            // Carrera con otra solicitud que usó la misma referencia (única en la tabla).
            throw duplicateRequest();
        }
        application.setPlan(plan);
        application.setPaidThrough(periodEnd);
        subscriptionEvents.paymentApproved(application, payment, previousPlanId, accountId, now);
        return new SimulatedPaymentResult(SubscriptionPaymentView.from(payment), true);
    }

    private static ConflictException duplicateRequest() {
        return new ConflictException(
                "SUBSCRIPTION_DUPLICATE_REQUEST",
                "La clave de solicitud ya fue utilizada. Genera una nueva e inténtalo de nuevo."
        );
    }

    // D3: con periodo pagado vigente no se cambia de plan; se paga el mismo plan o se espera al vencimiento.
    private void requirePlanChangeAllowed(StoreSubscription application, Plan plan, LocalDateTime now) {
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

    private static Map<String, Object> snapshot(Plan plan) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("planId", plan.getId() == null ? null : plan.getId().toString());
        snapshot.put("code", plan.getCode());
        snapshot.put("name", plan.getName());
        snapshot.put("monthlyPriceCop", plan.getMonthlyPriceCop());
        snapshot.put("currency", plan.getCurrency());
        snapshot.put("maxBranches", plan.getMaxBranches());
        snapshot.put("maxProducts", plan.getMaxProducts());
        snapshot.put("maxUsers", plan.getMaxUsers());
        snapshot.put("capabilities", plan.getCapabilities());
        return snapshot;
    }
}
