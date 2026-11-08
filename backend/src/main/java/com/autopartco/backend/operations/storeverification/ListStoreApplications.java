package com.autopartco.backend.operations.storeverification;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StoreApplicationView;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Historial paginado de solicitudes de almacén con filtro de estado opcional (solo root; lo exige el controlador). */
@Service
@AllArgsConstructor
public class ListStoreApplications {
    private final StoreApplications storeApplications;
    private final PlanService planService;

    /**
     * @param status estado opcional; vacío o {@code null} = todas
     * @throws BadRequestException {@code INVALID_APPLICATION_STATUS} si el estado no existe
     */
    @Transactional(readOnly = true)
    public PageResponse<StoreApplicationResponse> execute(String status, int page, int size) {
        PageResponse<StoreApplicationView> views = storeApplications.history(parseStatus(status), page, size);
        return new PageResponse<>(
                views.items().stream()
                        .map(view -> StoreApplicationResponse.from(view, planService))
                        .toList(),
                views.page(),
                views.size(),
                views.total(),
                views.totalPages()
        );
    }

    static StoreApplicationStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StoreApplicationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException(
                    "INVALID_APPLICATION_STATUS",
                    "El estado debe ser PENDING_REVIEW, CONFIGURATION_PENDING, REJECTED o ACTIVE."
            );
        }
    }
}
