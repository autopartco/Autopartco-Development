import type { BuyerOrderDto, BuyerSaleDto } from '@/features/checkout/api/types'

/**
 * Contrato de "Mis pedidos" (`docs/architecture/2026-10-14-mvp-marketplace-plan.md` §4.3).
 * Todo lo muestra el servidor: el cliente no calcula montos ni estados.
 */

export interface BuyerOrderSummaryDto {
  orderId: string
  orderNumber: number
  status: string
  /** `LocalDateTime` sin zona, hora del servidor. */
  createdAt: string
  currency: string
  total: number
  itemCount: number
  sales: Array<{ saleId: string; storeId: string; storeName: string; status: string }>
}

export interface PageDto<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface ShipmentEventDto {
  status: string
  notes: string | null
  occurredAt: string
}

export interface ShipmentDto {
  carrier: string
  trackingNumber: string
  status: string
  shippedAt: string | null
  deliveredAt: string | null
  events: ShipmentEventDto[]
}

/** Venta del detalle: la del checkout más el envío (`null` si aún no tiene). */
export interface BuyerOrderSaleDto extends BuyerSaleDto {
  shipment: ShipmentDto | null
}

export interface BuyerOrderDetailDto extends Omit<BuyerOrderDto, 'sales'> {
  sales: BuyerOrderSaleDto[]
}

export interface OrdersService {
  /** `true` con la fuente `mock`. */
  readonly simulated: boolean
  /** `page` desde 1; `size` de 1 a 50. */
  list(page: number, size: number): Promise<PageDto<BuyerOrderSummaryDto>>
  /** Lanza `ApiError` 404 `ORDER_NOT_FOUND` si no existe o es ajeno. */
  get(orderId: string): Promise<BuyerOrderDetailDto>
}
