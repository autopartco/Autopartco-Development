import { DATA_SOURCE } from '@/lib/api'
import type { OrderDetail, OrderStatusTarget, OrderSummary, ShipmentCreateInput, ShipmentStatusInput } from './types'

/** Pedidos (ventas) del almacén y sus envíos. */
export interface OrderService {
  /** `GET /stores/{id}/orders?status&page&size` (la página empieza en 0 y la respuesta es un arreglo). */
  list(storeId: string, query?: { status?: string; page?: number; size?: number }): Promise<OrderSummary[]>
  /** `GET /stores/{id}/orders/{saleId}`. */
  get(storeId: string, saleId: string): Promise<OrderDetail>
  /** `PATCH /stores/{id}/orders/{saleId}/status`. */
  updateStatus(storeId: string, saleId: string, status: OrderStatusTarget): Promise<OrderDetail>
  /** `POST /stores/{id}/orders/{saleId}/shipment` (solo con el pedido en preparación). */
  createShipment(storeId: string, saleId: string, input: ShipmentCreateInput): Promise<OrderDetail>
  /** `PATCH /stores/{id}/orders/{saleId}/shipment`. */
  updateShipment(storeId: string, saleId: string, input: ShipmentStatusInput): Promise<OrderDetail>
}

let servicePromise: Promise<OrderService> | undefined

export function getOrderService(): Promise<OrderService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-order-service').then((module) => module.createHttpOrderService())
      : import('./mock/mock-order-service').then((module) => module.createMockOrderService())
  return servicePromise
}
