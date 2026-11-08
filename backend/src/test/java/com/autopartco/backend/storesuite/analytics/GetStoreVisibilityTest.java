package com.autopartco.backend.storesuite.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.analytics.DateRange;
import com.autopartco.backend.core.analytics.VisibilityQueries;
import com.autopartco.backend.core.analytics.VisibilityReport;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;

class GetStoreVisibilityTest {
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final VisibilityQueries queries = mock(VisibilityQueries.class);
    private final GetStoreVisibility useCase = new GetStoreVisibility(storeAccess, queries);
    private final UUID storeId = UUID.randomUUID();

    @Test
    void returnsReportForTheRequestedRange() {
        VisibilityReport report = mock(VisibilityReport.class);
        DateRange range = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        when(queries.report(storeId, range)).thenReturn(report);

        assertThat(useCase.execute(storeId, "ana@la30.co", "2026-10-01", "2026-10-31")).isSameAs(report);
    }

    @Test
    void requiresAnalyticsReadButNotThePlanCapability() {
        doThrow(new AccessDeniedException("denied"))
                .when(storeAccess).requirePermission("ana@la30.co", storeId, StorePermissionCodes.ANALYTICS_READ);

        assertThatThrownBy(() -> useCase.execute(storeId, "ana@la30.co", null, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(queries);
    }

    @Test
    void rangeOverOneYearIsRejected() {
        assertThatThrownBy(() -> useCase.execute(storeId, "ana@la30.co", "2025-01-01", "2026-10-31"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(queries);
    }

    @Test
    void permissionIsCheckedBeforeQuerying() {
        useCase.execute(storeId, "ana@la30.co", null, null);
        org.mockito.Mockito.verify(storeAccess)
                .requirePermission(eq("ana@la30.co"), eq(storeId), eq(StorePermissionCodes.ANALYTICS_READ));
        org.mockito.Mockito.verify(queries).report(eq(storeId), any(DateRange.class));
    }
}
