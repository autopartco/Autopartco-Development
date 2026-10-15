import { buildActivity, buildSalesAnalytics } from '../../lib/sales-analytics'
import type { SalesService } from '../sales-service'
import type { OrderSummary, ProductRow } from '../types'
import { portalRequest, storePath } from './portal-http'

const PAGE_SIZE = 100
const MAX_PAGES = 5

/** Pedidos más recientes primero, hasta cubrir `since` (o `MAX_PAGES` páginas). */
async function recentOrders(storeId: string, since: Date): Promise<OrderSummary[]> {
  const all: OrderSummary[] = []
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const rows = await portalRequest<OrderSummary[]>(storePath(storeId, `/orders?page=${page}&size=${PAGE_SIZE}`))
    all.push(...rows)
    if (rows.length < PAGE_SIZE || rows.some((row) => new Date(row.createdAt) < since)) break
  }
  return all
}

/** Sin `PRODUCT_READ` el stock bajo simplemente no aparece (no es un error de la pantalla). */
async function productsOrEmpty(storeId: string): Promise<ProductRow[]> {
  try {
    return await portalRequest<ProductRow[]>(storePath(storeId, '/products'))
  } catch {
    return []
  }
}

/**
 * Derivado en el cliente: el backend aún no tiene `analytics/sales` ni `activity`
 * (ver `sales-service.ts`). Solo agrega lo que devuelve el servidor, no decide nada.
 */
export function createHttpSalesService(): SalesService {
  return {
    async sales(storeId, days) {
      const since = new Date(Date.now() - days * 2 * 86_400_000)
      return buildSalesAnalytics(await recentOrders(storeId, since), days)
    },
    async activity(storeId) {
      const [orders, products] = await Promise.all([
        portalRequest<OrderSummary[]>(storePath(storeId, `/orders?page=0&size=${PAGE_SIZE}`)),
        productsOrEmpty(storeId),
      ])
      return buildActivity(orders, products)
    },
  }
}
