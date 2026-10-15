import { DATA_SOURCE } from '@/lib/api'
import type { SalesAnalytics, SalesEvent, SalesRange } from './types'

/**
 * Analítica de ventas del almacén.
 *
 * Endpoints que FALTAN en el backend (hoy el servicio HTTP los deriva en el cliente de
 * `GET /stores/{id}/orders` y `GET /stores/{id}/products`):
 * - `GET /stores/{id}/analytics/sales?days=7|30|90` -> `SalesAnalytics` (agregación en servidor).
 * - `GET /stores/{id}/activity?limit=20` -> `SalesEvent[]` (pedidos, pagos y alertas de stock bajo).
 */
export interface SalesService {
  sales(storeId: string, days: SalesRange): Promise<SalesAnalytics>
  activity(storeId: string): Promise<SalesEvent[]>
}

let servicePromise: Promise<SalesService> | undefined

export function getSalesService(): Promise<SalesService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-sales-service').then((module) => module.createHttpSalesService())
      : import('./mock/mock-sales-service').then((module) => module.createMockSalesService())
  return servicePromise
}
