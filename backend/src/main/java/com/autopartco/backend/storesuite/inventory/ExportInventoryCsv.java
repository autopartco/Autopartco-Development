package com.autopartco.backend.storesuite.inventory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.InventoryQueries.ExportRow;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Exporta el inventario del almacén (o de una sucursal) como CSV UTF-8 con BOM y {@code ;}. Columnas:
 * {@code sku;referencia;nombre;sucursal;stock;stock_base;precio;stock_minimo;estado}
 * ({@code stock_base} = stock al exportar, base del delta al importar). Requiere {@code PRODUCT_READ}. Las celdas
 * de texto que empiezan por {@code = + - @} se neutralizan. El precio va sin separador de miles, con coma decimal solo
 * si tiene centavos; {@code stock_minimo} vacío = sin política (umbral por defecto).
 */
@Service
@AllArgsConstructor
public class ExportInventoryCsv {
    static final String HEADER = "sku;referencia;nombre;sucursal;stock;stock_base;precio;stock_minimo;estado";

    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final BranchService branchService;
    private final InventoryQueries inventoryQueries;

    @Transactional(readOnly = true)
    public byte[] execute(UUID storeId, String email, UUID branchId) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        storeService.requireExists(storeId);
        if (branchId != null) {
            branchService.requireActiveInStore(storeId, branchId);
        }
        return render(inventoryQueries.exportRows(storeId, branchId));
    }

    static byte[] render(List<ExportRow> rows) {
        StringBuilder csv = new StringBuilder(CsvCodec.BOM).append(HEADER).append("\r\n");
        for (ExportRow row : rows) {
            csv.append(CsvCodec.cell(row.sku())).append(CsvCodec.SEPARATOR)
                    .append(CsvCodec.cell(row.partNumber())).append(CsvCodec.SEPARATOR)
                    .append(CsvCodec.cell(row.productName())).append(CsvCodec.SEPARATOR)
                    .append(CsvCodec.cell(row.branchName())).append(CsvCodec.SEPARATOR)
                    .append(row.stock()).append(CsvCodec.SEPARATOR)
                    .append(row.stock()).append(CsvCodec.SEPARATOR)
                    .append(price(row.price())).append(CsvCodec.SEPARATOR)
                    .append(row.minStock() == null ? "" : row.minStock().toString()).append(CsvCodec.SEPARATOR)
                    .append(CsvCodec.cell(row.productState()))
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String price(BigDecimal price) {
        if (price == null) {
            return "";
        }
        BigDecimal normalized = price.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString().replace('.', ',');
    }
}
