package com.autopartco.backend.storesuite.inventory;

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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.InventoryQueries.ExportRow;
import com.autopartco.backend.core.inventory.InventoryQueries.ImportCandidate;
import com.autopartco.backend.core.inventory.ReorderPolicies;
import com.autopartco.backend.core.inventory.StockLedger;
import com.autopartco.backend.core.inventory.StockLedger.StockChange;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;

@ExtendWith(MockitoExtension.class)
class InventoryCsvTest {
    private static final String EMAIL = "staff@example.com";
    private static final String HEADER = "sku;referencia;nombre;sucursal;stock;precio;stock_minimo;estado\n";

    @Mock private StoreAccess storeAccess;
    @Mock private StoreService storeService;
    @Mock private BranchService branchService;
    @Mock private InventoryQueries inventoryQueries;
    @Mock private StockService stockService;
    @Mock private StockLedger stockLedger;
    @Mock private ReorderPolicies reorderPolicies;
    @Mock private InventoryActor inventoryActor;

    private final UUID storeId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID inv1 = UUID.randomUUID();
    private final UUID inv2 = UUID.randomUUID();

    private ImportInventoryCsv importer() {
        return new ImportInventoryCsv(storeAccess, storeService, inventoryQueries, stockService, stockLedger,
                reorderPolicies, inventoryActor);
    }

    private ImportCandidate candidate(UUID inventory, String sku, String ref, String branch, int stock, String price,
            Integer min) {
        return new ImportCandidate(inventory, UUID.randomUUID(), sku, ref, branch, stock, new BigDecimal(price), min);
    }

    private void catalog() {
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of(
                candidate(inv1, "SKU-1", "REF-1", "Centro", 10, "1000.00", null),
                candidate(inv2, null, "REF-2", "Centro", 5, "2000.00", 4)));
    }

    private static MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "inv.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    // --- exportación ---

    @Test
    void exportHasBomSemicolonsAndNeutralizesFormulas() {
        byte[] bytes = ExportInventoryCsv.render(List.of(
                new ExportRow("=CMD()", "+REF", "Filtro; \"aceite\"", "-Centro", 3, new BigDecimal("1500.00"), null,
                        "ACTIVE"),
                new ExportRow("SKU-2", null, "@nombre", "Norte", 0, new BigDecimal("99.50"), 7, "INACTIVE")));
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        assertThat(csv).startsWith("﻿sku;referencia;nombre;sucursal;stock;stock_base;precio;stock_minimo;estado\r\n");
        assertThat(csv).contains("'=CMD();'+REF;\"Filtro; \"\"aceite\"\"\";'-Centro;3;3;1500;;ACTIVE\r\n");
        assertThat(csv).contains("SKU-2;;'@nombre;Norte;0;0;99,5;7;INACTIVE\r\n");
    }

    @Test
    void exportRequiresProductReadAndValidatesBranch() {
        doThrow(new AccessDeniedException("no")).when(storeAccess)
                .requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_READ);
        ExportInventoryCsv export = new ExportInventoryCsv(storeAccess, storeService, branchService, inventoryQueries);

        assertThatThrownBy(() -> export.execute(storeId, EMAIL, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(inventoryQueries, branchService);
    }

    // --- parser ---

    @Test
    void parserHandlesBomQuotesEmbeddedNewlinesAndBlankLines() {
        List<CsvCodec.Record> records = CsvCodec.parse("﻿a;b\r\n\r\n\"x;1\";\"line1\nline2\"\n\"q\"\"q\";z", 10);

        assertThat(records).hasSize(3);
        assertThat(records.get(1).cells()).containsExactly("x;1", "line1\nline2");
        assertThat(records.get(1).line()).isEqualTo(3);
        assertThat(records.get(2).cells()).containsExactly("q\"q", "z");
        assertThat(records.get(2).line()).isEqualTo(5);
        assertThatThrownBy(() -> CsvCodec.parse("a;\"b", 10)).isInstanceOf(BadRequestException.class);
    }

    // --- importación ---

    @Test
    void permissionsAreCheckedBeforeReadingTheFile() {
        doNothing().when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        doThrow(new AccessDeniedException("no")).when(storeAccess)
                .requirePermission(EMAIL, storeId, StorePermissionCodes.INVENTORY_ADJUST);

        assertThatThrownBy(() -> importer().execute(storeId, EMAIL, file(HEADER), false))
                .isInstanceOf(AccessDeniedException.class);

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        verifyNoInteractions(inventoryQueries, stockLedger, stockService, reorderPolicies);
    }

    @Test
    void importAppliesStockPriceAndMinStockThroughLedgerWithOneJob() {
        catalog();
        when(inventoryActor.bind(EMAIL)).thenReturn(actor);
        String csv = "﻿" + HEADER
                + "sku-1;;Filtro;CENTRO;12;1.500,00;2;ACTIVE\n"   // mezcla de separadores: error
                + "SKU-1;;Filtro;centro;12;1500,50;2;ACTIVE\n"
                + ";ref-2;Aceite;Centro;;;;\n"                      // sin cambios
                ;
        // La primera fila usa "1.500,00" (inválido) y debe reportarse; se prueba aparte.
        ImportInventoryCsv.Result invalid = importer().execute(storeId, EMAIL, file(csv), false);
        assertThat(invalid.errors()).extracting(ImportInventoryCsv.RowError::code).containsExactly("INVALID_PRICE");
        assertThat(invalid.errors().getFirst().line()).isEqualTo(2);
        assertThat(invalid.updated()).isZero();
        verifyNoInteractions(stockLedger, stockService, reorderPolicies);

        String ok = HEADER + "SKU-1;;Filtro;centro;12;1500,50;2;ACTIVE\n;ref-2;Aceite;Centro;;;;\n";
        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL, file(ok), false);

        assertThat(result.rows()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verify(stockService).setPrice(inv1, new BigDecimal("1500.50"));
        verify(reorderPolicies).setMinStock(storeId, inv1, 2, actor);
        ArgumentCaptor<List<StockChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).apply(eq(actor), eq(StockLedger.SOURCE_CSV), changes.capture());
        assertThat(changes.getValue()).singleElement().satisfies(change -> {
            assertThat(change.inventoryId()).isEqualTo(inv1);
            assertThat(change.newStock()).isEqualTo(12);
            assertThat(change.reasonCode()).isEqualTo("CSV_IMPORT");
            assertThat(change.referenceType()).isEqualTo("IMPORT_JOB");
            assertThat(change.referenceId()).isNotNull();
        });
        verify(stockService, never()).setPrice(eq(inv2), any());
    }

    @Test
    void rowErrorsApplyNothingEvenForValidRows() {
        catalog();
        String csv = HEADER
                + "SKU-1;;Filtro;Centro;20;;;\n"
                + "NOPE;;X;Centro;1;;;\n"
                + "SKU-1;;Filtro;Centro;3;;;\n"
                + ";;X;Centro;1;;;\n"
                + "SKU-1;;Filtro;Norte;1;;;\n"
                + ";REF-2;Aceite;Centro;-4;;;\n";

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL, file(csv), false);

        assertThat(result.rows()).isEqualTo(6);
        assertThat(result.updated()).isZero();
        assertThat(result.errors()).extracting(ImportInventoryCsv.RowError::line, ImportInventoryCsv.RowError::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3, "PRODUCT_NOT_FOUND"),
                        org.assertj.core.groups.Tuple.tuple(4, "DUPLICATE_ROW"),
                        org.assertj.core.groups.Tuple.tuple(5, "MISSING_PRODUCT_KEY"),
                        org.assertj.core.groups.Tuple.tuple(6, "PRODUCT_NOT_FOUND"),
                        org.assertj.core.groups.Tuple.tuple(7, "INVALID_STOCK"));
        verifyNoInteractions(stockLedger, stockService, reorderPolicies, inventoryActor);
    }

    @Test
    void dryRunReportsChangesButWritesNothing() {
        catalog();
        String csv = HEADER + "SKU-1;;Filtro;Centro;20;2500;5;\n;REF-2;Aceite;Centro;5;;4;\n";

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL, file(csv), true);

        assertThat(result.rows()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verifyNoInteractions(stockLedger, stockService, reorderPolicies, inventoryActor);
    }

    @Test
    void referenceMatchesWhenThereIsNoSkuAndNeutralizedApostropheIsStripped() {
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of(
                candidate(inv2, null, "-REF", "Centro", 5, "2000.00", 4)));
        String csv = HEADER + ";'-REF;Aceite;Centro;9;;;\n";

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL, file(csv), true);

        assertThat(result.errors()).isEmpty();
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void limitsAndBadFilesAreBadRequest() {
        assertBadRequest(new MockMultipartFile("file", "a.csv", "text/csv", new byte[0]), "FILE_REQUIRED");
        assertBadRequest(new MockMultipartFile("file", "a.csv", "text/csv",
                new byte[(int) ImportInventoryCsv.MAX_BYTES + 1]), "FILE_TOO_LARGE");

        StringBuilder rows = new StringBuilder(HEADER);
        for (int i = 0; i <= ImportInventoryCsv.MAX_ROWS; i++) {
            rows.append("SKU-").append(i).append(";;;Centro;1;;;\n");
        }
        assertBadRequest(file(rows.toString()), "TOO_MANY_ROWS");
        assertBadRequest(file("nombre;estado\nA;B\n"), "INVALID_HEADER");
        assertBadRequest(file("sku;sucursal\nA;B\n"), "INVALID_HEADER");
        verifyNoInteractions(stockLedger, stockService);
    }

    @Test
    void exactlyMaxRowsIsAccepted() {
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of());
        StringBuilder rows = new StringBuilder(HEADER);
        for (int i = 0; i < ImportInventoryCsv.MAX_ROWS; i++) {
            rows.append("SKU-").append(i).append(";;;Centro;1;;;\n");
        }

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL, file(rows.toString()), true);

        assertThat(result.rows()).isEqualTo(ImportInventoryCsv.MAX_ROWS);
        assertThat(result.errors()).hasSize(ImportInventoryCsv.MAX_ROWS);
    }

    @Test
    void windows1252FilesAreReadWhenNotUtf8() {
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of(
                candidate(inv1, "SKU-1", null, "Bogotá", 1, "10", null)));
        byte[] bytes = (HEADER + "SKU-1;;;Bogotá;5;;;\n").getBytes(java.nio.charset.Charset.forName("windows-1252"));

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL,
                new MockMultipartFile("file", "a.csv", "text/csv", bytes), true);

        assertThat(result.errors()).isEmpty();
        assertThat(result.updated()).isEqualTo(1);
    }

    // --- stock_base (delta) ---

    private static final String HEADER_BASE =
            "sku;referencia;nombre;sucursal;stock;stock_base;precio;stock_minimo;estado\n";

    private void lockedStock(UUID inventory, int stock) {
        when(stockLedger.lock(any())).thenReturn(List.of(
                new StockLedger.LockedListing(inventory, UUID.randomUUID(), UUID.randomUUID(), stock)));
    }

    @Test
    void saleBetweenExportAndImportIsNotUndone() {
        // exportado con 10; se vendieron 2 (actual 8); el usuario solo cambió el precio
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of(
                candidate(inv1, "SKU-1", null, "Centro", 8, "1000.00", null)));
        lockedStock(inv1, 8);
        when(inventoryActor.bind(EMAIL)).thenReturn(actor);

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL,
                file(HEADER_BASE + "SKU-1;;;Centro;10;10;1500;;\n"), false);

        assertThat(result.errors()).isEmpty();
        verify(stockService).setPrice(inv1, new BigDecimal("1500"));
        ArgumentCaptor<List<StockChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).apply(eq(actor), eq(StockLedger.SOURCE_CSV), changes.capture());
        assertThat(changes.getValue()).isEmpty();
    }

    @Test
    void positiveAndNegativeDeltasApplyOverLockedStock() {
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of(
                candidate(inv1, "SKU-1", null, "Centro", 8, "1000.00", null),
                candidate(inv2, "SKU-2", null, "Centro", 8, "1000.00", null)));
        when(stockLedger.lock(any())).thenReturn(List.of(
                new StockLedger.LockedListing(inv1, UUID.randomUUID(), UUID.randomUUID(), 8),
                new StockLedger.LockedListing(inv2, UUID.randomUUID(), UUID.randomUUID(), 8)));
        when(inventoryActor.bind(EMAIL)).thenReturn(actor);

        importer().execute(storeId, EMAIL,
                file(HEADER_BASE + "SKU-1;;;Centro;15;10;;;\nSKU-2;;;Centro;7;10;;;\n"), false);

        ArgumentCaptor<List<StockChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).apply(eq(actor), eq(StockLedger.SOURCE_CSV), changes.capture());
        assertThat(changes.getValue()).extracting(StockChange::inventoryId, StockChange::newStock)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(inv1, 13),
                        org.assertj.core.groups.Tuple.tuple(inv2, 5));
    }

    @Test
    void deltaThatWouldGoNegativeIsStockChanged() {
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of(
                candidate(inv1, "SKU-1", null, "Centro", 2, "1000.00", null)));

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL,
                file(HEADER_BASE + "SKU-1;;;Centro;0;10;;;\n"), false);

        assertThat(result.errors()).extracting(ImportInventoryCsv.RowError::code).containsExactly("STOCK_CHANGED");
        assertThat(result.updated()).isZero();
        verifyNoInteractions(stockLedger, stockService, reorderPolicies);
    }

    @Test
    void stockChangedAfterLockingAppliesNothing() {
        // lectura sin bloqueo da 12, pero al bloquear ya quedan 1: 1 + (0 - 5) < 0
        when(inventoryQueries.importCandidates(eq(storeId), any())).thenReturn(List.of(
                candidate(inv1, "SKU-1", null, "Centro", 12, "1000.00", null)));
        lockedStock(inv1, 1);
        when(inventoryActor.bind(EMAIL)).thenReturn(actor);

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL,
                file(HEADER_BASE + "SKU-1;;;Centro;0;5;2000;;\n"), false);

        assertThat(result.errors()).extracting(ImportInventoryCsv.RowError::code).containsExactly("STOCK_CHANGED");
        assertThat(result.updated()).isZero();
        verify(stockService, never()).setPrice(any(), any());
        verify(stockLedger, never()).apply(any(), any(), any());
    }

    @Test
    void invalidStockBaseIsInvalidStock() {
        catalog();

        ImportInventoryCsv.Result result = importer().execute(storeId, EMAIL,
                file(HEADER_BASE + "SKU-1;;;Centro;3;x;;;\n"), true);

        assertThat(result.errors()).extracting(ImportInventoryCsv.RowError::code).containsExactly("INVALID_STOCK");
    }

    @Test
    void withoutStockBaseColumnStockIsAbsolute() {
        catalog();
        when(inventoryActor.bind(EMAIL)).thenReturn(actor);

        importer().execute(storeId, EMAIL, file(HEADER + "SKU-1;;;Centro;3;;;\n"), false);

        ArgumentCaptor<List<StockChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).apply(eq(actor), eq(StockLedger.SOURCE_CSV), changes.capture());
        assertThat(changes.getValue()).singleElement().satisfies(c -> assertThat(c.newStock()).isEqualTo(3));
    }

    @Test
    void listingsAreLockedBeforeAnyWrite() {
        catalog();
        when(inventoryActor.bind(EMAIL)).thenReturn(actor);

        importer().execute(storeId, EMAIL, file(HEADER + "SKU-1;;;Centro;3;1500;2;\n"), false);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(stockLedger, stockService, reorderPolicies);
        order.verify(stockLedger).lock(any());
        order.verify(stockService).setPrice(inv1, new BigDecimal("1500"));
        order.verify(reorderPolicies).setMinStock(storeId, inv1, 2, actor);
        order.verify(stockLedger).apply(eq(actor), eq(StockLedger.SOURCE_CSV), any());
    }

    private void assertBadRequest(MockMultipartFile file, String code) {
        assertThatThrownBy(() -> importer().execute(storeId, EMAIL, file, false))
                .isInstanceOfSatisfying(BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
