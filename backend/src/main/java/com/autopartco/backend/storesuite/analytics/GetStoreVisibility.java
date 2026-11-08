package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.Analytics;
import com.autopartco.backend.core.analytics.DateRange;
import com.autopartco.backend.core.analytics.VisibilityQueries;
import com.autopartco.backend.core.analytics.VisibilityReport;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/**
 * Visitas al perfil y a las publicaciones, compartidos y origen del tráfico. Requiere {@code ANALYTICS_READ}; a
 * diferencia del resto de la inteligencia comercial, no exige {@code ADVANCED_REPORTS}: está en todos los planes.
 */
@Service
@AllArgsConstructor
public class GetStoreVisibility {
    private final StoreAccess storeAccess;
    private final VisibilityQueries visibilityQueries;

    @Transactional(readOnly = true)
    public VisibilityReport execute(UUID storeId, String email, String from, String to) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ANALYTICS_READ);
        return visibilityQueries.report(storeId, DateRange.resolve(from, to, Analytics.today()));
    }
}
