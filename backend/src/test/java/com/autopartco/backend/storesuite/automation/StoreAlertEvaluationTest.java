package com.autopartco.backend.storesuite.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.analytics.AlertRules;
import com.autopartco.backend.core.analytics.StoreSnapshots;
import com.autopartco.backend.core.notification.StoreAlertMailer;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;

class StoreAlertEvaluationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final String EMAIL = "owner@example.com";

    private final UUID storeId = UUID.randomUUID();
    private final StoreSnapshots snapshots = mock(StoreSnapshots.class);
    private final StoreAlertQueries queries = mock(StoreAlertQueries.class);
    private final StaffDirectory staff = mock(StaffDirectory.class);
    private final StoreAlertMailer mailer = mock(StoreAlertMailer.class);
    private final StoreAlertEvaluation evaluation = new StoreAlertEvaluation(snapshots, queries, staff, mailer);

    private AlertRules.ProductState stockedOut(String name) {
        return new AlertRules.ProductState(UUID.randomUUID(), name, "A", null, 0, 5, 1, 400);
    }

    private void snapshotWith(AlertRules.ProductState... products) {
        when(snapshots.capture(storeId)).thenReturn(new AlertRules.Snapshot(storeId, TODAY, List.of(),
                List.of(products), List.of()));
    }

    @Test
    void emailsOnlyNewRowsOfTypesWithEmailEnabled() {
        AlertRules.ProductState fresh = stockedOut("Filtro de aceite");
        AlertRules.ProductState repeated = stockedOut("Bujía");
        snapshotWith(fresh, repeated);
        when(queries.preferences(storeId)).thenReturn(Map.of(AlertRules.Type.STOCKOUT,
                new AlertPreferenceResponse("STOCKOUT", true, true, null)));
        when(queries.insertIfAbsent(eq(storeId), any())).thenAnswer(call ->
                ((AlertRules.Candidate) call.getArgument(1)).payload().get("productId").equals(fresh.productId()));
        when(staff.emailsWithPermission(storeId, StorePermissionCodes.ALERT_MANAGE))
                .thenReturn(List.of("a@example.com", "b@example.com"));

        List<AlertRules.Candidate> inserted = evaluation.evaluate(storeId, "Repuestos Sur", "negocio@example.com");

        assertThat(inserted).singleElement().satisfies(candidate ->
                assertThat(candidate.payload()).containsEntry("productId", fresh.productId()));
        verify(mailer).send(eq("a@example.com"), eq(storeId), eq("Repuestos Sur"),
                argThatSingleLineContaining("Filtro de aceite"));
        verify(mailer).send(eq("b@example.com"), eq(storeId), eq("Repuestos Sur"),
                argThatSingleLineContaining("Filtro de aceite"));
        verify(mailer, never()).send(eq("negocio@example.com"), any(), anyString(), anyList());
    }

    private static List<String> argThatSingleLineContaining(String text) {
        return org.mockito.ArgumentMatchers.argThat(lines -> lines != null && lines.size() == 1
                && lines.get(0).contains(text) && !lines.get(0).contains("Bujía"));
    }

    @Test
    void noEmailWhenPreferenceDisabledByDefault() {
        snapshotWith(stockedOut("Filtro"));
        when(queries.preferences(storeId)).thenReturn(Map.of());
        when(queries.insertIfAbsent(eq(storeId), any())).thenReturn(true);

        assertThat(evaluation.evaluate(storeId, "Repuestos Sur", "negocio@example.com")).hasSize(1);

        verify(mailer, never()).send(anyString(), any(), anyString(), anyList());
        verify(staff, never()).emailsWithPermission(any(), anyString());
    }

    @Test
    void noEmailWhenNothingWasInserted() {
        snapshotWith(stockedOut("Filtro"));
        when(queries.preferences(storeId)).thenReturn(Map.of(AlertRules.Type.STOCKOUT,
                new AlertPreferenceResponse("STOCKOUT", true, true, null)));
        when(queries.insertIfAbsent(eq(storeId), any())).thenReturn(false);

        assertThat(evaluation.evaluate(storeId, "Repuestos Sur", "negocio@example.com")).isEmpty();

        verify(mailer, never()).send(anyString(), any(), anyString(), anyList());
    }

    @Test
    void inAppDisabledTypeIsNotInsertedNorEmailed() {
        snapshotWith(stockedOut("Filtro"));
        when(queries.preferences(storeId)).thenReturn(Map.of(AlertRules.Type.STOCKOUT,
                new AlertPreferenceResponse("STOCKOUT", false, true, null)));

        assertThat(evaluation.evaluate(storeId, "Repuestos Sur", "negocio@example.com")).isEmpty();

        verify(queries, never()).insertIfAbsent(any(), any());
        verify(mailer, never()).send(anyString(), any(), anyString(), anyList());
    }

    @Test
    void fallsBackToBusinessEmailWithoutAlertManagers() {
        snapshotWith(stockedOut("Filtro"));
        when(queries.preferences(storeId)).thenReturn(Map.of(AlertRules.Type.STOCKOUT,
                new AlertPreferenceResponse("STOCKOUT", true, true, null)));
        when(queries.insertIfAbsent(eq(storeId), any())).thenReturn(true);
        when(staff.emailsWithPermission(storeId, StorePermissionCodes.ALERT_MANAGE)).thenReturn(List.of());

        evaluation.evaluate(storeId, "Repuestos Sur", "negocio@example.com");

        verify(mailer).send(eq("negocio@example.com"), eq(storeId), eq("Repuestos Sur"), anyList());
    }

    @Test
    void mailFailureDoesNotStopOtherRecipients() {
        snapshotWith(stockedOut("Filtro"));
        when(queries.preferences(storeId)).thenReturn(Map.of(AlertRules.Type.STOCKOUT,
                new AlertPreferenceResponse("STOCKOUT", true, true, null)));
        when(queries.insertIfAbsent(eq(storeId), any())).thenReturn(true);
        when(staff.emailsWithPermission(storeId, StorePermissionCodes.ALERT_MANAGE))
                .thenReturn(List.of("a@example.com", "b@example.com"));
        doThrow(new IllegalStateException("smtp")).when(mailer).send(eq("a@example.com"), any(), anyString(),
                anyList());

        evaluation.evaluate(storeId, "Repuestos Sur", null);

        verify(mailer).send(eq("b@example.com"), eq(storeId), eq("Repuestos Sur"), anyList());
    }

    @Test
    void resolvesStockoutAlertsThatNoLongerApplyKeepingCurrentOnes() {
        AlertRules.ProductState stillOut = stockedOut("Filtro");
        snapshotWith(stillOut);
        when(queries.preferences(storeId)).thenReturn(Map.of());
        when(queries.insertIfAbsent(eq(storeId), any())).thenReturn(false);

        evaluation.evaluate(storeId, "Repuestos Sur", null);

        verify(queries).resolveOthers(storeId, AlertRules.Type.STOCKOUT,
                Set.of("STOCKOUT:" + stillOut.productId() + ":2026-W45"));
        // Sin candidatas de STOCKOUT_SOON: se resuelven todas las abiertas de ese tipo.
        verify(queries).resolveOthers(eq(storeId), eq(AlertRules.Type.STOCKOUT_SOON), eq(Set.of()));
        verify(queries, never()).resolveOthers(any(), eq(AlertRules.Type.SALES_DROP), anyCollection());
    }

    @Test
    void usesThresholdsFromPreferences() {
        // Cobertura 10 días: no alerta con 7 (defecto) pero sí con el umbral guardado de 14.
        snapshotWith(new AlertRules.ProductState(UUID.randomUUID(), "Filtro", "A", 10.0, 50, 10, 1, 400));
        when(queries.preferences(storeId)).thenReturn(Map.of(AlertRules.Type.STOCKOUT_SOON,
                new AlertPreferenceResponse("STOCKOUT_SOON", true, false, Map.of("days", 14))));
        when(queries.insertIfAbsent(eq(storeId), any())).thenReturn(true);

        assertThat(evaluation.evaluate(storeId, "Repuestos Sur", null))
                .singleElement().extracting(AlertRules.Candidate::type).isEqualTo(AlertRules.Type.STOCKOUT_SOON);
    }

    // ---- Tarea nocturna ---------------------------------------------------------------------------------------

    @Test
    void jobContinuesWhenOneStoreFails() {
        StoreService storeService = mock(StoreService.class);
        StoreAlertEvaluation evaluationMock = mock(StoreAlertEvaluation.class);
        StoreResponse broken = new StoreResponse(UUID.randomUUID(), "Roto", "1", "roto@example.com", "ACTIVE");
        StoreResponse healthy = new StoreResponse(UUID.randomUUID(), "Sano", "2", "sano@example.com", "ACTIVE");
        when(storeService.published()).thenReturn(List.of(broken, healthy));
        when(evaluationMock.evaluate(eq(broken.id()), anyString(), anyString()))
                .thenThrow(new IllegalStateException("fallo"));

        int evaluated = new StoreAlertJob(storeService, evaluationMock).run();

        assertThat(evaluated).isEqualTo(1);
        verify(evaluationMock).evaluate(healthy.id(), "Sano", "sano@example.com");
    }

    // ---- Casos de uso -----------------------------------------------------------------------------------------

    @Test
    void updatingAnAlertOfAnotherStoreIsNotFound() {
        StoreAccess access = mock(StoreAccess.class);
        UUID alertId = UUID.randomUUID();
        when(queries.update(storeId, alertId, true, false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UpdateAlert(access, queries)
                .execute(storeId, alertId, EMAIL, new AlertUpdateRequest(true, null)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ALERT_NOT_FOUND");
        verify(access).requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_READ);
    }

    @Test
    void resolvingAnAlsoMarksItRead() {
        StoreAccess access = mock(StoreAccess.class);
        UUID alertId = UUID.randomUUID();
        AlertResponse updated = new AlertResponse(alertId, "STOCKOUT", "CRITICAL", "t", "m", Map.of(), null, null,
                null);
        when(queries.update(storeId, alertId, true, true)).thenReturn(Optional.of(updated));

        assertThat(new UpdateAlert(access, queries)
                .execute(storeId, alertId, EMAIL, new AlertUpdateRequest(null, true))).isSameAs(updated);
    }

    @Test
    void updateWithoutTrueFlagIsRejected() {
        StoreAccess access = mock(StoreAccess.class);

        assertThatThrownBy(() -> new UpdateAlert(access, queries)
                .execute(storeId, UUID.randomUUID(), EMAIL, new AlertUpdateRequest(false, null)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("INVALID_ALERT_UPDATE");
        verify(queries, never()).update(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void readingAlertsRequiresStoreReadAndValidatesStatus() {
        StoreAccess access = mock(StoreAccess.class);
        ListAlerts listAlerts = new ListAlerts(access, queries);

        listAlerts.list(storeId, EMAIL, null);
        listAlerts.list(storeId, EMAIL, "unread");
        listAlerts.list(storeId, EMAIL, "ALL");
        verify(queries).list(storeId, true, false);
        verify(queries).list(storeId, true, true);
        verify(queries).list(storeId, false, false);
        verify(access, org.mockito.Mockito.times(3)).requirePermission(EMAIL, storeId,
                StorePermissionCodes.STORE_READ);

        assertThatThrownBy(() -> listAlerts.list(storeId, EMAIL, "closed"))
                .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("INVALID_ALERT_STATUS");
    }

    @Test
    void deniedPermissionStopsBeforeQuerying() {
        StoreAccess access = mock(StoreAccess.class);
        doThrow(new AccessDeniedException("no")).when(access)
                .requirePermission(EMAIL, storeId, StorePermissionCodes.ALERT_MANAGE);

        assertThatThrownBy(() -> new AlertPreferences(access, queries).get(storeId, EMAIL))
                .isInstanceOf(AccessDeniedException.class);
        verify(queries, never()).preferences(any());
    }
}
