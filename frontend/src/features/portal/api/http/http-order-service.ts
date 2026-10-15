import type { OrderService } from '../order-service'
import type { OrderDetail, OrderSummary } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/orders/StoreOrderController`. */
export function createHttpOrderService(): OrderService {
  const orderPath = (storeId: string, saleId: string, suffix = '') =>
    storePath(storeId, `/orders/${encodeURIComponent(saleId)}${suffix}`)

  return {
    list(storeId, query = {}) {
      const params = new URLSearchParams()
      if (query.status) params.set('status', query.status)
      params.set('page', String(query.page ?? 0))
      params.set('size', String(query.size ?? 100))
      return portalRequest<OrderSummary[]>(`${storePath(storeId, '/orders')}?${params.toString()}`)
    },
    get: (storeId, saleId) => portalRequest<OrderDetail>(orderPath(storeId, saleId)),
    updateStatus: (storeId, saleId, status) =>
      portalRequest<OrderDetail>(orderPath(storeId, saleId, '/status'), { method: 'PATCH', body: { status } }),
    createShipment: (storeId, saleId, input) =>
      portalRequest<OrderDetail>(orderPath(storeId, saleId, '/shipment'), { method: 'POST', body: input }),
    updateShipment: (storeId, saleId, input) =>
      portalRequest<OrderDetail>(orderPath(storeId, saleId, '/shipment'), { method: 'PATCH', body: input }),
  }
}
