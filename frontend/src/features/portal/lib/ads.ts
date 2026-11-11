import type { AdCampaign, AdCampaignStatus, AdPlacement, AdPlacementPrice, AdTargeting } from '../api/ads-api'
import type { Tone } from './labels'

export const PLACEMENT_LABEL: Record<AdPlacement, string> = {
  SEARCH_TOP: 'Búsqueda',
  HOME_FEATURED: 'Inicio · Destacados',
  CATEGORY_TOP: 'Cabecera de categoría',
  PRODUCT_RELATED: 'Producto · También te puede servir',
}

export const PLACEMENT_HINT: Record<AdPlacement, string> = {
  SEARCH_TOP: 'Primeras posiciones cuando la búsqueda, la categoría o el vehículo coinciden con tu segmentación.',
  HOME_FEATURED: 'Carrusel «Destacados» de la página de inicio.',
  CATEGORY_TOP: 'Cabecera de la página de la categoría.',
  PRODUCT_RELATED: 'Sección «También te puede servir» del detalle de producto.',
}

export const PLACEMENT_ORDER: AdPlacement[] = ['SEARCH_TOP', 'HOME_FEATURED', 'CATEGORY_TOP', 'PRODUCT_RELATED']

export const placementLabel = (placement: string) => PLACEMENT_LABEL[placement as AdPlacement] ?? placement

export const STATUS_LABEL: Record<AdCampaignStatus, { label: string; tone: Tone }> = {
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  PENDING_PAYMENT: { label: 'Pago pendiente', tone: 'warning' },
  SCHEDULED: { label: 'Programada', tone: 'info' },
  ACTIVE: { label: 'Activa', tone: 'success' },
  PAUSED: { label: 'Pausada', tone: 'warning' },
  FINISHED: { label: 'Finalizada', tone: 'neutral' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  CANCELLED: { label: 'Cancelada', tone: 'neutral' },
}

/** Estado visible: «Pausada por Autopartco» cuando la pausa la hizo el admin. */
export function campaignStatusView(campaign: Pick<AdCampaign, 'status' | 'pausedBy'>): { label: string; tone: Tone } {
  if (campaign.status === 'PAUSED' && campaign.pausedBy === 'ADMIN') return { label: 'Pausada por Autopartco', tone: 'danger' }
  return STATUS_LABEL[campaign.status] ?? { label: campaign.status, tone: 'neutral' }
}

export const STATUS_FILTERS: AdCampaignStatus[] = ['DRAFT', 'PENDING_PAYMENT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'FINISHED', 'REJECTED', 'CANCELLED']

export const NO_REFUND_NOTICE =
  'Sin reembolso automático: si un producto deja de estar publicado, su anuncio deja de mostrarse y no se devuelve el pago.'

export const EMPTY_TARGETING: AdTargeting = { keywords: [], categoryIds: [], makeIds: [], modelIds: [], cityCodes: [] }

const DAY_MS = 86_400_000

/** `yyyy-MM-dd` a fecha local (evita el desfase de un día de `new Date('yyyy-MM-dd')`, que se lee en UTC). */
export function parseDay(value: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return undefined
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

export function toDayString(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const dayFormatter = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })
const shortDayFormatter = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' })

export function formatDay(value: string | null | undefined): string {
  const date = value ? parseDay(value.slice(0, 10)) : undefined
  return date ? dayFormatter.format(date) : '—'
}

export function formatShortDay(value: string): string {
  const date = parseDay(value.slice(0, 10))
  return date ? shortDayFormatter.format(date) : value
}

/** Días de la campaña, extremos incluidos; `0` si las fechas no son válidas. */
export function daysBetween(startsOn: string, endsOn: string): number {
  const start = parseDay(startsOn)
  const end = parseDay(endsOn)
  if (!start || !end || end < start) return 0
  return Math.round((end.getTime() - start.getTime()) / DAY_MS) + 1
}

/**
 * Vista previa del total: suma de tarifa × días de cada ubicación elegida. Es solo informativa: el servidor
 * calcula y cobra el importe real (`totalPrice`).
 */
export function estimateTotal(placements: AdPlacement[], prices: AdPlacementPrice[], days: number): number {
  return placements.reduce((sum, placement) => sum + (prices.find((price) => price.placement === placement)?.dailyPrice ?? 0) * days, 0)
}

export const formatPercent = (ratio: number) => `${(ratio * 100).toLocaleString('es-CO', { maximumFractionDigits: 2 })} %`

/** Cobro de anuncios en curso: Wompi vuelve a `/portal/publicidad/pago?id=…` sin almacén ni campaña. */
export interface PendingAdCheckout {
  storeId: string
  campaignId: string
  campaignName: string
  reference: string
  amountInCents: number
  startedAt: string
}

const KEY = 'autopartco.pendingAdCheckout'

export function savePendingAdCheckout(value: PendingAdCheckout): void {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(value))
  } catch {
    // Sin almacenamiento: la página de retorno mostrará el estado neutro.
  }
}

export function loadPendingAdCheckout(): PendingAdCheckout | undefined {
  try {
    const raw = window.sessionStorage.getItem(KEY)
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as Partial<PendingAdCheckout> | null
    if (!parsed || typeof parsed.storeId !== 'string' || typeof parsed.campaignId !== 'string' || typeof parsed.reference !== 'string') return undefined
    return {
      storeId: parsed.storeId,
      campaignId: parsed.campaignId,
      campaignName: typeof parsed.campaignName === 'string' ? parsed.campaignName : 'Campaña',
      reference: parsed.reference,
      amountInCents: typeof parsed.amountInCents === 'number' ? parsed.amountInCents : 0,
      startedAt: typeof parsed.startedAt === 'string' ? parsed.startedAt : '',
    }
  } catch {
    return undefined
  }
}

export function clearPendingAdCheckout(): void {
  try {
    window.sessionStorage.removeItem(KEY)
  } catch {
    // Nada que limpiar.
  }
}

/** Mensajes accionables para los errores de negocio de publicidad. */
const AD_ERROR_MESSAGE: Record<string, string> = {
  AD_PRODUCT_NOT_PUBLISHED: 'Alguno de los productos ya no está publicado. Quítalo o publícalo de nuevo.',
  AD_INVALID_DATES: 'Revisa las fechas: dura de 1 a 90 días, empieza desde hoy y hasta dentro de 60 días.',
  AD_TOO_MANY_PRODUCTS: 'Una campaña admite de 1 a 20 productos.',
  AD_PLACEMENT_UNAVAILABLE: 'Alguna de las ubicaciones elegidas no está disponible. Elige otra.',
  AD_TARGETING_INVALID: 'La segmentación no es válida. Revisa palabras clave, categorías y marcas.',
  AD_CAMPAIGN_STATUS_INVALID: 'El estado pedido no es válido para esta campaña.',
  AD_CAMPAIGN_NOT_FOUND: 'No encontramos la campaña. Puede que se haya eliminado.',
  SUBSCRIPTION_INACTIVE: 'Necesitas una suscripción activa para anunciar. Renueva tu plan e inténtalo de nuevo.',
  AD_CAMPAIGN_INVALID_STATE: 'La campaña cambió de estado y esta acción ya no aplica. Recarga la lista.',
  AD_PAYMENT_UNAVAILABLE: 'El pago de anuncios no está disponible por ahora. Inténtalo en unos minutos.',
  AD_CAMPAIGN_PAUSED_BY_ADMIN: 'Autopartco pausó esta campaña. Escríbenos para que la revisemos y la reactivemos.',
}

export function adErrorMessage(error: unknown, fallback: string): string {
  const code = typeof error === 'object' && error !== null && 'code' in error ? String((error as { code?: unknown }).code) : ''
  return AD_ERROR_MESSAGE[code] ?? fallback
}
