package com.autopartco.backend.seller.controller;

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

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.seller.dto.SellerPlanRequest;
import com.autopartco.backend.seller.dto.SellerPlanResponse;
import com.autopartco.backend.seller.service.SellerPlanService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/seller-plans")
@AllArgsConstructor
public class SellerPlanController {
    private final SellerPlanService planService;

    @GetMapping
    public List<SellerPlanResponse> publicPlans() {
        return planService.publicPlans();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<SellerPlanResponse> allPlans() {
        return planService.allPlans();
    }

    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public SellerPlanResponse create(@Valid @RequestBody SellerPlanRequest request) {
        return planService.create(request);
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public SellerPlanResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody SellerPlanRequest request
    ) {
        return planService.update(id, request);
    }

    @DeleteMapping("/admin/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public void disable(@PathVariable UUID id) {
        planService.disable(id);
    }
}
