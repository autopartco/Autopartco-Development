import type { OrderService } from '../order-service'
import type { OrderDetail, OrderSummary, SaleInvoice } from '../types'
import { apiBlob } from '@/lib/api'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/orders/StoreOrderController`. */
export function createHttpOrderService(): OrderService {
  const orderPath = (storeId: string, saleId: string, suffix = '') =>
    storePath(storeId, `/orders/${encodeURIComponent(saleId)}${suffix}`)

  return {
    list(storeId, query = {}) {
      const params = new URLSearchParams()
      if (query.status) params.set('status', query.status)
      if (query.branchId) params.set('branchId', query.branchId)
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
    uploadInvoice(storeId, saleId, file) {
      const body = new FormData()
      body.append('file', file)
      return portalRequest<SaleInvoice>(orderPath(storeId, saleId, '/invoice'), { method: 'POST', body })
    },
    deleteInvoice: (storeId, saleId) => portalRequest<void>(orderPath(storeId, saleId, '/invoice'), { method: 'DELETE' }),
    downloadInvoice: async (storeId, saleId) => apiBlob(orderPath(storeId, saleId, '/invoice'), { token: await getAccessToken() }),
  }
}
