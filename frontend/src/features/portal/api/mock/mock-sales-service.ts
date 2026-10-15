import { buildActivity, buildSalesAnalytics } from '../../lib/sales-analytics'
import type { SalesService } from '../sales-service'
import type { OrderStatus, OrderSummary, SalesEvent } from '../types'
import { getStore, requirePermission, simulateNetwork, type MockStore } from './mock-state'

/** PRNG determinista (mulberry32) para que la demo sea estable entre recargas. */
function prng(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const CUSTOMERS = ['Camila Rodríguez', 'Andrés Gómez', 'Laura Pineda', 'Sebastián Ramírez', 'Diana Cárdenas', 'Julián Mora', 'Paola Vega', 'Carlos Ospina']

/** ~180 días de pedidos de demostración, con más actividad a media mañana y en la tarde. */
function syntheticOrders(store: MockStore): OrderSummary[] {
  const rand = prng([...store.summary.storeId].reduce((sum, char) => sum + char.charCodeAt(0), 7))
  const branch = store.branches[0]
  const rows: OrderSummary[] = []
  const now = new Date()
  let number = 20000
  for (let back = 0; back < 180; back += 1) {
    const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() - back)
    const weekday = day.getDay()
    const perDay = Math.floor(rand() * (weekday === 0 ? 3 : weekday === 6 ? 5 : 7))
    for (let index = 0; index < perDay; index += 1) {
      const hour = Math.min(23, Math.max(0, Math.round(8 + (rand() + rand() + rand() - 1.5) * 6 + (rand() > 0.8 ? 6 : 0))))
      const created = new Date(day.getFullYear(), day.getMonth(), day.getDate(), hour, Math.floor(rand() * 60))
      if (created > now) continue
      const roll = rand()
      const status: OrderStatus =
        roll < 0.07 ? 'CANCELLED' : back < 1 ? 'PENDING' : back < 3 ? (roll < 0.5 ? 'PREPARING' : 'CONFIRMED') : back < 6 ? 'SHIPPED' : 'DELIVERED'
      const subtotal = Math.round((40_000 + rand() * 360_000) / 1000) * 1000
      number += 1
      rows.push({
        saleId: `demo-${store.summary.storeId.slice(-4)}-${number}`,
        orderId: `demo-order-${number}`,
        orderNumber: number,
        branchId: branch?.id ?? 'demo-branch',
        branchName: branch?.name ?? 'Sede principal',
        status,
        subtotal,
        shippingCost: 12000,
        total: subtotal + 12000,
        createdAt: created.toISOString(),
        customerName: CUSTOMERS[Math.floor(rand() * CUSTOMERS.length)] ?? null,
        paymentStatus: status === 'PENDING' ? (rand() > 0.5 ? 'PENDING' : 'APPROVED') : status === 'CANCELLED' ? 'DECLINED' : 'APPROVED',
        shipmentStatus: null,
        itemCount: 1 + Math.floor(rand() * 4),
      })
    }
  }
  return rows
}

function realOrders(store: MockStore): OrderSummary[] {
  return store.orders.map((sale) => ({
    saleId: sale.saleId,
    orderId: sale.orderId,
    orderNumber: sale.orderNumber,
    branchId: sale.branchId,
    branchName: sale.branchName,
    status: sale.status,
    subtotal: sale.subtotal,
    shippingCost: sale.shippingCost,
    total: sale.total,
    createdAt: sale.createdAt,
    customerName: sale.customerName,
    paymentStatus: sale.paymentStatus,
    shipmentStatus: sale.shipment?.status ?? null,
    itemCount: sale.items.reduce((sum, item) => sum + item.units, 0),
  }))
}

let simulatedArrivals = 0

/**
 * Analítica simulada: pedidos reales del mock más historial sintético. Los pedidos con
 * `saleId` que empieza por `demo-` no tienen detalle (abrirlos da «no encontrado»).
 */
export function createMockSalesService(): SalesService {
  return {
    async sales(storeId, days) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'ORDER_READ')
      return buildSalesAnalytics([...realOrders(store), ...syntheticOrders(store)], days)
    },

    async activity(storeId) {
      await simulateNetwork(250)
      const store = getStore(storeId)
      requirePermission(store, 'ORDER_READ')
      const events = buildActivity([...realOrders(store), ...syntheticOrders(store)], store.products)
      // Cada consulta añade un evento nuevo simulado para ver entrar actividad en vivo.
      simulatedArrivals += 1
      const number = 30000 + simulatedArrivals
      const payment = simulatedArrivals % 3 === 0
      const arrival: SalesEvent = {
        id: `live-${number}`,
        kind: payment ? 'PAYMENT' : 'ORDER',
        at: new Date().toISOString(),
        title: payment ? `Pago aprobado del pedido #${number - 1}` : `Pedido #${number}`,
        detail: `${CUSTOMERS[simulatedArrivals % CUSTOMERS.length]} · ${1 + (simulatedArrivals % 3)} unidades`,
      }
      return [arrival, ...events].slice(0, 20)
    },
  }
}
