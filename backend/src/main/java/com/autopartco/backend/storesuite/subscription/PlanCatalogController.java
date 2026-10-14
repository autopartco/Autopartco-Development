package com.autopartco.backend.storesuite.subscription;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/**
 * Catálogo público de planes habilitados, ordenados por precio.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/plans")
@AllArgsConstructor
public class PlanCatalogController {
    private final PlanService planService;

    @GetMapping
    public List<PlanResponse> publicPlans() {
        return planService.publicPlans();
    }
}
