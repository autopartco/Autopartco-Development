package com.autopartco.backend.storesuite.inventory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;
import com.autopartco.backend.storesuite.stockalerts.LowStock;
import com.autopartco.backend.storesuite.stockalerts.LowStockResponse;
import com.autopartco.backend.storesuite.stockalerts.MinStockRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Inventario del almacén: ajustes, transferencias, historial de movimientos, motivos, stock bajo y stock mínimo. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}")
@AllArgsConstructor
public class InventoryController {
    private final AdjustStock adjustStock;
    private final ListMovements listMovements;
    private final LowStock lowStock;
    private final TransferStock transferStock;
    private final ListTransfers listTransfers;
    private final ExportInventoryCsv exportInventoryCsv;
    private final ImportInventoryCsv importInventoryCsv;

    /** CSV UTF-8 con BOM y {@code ;}; {@code branchId} opcional. */
    @GetMapping("/inventory/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        byte[] body = exportInventoryCsv.execute(storeId, authentication.getName(), branchId);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"inventario.csv\"")
                .body(body);
    }

    /** Importación multipart ({@code file} ≤ 2 MB, ≤ 5.000 filas); {@code dryRun=true} solo simula. */
    @PostMapping(value = "/inventory/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportInventoryCsv.Result importCsv(
            @PathVariable UUID storeId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            Authentication authentication
    ) {
        return importInventoryCsv.execute(storeId, authentication.getName(), file, dryRun);
    }

    @PostMapping("/inventory/adjustments")
    public ResponseEntity<MovementResponse> adjust(
            @PathVariable UUID storeId,
            @Valid @RequestBody StockAdjustmentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adjustStock.execute(storeId, authentication.getName(), request));
    }

    /** {@code page} desde 1 (convención de la API); {@code from}/{@code to} en ISO 8601 con zona. */
    @GetMapping("/inventory/movements")
    public PageResponse<MovementResponse> movements(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return listMovements.execute(storeId, authentication.getName(), branchId, productId, reasonCode, from, to,
                page, size);
    }

    /** 201 si se crea; 200 si la {@code idempotencyKey} ya existía (devuelve la transferencia original). */
    @PostMapping("/inventory/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @PathVariable UUID storeId,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication
    ) {
        TransferStock.Outcome outcome = transferStock.execute(storeId, authentication.getName(), request);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.transfer());
    }

    /** {@code page} desde 1 (convención de la API); {@code branchId}: transferencias que salen o llegan a ella. */
    @GetMapping("/inventory/transfers")
    public PageResponse<TransferSummary> transfers(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return listTransfers.list(storeId, authentication.getName(), branchId, page, size);
    }

    @GetMapping("/inventory/transfers/{transferId}")
    public TransferResponse transferDetail(
            @PathVariable UUID storeId,
            @PathVariable UUID transferId,
            Authentication authentication
    ) {
        return listTransfers.get(storeId, authentication.getName(), transferId);
    }

    @GetMapping("/inventory/movement-reasons")
    public List<MovementReasonResponse> reasons(@PathVariable UUID storeId, Authentication authentication) {
        return listMovements.reasons(storeId, authentication.getName());
    }

    @GetMapping("/inventory/low-stock")
    public List<LowStockResponse> lowStock(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return lowStock.list(storeId, authentication.getName(), branchId);
    }

    @PatchMapping("/branches/{branchId}/products/{productId}/min-stock")
    public ResponseEntity<Void> minStock(
            @PathVariable UUID storeId,
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody MinStockRequest request,
            Authentication authentication
    ) {
        lowStock.setMinStock(storeId, branchId, productId, authentication.getName(), request.minStock());
        return ResponseEntity.noContent().build();
    }
}
