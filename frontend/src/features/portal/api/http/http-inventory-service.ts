import { ApiError, apiBlob } from '@/lib/api'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import { setSession } from '@/features/auth/session-store'
import type { InventoryService } from '../inventory-service'
import type { CsvImportResult, LowStockItem, MovementReason, Page, StockMovement, StockTransfer, StockTransferSummary } from '../types'
import { portalRequest, storePath } from './portal-http'
import { csvFilename } from '../../lib/inventory'

const id = encodeURIComponent

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) if (value !== undefined && value !== '') search.set(key, String(value))
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** `storesuite/inventory/InventoryController` y `storesuite/stockalerts`. */
export function createHttpInventoryService(): InventoryService {
  return {
    listReasons: (storeId) => portalRequest<MovementReason[]>(storePath(storeId, '/inventory/movement-reasons')),
    adjust: (storeId, input) => portalRequest<StockMovement>(storePath(storeId, '/inventory/adjustments'), { method: 'POST', body: input }),
    listMovements: (storeId, filters) =>
      portalRequest<Page<StockMovement>>(storePath(storeId, `/inventory/movements${query({ ...filters })}`)),
    listLowStock: (storeId, branchId) =>
      portalRequest<LowStockItem[]>(storePath(storeId, `/inventory/low-stock${query({ branchId })}`)),
    async setMinStock(storeId, branchId, productId, minStock) {
      await portalRequest(storePath(storeId, `/branches/${id(branchId)}/products/${id(productId)}/min-stock`), {
        method: 'PATCH',
        body: { minStock },
      })
    },
    transfer: (storeId, input) => portalRequest<StockTransfer>(storePath(storeId, '/inventory/transfers'), { method: 'POST', body: input }),
    listTransfers: (storeId, filters) =>
      portalRequest<Page<StockTransferSummary>>(storePath(storeId, `/inventory/transfers${query({ ...filters })}`)),
    getTransfer: (storeId, transferId) => portalRequest<StockTransfer>(storePath(storeId, `/inventory/transfers/${id(transferId)}`)),
    async exportCsv(storeId, branchId) {
      try {
        const blob = await apiBlob(storePath(storeId, `/inventory/export.csv${query({ branchId })}`), { token: await getAccessToken() })
        return { blob, filename: csvFilename() }
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) setSession(undefined)
        throw error
      }
    },
    importCsv(storeId, file, dryRun) {
      const form = new FormData()
      form.append('file', file, file.name)
      return portalRequest<CsvImportResult>(storePath(storeId, `/inventory/import?dryRun=${dryRun}`), { method: 'POST', body: form })
    },
  }
}
