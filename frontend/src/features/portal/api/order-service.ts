import { DATA_SOURCE } from '@/lib/api'
import type { OrderDetail, OrderStatusTarget, OrderSummary, SaleInvoice, ShipmentCreateInput, ShipmentStatusInput } from './types'

/** Pedidos (ventas) del almacén y sus envíos. */
export interface OrderService {
  /** `GET /stores/{id}/orders?status&branchId&page&size` (la página empieza en 0 y la respuesta es un arreglo). 404 `BRANCH_NOT_FOUND`. */
  list(storeId: string, query?: { status?: string; branchId?: string; page?: number; size?: number }): Promise<OrderSummary[]>
  /** `GET /stores/{id}/orders/{saleId}`. */
  get(storeId: string, saleId: string): Promise<OrderDetail>
  /** `PATCH /stores/{id}/orders/{saleId}/status`. */
  updateStatus(storeId: string, saleId: string, status: OrderStatusTarget): Promise<OrderDetail>
  /** `POST /stores/{id}/orders/{saleId}/shipment` (solo con el pedido en preparación). */
  createShipment(storeId: string, saleId: string, input: ShipmentCreateInput): Promise<OrderDetail>
  /** `PATCH /stores/{id}/orders/{saleId}/shipment`. */
  updateShipment(storeId: string, saleId: string, input: ShipmentStatusInput): Promise<OrderDetail>
  /** `POST /stores/{id}/orders/{saleId}/invoice` (multipart `file`, PDF ≤ 5 MB; reemplaza la anterior). `ORDER_UPDATE`. */
  uploadInvoice(storeId: string, saleId: string, file: File): Promise<SaleInvoice>
  /** `DELETE /stores/{id}/orders/{saleId}/invoice`. */
  deleteInvoice(storeId: string, saleId: string): Promise<void>
  /** `GET /stores/{id}/orders/{saleId}/invoice` (PDF). */
  downloadInvoice(storeId: string, saleId: string): Promise<Blob>
}

let servicePromise: Promise<OrderService> | undefined

export function getOrderService(): Promise<OrderService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-order-service').then((module) => module.createHttpOrderService())
      : import('./mock/mock-order-service').then((module) => module.createMockOrderService())
  return servicePromise
}
