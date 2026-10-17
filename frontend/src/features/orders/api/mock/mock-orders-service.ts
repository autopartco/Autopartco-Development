import { ApiError, simulateLatency } from '@/lib/api'
import type { BuyerOrderDetailDto, BuyerOrderSaleDto, BuyerOrderSummaryDto, OrdersService, ShipmentDto } from '../types'

const ADDRESS = {
  addressLine: 'Calle 10 # 20-30',
  addressComplement: 'Apto 401',
  city: 'Bogotá',
  department: 'Cundinamarca',
  country: 'Colombia',
  postalCode: '110111',
}

function sale(
  id: string,
  storeName: string,
  status: string,
  items: Array<[string, string | null, number, number]>,
  shipment: ShipmentDto | null,
): BuyerOrderSaleDto {
  const lines = items.map(([name, partNumber, quantity, unitPrice], index) => ({
    productId: `${id}-p${index}`,
    name,
    partNumber,
    quantity,
    unitPrice,
    lineTotal: quantity * unitPrice,
  }))
  const subtotal = lines.reduce((sum, line) => sum + line.lineTotal, 0)
  return {
    saleId: id,
    storeId: `store-${id}`,
    storeName,
    branchId: `branch-${id}`,
    branchName: 'Sede principal',
    status,
    subtotal,
    shippingCost: 0,
    total: subtotal,
    items: lines,
    shipment,
  }
}

function order(orderId: string, orderNumber: number, status: string, createdAt: string, sales: BuyerOrderSaleDto[]): BuyerOrderDetailDto {
  const subtotal = sales.reduce((sum, item) => sum + item.subtotal, 0)
  return {
    orderId,
    orderNumber,
    status,
    createdAt,
    currency: 'COP',
    subtotal,
    shippingTotal: 0,
    total: subtotal,
    payment: { status: 'APPROVED', method: 'SIMULATED_CARD', methodName: 'Tarjeta simulada', reference: `mock-${orderNumber}`, amount: subtotal, paidAt: createdAt },
    deliveryAddress: ADDRESS,
    sales,
  }
}

/** Pedidos simulados, del más reciente al más antiguo. */
const ORDERS: BuyerOrderDetailDto[] = [
  order('ord-1004', 1004, 'CONFIRMED', '2026-10-16T09:12:00', [sale('s1004', 'Repuestos El Cruce', 'PENDING', [['Pastillas de freno delanteras', 'BP-2210', 1, 98000]], null)]),
  order('ord-1003', 1003, 'CONFIRMED', '2026-10-15T14:40:00', [
    sale('s1003a', 'Autopartes Medellín', 'PREPARING', [['Filtro de aceite', 'FO-118', 2, 24000], ['Filtro de aire', 'FA-340', 1, 42000]], null),
    sale('s1003b', 'Repuestos El Cruce', 'SHIPPED', [['Bujías (juego x4)', 'NG-4417', 1, 76000]], {
      carrier: 'Servientrega',
      trackingNumber: 'AP-000123',
      status: 'IN_TRANSIT',
      shippedAt: '2026-10-16T10:00:00',
      deliveredAt: null,
      events: [
        { status: 'PREPARING', notes: 'Pedido empacado.', occurredAt: '2026-10-15T16:00:00' },
        { status: 'DISPATCHED', notes: null, occurredAt: '2026-10-16T10:00:00' },
        { status: 'IN_TRANSIT', notes: 'Salió de la bodega de Bogotá.', occurredAt: '2026-10-16T18:30:00' },
      ],
    }),
  ]),
  order('ord-1002', 1002, 'CONFIRMED', '2026-10-08T11:05:00', [
    sale('s1002', 'Frenos y Embragues Ltda.', 'DELIVERED', [['Disco de freno ventilado', 'DF-7781', 2, 185000]], {
      carrier: 'Coordinadora',
      trackingNumber: 'AP-000087',
      status: 'DELIVERED',
      shippedAt: '2026-10-09T08:30:00',
      deliveredAt: '2026-10-11T15:20:00',
      events: [
        { status: 'DISPATCHED', notes: null, occurredAt: '2026-10-09T08:30:00' },
        { status: 'IN_TRANSIT', notes: null, occurredAt: '2026-10-10T07:45:00' },
        { status: 'OUT_FOR_DELIVERY', notes: 'En reparto en tu zona.', occurredAt: '2026-10-11T09:10:00' },
        { status: 'DELIVERED', notes: 'Recibido por portería.', occurredAt: '2026-10-11T15:20:00' },
      ],
    }),
  ]),
  order('ord-1001', 1001, 'CANCELLED', '2026-09-28T17:50:00', [sale('s1001', 'Autopartes Medellín', 'CANCELLED', [['Amortiguador trasero', 'AM-5502', 1, 210000]], null)]),
]

function summarize(item: BuyerOrderDetailDto): BuyerOrderSummaryDto {
  return {
    orderId: item.orderId,
    orderNumber: item.orderNumber,
    status: item.status,
    createdAt: item.createdAt,
    currency: item.currency,
    total: item.total,
    itemCount: item.sales.reduce((sum, s) => sum + s.items.reduce((units, line) => units + line.quantity, 0), 0),
    sales: item.sales.map(({ saleId, storeId, storeName, status }) => ({ saleId, storeId, storeName, status })),
  }
}

export function createMockOrdersService(): OrdersService {
  return {
    simulated: true,
    async list(page, size) {
      await simulateLatency(500)
      if (!Number.isInteger(page) || !Number.isInteger(size) || page < 1 || size < 1 || size > 50) {
        throw new ApiError('La paginación no es válida.', { status: 400, code: 'VALIDATION_ERROR' })
      }
      const start = (page - 1) * size
      return {
        items: ORDERS.slice(start, start + size).map(summarize),
        page,
        size,
        total: ORDERS.length,
        totalPages: Math.max(1, Math.ceil(ORDERS.length / size)),
      }
    },
    async get(orderId) {
      await simulateLatency(400)
      const found = ORDERS.find((item) => item.orderId === orderId)
      if (!found) throw new ApiError('No se encontró el pedido.', { status: 404, code: 'ORDER_NOT_FOUND' })
      return found
    },
  }
}
