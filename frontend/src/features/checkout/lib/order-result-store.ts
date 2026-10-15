import type { BuyerOrderDto } from '../api/types'

/** `BuyerOrderDto` sin `deliveryAddress`: la dirección es dato personal y no se persiste (D12). */
export type OrderResultSummary = Omit<BuyerOrderDto, 'deliveryAddress' | 'currency' | 'subtotal' | 'shippingTotal'>

const key = (orderId: string) => `autopartco.order-result.${orderId}`

/**
 * Guarda el resumen en `sessionStorage` para poder mostrar la confirmación tras recargar,
 * hasta que exista `GET /me/orders/{id}` (B2). Se construye campo por campo para que
 * ningún dato de entrega pueda colarse.
 */
export function saveOrderResult(order: BuyerOrderDto): void {
  const summary: OrderResultSummary = {
    orderId: order.orderId,
    orderNumber: order.orderNumber,
    status: order.status,
    createdAt: order.createdAt,
    total: order.total,
    payment: order.payment,
    sales: order.sales,
  }
  try {
    window.sessionStorage.setItem(key(order.orderId), JSON.stringify(summary))
  } catch {
    // Sin almacenamiento disponible, la confirmación solo se verá en esta visita.
  }
}

export function loadOrderResult(orderId: string): OrderResultSummary | undefined {
  try {
    const raw = window.sessionStorage.getItem(key(orderId))
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as Partial<OrderResultSummary> | null
    if (
      !parsed ||
      parsed.orderId !== orderId ||
      typeof parsed.orderNumber !== 'number' ||
      typeof parsed.total !== 'number' ||
      !parsed.payment ||
      !Array.isArray(parsed.sales)
    ) {
      return undefined
    }
    return parsed as OrderResultSummary
  } catch {
    return undefined
  }
}
