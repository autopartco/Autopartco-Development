import { DATA_SOURCE } from '@/lib/api'
import type {
  CsvImportResult,
  DownloadedFile,
  LowStockItem,
  MovementQuery,
  MovementReason,
  Page,
  StockAdjustmentInput,
  StockMovement,
  StockTransfer,
  StockTransferSummary,
  TransferInput,
} from './types'

/** Inventario del almacén: ajustes, movimientos, stock bajo, transferencias y CSV (`storesuite/inventory`, `stockalerts`). */
export interface InventoryService {
  /** `GET /stores/{id}/inventory/movement-reasons` (`PRODUCT_READ`): solo motivos ajustables. */
  listReasons(storeId: string): Promise<MovementReason[]>
  /** `POST /stores/{id}/inventory/adjustments` (`INVENTORY_ADJUST`). */
  adjust(storeId: string, input: StockAdjustmentInput): Promise<StockMovement>
  /** `GET /stores/{id}/inventory/movements` (`PRODUCT_READ`), páginas desde 1. */
  listMovements(storeId: string, query: MovementQuery): Promise<Page<StockMovement>>
  /** `GET /stores/{id}/inventory/low-stock?branchId=` (`PRODUCT_READ`). */
  listLowStock(storeId: string, branchId?: string): Promise<LowStockItem[]>
  /** `PATCH /stores/{id}/branches/{branchId}/products/{productId}/min-stock` (`PRODUCT_UPDATE`); `null` vuelve al umbral por defecto. */
  setMinStock(storeId: string, branchId: string, productId: string, minStock: number | null): Promise<void>
  /**
   * `POST /stores/{id}/inventory/transfers` (`INVENTORY_TRANSFER`). Errores: 400 `SAME_BRANCH`, `DUPLICATE_PRODUCT`,
   * `INVALID_QUANTITY`; 404 `BRANCH_NOT_FOUND`, `LISTING_NOT_FOUND`; 409 `TRANSFER_EMPTY`, `INSUFFICIENT_STOCK`
   * (`details: [{productId, requested, available}]`), `TRANSFER_TOO_LARGE`, `TRANSFER_CONFLICT` (reintentar con la misma clave).
   */
  transfer(storeId: string, input: TransferInput): Promise<StockTransfer>
  /** `GET /stores/{id}/inventory/transfers` (`PRODUCT_READ`): las que salen o llegan a `branchId`. Páginas desde 1. */
  listTransfers(storeId: string, query: { branchId?: string; page: number; size: number }): Promise<Page<StockTransferSummary>>
  /** `GET /stores/{id}/inventory/transfers/{transferId}` (`PRODUCT_READ`). */
  getTransfer(storeId: string, transferId: string): Promise<StockTransfer>
  /** `GET /stores/{id}/inventory/export.csv?branchId=` (`PRODUCT_READ`): descarga `inventario-AAAA-MM-DD.csv`. */
  exportCsv(storeId: string, branchId?: string): Promise<DownloadedFile>
  /**
   * `POST /stores/{id}/inventory/import?dryRun=` (multipart `file`, hasta 2 MB y 5.000 filas; `PRODUCT_UPDATE` e
   * `INVENTORY_ADJUST`). Errores de archivo: 400 `FILE_REQUIRED`, `FILE_TOO_LARGE`, `EMPTY_FILE`, `INVALID_CSV`,
   * `INVALID_HEADER`, `TOO_MANY_ROWS`.
   */
  importCsv(storeId: string, file: File, dryRun: boolean): Promise<CsvImportResult>
}

let servicePromise: Promise<InventoryService> | undefined

export function getInventoryService(): Promise<InventoryService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-inventory-service').then((module) => module.createHttpInventoryService())
      : import('./mock/mock-inventory-service').then((module) => module.createMockInventoryService())
  return servicePromise
}
