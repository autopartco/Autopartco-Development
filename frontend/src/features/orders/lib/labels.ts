import type { Tone } from '@/features/portal/lib/labels'

/** Estados de pedido y venta que devuelve el servidor, en español. Un código desconocido se muestra tal cual. */
const STATUS: Record<string, { label: string; tone: Tone }> = {
  PENDING: { label: 'Pendiente de la tienda', tone: 'warning' },
  CONFIRMED: { label: 'Confirmado', tone: 'info' },
  PREPARING: { label: 'En preparación', tone: 'info' },
  SHIPPED: { label: 'Enviado', tone: 'info' },
  DELIVERED: { label: 'Entregado', tone: 'success' },
  CANCELLED: { label: 'Cancelado', tone: 'neutral' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
}

export function orderStatus(code: string): { label: string; tone: Tone } {
  return STATUS[code] ?? { label: code, tone: 'neutral' }
}

const SHIPMENT: Record<string, string> = {
  PREPARING: 'Preparando envío',
  DISPATCHED: 'Despachado',
  IN_TRANSIT: 'En camino',
  OUT_FOR_DELIVERY: 'En reparto',
  DELIVERED: 'Entregado',
}

export function shipmentStatusLabel(code: string): string {
  return SHIPMENT[code] ?? code
}

export function ordersPath(): string {
  return '/mis-pedidos'
}

export function orderDetailPath(orderId: string): string {
  return `/mis-pedidos/${encodeURIComponent(orderId)}`
}
