/**
 * Inventario del almacén: ajustes con motivo ({@code INVENTORY_ADJUST}), transferencias entre sucursales
 * ({@code INVENTORY_TRANSFER}; listado y detalle con {@code PRODUCT_READ}), historial de movimientos y motivos
 * ({@code PRODUCT_READ}). Los cambios de stock pasan por {@code core.inventory.StockLedger}, que escribe el ledger
 * {@code inventory_movements}. Exportación e importación CSV: la
 * exportación incluye {@code stock_base}; la importación aplica el delta {@code stock − stock_base} sobre el stock
 * bloqueado (sin esa columna, stock absoluto) y bloquea los listados afectados en orden antes de escribir.
 */
package com.autopartco.backend.storesuite.inventory;
