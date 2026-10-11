package com.autopartco.backend.seller.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.exception.ConflictException;
import com.autopartco.backend.exception.NotFoundException;
import com.autopartco.backend.exception.BadRequestException;
import com.autopartco.backend.seller.dto.SellerPlanRequest;
import com.autopartco.backend.seller.dto.SellerPlanResponse;
import com.autopartco.backend.seller.entity.SellerPlan;
import com.autopartco.backend.seller.repository.SellerPlanRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SellerPlanService {
    private static final String TRIAL_CODE = "TRY";

    private final SellerPlanRepository repository;

    @Transactional(readOnly = true)
    public List<SellerPlanResponse> publicPlans() {
        return repository.findByEnabledTrueOrderByMonthlyPriceCopAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerPlanResponse> allPlans() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public SellerPlanResponse create(SellerPlanRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("SELLER_PLAN_CODE_EXISTS", "Ya existe un plan con ese código.");
        }
        SellerPlan plan = new SellerPlan();
        apply(plan, request, code);
        return toResponse(repository.save(plan));
    }

    @Transactional
    public SellerPlanResponse update(UUID id, SellerPlanRequest request) {
        SellerPlan plan = find(id);
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("SELLER_PLAN_CODE_EXISTS", "Ya existe un plan con ese código.");
        }
        apply(plan, request, code);
        return toResponse(plan);
    }

    @Transactional
    public void disable(UUID id) {
        SellerPlan plan = find(id);
        if (TRIAL_CODE.equalsIgnoreCase(plan.getCode())) {
            throw new ConflictException("TRIAL_PLAN_REQUIRED", "El plan de prueba no se puede desactivar.");
        }
        plan.setEnabled(false);
        plan.setRecommended(false);
    }

    @Transactional(readOnly = true)
    public SellerPlan find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("SELLER_PLAN_NOT_FOUND", "No se encontró el plan."));
    }

    @Transactional(readOnly = true)
    public SellerPlan trialPlan() {
        return repository.findByCodeIgnoreCase(TRIAL_CODE)
                .filter(SellerPlan::isEnabled)
                .orElseThrow(() -> new NotFoundException(
                        "TRIAL_PLAN_NOT_CONFIGURED",
                        "No está configurado el plan gratuito de prueba."
                ));
    }

    @Transactional(readOnly = true)
    public SellerPlan recommendedPlan() {
        return repository.findByEnabledTrueOrderByMonthlyPriceCopAsc().stream()
                .filter(SellerPlan::isRecommended)
                .findFirst()
                .orElseGet(this::trialPlan);
    }

    private void apply(SellerPlan plan, SellerPlanRequest request, String code) {
        if ("TRY".equalsIgnoreCase(plan.getCode()) && !"TRY".equals(code)) {
            throw new ConflictException("TRIAL_PLAN_CODE_IMMUTABLE", "El código del plan de prueba no se puede cambiar.");
        }
        if (TRIAL_CODE.equals(code) && request.monthlyPriceCop() != 0) {
            throw new ConflictException("TRIAL_PLAN_MUST_BE_FREE", "El plan TRY debe tener precio cero.");
        }
        if (!"COP".equalsIgnoreCase(request.currency())) {
            throw new BadRequestException("SELLER_PLAN_CURRENCY_UNSUPPORTED", "Los planes de vendedor deben cobrarse en COP.");
        }
        if (request.recommended() && !request.enabled()) {
            throw new BadRequestException("DISABLED_PLAN_CANNOT_BE_RECOMMENDED", "Un plan deshabilitado no puede ser recomendado.");
        }
        if (TRIAL_CODE.equals(code) && !request.enabled()) {
            throw new ConflictException("TRIAL_PLAN_REQUIRED", "El plan TRY no se puede desactivar.");
        }
        if (plan.getMaxBranches() == null && plan.getMaxProducts() == null && plan.getMaxUsers() == null) {
            applyDefaultEntitlements(plan, code);
        }
        if (request.recommended()) {
            repository.findByEnabledTrueOrderByMonthlyPriceCopAsc().stream()
                    .filter(SellerPlan::isRecommended)
                    .filter(existing -> !existing.getId().equals(plan.getId()))
                    .forEach(existing -> existing.setRecommended(false));
        }
        plan.setCode(code);
        plan.setName(request.name().trim());
        plan.setDescription(request.description() == null ? null : request.description().trim());
        plan.setMonthlyPriceCop(request.monthlyPriceCop());
        plan.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        plan.setFeatures(List.copyOf(request.features()));
        if (request.maxBranches() != null) {
            plan.setMaxBranches(request.maxBranches());
        }
        if (request.maxProducts() != null) {
            plan.setMaxProducts(request.maxProducts());
        }
        if (request.maxUsers() != null) {
            plan.setMaxUsers(request.maxUsers());
        }
        if (request.capabilities() != null) {
            plan.setCapabilities(List.copyOf(request.capabilities()));
        }
        plan.setRecommended(request.recommended());
        plan.setEnabled(request.enabled());
    }

    private void applyDefaultEntitlements(SellerPlan plan, String code) {
        switch (code) {
            case "TRY", "FREE" -> {
                plan.setMaxBranches(1);
                plan.setMaxProducts(50);
                plan.setMaxUsers(1);
                plan.setCapabilities(List.of("BASIC_ANALYTICS"));
            }
            case "BUSINESS" -> {
                plan.setMaxBranches(5);
                plan.setMaxProducts(5000);
                plan.setMaxUsers(10);
                plan.setCapabilities(List.of(
                        "BASIC_ANALYTICS", "ADVANCED_REPORTS", "PROMOTIONS", "CSV_IMPORT",
                        "WHATSAPP_NOTIFICATIONS", "INTEGRATIONS", "CUSTOM_ROLES"
                ));
            }
            case "ENTERPRISE" -> {
                plan.setMaxBranches(null);
                plan.setMaxProducts(null);
                plan.setMaxUsers(null);
                plan.setCapabilities(List.of(
                        "BASIC_ANALYTICS", "ADVANCED_REPORTS", "PROMOTIONS", "CSV_IMPORT",
                        "WHATSAPP_NOTIFICATIONS", "INTEGRATIONS", "CUSTOM_ROLES", "API_ACCESS",
                        "WEBHOOKS", "ERP_INTEGRATIONS", "CUSTOM_DOMAIN"
                ));
            }
            default -> {
                plan.setMaxBranches(1);
                plan.setMaxProducts(0);
                plan.setMaxUsers(1);
                plan.setCapabilities(List.of());
            }
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public SellerPlanResponse toResponse(SellerPlan plan) {
        return new SellerPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getMonthlyPriceCop(),
                plan.getCurrency(),
                List.copyOf(plan.getFeatures()),
                plan.isRecommended(),
                plan.isEnabled(),
                plan.getMaxBranches(),
                plan.getMaxProducts(),
                plan.getMaxUsers(),
                plan.getCapabilities() == null ? List.of() : List.copyOf(plan.getCapabilities())
        );
    }
}
