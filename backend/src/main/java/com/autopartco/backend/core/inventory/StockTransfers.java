package com.autopartco.backend.core.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.StockLedger.StockChange;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Transferencia inmediata de existencias entre dos sucursales del mismo almacén ({@code stock_transfers} y
 * {@code stock_transfer_items}, solo inserción). Todo ocurre en la transacción del llamador: si algo falla no queda
 * ningún cambio.
 *
 * <p>No valida permisos ni que las sucursales estén activas y sean del almacén (lo hace el caso de uso con
 * {@code organization.BranchService}); sí filtra listados y productos por almacén.</p>
 *
 * <p>Orden: lee los listados de origen y destino, los bloquea ({@code FOR UPDATE}) en el orden del checkout
 * ({@code branch_id, product_id}), valida cantidades sobre el stock bloqueado, crea o reactiva los listados de destino
 * que falten (precio del origen, sin cupo de plan), escribe cabecera e ítems y aplica dos movimientos por ítem por
 * {@link StockLedger} ({@code TRANSFER_OUT}/{@code TRANSFER_IN}, {@code reference_type = TRANSFER_ITEM}).</p>
 */
@Service
@AllArgsConstructor
public class StockTransfers {
    public static final String MODE_ALL = "ALL";
    public static final String MODE_ITEMS = "ITEMS";
    /** Máximo de productos de una transferencia {@code ITEMS}. */
    public static final int MAX_ITEMS = 500;
    /** Máximo de productos de una transferencia {@code ALL} (límite de parámetros por sentencia). */
    public static final int MAX_ALL_ITEMS = 10_000;

    /** Serializa peticiones con la misma clave de idempotencia del almacén hasta el final de la transacción. */
    static final String IDEMPOTENCY_LOCK_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0)) IS NULL AS locked
            """;

    static final String FIND_BY_KEY_SQL = """
            SELECT id
            FROM stock_transfers
            WHERE store_id = :storeId AND idempotency_key = :idempotencyKey
            """;

    private static final String SOURCE_COLUMNS = """
            SELECT i.id, i.product_id, i.price, p.name AS product_name
            FROM inventory i
            JOIN branches b ON b.id = i.branch_id AND b.store_id = :storeId
            JOIN products p ON p.id = i.product_id AND p.store_id = :storeId AND p.deleted_at IS NULL
            WHERE i.branch_id = :branchId AND i.deleted_at IS NULL
            """;

    /** {@code ALL}: listados vivos con stock de la sucursal origen. */
    static final String SOURCES_WITH_STOCK_SQL = SOURCE_COLUMNS + """
              AND i.stock > 0
            ORDER BY i.product_id
            LIMIT :limit
            """;

    /** {@code ITEMS}: listados vivos de la sucursal origen para los productos pedidos. */
    static final String SOURCES_FOR_PRODUCTS_SQL = SOURCE_COLUMNS + """
              AND i.product_id IN (:productIds)
            """;

    /** Listados de destino de esos productos, también los borrados (se reactivan). */
    static final String DESTINATIONS_SQL = """
            SELECT id, product_id
            FROM inventory
            WHERE branch_id = :branchId AND product_id IN (:productIds)
            """;

    /** Mismo orden que el descuento del checkout y {@code StockService.LOCK_FOR_RETURN_SQL}. */
    static final String LOCK_SQL = """
            SELECT id, branch_id, product_id, stock, (deleted_at IS NOT NULL) AS deleted
            FROM inventory
            WHERE id IN (:inventoryIds)
            ORDER BY branch_id, product_id
            FOR UPDATE
            """;

    static final String REACTIVATE_LISTING_SQL = """
            UPDATE inventory
            SET deleted_at = NULL, price = :price, updated_at = now(), version = coalesce(version, 0) + 1
            WHERE id = :inventoryId AND deleted_at IS NOT NULL
            """;

    /** Stock 0: las unidades llegan por el ledger ({@code TRANSFER_IN}). */
    static final String INSERT_LISTING_SQL = """
            INSERT INTO inventory (branch_id, product_id, stock, price, version, created_at, updated_at)
            VALUES (:branchId, :productId, 0, :price, 0, now(), now())
            ON CONFLICT (branch_id, product_id) DO NOTHING
            RETURNING id
            """;

    static final String INSERT_TRANSFER_SQL = """
            INSERT INTO stock_transfers
                (id, store_id, from_branch_id, to_branch_id, mode, total_units, item_count, notes,
                 actor_account_id, idempotency_key)
            VALUES
                (:id, :storeId, :fromBranchId, :toBranchId, :mode, :totalUnits, :itemCount, :notes,
                 CAST(:actorAccountId AS uuid), :idempotencyKey)
            """;

    static final String INSERT_ITEM_SQL = """
            INSERT INTO stock_transfer_items (id, transfer_id, product_id, from_inventory_id, to_inventory_id, quantity)
            VALUES (:id, :transferId, :productId, :fromInventoryId, :toInventoryId, :quantity)
            """;

    private static final String SUMMARY_FROM = """
            FROM stock_transfers t
            JOIN branches fb ON fb.id = t.from_branch_id
            JOIN branches tb ON tb.id = t.to_branch_id
            LEFT JOIN accounts a ON a.id = t.actor_account_id
            LEFT JOIN persons pe ON pe.id = a.id_person
            WHERE t.store_id = :storeId
            """;

    private static final String SUMMARY_COLUMNS = """
            SELECT t.id, t.from_branch_id, fb.name AS from_branch_name, t.to_branch_id, tb.name AS to_branch_name,
                   t.mode, t.total_units, t.item_count, t.notes, t.created_at,
                   nullif(trim(concat_ws(' ', pe.name, pe.surname)), '') AS actor_name
            """;

    private static final String BRANCH_FILTER = " AND (t.from_branch_id = :branchId OR t.to_branch_id = :branchId)";

    static final String COUNT_SQL = "SELECT count(*) AS total " + SUMMARY_FROM;
    static final String COUNT_BY_BRANCH_SQL = COUNT_SQL + BRANCH_FILTER;
    private static final String PAGE_SUFFIX = " ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset";
    static final String PAGE_SQL = SUMMARY_COLUMNS + SUMMARY_FROM + PAGE_SUFFIX;
    static final String PAGE_BY_BRANCH_SQL = SUMMARY_COLUMNS + SUMMARY_FROM + BRANCH_FILTER + PAGE_SUFFIX;
    static final String HEADER_SQL = SUMMARY_COLUMNS + SUMMARY_FROM + " AND t.id = :transferId";

    /** Stock resultante de cada lado: el {@code new_stock} de los movimientos del ítem. */
    static final String ITEMS_SQL = """
            SELECT it.product_id, p.name AS product_name, it.quantity,
                   mo.new_stock AS from_stock, mi.new_stock AS to_stock
            FROM stock_transfer_items it
            JOIN products p ON p.id = it.product_id
            LEFT JOIN inventory_movements mo ON mo.reference_type = 'TRANSFER_ITEM' AND mo.reference_id = it.id
                                            AND mo.reason_code = 'TRANSFER_OUT'
            LEFT JOIN inventory_movements mi ON mi.reference_type = 'TRANSFER_ITEM' AND mi.reference_id = it.id
                                            AND mi.reason_code = 'TRANSFER_IN'
            WHERE it.transfer_id = :transferId
            ORDER BY p.name, it.product_id
            """;

    private final JdbcClient jdbcClient;
    private final StockLedger stockLedger;

    /** Producto y cantidad de una transferencia {@code ITEMS}. */
    public record TransferLine(UUID productId, int quantity) {
    }

    /**
     * Transferencia pedida. {@code items} solo en {@code ITEMS} (1..{@value #MAX_ITEMS}, sin productos repetidos);
     * {@code notes} e {@code idempotencyKey} opcionales (ya normalizados por el llamador).
     */
    public record TransferCommand(
            UUID storeId,
            UUID fromBranchId,
            UUID toBranchId,
            String mode,
            List<TransferLine> items,
            String notes,
            String idempotencyKey
    ) {
    }

    /** {@code fromStock}/{@code toStock}: existencias de origen y destino tras la transferencia. */
    public record TransferItemView(UUID productId, String productName, int quantity, int fromStock, int toStock) {
    }

    public record TransferView(
            UUID id,
            UUID fromBranchId,
            String fromBranchName,
            UUID toBranchId,
            String toBranchName,
            String mode,
            int totalUnits,
            int itemCount,
            String notes,
            String actorName,
            OffsetDateTime createdAt,
            List<TransferItemView> items
    ) {
    }

    public record TransferSummaryView(
            UUID id,
            UUID fromBranchId,
            String fromBranchName,
            UUID toBranchId,
            String toBranchName,
            String mode,
            int totalUnits,
            int itemCount,
            String notes,
            String actorName,
            OffsetDateTime createdAt
    ) {
    }

    /** {@code created = false}: la clave de idempotencia ya existía y se devuelve la transferencia original. */
    public record TransferResult(TransferView transfer, boolean created) {
    }

    private record Source(UUID inventoryId, UUID productId, BigDecimal price, String productName) {
    }

    private record Locked(UUID inventoryId, int stock, boolean deleted) {
    }

    /**
     * Ejecuta la transferencia. Errores: {@code 409 TRANSFER_EMPTY} (ALL sin stock), {@code 404 LISTING_NOT_FOUND}
     * (ITEMS sin listado vivo en origen, {@code details: [{productId}]}), {@code 409 INSUFFICIENT_STOCK}
     * ({@code details: [{productId, requested, available}]}), {@code 409 TRANSFER_TOO_LARGE}.
     *
     * @param actorAccountId cuenta que transfiere (cabecera y movimientos)
     */
    @Transactional
    public TransferResult transfer(UUID actorAccountId, TransferCommand command) {
        validate(command);
        UUID storeId = command.storeId();
        if (command.idempotencyKey() != null) {
            jdbcClient.sql(IDEMPOTENCY_LOCK_SQL)
                    .param("lockKey", "stock_transfers:" + storeId + ":" + command.idempotencyKey())
                    .query((result, row) -> Boolean.TRUE)
                    .list();
            Optional<UUID> existing = findIdByKey(storeId, command.idempotencyKey());
            if (existing.isPresent()) {
                return new TransferResult(find(storeId, existing.get()).orElseThrow(), false);
            }
        }

        boolean all = MODE_ALL.equals(command.mode());
        List<Source> sources = all ? sourcesWithStock(command) : sourcesForProducts(command);
        if (sources.isEmpty()) {
            throw empty();
        }
        Map<UUID, UUID> destinations = destinations(command.toBranchId(),
                sources.stream().map(Source::productId).toList());

        List<UUID> toLock = new ArrayList<>(sources.stream().map(Source::inventoryId).toList());
        toLock.addAll(destinations.values());
        Map<UUID, Locked> locked = lock(toLock);

        // Cantidades sobre el stock bloqueado: nada se escribe antes de validar.
        Map<UUID, Integer> requested = all ? Map.of() : requestedByProduct(command.items());
        List<Source> moving = new ArrayList<>();
        Map<UUID, Integer> quantities = new HashMap<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        List<Map<String, Object>> insufficient = new ArrayList<>();
        for (Source source : sources) {
            Locked origin = locked.get(source.inventoryId());
            if (origin == null || origin.deleted()) {
                if (!all) {
                    missing.add(Map.of("productId", source.productId()));
                }
                continue;
            }
            int quantity = all ? origin.stock() : requested.get(source.productId());
            if (quantity <= 0) {
                continue;
            }
            if (quantity > origin.stock()) {
                insufficient.add(Map.of(
                        "productId", source.productId(),
                        "requested", quantity,
                        "available", Math.max(origin.stock(), 0)));
                continue;
            }
            moving.add(source);
            quantities.put(source.productId(), quantity);
        }
        if (!missing.isEmpty()) {
            throw listingNotFound(missing);
        }
        if (!insufficient.isEmpty()) {
            throw StockLedger.insufficientStock(insufficient);
        }
        if (moving.isEmpty()) {
            throw empty();
        }

        long totalUnits = 0;
        Map<UUID, Integer> destinationStock = new HashMap<>();
        Map<UUID, UUID> destinationIds = new HashMap<>();
        for (Source source : moving) {
            UUID destinationId = destinations.get(source.productId());
            Locked destination = destinationId == null ? null : locked.get(destinationId);
            if (destinationId != null && destination == null) {
                throw conflict();
            }
            int current = destination == null ? 0 : destination.stock();
            long target = (long) current + quantities.get(source.productId());
            if (target > Integer.MAX_VALUE) {
                throw new BadRequestException("INVALID_QUANTITY",
                        "La existencia resultante en la sucursal destino es demasiado grande.");
            }
            totalUnits += quantities.get(source.productId());
            destinationStock.put(source.productId(), current);
        }
        if (totalUnits > Integer.MAX_VALUE) {
            throw new BadRequestException("INVALID_QUANTITY", "La transferencia tiene demasiadas unidades.");
        }

        // Destino sin listado: se crea o se reactiva con el precio del origen (no consume cupo de PRODUCTS).
        for (Source source : moving) {
            UUID destinationId = destinations.get(source.productId());
            if (destinationId == null) {
                destinationId = createListing(command.toBranchId(), source);
            } else if (locked.get(destinationId).deleted()) {
                jdbcClient.sql(REACTIVATE_LISTING_SQL)
                        .param("price", source.price())
                        .param("inventoryId", destinationId)
                        .update();
            }
            destinationIds.put(source.productId(), destinationId);
        }

        UUID transferId = UUID.randomUUID();
        jdbcClient.sql(INSERT_TRANSFER_SQL)
                .param("id", transferId)
                .param("storeId", storeId)
                .param("fromBranchId", command.fromBranchId())
                .param("toBranchId", command.toBranchId())
                .param("mode", command.mode())
                .param("totalUnits", (int) totalUnits)
                .param("itemCount", moving.size())
                .param("notes", command.notes())
                .param("actorAccountId", actorAccountId == null ? null : actorAccountId.toString())
                .param("idempotencyKey", command.idempotencyKey())
                .update();

        List<StockChange> changes = new ArrayList<>();
        for (Source source : moving) {
            UUID itemId = UUID.randomUUID();
            int quantity = quantities.get(source.productId());
            UUID destinationId = destinationIds.get(source.productId());
            jdbcClient.sql(INSERT_ITEM_SQL)
                    .param("id", itemId)
                    .param("transferId", transferId)
                    .param("productId", source.productId())
                    .param("fromInventoryId", source.inventoryId())
                    .param("toInventoryId", destinationId)
                    .param("quantity", quantity)
                    .update();
            int fromStock = locked.get(source.inventoryId()).stock() - quantity;
            int toStock = destinationStock.get(source.productId()) + quantity;
            changes.add(new StockChange(source.inventoryId(), fromStock, StockLedger.REASON_TRANSFER_OUT,
                    StockLedger.REFERENCE_TRANSFER_ITEM, itemId, command.notes()));
            changes.add(new StockChange(destinationId, toStock, StockLedger.REASON_TRANSFER_IN,
                    StockLedger.REFERENCE_TRANSFER_ITEM, itemId, command.notes()));
        }
        stockLedger.apply(actorAccountId, StockLedger.SOURCE_USER, changes);

        // Se relee en la misma transacción: nombres de sucursal, actor y stock final de cada lado.
        TransferView view = find(storeId, transferId)
                .orElseThrow(() -> new IllegalStateException("transfer not readable: " + transferId));
        return new TransferResult(view, true);
    }

    /** Transferencia del almacén con sus ítems; vacío si no existe o es de otro almacén. */
    @Transactional(readOnly = true)
    public Optional<TransferView> find(UUID storeId, UUID transferId) {
        Optional<TransferSummaryView> header = jdbcClient.sql(HEADER_SQL)
                .param("storeId", storeId)
                .param("transferId", transferId)
                .query((result, row) -> toSummary(result))
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<TransferItemView> items = jdbcClient.sql(ITEMS_SQL)
                .param("transferId", transferId)
                .query((result, row) -> new TransferItemView(
                        result.getObject("product_id", UUID.class),
                        result.getString("product_name"),
                        result.getInt("quantity"),
                        result.getInt("from_stock"),
                        result.getInt("to_stock")))
                .list();
        TransferSummaryView summary = header.get();
        return Optional.of(new TransferView(summary.id(), summary.fromBranchId(), summary.fromBranchName(),
                summary.toBranchId(), summary.toBranchName(), summary.mode(), summary.totalUnits(),
                summary.itemCount(), summary.notes(), summary.actorName(), summary.createdAt(), List.copyOf(items)));
    }

    /** Id de la transferencia del almacén con esa clave de idempotencia. */
    @Transactional(readOnly = true)
    public Optional<UUID> findIdByKey(UUID storeId, String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return jdbcClient.sql(FIND_BY_KEY_SQL)
                .param("storeId", storeId)
                .param("idempotencyKey", idempotencyKey)
                .query((result, row) -> result.getObject("id", UUID.class))
                .optional();
    }

    /** Transferencias del almacén (con {@code branchId}: las que salen o llegan a esa sucursal), más recientes primero. */
    @Transactional(readOnly = true)
    public PageResponse<TransferSummaryView> list(UUID storeId, UUID branchId, int page, int size) {
        boolean byBranch = branchId != null;
        JdbcClient.StatementSpec count = jdbcClient.sql(byBranch ? COUNT_BY_BRANCH_SQL : COUNT_SQL)
                .param("storeId", storeId);
        if (byBranch) {
            count = count.param("branchId", branchId);
        }
        long total = count.query((result, row) -> result.getLong("total")).list().stream()
                .findFirst().orElse(0L);
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        JdbcClient.StatementSpec select = jdbcClient.sql(byBranch ? PAGE_BY_BRANCH_SQL : PAGE_SQL)
                .param("storeId", storeId);
        if (byBranch) {
            select = select.param("branchId", branchId);
        }
        List<TransferSummaryView> items = select
                .param("limit", size)
                .param("offset", (long) (page - 1) * size)
                .query((result, row) -> toSummary(result))
                .list();
        return PageResponse.of(items, page, size, total);
    }

    private static void validate(TransferCommand command) {
        if (command.storeId() == null || command.fromBranchId() == null || command.toBranchId() == null) {
            throw new IllegalArgumentException("storeId, fromBranchId and toBranchId are required");
        }
        if (command.fromBranchId().equals(command.toBranchId())) {
            throw new BadRequestException("SAME_BRANCH", "La sucursal de origen y la de destino deben ser distintas.");
        }
        if (MODE_ALL.equals(command.mode())) {
            if (command.items() != null && !command.items().isEmpty()) {
                throw new BadRequestException("VALIDATION_ERROR",
                        "Una transferencia de todo el inventario no lleva productos.");
            }
            return;
        }
        if (!MODE_ITEMS.equals(command.mode())) {
            throw new BadRequestException("VALIDATION_ERROR", "El modo debe ser ALL o ITEMS.");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new BadRequestException("VALIDATION_ERROR", "Indica al menos un producto a transferir.");
        }
        if (command.items().size() > MAX_ITEMS) {
            throw new BadRequestException("VALIDATION_ERROR",
                    "Una transferencia admite como mucho " + MAX_ITEMS + " productos.");
        }
        Set<UUID> seen = new HashSet<>();
        for (TransferLine line : command.items()) {
            if (line == null || line.productId() == null || line.quantity() < 1) {
                throw new BadRequestException("INVALID_QUANTITY", "Cada producto debe tener una cantidad mayor que 0.");
            }
            if (!seen.add(line.productId())) {
                throw new BadRequestException("DUPLICATE_PRODUCT", "Un producto aparece más de una vez.",
                        List.of(Map.of("productId", line.productId())));
            }
        }
    }

    private List<Source> sourcesWithStock(TransferCommand command) {
        List<Source> sources = jdbcClient.sql(SOURCES_WITH_STOCK_SQL)
                .param("storeId", command.storeId())
                .param("branchId", command.fromBranchId())
                .param("limit", MAX_ALL_ITEMS + 1)
                .query((result, row) -> toSource(result))
                .list();
        if (sources.size() > MAX_ALL_ITEMS) {
            throw new ConflictException("TRANSFER_TOO_LARGE", "La sucursal tiene más de " + MAX_ALL_ITEMS
                    + " productos con existencias; transfiérelos por partes.");
        }
        return sources;
    }

    private List<Source> sourcesForProducts(TransferCommand command) {
        List<UUID> productIds = command.items().stream().map(TransferLine::productId).toList();
        List<Source> sources = jdbcClient.sql(SOURCES_FOR_PRODUCTS_SQL)
                .param("storeId", command.storeId())
                .param("branchId", command.fromBranchId())
                .param("productIds", productIds)
                .query((result, row) -> toSource(result))
                .list();
        Set<UUID> found = new HashSet<>();
        sources.forEach(source -> found.add(source.productId()));
        List<Map<String, Object>> missing = productIds.stream()
                .filter(productId -> !found.contains(productId))
                .map(productId -> Map.<String, Object>of("productId", productId))
                .toList();
        if (!missing.isEmpty()) {
            throw listingNotFound(missing);
        }
        return sources;
    }

    private Map<UUID, UUID> destinations(UUID toBranchId, List<UUID> productIds) {
        Map<UUID, UUID> byProduct = new HashMap<>();
        jdbcClient.sql(DESTINATIONS_SQL)
                .param("branchId", toBranchId)
                .param("productIds", productIds)
                .query((result, row) -> Map.entry(
                        result.getObject("product_id", UUID.class),
                        result.getObject("id", UUID.class)))
                .list()
                .forEach(entry -> byProduct.put(entry.getKey(), entry.getValue()));
        return byProduct;
    }

    private Map<UUID, Locked> lock(List<UUID> inventoryIds) {
        Map<UUID, Locked> locked = new LinkedHashMap<>();
        jdbcClient.sql(LOCK_SQL)
                .param("inventoryIds", inventoryIds.stream().distinct().toList())
                .query((result, row) -> new Locked(
                        result.getObject("id", UUID.class),
                        result.getInt("stock"),
                        result.getBoolean("deleted")))
                .list()
                .forEach(listing -> locked.put(listing.inventoryId(), listing));
        return locked;
    }

    private UUID createListing(UUID toBranchId, Source source) {
        return jdbcClient.sql(INSERT_LISTING_SQL)
                .param("branchId", toBranchId)
                .param("productId", source.productId())
                .param("price", source.price())
                .query((result, row) -> result.getObject("id", UUID.class))
                .optional()
                // Otra operación creó el listado entre la lectura y el bloqueo.
                .orElseThrow(StockTransfers::conflict);
    }

    private static Map<UUID, Integer> requestedByProduct(List<TransferLine> items) {
        Map<UUID, Integer> requested = new HashMap<>();
        items.forEach(line -> requested.put(line.productId(), line.quantity()));
        return requested;
    }

    private static Source toSource(java.sql.ResultSet result) throws java.sql.SQLException {
        return new Source(
                result.getObject("id", UUID.class),
                result.getObject("product_id", UUID.class),
                result.getBigDecimal("price"),
                result.getString("product_name"));
    }

    private static TransferSummaryView toSummary(java.sql.ResultSet result) throws java.sql.SQLException {
        return new TransferSummaryView(
                result.getObject("id", UUID.class),
                result.getObject("from_branch_id", UUID.class),
                result.getString("from_branch_name"),
                result.getObject("to_branch_id", UUID.class),
                result.getString("to_branch_name"),
                result.getString("mode"),
                result.getInt("total_units"),
                result.getInt("item_count"),
                result.getString("notes"),
                result.getString("actor_name"),
                result.getObject("created_at", OffsetDateTime.class));
    }

    private static ConflictException conflict() {
        return new ConflictException("TRANSFER_CONFLICT",
                "El inventario cambió mientras se transfería. Intenta de nuevo.");
    }

    private static ConflictException empty() {
        return new ConflictException("TRANSFER_EMPTY", "La sucursal de origen no tiene existencias para transferir.");
    }

    private static NotFoundException listingNotFound(Object details) {
        return new NotFoundException("LISTING_NOT_FOUND",
                "Algún producto no está listado en la sucursal de origen.", details);
    }
}
