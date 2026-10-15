import { ApiError } from '@/lib/api'
import type { OrderService } from '../order-service'
import type { OrderDetail, OrderShipment, OrderSummary, ShipmentStatus } from '../types'
import { getStore, requirePermission, simulateNetwork, type MockStore } from './mock-state'

function notFound(): ApiError {
  return new ApiError('No encontramos ese pedido en tu almacén.', { status: 404, code: 'SELLER_ORDER_NOT_FOUND' })
}

function find(store: MockStore, saleId: string): OrderDetail {
  const sale = store.orders.find((candidate) => candidate.saleId === saleId)
  if (!sale) throw notFound()
  return sale
}

function summary(sale: OrderDetail): OrderSummary {
  return {
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
  }
}

const SHIPMENT_NEXT: Record<ShipmentStatus, ShipmentStatus[]> = {
  PREPARING: ['DISPATCHED'],
  DISPATCHED: ['IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'DELIVERED'],
  OUT_FOR_DELIVERY: ['DELIVERED'],
  DELIVERED: [],
}

function clone(sale: OrderDetail): OrderDetail {
  return structuredClone(sale)
}

/** Pedidos simulados con las mismas transiciones que `SaleStatusService` y `ShipmentService`. */
export function createMockOrderService(): OrderService {
  return {
    async list(storeId, query = {}) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'ORDER_READ')
      return store.orders
        .filter((sale) => !query.status || sale.status === query.status)
        .toSorted((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(summary)
    },

    async get(storeId, saleId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'ORDER_READ')
      return clone(find(store, saleId))
    },

    async updateStatus(storeId, saleId, status) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'ORDER_UPDATE')
      const sale = find(store, saleId)
      const allowed =
        (sale.status === 'PENDING' && (status === 'CONFIRMED' || status === 'CANCELLED')) ||
        (sale.status === 'CONFIRMED' && (status === 'PREPARING' || status === 'CANCELLED'))
      if (!allowed) {
        throw new ApiError(`No se puede cambiar el pedido de ${sale.status} a ${status}.`, {
          status: 409,
          code: 'SELLER_ORDER_STATUS_TRANSITION_INVALID',
        })
      }
      sale.status = status
      return clone(sale)
    },

    async createShipment(storeId, saleId, input) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'SHIPMENT_UPDATE')
      const sale = find(store, saleId)
      if (sale.status !== 'PREPARING') {
        throw new ApiError('Solo se puede registrar el envío cuando la venta está en preparación.', {
          status: 409,
          code: 'SELLER_SHIPMENT_ORDER_NOT_PREPARING',
        })
      }
      if (sale.shipment) {
        throw new ApiError('La venta ya tiene un envío registrado.', { status: 409, code: 'SELLER_SHIPMENT_ALREADY_EXISTS' })
      }
      sale.shipment = {
        id: crypto.randomUUID(),
        carrier: input.carrier,
        trackingNumber: input.trackingNumber,
        status: 'PREPARING',
        shippedAt: null,
        deliveredAt: null,
        events: [{ status: 'PREPARING', notes: 'Envío registrado', occurredAt: new Date().toISOString().slice(0, 19) }],
      }
      return clone(sale)
    },

    async updateShipment(storeId, saleId, input) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'SHIPMENT_UPDATE')
      const sale = find(store, saleId)
      const shipment: OrderShipment | null = sale.shipment
      if (!shipment) {
        throw new ApiError('No se encontró el envío de esta venta.', { status: 404, code: 'SELLER_SHIPMENT_NOT_FOUND' })
      }
      if (!SHIPMENT_NEXT[shipment.status].includes(input.status)) {
        throw new ApiError(`No se puede cambiar el envío de ${shipment.status} a ${input.status}.`, {
          status: 409,
          code: 'SELLER_SHIPMENT_STATUS_TRANSITION_INVALID',
        })
      }
      const now = new Date().toISOString().slice(0, 19)
      shipment.status = input.status
      shipment.events.push({ status: input.status, notes: input.notes?.trim() || null, occurredAt: now })
      if (input.status === 'DISPATCHED') {
        shipment.shippedAt ??= now
        sale.status = 'SHIPPED'
      }
      if (input.status === 'DELIVERED') {
        shipment.deliveredAt = now
        sale.status = 'DELIVERED'
      }
      return clone(sale)
    },
  }
}
