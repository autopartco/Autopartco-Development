package com.autopartco.backend.operations.plans;

import com.autopartco.backend.operations.auditlog.AdminAudit;
import java.util.Map;

import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.billing.PlanRequest;
import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Administración de planes de suscripción (solo root).
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/plans")
@AllArgsConstructor
public class PlanAdminController {
    private final PlanService planService;
    private final AdminAudit adminAudit;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<PlanResponse> allPlans() {
        return planService.allPlans();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public PlanResponse create(@Valid @RequestBody PlanRequest request, Authentication authentication) {
        PlanResponse plan = planService.create(request);
        adminAudit.record(authentication, "PLAN_CREATED", "PLAN", plan.id(), Map.of("code", plan.code(), "name", plan.name()));
        return plan;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public PlanResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody PlanRequest request,
            Authentication authentication
    ) {
        PlanResponse plan = planService.update(id, request);
        adminAudit.record(authentication, "PLAN_UPDATED", "PLAN", plan.id(),
                Map.of("code", plan.code(), "monthlyPriceCop", plan.monthlyPriceCop(), "enabled", plan.enabled()));
        return plan;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public void disable(@PathVariable UUID id, Authentication authentication) {
        planService.disable(id);
        adminAudit.record(authentication, "PLAN_DISABLED", "PLAN", id, Map.of());
    }
}
