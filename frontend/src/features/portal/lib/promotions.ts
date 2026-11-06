import { formatCOP } from '@/lib/format'
import type { CustomerSegmentCode } from '../api/analytics-types'
import type { Promotion, PromotionAudience, PromotionTargetType } from '../api/promotion-types'
import type { Tone } from './labels'

export const AUDIENCE_LABEL: Record<PromotionAudience, string> = {
  ALL: 'Todos los compradores',
  PLATFORM_FREQUENT: 'Clientes frecuentes de Autopartco',
  STORE_FREQUENT: 'Mis clientes frecuentes',
  STORE_AT_RISK: 'Mis clientes en riesgo',
  STORE_NEW: 'Mis clientes nuevos',
  STORE_DORMANT: 'Mis clientes dormidos',
}

export const AUDIENCE_HINT: Record<PromotionAudience, string> = {
  ALL: 'El precio con descuento lo ve cualquier persona, también quien no ha ingresado.',
  PLATFORM_FREQUENT: 'Solo lo ven los compradores que compran seguido en toda la plataforma. Requiere el plan con Reportes avanzados.',
  STORE_FREQUENT: 'Solo lo ven quienes compran seguido en tu almacén.',
  STORE_AT_RISK: 'Solo lo ven quienes compraban seguido en tu almacén y llevan tiempo sin volver.',
  STORE_NEW: 'Solo lo ven quienes hicieron su primera compra en tu almacén hace poco.',
  STORE_DORMANT: 'Solo lo ven quienes llevan mucho tiempo sin comprar en tu almacén.',
}

/** Audiencias que ofrece el portal del almacén y la administración. */
export const STORE_AUDIENCES: PromotionAudience[] = ['ALL', 'PLATFORM_FREQUENT', 'STORE_FREQUENT', 'STORE_AT_RISK', 'STORE_NEW', 'STORE_DORMANT']
export const PLATFORM_AUDIENCES: PromotionAudience[] = ['ALL', 'PLATFORM_FREQUENT']

const AUDIENCES = new Set<string>(STORE_AUDIENCES)

export function isPromotionAudience(value: string | null | undefined): value is PromotionAudience {
  return value !== null && value !== undefined && AUDIENCES.has(value)
}

/** Segmento de Inteligencia > Clientes -> audiencia de promoción del almacén. */
export const SEGMENT_AUDIENCE: Partial<Record<CustomerSegmentCode, PromotionAudience>> = {
  FREQUENT: 'STORE_FREQUENT',
  AT_RISK: 'STORE_AT_RISK',
  NEW: 'STORE_NEW',
  DORMANT: 'STORE_DORMANT',
}

export const TARGET_LABEL: Record<PromotionTargetType, string> = {
  ALL: 'Toda la plataforma',
  STORE: 'Todo el almacén',
  PRODUCT: 'Un producto',
  CATEGORY: 'Una categoría',
  MANUFACTURER: 'Una marca',
  ATTRIBUTE: 'Productos con un atributo',
}

export function discountText(promotion: Pick<Promotion, 'discountType' | 'discountValue'>): string {
  return promotion.discountType === 'PERCENTAGE'
    ? `${new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 }).format(promotion.discountValue)} %`
    : formatCOP(promotion.discountValue)
}

export type PromotionPhase = 'ACTIVE' | 'SCHEDULED' | 'EXPIRED' | 'PAUSED'

/** Estado para mostrar: lo que dice el servidor (`currentlyActive`) y, si no está vigente, por qué. */
export function phaseOf(promotion: Promotion, now = Date.now()): PromotionPhase {
  if (promotion.currentlyActive) return 'ACTIVE'
  if (Date.parse(promotion.endsAt) <= now) return 'EXPIRED'
  if (!promotion.enabled) return 'PAUSED'
  if (Date.parse(promotion.startsAt) > now) return 'SCHEDULED'
  return 'PAUSED'
}

export const PHASE_LABEL: Record<PromotionPhase, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Vigente', tone: 'success' },
  SCHEDULED: { label: 'Programada', tone: 'info' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
  PAUSED: { label: 'Pausada', tone: 'warning' },
}

const percentFormatter = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 })

/** Fracción 0,25 -> «25 %». */
export function formatFraction(value: number): string {
  return percentFormatter.format(value)
}

/** Mínimo de compradores expuestos que exige el servidor para medir el efecto frente al grupo de control. */
export const MIN_EXPOSED_BUYERS = 30

/** `YYYY-MM-DDTHH:mm` en hora local, para `<input type="datetime-local">`. */
export function toLocalInput(iso: string): string {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function fromLocalInput(value: string): string | undefined {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}
