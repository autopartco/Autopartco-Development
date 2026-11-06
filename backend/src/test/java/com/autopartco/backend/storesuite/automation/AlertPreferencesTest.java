package com.autopartco.backend.storesuite.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.analytics.AlertRules;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;

class AlertPreferencesTest {
    private static final String EMAIL = "owner@example.com";

    private final UUID storeId = UUID.randomUUID();
    private final StoreAccess access = mock(StoreAccess.class);
    private final StoreAlertQueries queries = mock(StoreAlertQueries.class);
    private final AlertPreferences preferences = new AlertPreferences(access, queries);

    @Test
    void withoutRowsReturnsDefaultsForTheFiveTypes() {
        when(queries.preferences(storeId)).thenReturn(Map.of());

        List<AlertPreferenceResponse> result = preferences.get(storeId, EMAIL);

        assertThat(result).extracting(AlertPreferenceResponse::type).containsExactly(
                "SALES_DROP", "STOCKOUT_SOON", "STOCKOUT", "CUSTOMER_AT_RISK", "DEAD_STOCK");
        assertThat(result).allSatisfy(item -> {
            assertThat(item.inApp()).isTrue();
            assertThat(item.email()).isFalse();
            assertThat(item.threshold()).isNull();
        });
        verify(access).requirePermission(EMAIL, storeId, StorePermissionCodes.ALERT_MANAGE);
    }

    @Test
    void savedRowOverridesDefaultOnlyForItsType() {
        when(queries.preferences(storeId)).thenReturn(Map.of(AlertRules.Type.STOCKOUT,
                new AlertPreferenceResponse("STOCKOUT", false, true, null)));

        List<AlertPreferenceResponse> result = preferences.get(storeId, EMAIL);

        assertThat(result.get(2)).isEqualTo(new AlertPreferenceResponse("STOCKOUT", false, true, null));
        assertThat(result.get(0).inApp()).isTrue();
    }

    @Test
    void saveUpsertsEachTypeWithNormalizedThresholdAndDefaults() {
        when(queries.preferences(storeId)).thenReturn(Map.of());

        preferences.save(storeId, EMAIL, List.of(
                new AlertPreferenceRequest("sales_drop", null, true, Map.of("ratio", 0.5)),
                new AlertPreferenceRequest("STOCKOUT_SOON", false, null, Map.of("days", 10)),
                new AlertPreferenceRequest("DEAD_STOCK", true, true, Map.of())));

        verify(queries).savePreference(storeId, new AlertPreferenceResponse("SALES_DROP", true, true,
                Map.of("ratio", new BigDecimal("0.5"))));
        verify(queries).savePreference(storeId, new AlertPreferenceResponse("STOCKOUT_SOON", false, false,
                Map.of("days", 10)));
        verify(queries).savePreference(storeId, new AlertPreferenceResponse("DEAD_STOCK", true, true, null));
    }

    @Test
    void acceptsKnownThresholds() {
        assertThat(AlertThresholds.validate(AlertRules.Type.SALES_DROP, Map.of("ratio", 0.65)))
                .containsEntry("ratio", new BigDecimal("0.65"));
        assertThat(AlertThresholds.validate(AlertRules.Type.STOCKOUT_SOON, Map.of("days", 7)))
                .containsEntry("days", 7);
        assertThat(AlertThresholds.validate(AlertRules.Type.STOCKOUT_SOON, Map.of("days", 7.0)))
                .containsEntry("days", 7);
        assertThat(AlertThresholds.validate(AlertRules.Type.DEAD_STOCK, Map.of("days", 90)))
                .containsEntry("days", 90);
        assertThat(AlertThresholds.validate(AlertRules.Type.STOCKOUT, null)).isNull();
    }

    @Test
    void rejectsUnknownOutOfRangeOrWrongTypeThresholds() {
        assertInvalid(AlertRules.Type.SALES_DROP, Map.of("ratio", 1.5));
        assertInvalid(AlertRules.Type.SALES_DROP, Map.of("ratio", 0.05));
        assertInvalid(AlertRules.Type.SALES_DROP, Map.of("days", 7));
        assertInvalid(AlertRules.Type.SALES_DROP, Map.of("ratio", "0.5"));
        assertInvalid(AlertRules.Type.SALES_DROP, Map.of("ratio", 0.5, "extra", 1));
        assertInvalid(AlertRules.Type.STOCKOUT_SOON, Map.of("days", 0));
        assertInvalid(AlertRules.Type.STOCKOUT_SOON, Map.of("days", 7.5));
        assertInvalid(AlertRules.Type.DEAD_STOCK, Map.of("days", 10));
        assertInvalid(AlertRules.Type.DEAD_STOCK, Map.of("days", 400));
        assertInvalid(AlertRules.Type.STOCKOUT, Map.of("days", 7));
        assertInvalid(AlertRules.Type.CUSTOMER_AT_RISK, Map.of("ratio", 0.5));
    }

    @Test
    void saveRejectsInvalidThresholdBeforeWritingAnything() {
        assertThatThrownBy(() -> preferences.save(storeId, EMAIL, List.of(
                new AlertPreferenceRequest("DEAD_STOCK", true, false, Map.of("days", 90)),
                new AlertPreferenceRequest("SALES_DROP", true, false, Map.of("ratio", 3)))))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("INVALID_ALERT_THRESHOLD");
        org.mockito.Mockito.verify(queries, org.mockito.Mockito.never())
                .savePreference(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnknownAndRepeatedTypes() {
        assertThatThrownBy(() -> preferences.save(storeId, EMAIL,
                List.of(new AlertPreferenceRequest("PRICE_DROP", true, false, null))))
                .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("INVALID_ALERT_TYPE");
        assertThatThrownBy(() -> preferences.save(storeId, EMAIL, List.of(
                new AlertPreferenceRequest("STOCKOUT", true, false, null),
                new AlertPreferenceRequest("stockout", true, false, null))))
                .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("INVALID_ALERT_TYPE");
    }

    @Test
    void effectiveThresholdsUseStoredValuesOrDefaults() {
        AlertRules.Thresholds defaults = AlertThresholds.effective(Map.of());
        assertThat(defaults).isEqualTo(AlertRules.Thresholds.defaults());

        AlertRules.Thresholds custom = AlertThresholds.effective(Map.of(
                AlertRules.Type.SALES_DROP, new AlertPreferenceResponse("SALES_DROP", true, false,
                        Map.of("ratio", 0.5)),
                AlertRules.Type.STOCKOUT_SOON, new AlertPreferenceResponse("STOCKOUT_SOON", true, false,
                        Map.of("days", 10)),
                AlertRules.Type.DEAD_STOCK, new AlertPreferenceResponse("DEAD_STOCK", true, false,
                        Map.of("days", 120))));
        assertThat(custom.salesDropRatio()).isEqualByComparingTo("0.5");
        assertThat(custom.coverageDays()).isEqualTo(10.0);
        assertThat(custom.deadStockDays()).isEqualTo(120);
    }

    private static void assertInvalid(AlertRules.Type type, Map<String, Object> threshold) {
        assertThatThrownBy(() -> AlertThresholds.validate(type, threshold))
                .as("%s %s", type, threshold)
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("INVALID_ALERT_THRESHOLD");
    }
}
