package com.autopartco.backend.storesuite.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductCatalog.BulkSkip;
import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.storesuite.inventory.InventoryActor;

import jakarta.validation.Validation;

@ExtendWith(MockitoExtension.class)
class BulkProductActionTest {
    private static final String EMAIL = "staff@example.com";

    @Mock private StoreAccess storeAccess;
    @Mock private StoreService storeService;
    @Mock private BranchService branchService;
    @Mock private PlanEntitlements planEntitlements;
    @Mock private ProductCatalog productCatalog;
    @Mock private InventoryQueries inventoryQueries;
    @Mock private StockService stockService;
    @Mock private InventoryActor inventoryActor;

    private final UUID storeId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID p1 = new UUID(0, 1);
    private final UUID p2 = new UUID(0, 2);

    private BulkProductAction action() {
        return new BulkProductAction(storeAccess, storeService, branchService, planEntitlements, productCatalog,
                inventoryQueries, stockService, inventoryActor);
    }

    private static BulkProductRequest request(String action, UUID branchId, String mode, String value,
            UUID... ids) {
        return new BulkProductRequest(List.of(ids), action, branchId, mode,
                value == null ? null : new BigDecimal(value));
    }

    @Test
    void permissionPerActionIsCheckedFirst() {
        String[][] cases = {
                {"PUBLISH", StorePermissionCodes.PRODUCT_PUBLISH},
                {"UNPUBLISH", StorePermissionCodes.PRODUCT_PUBLISH},
                {"DELETE", StorePermissionCodes.PRODUCT_DELETE},
                {"PRICE", StorePermissionCodes.PRODUCT_UPDATE}};
        for (String[] c : cases) {
            doThrow(new AccessDeniedException("no")).when(storeAccess).requirePermission(EMAIL, storeId, c[1]);
            assertThatThrownBy(() -> action().execute(storeId, EMAIL, request(c[0], branchId, "AMOUNT", "5", p1)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(productCatalog, stockService, inventoryQueries);
    }

    @Test
    void publishReturnsBlockedOnesInSkipped() {
        when(planEntitlements.isSubscriptionActive(storeId)).thenReturn(true);
        when(productCatalog.publishMany(eq(storeId), any(), eq(true)))
                .thenReturn(List.of(new BulkSkip(p2, "NO_IMAGES")));

        BulkProductResult result = action().execute(storeId, EMAIL, request("PUBLISH", null, null, null, p1, p2, p1));

        assertThat(result.affected()).isEqualTo(1);
        assertThat(result.skipped()).containsExactly(new BulkProductResult.Skipped(p2, "NO_IMAGES"));
    }

    @Test
    void deleteIsAtomicAndForeignProductIsNotFoundBeforeDeletingAnything() {
        doNothing().when(productCatalog).requireOwnedForUpdate(storeId, p1);
        doThrow(new NotFoundException("PRODUCT_NOT_FOUND", "x")).when(productCatalog)
                .requireOwnedForUpdate(storeId, p2);

        assertThatThrownBy(() -> action().execute(storeId, EMAIL, request("DELETE", null, null, null, p1, p2)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PRODUCT_NOT_FOUND"));

        verify(productCatalog, never()).delete(any(), any());
    }

    @Test
    void deleteOver50IsBadRequest() {
        UUID[] ids = new UUID[51];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.randomUUID();
        }

        assertThatThrownBy(() -> action().execute(storeId, EMAIL, request("DELETE", null, null, null, ids)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BULK_DELETE_LIMIT"));
        verify(productCatalog, never()).delete(any(), any());
    }

    @Test
    void deleteRemovesEveryProduct() {
        BulkProductResult result = action().execute(storeId, EMAIL, request("DELETE", null, null, null, p1, p2));

        assertThat(result.affected()).isEqualTo(2);
        verify(productCatalog).delete(storeId, p1);
        verify(productCatalog).delete(storeId, p2);
    }

    @Test
    void percentPriceRoundsToWholePesos() {
        when(inventoryQueries.prices(eq(storeId), eq(branchId), any()))
                .thenReturn(Map.of(p1, new BigDecimal("10001.00"), p2, new BigDecimal("999.00")));

        BulkProductResult result = action().execute(storeId, EMAIL, request("PRICE", branchId, "PERCENT", "10", p1, p2));

        assertThat(result.affected()).isEqualTo(2);
        verify(stockService).updateListing(branchId, p1, new BigDecimal("11001"), null);
        verify(stockService).updateListing(branchId, p2, new BigDecimal("1099"), null);
        verify(inventoryActor).bind(EMAIL);
    }

    @Test
    void amountPriceSetsAbsoluteValue() {
        when(inventoryQueries.prices(eq(storeId), eq(branchId), any())).thenReturn(Map.of(p1, new BigDecimal("50")));

        action().execute(storeId, EMAIL, request("PRICE", branchId, "AMOUNT", "1234.56", p1));

        verify(stockService).updateListing(branchId, p1, new BigDecimal("1235"), null);
    }

    @Test
    void priceWithoutListingInBranchIsNotFoundAndWritesNothing() {
        when(inventoryQueries.prices(eq(storeId), eq(branchId), any())).thenReturn(Map.of(p1, BigDecimal.TEN));

        assertThatThrownBy(() -> action().execute(storeId, EMAIL, request("PRICE", branchId, "AMOUNT", "5", p1, p2)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LISTING_NOT_FOUND"));

        verify(stockService, never()).updateListing(any(), any(), any(), any());
    }

    @Test
    void percentOutOfRangeIs400() {
        when(inventoryQueries.prices(eq(storeId), eq(branchId), any())).thenReturn(Map.of(p1, BigDecimal.TEN));

        assertThatThrownBy(() -> action().execute(storeId, EMAIL, request("PRICE", branchId, "PERCENT", "-91", p1)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> action().execute(storeId, EMAIL, request("PRICE", branchId, "PERCENT", "501", p1)))
                .isInstanceOf(BadRequestException.class);
        verify(stockService, never()).updateListing(any(), any(), any(), any());
    }

    @Test
    void resultingPriceBelowOnePesoIs400WithoutPartialWrites() {
        when(inventoryQueries.prices(eq(storeId), eq(branchId), any()))
                .thenReturn(Map.of(p1, new BigDecimal("100"), p2, new BigDecimal("4")));

        assertThatThrownBy(() -> action().execute(storeId, EMAIL, request("PRICE", branchId, "PERCENT", "-90", p1, p2)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_PRICE"));
    }

    @Test
    void validationRequiresPriceFieldsAndLimitsSelection() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(request("PRICE", null, null, null, p1))).isNotEmpty();
        assertThat(validator.validate(request("PRICE", branchId, "AMOUNT", "9", p1))).isEmpty();
        assertThat(validator.validate(request("EXPLODE", null, null, null, p1))).isNotEmpty();
        assertThat(validator.validate(new BulkProductRequest(List.of(), "DELETE", null, null, null))).isNotEmpty();
        List<UUID> many = java.util.stream.IntStream.range(0, 501).mapToObj(i -> UUID.randomUUID()).toList();
        assertThat(validator.validate(new BulkProductRequest(many, "DELETE", null, null, null))).isNotEmpty();
        assertThat(validator.validate(new BulkProductRequest(many.subList(0, 500), "DELETE", null, null, null)))
                .isEmpty();
    }
}
