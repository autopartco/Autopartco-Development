import type { Capacity, OrderStatus, ShipmentStatus, StorePermission, SubscriptionStatus } from '../api/types'

export type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info'

export const SUBSCRIPTION_STATUS_LABEL: Record<SubscriptionStatus, string> = {
  PENDING_SETUP: 'Configuración pendiente',
  TRIALING: 'Prueba gratuita',
  ACTIVE: 'Plan activo',
  EXPIRED: 'Vencida',
}

export const SUBSCRIPTION_STATUS_TONE: Record<SubscriptionStatus, Tone> = {
  PENDING_SETUP: 'neutral',
  TRIALING: 'info',
  ACTIVE: 'success',
  EXPIRED: 'danger',
}

export const CAPACITY_LABEL: Record<Capacity, { singular: string; plural: string }> = {
  PRODUCTS: { singular: 'producto', plural: 'productos' },
  BRANCHES: { singular: 'sucursal', plural: 'sucursales' },
  USERS: { singular: 'usuario', plural: 'usuarios' },
}

/** Funciones de plan (`PlanCapabilities`) en lenguaje de almacén. */
export const CAPABILITY_LABEL: Record<string, string> = {
  BASIC_ANALYTICS: 'Resumen de ventas',
  ADVANCED_REPORTS: 'Reportes avanzados',
  PROMOTIONS: 'Promociones',
  CSV_IMPORT: 'Importación de productos por CSV',
  CUSTOM_ROLES: 'Roles y equipo personalizados',
  INTEGRATIONS: 'Integraciones',
  WHATSAPP_NOTIFICATIONS: 'Avisos por WhatsApp',
  API_ACCESS: 'Acceso por API',
  WEBHOOKS: 'Webhooks',
  ERP_INTEGRATIONS: 'Integración con ERP',
  CUSTOM_DOMAIN: 'Dominio propio',
}

export function capabilityLabel(code: string): string {
  return CAPABILITY_LABEL[code] ?? code
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Pendiente',
  CONFIRMED: 'Confirmado',
  PREPARING: 'En preparación',
  SHIPPED: 'Enviado',
  DELIVERED: 'Entregado',
  CANCELLED: 'Cancelado',
  REJECTED: 'Rechazado',
}

export const ORDER_STATUS_TONE: Record<OrderStatus, Tone> = {
  PENDING: 'warning',
  CONFIRMED: 'info',
  PREPARING: 'info',
  SHIPPED: 'info',
  DELIVERED: 'success',
  CANCELLED: 'neutral',
  REJECTED: 'danger',
}

export const SHIPMENT_STATUS_LABEL: Record<ShipmentStatus, string> = {
  PREPARING: 'Preparando envío',
  DISPATCHED: 'Despachado',
  IN_TRANSIT: 'En camino',
  OUT_FOR_DELIVERY: 'En reparto',
  DELIVERED: 'Entregado',
}

/** Estados de pago de pedidos y de suscripción (`payment_statuses`). */
export const PAYMENT_STATUS_LABEL: Record<string, { label: string; tone: Tone }> = {
  APPROVED: { label: 'Aprobado', tone: 'success' },
  PENDING: { label: 'Pendiente', tone: 'warning' },
  DECLINED: { label: 'Rechazado', tone: 'danger' },
  ERROR: { label: 'Con error', tone: 'danger' },
  FAILED: { label: 'Fallido', tone: 'danger' },
  VOIDED: { label: 'Anulado', tone: 'neutral' },
}

export function paymentStatus(code: string | null): { label: string; tone: Tone } {
  if (!code) return { label: 'Sin pago', tone: 'neutral' }
  return PAYMENT_STATUS_LABEL[code] ?? { label: code, tone: 'neutral' }
}

/** Transiciones de pedido que ofrece el servidor (`SaleStatusService`); aquí solo se muestran. */
export const ORDER_NEXT: Partial<Record<OrderStatus, ('CONFIRMED' | 'PREPARING' | 'CANCELLED')[]>> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['PREPARING', 'CANCELLED'],
}

/** Siguientes estados de envío que acepta el servidor (`ShipmentService`). */
export const SHIPMENT_NEXT: Record<ShipmentStatus, Exclude<ShipmentStatus, 'PREPARING'>[]> = {
  PREPARING: ['DISPATCHED'],
  DISPATCHED: ['IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'DELIVERED'],
  OUT_FOR_DELIVERY: ['DELIVERED'],
  DELIVERED: [],
}

export const SHIPMENT_ACTION_LABEL: Record<Exclude<ShipmentStatus, 'PREPARING'>, string> = {
  DISPATCHED: 'Marcar como despachado',
  IN_TRANSIT: 'Marcar en camino',
  OUT_FOR_DELIVERY: 'Marcar en reparto',
  DELIVERED: 'Marcar como entregado',
}

export const ORDER_ACTION_LABEL = {
  CONFIRMED: 'Confirmar pedido',
  PREPARING: 'Empezar a preparar',
  CANCELLED: 'Cancelar pedido',
} as const

export const PERMISSION_LABEL: Partial<Record<StorePermission, string>> = {
  STORE_READ: 'Ver el almacén',
  PRODUCT_READ: 'Ver productos',
  PRODUCT_CREATE: 'Crear productos',
  PRODUCT_UPDATE: 'Editar productos',
  PRODUCT_PUBLISH: 'Publicar productos',
  ORDER_READ: 'Ver pedidos',
  ORDER_UPDATE: 'Gestionar pedidos',
  SHIPMENT_UPDATE: 'Gestionar envíos',
  BRANCH_READ: 'Ver sucursales',
}

/** "SUSPENSION_Y_DIRECCION" -> "Suspensión y dirección" aproximado: el API solo trae el código. */
export function humanizeCode(code: string): string {
  const words = code.replace(/[_-]+/g, ' ').trim().toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

const dateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' })
const dateTimeFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' })

/** Fecha corta es-CO. Los `LocalDateTime` del API no traen zona: se leen como hora local. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  return dateFormatter.format(new Date(value))
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return dateTimeFormatter.format(new Date(value))
}

/** "1 día" / "12 días". */
export function formatDays(days: number): string {
  return `${days} ${days === 1 ? 'día' : 'días'}`
}

/** Porcentaje de uso (0–100) o `null` si el cupo es ilimitado. */
export function usagePercent(used: number, limit: number | null): number | null {
  if (limit === null) return null
  if (limit <= 0) return 100
  return Math.min(100, Math.round((used / limit) * 100))
}

/** "40 de 100 productos" / "3 sucursales (sin límite)". */
export function usageText(used: number, limit: number | null, capacity: Capacity): string {
  const names = CAPACITY_LABEL[capacity]
  const noun = (count: number) => (count === 1 ? names.singular : names.plural)
  return limit === null ? `${used} ${noun(used)} · sin límite` : `${used} de ${limit} ${noun(limit)}`
}

export function limitText(limit: number | null, capacity: Capacity): string {
  const names = CAPACITY_LABEL[capacity]
  if (limit === null) return `${names.plural.charAt(0).toUpperCase()}${names.plural.slice(1)} ilimitados`
  return `${limit} ${limit === 1 ? names.singular : names.plural}`
}
