package com.autopartco.backend.storesuite.inventory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.InventoryQueries.ImportCandidate;
import com.autopartco.backend.core.inventory.ReorderPolicies;
import com.autopartco.backend.core.inventory.StockLedger;
import com.autopartco.backend.core.inventory.StockLedger.LockedListing;
import com.autopartco.backend.core.inventory.StockLedger.StockChange;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;

import lombok.AllArgsConstructor;

/**
 * Importa actualizaciones de inventario desde CSV ({@code ;}, UTF-8 con o sin BOM; si no es UTF-8 válido se lee como
 * Windows-1252). Requiere {@code PRODUCT_UPDATE} e {@code INVENTORY_ADJUST}, verificados antes de leer el archivo.
 *
 * <p>Cabecera (sin distinguir mayúsculas ni tildes): {@code sucursal}, {@code sku} y/o {@code referencia}, y al menos
 * una de {@code stock}, {@code precio}, {@code stock_minimo}; otras columnas ({@code nombre}, {@code estado}) se
 * ignoran. Si la fila trae {@code stock} y {@code stock_base} (la exportación lo incluye), se aplica el delta
 * {@code stock − stock_base} sobre el stock actual bloqueado para no deshacer ventas posteriores a la exportación;
 * un resultado negativo es {@code STOCK_CHANGED}. Sin {@code stock_base} el stock es absoluto. Solo actualiza listados existentes: coincide por SKU (o referencia si la fila no trae SKU) y nombre de
 * sucursal sin distinguir mayúsculas. Celda vacía = no cambiar. Precio: pesos con coma o punto decimal de 1-2 dígitos
 * ({@code 12500,50}); un punto o coma seguido de exactamente 3 dígitos es separador de miles.</p>
 *
 * <p>Con cualquier error de fila y {@code dryRun=false} no se aplica nada. El stock se escribe por el ledger
 * ({@code CSV_IMPORT}, {@code IMPORT_JOB} con un UUID por importación, fuente {@code CSV}).</p>
 */
@Service
@AllArgsConstructor
public class ImportInventoryCsv {
    public static final long MAX_BYTES = 2L * 1024 * 1024;
    public static final int MAX_ROWS = 5_000;

    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,10}([.,]\\d{1,2})?");
    private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}([.,]\\d{3})+");
    private static final String STOCK_CHANGED_MESSAGE =
            "El stock cambió desde la exportación y no alcanza; exporta de nuevo.";
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final InventoryQueries inventoryQueries;
    private final StockService stockService;
    private final StockLedger stockLedger;
    private final ReorderPolicies reorderPolicies;
    private final InventoryActor inventoryActor;

    public record RowError(int line, String code, String message) {
    }

    /** {@code updated}: filas con algún cambio (las que se aplicarían en simulación). */
    public record Result(int rows, int updated, List<RowError> errors) {
    }

    /** {@code stock}/{@code stockBase}: valores del CSV (no el stock final); {@code stockBase} solo si la fila lo trae. */
    private record Change(ImportCandidate target, Integer stock, Integer stockBase, BigDecimal price,
            Integer minStock, boolean minStockGiven, int line) {
    }

    @Transactional
    public Result execute(UUID storeId, String email, MultipartFile file, boolean dryRun) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.INVENTORY_ADJUST);
        storeService.requireExists(storeId);
        return process(storeId, email, read(file), dryRun);
    }

    Result process(UUID storeId, String email, String text, boolean dryRun) {
        List<CsvCodec.Record> records = CsvCodec.parse(text, MAX_ROWS + 1);
        if (records.isEmpty()) {
            throw new BadRequestException("EMPTY_FILE", "El archivo no tiene datos.");
        }
        Map<String, Integer> columns = columns(records.getFirst().cells());
        List<CsvCodec.Record> data = records.subList(1, records.size());

        Set<String> keys = new HashSet<>();
        for (CsvCodec.Record record : data) {
            String key = productKey(record, columns);
            if (key != null) {
                keys.add(lower(key.substring(2)));
            }
        }
        Map<String, List<ImportCandidate>> index = new HashMap<>();
        for (ImportCandidate candidate : inventoryQueries.importCandidates(storeId, keys)) {
            String branch = lower(candidate.branchName());
            if (candidate.sku() != null && !candidate.sku().isBlank()) {
                index.computeIfAbsent("s:" + lower(candidate.sku()) + "|" + branch, k -> new ArrayList<>())
                        .add(candidate);
            }
            if (candidate.partNumber() != null && !candidate.partNumber().isBlank()) {
                index.computeIfAbsent("r:" + lower(candidate.partNumber()) + "|" + branch, k -> new ArrayList<>())
                        .add(candidate);
            }
        }

        List<RowError> errors = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (CsvCodec.Record record : data) {
            int before = errors.size();
            String branch = value(record, columns, "sucursal");
            String key = productKey(record, columns);
            if (branch.isEmpty()) {
                errors.add(error(record, "MISSING_BRANCH", "Falta la sucursal."));
            }
            if (key == null) {
                errors.add(error(record, "MISSING_PRODUCT_KEY", "Falta el SKU o la referencia."));
            }
            Integer stock = parseInt(record, columns, "stock", "INVALID_STOCK",
                    "El stock debe ser un entero mayor o igual a 0.", errors);
            Integer minStock = parseInt(record, columns, "stock_minimo", "INVALID_MIN_STOCK",
                    "El stock mínimo debe ser un entero mayor o igual a 0.", errors);
            Integer stockBase = parseInt(record, columns, "stock_base", "INVALID_STOCK",
                    "El stock base debe ser un entero mayor o igual a 0.", errors);
            BigDecimal price = parsePrice(record, columns, errors);
            if (errors.size() > before || key == null || branch.isEmpty()) {
                continue;
            }
            List<ImportCandidate> matches = index.getOrDefault(key.substring(0, 2) + lower(key.substring(2)) + "|"
                    + lower(branch), List.of());
            if (matches.isEmpty()) {
                errors.add(error(record, "PRODUCT_NOT_FOUND",
                        "No hay un listado con ese SKU o referencia en esa sucursal."));
                continue;
            }
            if (matches.size() > 1) {
                errors.add(error(record, "AMBIGUOUS_PRODUCT",
                        "Más de un producto coincide con el SKU o la referencia; usa el SKU."));
                continue;
            }
            ImportCandidate target = matches.getFirst();
            if (!seen.add(target.inventoryId())) {
                errors.add(error(record, "DUPLICATE_ROW", "El producto ya aparece en otra fila para esa sucursal."));
                continue;
            }
            if (stock == null) {
                stockBase = null;
            }
            boolean stockChanges = false;
            if (stock != null) {
                int resolved = resolveStock(stock, stockBase, target.stock());
                if (resolved < 0) {
                    errors.add(error(record, "STOCK_CHANGED", STOCK_CHANGED_MESSAGE));
                    continue;
                }
                stockChanges = resolved != target.stock();
            }
            BigDecimal newPrice = price != null && price.compareTo(target.price()) != 0 ? price : null;
            boolean minGiven = minStock != null && !Objects.equals(minStock, target.minStock());
            if (stockChanges || newPrice != null || minGiven) {
                changes.add(new Change(target, stock, stockBase, newPrice, minStock, minGiven, record.line()));
            }
        }

        boolean apply = !dryRun && errors.isEmpty() && !changes.isEmpty();
        if (apply) {
            errors.addAll(apply(storeId, email, changes));
        }
        int updated = !dryRun && !errors.isEmpty() ? 0 : changes.size();
        return new Result(data.size(), updated, List.copyOf(errors));
    }

    /**
     * Bloquea todos los listados afectados en el orden del ledger antes de escribir nada y recalcula el stock final
     * con el valor bloqueado. Devuelve errores de fila ({@code STOCK_CHANGED}); si hay alguno no escribe nada.
     */
    private List<RowError> apply(UUID storeId, String email, List<Change> changes) {
        UUID actor = inventoryActor.bind(email);
        Map<UUID, Integer> current = new HashMap<>();
        for (LockedListing listing : stockLedger.lock(changes.stream()
                .map(change -> change.target().inventoryId()).toList())) {
            current.put(listing.inventoryId(), listing.stock());
        }
        List<RowError> conflicts = new ArrayList<>();
        List<StockChange> stock = new ArrayList<>();
        UUID jobId = UUID.randomUUID();
        for (Change change : changes) {
            if (change.stock() == null) {
                continue;
            }
            UUID id = change.target().inventoryId();
            int locked = current.getOrDefault(id, change.target().stock());
            int resolved = resolveStock(change.stock(), change.stockBase(), locked);
            if (resolved < 0) {
                conflicts.add(new RowError(change.line(), "STOCK_CHANGED", STOCK_CHANGED_MESSAGE));
            } else if (resolved != locked) {
                stock.add(new StockChange(id, resolved, StockLedger.REASON_CSV_IMPORT,
                        StockLedger.REFERENCE_IMPORT_JOB, jobId, null));
            }
        }
        if (!conflicts.isEmpty()) {
            return conflicts;
        }
        for (Change change : changes) {
            if (change.price() != null) {
                stockService.setPrice(change.target().inventoryId(), change.price());
            }
        }
        for (Change change : changes) {
            if (change.minStockGiven()) {
                reorderPolicies.setMinStock(storeId, change.target().inventoryId(), change.minStock(), actor);
            }
        }
        stockLedger.apply(actor, StockLedger.SOURCE_CSV, stock);
        return List.of();
    }

    /** Con {@code stockBase}: actual + (stock − base) (-1 = no alcanza); sin él: stock absoluto. */
    private static int resolveStock(int stock, Integer stockBase, int current) {
        if (stockBase == null) {
            return stock;
        }
        long next = (long) current + stock - stockBase;
        return next < 0 ? -1 : (int) Math.min(next, Integer.MAX_VALUE);
    }

    private static String read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "Adjunta un archivo CSV.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("FILE_TOO_LARGE", "El archivo supera el máximo de 2 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("INVALID_CSV", "No se pudo leer el archivo.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadRequestException("FILE_TOO_LARGE", "El archivo supera el máximo de 2 MB.");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    private static Map<String, Integer> columns(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(normalizeHeader(header.get(i)), i);
        }
        if (!columns.containsKey("sucursal") || !(columns.containsKey("sku") || columns.containsKey("referencia"))) {
            throw new BadRequestException("INVALID_HEADER",
                    "La cabecera debe incluir sucursal y sku o referencia.");
        }
        if (!(columns.containsKey("stock") || columns.containsKey("precio") || columns.containsKey("stock_minimo"))) {
            throw new BadRequestException("INVALID_HEADER",
                    "La cabecera debe incluir al menos una de: stock, precio, stock_minimo.");
        }
        return columns;
    }

    private static String normalizeHeader(String header) {
        String stripped = Normalizer.normalize(header.replace(CsvCodec.BOM, "").trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** Celda recortada; deshace el apóstrofo de neutralización de la exportación. */
    private static String value(CsvCodec.Record record, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= record.cells().size()) {
            return "";
        }
        String value = record.cells().get(index).trim();
        if (value.length() > 1 && value.charAt(0) == '\'' && "=+-@".indexOf(value.charAt(1)) >= 0) {
            value = value.substring(1);
        }
        return value;
    }

    /** {@code "s:<sku>"} o {@code "r:<referencia>"}, o nulo si la fila no trae ninguno. */
    private static String productKey(CsvCodec.Record record, Map<String, Integer> columns) {
        String sku = value(record, columns, "sku");
        if (!sku.isEmpty()) {
            return "s:" + sku;
        }
        String reference = value(record, columns, "referencia");
        return reference.isEmpty() ? null : "r:" + reference;
    }

    private static Integer parseInt(CsvCodec.Record record, Map<String, Integer> columns, String name, String code,
            String message, List<RowError> errors) {
        String cell = value(record, columns, name);
        if (cell.isEmpty()) {
            return null;
        }
        if (!DIGITS.matcher(cell).matches()) {
            errors.add(error(record, code, message));
            return null;
        }
        return Integer.parseInt(cell);
    }

    private static BigDecimal parsePrice(CsvCodec.Record record, Map<String, Integer> columns,
            List<RowError> errors) {
        String cell = value(record, columns, "precio");
        if (cell.isEmpty()) {
            return null;
        }
        String normalized = cell.replace(" ", "");
        if (THOUSANDS.matcher(normalized).matches()) {
            normalized = normalized.replace(".", "").replace(",", "");
        }
        if (DECIMAL.matcher(normalized).matches()) {
            BigDecimal price = new BigDecimal(normalized.replace(',', '.'));
            if (price.compareTo(new BigDecimal("0.01")) >= 0 && price.compareTo(MAX_PRICE) <= 0) {
                return price;
            }
        }
        errors.add(error(record, "INVALID_PRICE", "El precio debe ser un valor en pesos mayor que 0."));
        return null;
    }

    private static RowError error(CsvCodec.Record record, String code, String message) {
        return new RowError(record.line(), code, message);
    }

    private static String lower(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
