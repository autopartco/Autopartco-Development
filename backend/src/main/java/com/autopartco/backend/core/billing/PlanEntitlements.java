package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StaffDirectory;

import lombok.AllArgsConstructor;

/**
 * Derechos del plan de un almacén: cupos (sucursales, productos, usuarios), funciones incluidas y vigencia de la
 * suscripción. La llaman los casos de uso de los productos antes de invocar {@code core}.
 */
@Service
@AllArgsConstructor
public class PlanEntitlements {
    private final StoreSubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final BranchService branchService;
    private final ProductCatalog productCatalog;
    private final StaffDirectory staffDirectory;

    @Transactional(readOnly = true)
    public Optional<SubscriptionAccess> accessForStore(UUID storeId) {
        return findAccess(storeId).map(this::toView);
    }

    @Transactional(readOnly = true)
    public SubscriptionAccess requireAccess(UUID storeId) {
        return accessForStore(storeId).orElseThrow(() -> new NotFoundException(
                "SELLER_APPLICATION_NOT_FOUND",
                "No se encontró la suscripción de vendedor para este almacén."
        ));
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID storeId) {
        return new Usage(
                branchService.countActive(storeId),
                productCatalog.countInStore(storeId),
                staffDirectory.countActive(storeId)
        );
    }

    @Transactional(readOnly = true)
    public void requireCapacity(UUID storeId, Capacity capacity, long additional) {
        Optional<Access> access = findAccess(storeId);
        if (access.isEmpty()) {
            return;
        }
        Access current = access.get();
        requireNotExpired(current);
        long used = switch (capacity) {
            case BRANCHES -> branchService.countActive(storeId);
            case PRODUCTS -> productCatalog.countInStore(storeId);
            case USERS -> staffDirectory.countActive(storeId);
        };
        Integer maximum = switch (capacity) {
            case BRANCHES -> current.plan().getMaxBranches();
            case PRODUCTS -> current.plan().getMaxProducts();
            case USERS -> current.plan().getMaxUsers();
        };
        if (maximum != null && used + additional > maximum) {
            throw new ConflictException(
                    "SELLER_PLAN_LIMIT_REACHED",
                    "Alcanzaste el límite de " + capacity.label + " de tu plan "
                            + current.plan().getName() + " (" + maximum + ").",
                Map.of("capacity", capacity.name(), "limit", maximum, "used", used, "requested", additional, "planCode", current.plan().getCode())
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireCapability(UUID storeId, String capability) {
        Optional<Access> access = findAccess(storeId);
        if (access.isEmpty()) {
            return;
        }
        Access current = access.get();
        requireNotExpired(current);
        if (!current.plan().getCapabilities().contains(capability)) {
            throw new ConflictException(
                    "SELLER_PLAN_FEATURE_UNAVAILABLE",
                    "La función " + capability + " no está incluida en el plan " + current.plan().getName() + ".",
                Map.of("capability", capability, "planCode", current.plan().getCode())
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireSubscriptionActive(UUID storeId) {
        findAccess(storeId).ifPresent(this::requireNotExpired);
    }

    /**
     * Igual que {@link #requireSubscriptionActive} pero sin lanzar: {@code false} si la solicitud no está activa o la
     * prueba/suscripción venció. Sin suscripción registrada, {@code true} (mismo criterio que los demás chequeos).
     * Requisito de publicación {@code SUBSCRIPTION_INACTIVE}.
     */
    @Transactional(readOnly = true)
    public boolean isSubscriptionActive(UUID storeId) {
        try {
            return findAccess(storeId).map(access -> !access.subscriptionExpired()).orElse(true);
        } catch (ConflictException notActive) {
            return false;
        }
    }

    private Optional<Access> findAccess(UUID storeId) {
        return subscriptionRepository.findByStoreId(storeId)
                .map(this::accessFor);
    }

    private Access accessFor(StoreSubscription subscription) {
        if (subscription.getStatus() != StoreApplicationStatus.ACTIVE) {
            throw new ConflictException(
                    "SELLER_APPLICATION_NOT_ACTIVE",
                    "El almacén todavía no está habilitado para operar."
            );
        }
        LocalDateTime now = LocalDateTime.now();
        boolean paid = subscription.getPaidThrough() != null && subscription.getPaidThrough().isAfter(now);
        boolean trial = subscription.getTrialEndsAt() != null && subscription.getTrialEndsAt().isAfter(now);
        boolean expired = !paid && !trial;
        Plan effectivePlan = paid
                ? subscription.getPlan()
                : planService.trialPlan();
        LocalDateTime validThrough = paid ? subscription.getPaidThrough() : subscription.getTrialEndsAt();
        return new Access(
                subscription,
                effectivePlan,
                expired,
                validThrough
        );
    }

    private SubscriptionAccess toView(Access access) {
        StoreSubscription subscription = access.subscription();
        return new SubscriptionAccess(
                subscription.getStoreId(),
                planService.toResponse(access.plan()),
                planService.toResponse(subscription.getSelectedPlan()),
                access.subscriptionExpired(),
                access.validThrough(),
                subscription.getTrialEndsAt(),
                subscription.getPaidThrough()
        );
    }

    private void requireNotExpired(Access access) {
        if (access.subscriptionExpired()) {
            throw new ConflictException(
                    "SELLER_SUBSCRIPTION_EXPIRED",
                    "La prueba o suscripción venció. Renueva un plan para publicar productos y crear nuevas operaciones.",
                expiredDetails(access.validThrough())
            );
        }
    }

    private static Map<String, Object> expiredDetails(LocalDateTime validThrough) {
        Map<String, Object> details = new HashMap<>();
        details.put("expiredAt", validThrough == null ? null : validThrough.toString());
        return details;
    }

    public enum Capacity {
        BRANCHES("sucursales"),
        PRODUCTS("productos"),
        USERS("usuarios");

        private final String label;

        Capacity(String label) {
            this.label = label;
        }
    }

    public record Usage(long branches, long products, long users) {
    }

    private record Access(
            StoreSubscription subscription,
            Plan plan,
            boolean subscriptionExpired,
            LocalDateTime validThrough
    ) {
    }
}
