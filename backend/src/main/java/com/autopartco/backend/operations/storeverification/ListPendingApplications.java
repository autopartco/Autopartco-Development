package com.autopartco.backend.operations.storeverification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.organization.StoreApplications;

import lombok.AllArgsConstructor;

/** Solicitudes de almacén pendientes de revisión (solo root; lo exige el controlador). */
@Service
@AllArgsConstructor
public class ListPendingApplications {
    private final StoreApplications storeApplications;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<StoreApplicationResponse> execute() {
        return storeApplications.pending().stream()
                .map(view -> StoreApplicationResponse.from(view, planService))
                .toList();
    }
}
