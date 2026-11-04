import { apiRequest } from '@/lib/api'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'
import type { BuyerOrderDetailDto, BuyerOrderSummaryDto, OrdersService, PageDto } from '../types'

/** `MyOrdersController`: `GET /me/orders` y `GET /me/orders/{orderId}` (solo del titular de la sesión). */
export function createHttpOrdersService(): OrdersService {
  return {
    simulated: false,
    async list(page, size) {
      const token = await requireAccessToken()
      return apiRequest<PageDto<BuyerOrderSummaryDto>>(`/me/orders?page=${page}&size=${size}`, { token })
    },
    async get(orderId) {
      const token = await requireAccessToken()
      return apiRequest<BuyerOrderDetailDto>(`/me/orders/${encodeURIComponent(orderId)}`, { token })
    },
  }
}
