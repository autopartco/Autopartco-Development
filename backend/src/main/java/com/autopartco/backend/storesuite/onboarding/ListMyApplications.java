package com.autopartco.backend.storesuite.onboarding;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.organization.StoreApplications;

import lombok.AllArgsConstructor;

/** Solicitudes de almacén de la cuenta autenticada, de la más reciente a la más antigua. */
@Service
@AllArgsConstructor
public class ListMyApplications {
    private final StoreApplications storeApplications;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<StoreApplicationResponse> execute(String email) {
        return storeApplications.ofApplicant(email).stream()
                .map(view -> StoreApplicationResponse.from(view, planService))
                .toList();
    }
}
