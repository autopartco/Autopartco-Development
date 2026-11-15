import type { GeoPoint } from '@/features/location/api/types'
import { readAreaCookie } from '@/lib/area-cookie'
import type { ServiceSearchQuery, ServiceSort } from '../api/types'
import { isServiceCategory } from './categories'
import type { ServiceCategory } from '@/features/marketplace/api/types'

export const SERVICE_PAGE_SIZE = 12
export const SERVICE_RADIUS_OPTIONS_KM = [5, 10, 25, 50, 100] as const
export const SERVICE_DEFAULT_RADIUS_KM = 25

/**
 * Parámetros de la URL de servicios. `category` es la URL canónica (`/servicios?category=AC`); el resto va en español
 * y con los mismos nombres que `/buscar`, para que el estado se pueda compartir.
 */
export const SERVICE_PARAMS = {
  q: 'q',
  category: 'category',
  cityCode: 'ciudad',
  /** `1` = cerca de mí. Las coordenadas nunca van en la URL. */
  near: 'cerca',
  radiusKm: 'radio',
  priceMax: 'precio_max',
  onSale: 'oferta',
  bookable: 'agenda',
  openNow: 'abierto',
  sort: 'orden',
  page: 'pagina',
} as const

export const SERVICE_SORT_LABELS: Record<ServiceSort, string> = {
  relevance: 'Más relevantes',
  nearest: 'Más cercanos',
  price_asc: 'Menor precio',
  rating: 'Tiendas mejor calificadas',
}

const SORT_VALUES = Object.keys(SERVICE_SORT_LABELS) as ServiceSort[]

export interface ServiceState {
  q: string
  category?: ServiceCategory
  cityCode?: string
  near: boolean
  radiusKm?: number
  priceMax?: number
  onSale: boolean
  bookable: boolean
  openNow: boolean
  sort: ServiceSort
  page: number
}

function positiveInt(value: string | null, max: number): number | undefined {
  if (!value) return undefined
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, max) : undefined
}

export function parseServiceState(params: URLSearchParams): ServiceState {
  const category = params.get(SERVICE_PARAMS.category)?.trim().toUpperCase()
  const sort = params.get(SERVICE_PARAMS.sort) as ServiceSort | null
  const near = params.get(SERVICE_PARAMS.near) === '1'
  return {
    q: params.get(SERVICE_PARAMS.q)?.trim() ?? '',
    category: isServiceCategory(category) ? category : undefined,
    cityCode: params.get(SERVICE_PARAMS.cityCode)?.trim() || undefined,
    near,
    radiusKm: near ? positiveInt(params.get(SERVICE_PARAMS.radiusKm), 200) : undefined,
    priceMax: positiveInt(params.get(SERVICE_PARAMS.priceMax), 1_000_000_000),
    onSale: params.get(SERVICE_PARAMS.onSale) === '1',
    bookable: params.get(SERVICE_PARAMS.bookable) === '1',
    openNow: params.get(SERVICE_PARAMS.openNow) === '1',
    sort: sort && SORT_VALUES.includes(sort) ? sort : 'relevance',
    page: positiveInt(params.get(SERVICE_PARAMS.page), 1001) ?? 1,
  }
}

/** Serializa omitiendo los valores por defecto. */
export function toServiceParams(state: ServiceState): URLSearchParams {
  const params = new URLSearchParams()
  const set = (key: string, value: string | number | undefined) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  set(SERVICE_PARAMS.q, state.q)
  set(SERVICE_PARAMS.category, state.category)
  set(SERVICE_PARAMS.cityCode, state.cityCode)
  if (state.near) {
    set(SERVICE_PARAMS.near, 1)
    set(SERVICE_PARAMS.radiusKm, state.radiusKm)
  }
  set(SERVICE_PARAMS.priceMax, state.priceMax)
  if (state.onSale) set(SERVICE_PARAMS.onSale, 1)
  if (state.bookable) set(SERVICE_PARAMS.bookable, 1)
  if (state.openNow) set(SERVICE_PARAMS.openNow, 1)
  if (state.sort !== 'relevance') set(SERVICE_PARAMS.sort, state.sort)
  if (state.page > 1) set(SERVICE_PARAMS.page, state.page)
  return params
}

/**
 * Consulta para el servidor. Con ubicación exacta envía lat/lng; sin ella, «cerca» se apoya en la cookie `ap_area` del
 * área elegida (el servidor la lee). Sin ninguna de las dos no hay cercanía posible: se ordena por relevancia, porque
 * `nearest` o un radio sin ubicación son 400 `LOCATION_REQUIRED`.
 */
export function toServiceQuery(state: ServiceState, origin: GeoPoint | undefined): ServiceSearchQuery {
  const located = state.near && origin !== undefined
  const canNear = state.near && (origin !== undefined || readAreaCookie() !== undefined)
  return {
    q: state.q,
    category: state.category,
    cityCode: state.cityCode,
    lat: located ? origin.lat : undefined,
    lng: located ? origin.lng : undefined,
    radiusKm: canNear ? state.radiusKm : undefined,
    priceMax: state.priceMax,
    onSale: state.onSale,
    bookable: state.bookable,
    openNow: state.openNow,
    sort: state.sort === 'nearest' && !canNear ? 'relevance' : state.sort,
    page: state.page,
    pageSize: SERVICE_PAGE_SIZE,
  }
}

export function serviceQueryKey(query: ServiceSearchQuery): string {
  return `services:search?${JSON.stringify(query)}`
}

/** Filtros activos (sin contar texto, orden ni página). `skipCategory` si la categoría se elige aparte. */
export function countServiceFilters(state: ServiceState, options: { skipCategory?: boolean } = {}): number {
  let count = 0
  if (state.category && !options.skipCategory) count += 1
  if (state.cityCode) count += 1
  if (state.near) count += 1
  if (state.priceMax !== undefined) count += 1
  if (state.onSale) count += 1
  if (state.bookable) count += 1
  if (state.openNow) count += 1
  return count
}
