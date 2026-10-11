package com.autopartco.backend.seller.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.exception.ConflictException;
import com.autopartco.backend.exception.NotFoundException;
import com.autopartco.backend.product.repository.ProductRepository;
import com.autopartco.backend.seller.entity.SellerApplication;
import com.autopartco.backend.seller.entity.SellerApplicationStatus;
import com.autopartco.backend.seller.entity.SellerPlan;
import com.autopartco.backend.seller.repository.SellerApplicationRepository;
import com.autopartco.backend.store.repository.BranchRepository;
import com.autopartco.backend.store.repository.CollaboratorRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SellerPlanEntitlementService {
    private static final String ACTIVE_STATE = "ACTIVE";

    private final SellerApplicationRepository applicationRepository;
    private final SellerPlanService planService;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final CollaboratorRepository collaboratorRepository;

    @Transactional(readOnly = true)
    public Optional<Access> accessForStore(UUID storeId) {
        return applicationRepository.findByStoreId(storeId)
                .map(this::accessFor);
    }

    @Transactional(readOnly = true)
    public Access requireAccess(UUID storeId) {
        return accessForStore(storeId).orElseThrow(() -> new NotFoundException(
                "SELLER_APPLICATION_NOT_FOUND",
                "No se encontró la suscripción de vendedor para este almacén."
        ));
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID storeId) {
        return new Usage(
                branchRepository.countByStore_IdAndState_Code(storeId, ACTIVE_STATE),
                productRepository.countProductsInStore(storeId),
                collaboratorRepository.countByStore_IdAndState_Code(storeId, ACTIVE_STATE)
        );
    }

    @Transactional(readOnly = true)
    public void requireCapacity(UUID storeId, Capacity capacity, long additional) {
        Optional<Access> access = accessForStore(storeId);
        if (access.isEmpty()) {
            return;
        }
        Access current = access.get();
        requireNotExpired(current);
        long used = switch (capacity) {
            case BRANCHES -> branchRepository.countByStore_IdAndState_Code(storeId, ACTIVE_STATE);
            case PRODUCTS -> productRepository.countProductsInStore(storeId);
            case USERS -> collaboratorRepository.countByStore_IdAndState_Code(storeId, ACTIVE_STATE);
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
                            + current.plan().getName() + " (" + maximum + ")."
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireCapability(UUID storeId, String capability) {
        Optional<Access> access = accessForStore(storeId);
        if (access.isEmpty()) {
            return;
        }
        Access current = access.get();
        requireNotExpired(current);
        if (!current.plan().getCapabilities().contains(capability)) {
            throw new ConflictException(
                    "SELLER_PLAN_FEATURE_UNAVAILABLE",
                    "La función " + capability + " no está incluida en el plan " + current.plan().getName() + "."
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireSubscriptionActive(UUID storeId) {
        accessForStore(storeId).ifPresent(this::requireNotExpired);
    }

    private Access accessFor(SellerApplication application) {
        if (application.getStatus() != SellerApplicationStatus.ACTIVE) {
            throw new ConflictException(
                    "SELLER_APPLICATION_NOT_ACTIVE",
                    "El almacén todavía no está habilitado para operar."
            );
        }
        LocalDateTime now = LocalDateTime.now();
        boolean paid = application.getPaidThrough() != null && application.getPaidThrough().isAfter(now);
        boolean trial = application.getTrialEndsAt() != null && application.getTrialEndsAt().isAfter(now);
        boolean expired = !paid && !trial;
        SellerPlan effectivePlan = paid
                ? application.getPlan()
                : planService.trialPlan();
        LocalDateTime validThrough = paid ? application.getPaidThrough() : application.getTrialEndsAt();
        return new Access(
                application,
                effectivePlan,
                expired,
                validThrough
        );
    }

    private void requireNotExpired(Access access) {
        if (access.subscriptionExpired()) {
            throw new ConflictException(
                    "SELLER_SUBSCRIPTION_EXPIRED",
                    "La prueba o suscripción venció. Renueva un plan para publicar productos y crear nuevas operaciones."
            );
        }
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

    public record Access(
            SellerApplication application,
            SellerPlan plan,
            boolean subscriptionExpired,
            LocalDateTime validThrough
    ) {
        public UUID storeId() {
            return application.getStore().getId();
        }

        public List<String> capabilities() {
            return plan.getCapabilities() == null ? List.of() : List.copyOf(plan.getCapabilities());
        }
    }
}
