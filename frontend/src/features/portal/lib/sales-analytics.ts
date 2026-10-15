import type {
  OrderSummary,
  ProductRow,
  SalesAnalytics,
  SalesEvent,
  SalesFunnelStage,
  SalesHeatCell,
  SalesRange,
} from '../api/types'

const DAY_MS = 86_400_000

/** Pedidos que cuentan como venta (mismo criterio del dashboard: no cancelados). */
function counts(order: OrderSummary): boolean {
  return order.status !== 'CANCELLED' && order.status !== 'REJECTED'
}

function dayKey(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

/**
 * Agrega pedidos en las series de la pantalla «Ventas». Función pura: la usan el mock y el
 * servicio HTTP mientras el backend no exponga la analítica agregada.
 */
export function buildSalesAnalytics(orders: OrderSummary[], range: SalesRange, now = new Date()): SalesAnalytics {
  const today = startOfDay(now)
  const start = new Date(today.getTime() - (range - 1) * DAY_MS)
  const previousStart = new Date(start.getTime() - range * DAY_MS)
  const end = new Date(today.getTime() + DAY_MS)

  const daily = Array.from({ length: range }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    return { date: dayKey(date), revenue: 0, orders: 0, averageTicket: 0 }
  })
  const byDay = new Map(daily.map((point) => [point.date, point]))

  const cells = new Map<string, SalesHeatCell>()
  const reached: Record<SalesFunnelStage, number> = { RECEIVED: 0, PAID: 0, SHIPPED: 0, DELIVERED: 0 }
  let cancelled = 0
  let delivered = 0
  let previousRevenue = 0
  let previousOrders = 0

  for (const order of orders) {
    const created = new Date(order.createdAt)
    if (created < previousStart || created >= end) continue
    if (created < start) {
      if (counts(order)) {
        previousRevenue += order.total
        previousOrders += 1
      }
      continue
    }
    if (!counts(order)) {
      cancelled += 1
      continue
    }
    const point = byDay.get(dayKey(created))
    if (point) {
      point.revenue += order.total
      point.orders += 1
    }

    const day = (created.getDay() + 6) % 7
    const hour = created.getHours()
    const key = `${day}-${hour}`
    const cell = cells.get(key) ?? { day, hour, orders: [] }
    cell.orders.push({
      saleId: order.saleId,
      orderNumber: order.orderNumber,
      createdAt: order.createdAt,
      total: order.total,
      status: order.status,
      customerName: order.customerName,
    })
    cells.set(key, cell)

    reached.RECEIVED += 1
    if (order.paymentStatus === 'APPROVED') reached.PAID += 1
    if (order.status === 'SHIPPED' || order.status === 'DELIVERED') reached.SHIPPED += 1
    if (order.status === 'DELIVERED') {
      reached.DELIVERED += 1
      delivered += 1
    }
  }

  for (const point of daily) point.averageTicket = point.orders > 0 ? Math.round(point.revenue / point.orders) : 0
  const revenue = daily.reduce((sum, point) => sum + point.revenue, 0)
  const total = daily.reduce((sum, point) => sum + point.orders, 0)

  return {
    range,
    generatedAt: now.toISOString(),
    totals: { revenue, orders: total, averageTicket: total > 0 ? Math.round(revenue / total) : 0, delivered },
    previous: {
      revenue: previousRevenue,
      orders: previousOrders,
      averageTicket: previousOrders > 0 ? Math.round(previousRevenue / previousOrders) : 0,
    },
    daily,
    heatmap: [...cells.values()].map((cell) => ({ ...cell, orders: cell.orders.toSorted((a, b) => b.createdAt.localeCompare(a.createdAt)) })),
    funnel: (['RECEIVED', 'PAID', 'SHIPPED', 'DELIVERED'] as const).map((stage) => ({ stage, count: reached[stage] })),
    cancelled,
  }
}

/** Últimos eventos (pedidos, pagos aprobados y stock bajo), del más reciente al más antiguo. */
export function buildActivity(orders: OrderSummary[], products: ProductRow[], now = new Date(), limit = 20): SalesEvent[] {
  const events: SalesEvent[] = []
  for (const order of orders.toSorted((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, limit)) {
    const who = order.customerName ?? 'Un comprador'
    events.push({
      id: `order-${order.saleId}`,
      kind: 'ORDER',
      at: order.createdAt,
      title: `Pedido #${order.orderNumber}`,
      detail: `${who} · ${order.itemCount} ${order.itemCount === 1 ? 'unidad' : 'unidades'}`,
      to: `pedidos/${encodeURIComponent(order.saleId)}`,
    })
    if (order.paymentStatus === 'APPROVED') {
      events.push({
        id: `payment-${order.saleId}`,
        kind: 'PAYMENT',
        at: order.createdAt,
        title: `Pago aprobado del pedido #${order.orderNumber}`,
        detail: order.branchName,
        to: `pedidos/${encodeURIComponent(order.saleId)}`,
      })
    }
  }
  for (const product of products) {
    if (product.quantity > 3) continue
    events.push({
      id: `stock-${product.id}-${product.branchId}`,
      kind: 'LOW_STOCK',
      at: now.toISOString(),
      title: product.quantity === 0 ? `Agotado: ${product.name}` : `Stock bajo: ${product.name}`,
      detail: `${product.branchName} · ${product.quantity} ${product.quantity === 1 ? 'unidad' : 'unidades'}`,
      to: `inventario?stock=${product.quantity === 0 ? 'agotado' : 'bajo'}`,
    })
  }
  return events.toSorted((a, b) => b.at.localeCompare(a.at)).slice(0, limit)
}
