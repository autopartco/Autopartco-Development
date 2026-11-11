import type { GeoPoint } from '@/features/location/api/types'
import type { InterpretedQuery, SearchQuery, SearchSort } from '../api/types'

export const SEARCH_PAGE_SIZE = 12
export const RADIUS_OPTIONS_KM = [5, 10, 25, 50, 100] as const
export const DEFAULT_RADIUS_KM = 25

/** Parámetros de la URL de `/buscar` (en español, compartibles). */
export const SEARCH_PARAMS = {
  q: 'q',
  category: 'categoria',
  manufacturers: 'marca',
  store: 'tienda',
  makeId: 'vehiculo',
  modelId: 'modelo',
  versionId: 'version',
  year: 'anio',
  minPrice: 'precio_min',
  maxPrice: 'precio_max',
  inStock: 'disponible',
  onSale: 'oferta',
  cityCode: 'ciudad',
  /** `1` = cerca de mí. Las coordenadas nunca van en la URL: salen de la ubicación del dispositivo. */
  near: 'cerca',
  radiusKm: 'radio',
  minRating: 'calificacion',
  minProductRating: 'calificacion_producto',
  universal: 'universal',
  sort: 'orden',
  page: 'pagina',
  /** `0` = no interpretar el texto (se pone al quitar una ficha interpretada). */
  interpret: 'interpretar',
} as const

export const SEARCH_SORT_LABELS: Record<SearchSort, string> = {
  relevance: 'Más relevantes',
  price_asc: 'Menor precio',
  price_desc: 'Mayor precio',
  nearest: 'Más cercanos',
  newest: 'Más recientes',
  product_rating: 'Mejor calificados',
  rating: 'Tiendas mejor calificadas',
  name: 'Nombre (A–Z)',
}

const SORT_VALUES = Object.keys(SEARCH_SORT_LABELS) as SearchSort[]

/** Estado de búsqueda en la URL: la consulta más la intención de buscar cerca. */
export interface SearchState extends Omit<SearchQuery, 'lat' | 'lng'> {
  near: boolean
}

function positiveInt(value: string | null, max?: number): number | undefined {
  if (!value) return undefined
  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) return undefined
  return max !== undefined ? Math.min(parsed, max) : parsed
}

function text(value: string | null): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

export function parseSearchState(params: URLSearchParams, fixedCategory?: string): SearchState {
  const sort = params.get(SEARCH_PARAMS.sort) as SearchSort | null
  // Jerarquía válida: sin marca no hay modelo y sin modelo no hay versión (el servidor respondería 400).
  const makeId = text(params.get(SEARCH_PARAMS.makeId))
  const modelId = makeId ? text(params.get(SEARCH_PARAMS.modelId)) : undefined
  const versionId = modelId ? text(params.get(SEARCH_PARAMS.versionId)) : undefined
  const near = params.get(SEARCH_PARAMS.near) === '1'
  return {
    q: text(params.get(SEARCH_PARAMS.q)) ?? '',
    category: fixedCategory ?? text(params.get(SEARCH_PARAMS.category)),
    manufacturers: (params.get(SEARCH_PARAMS.manufacturers) ?? '').split(',').filter(Boolean),
    store: text(params.get(SEARCH_PARAMS.store)),
    makeId,
    modelId,
    versionId,
    year: positiveInt(params.get(SEARCH_PARAMS.year)),
    minPrice: positiveInt(params.get(SEARCH_PARAMS.minPrice)),
    maxPrice: positiveInt(params.get(SEARCH_PARAMS.maxPrice)),
    inStock: params.get(SEARCH_PARAMS.inStock) === '1',
    onSale: params.get(SEARCH_PARAMS.onSale) === '1',
    cityCode: text(params.get(SEARCH_PARAMS.cityCode)),
    near,
    radiusKm: near ? positiveInt(params.get(SEARCH_PARAMS.radiusKm), 200) : undefined,
    minRating: positiveInt(params.get(SEARCH_PARAMS.minRating), 5),
    minProductRating: positiveInt(params.get(SEARCH_PARAMS.minProductRating), 5),
    universal: params.get(SEARCH_PARAMS.universal) === '1',
    sort: sort && SORT_VALUES.includes(sort) ? sort : 'relevance',
    page: positiveInt(params.get(SEARCH_PARAMS.page), 1001) ?? 1,
    pageSize: SEARCH_PAGE_SIZE,
    interpret: params.get(SEARCH_PARAMS.interpret) !== '0',
  }
}

/** `/buscar` ordenado por cercanía («Cerca de ti» del inicio): `cerca=1&orden=nearest`. */
export function nearbyUrl(): string {
  return `/buscar?${SEARCH_PARAMS.near}=1&${SEARCH_PARAMS.sort}=nearest`
}

/** Serializa omitiendo los valores por defecto para mantener URLs cortas. */
export function toSearchParams(state: SearchState, options: { omitCategory?: boolean } = {}): URLSearchParams {
  const params = new URLSearchParams()
  const set = (key: string, value: string | number | undefined) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  set(SEARCH_PARAMS.q, state.q)
  if (!options.omitCategory) set(SEARCH_PARAMS.category, state.category)
  if (state.manufacturers.length > 0) set(SEARCH_PARAMS.manufacturers, state.manufacturers.join(','))
  set(SEARCH_PARAMS.store, state.store)
  set(SEARCH_PARAMS.makeId, state.makeId)
  set(SEARCH_PARAMS.modelId, state.makeId ? state.modelId : undefined)
  set(SEARCH_PARAMS.versionId, state.makeId && state.modelId ? state.versionId : undefined)
  set(SEARCH_PARAMS.year, state.year)
  set(SEARCH_PARAMS.minPrice, state.minPrice)
  set(SEARCH_PARAMS.maxPrice, state.maxPrice)
  if (state.inStock) set(SEARCH_PARAMS.inStock, 1)
  if (state.onSale) set(SEARCH_PARAMS.onSale, 1)
  set(SEARCH_PARAMS.cityCode, state.cityCode)
  if (state.near) {
    set(SEARCH_PARAMS.near, 1)
    set(SEARCH_PARAMS.radiusKm, state.radiusKm)
  }
  set(SEARCH_PARAMS.minRating, state.minRating)
  set(SEARCH_PARAMS.minProductRating, state.minProductRating)
  if (state.universal) set(SEARCH_PARAMS.universal, 1)
  if (state.sort !== 'relevance') set(SEARCH_PARAMS.sort, state.sort)
  if (state.page > 1) set(SEARCH_PARAMS.page, state.page)
  if (!state.interpret) set(SEARCH_PARAMS.interpret, 0)
  return params
}

/** Consulta para el servidor: añade las coordenadas solo si hay ubicación exacta y se pidió cerca. */
export function toSearchQuery(state: SearchState, origin: GeoPoint | undefined): SearchQuery {
  const { near, ...rest } = state
  const located = near && origin !== undefined
  return {
    ...rest,
    lat: located ? origin.lat : undefined,
    lng: located ? origin.lng : undefined,
    radiusKm: located ? rest.radiusKm : undefined,
    // `nearest` sin coordenadas es 400 `LOCATION_REQUIRED`: sin ubicación se ordena por relevancia.
    sort: rest.sort === 'nearest' && !located ? 'relevance' : rest.sort,
  }
}

/** Clave estable para cachear una consulta. */
export function searchQueryKey(query: SearchQuery): string {
  return `search?${JSON.stringify(query)}`
}

/** Filtros activos (sin contar texto, orden ni página). La categoría fija de una ruta no cuenta. */
export function countSearchFilters(state: SearchState, options: { fixedCategory?: boolean } = {}): number {
  let count = state.manufacturers.length
  if (state.category && !options.fixedCategory) count += 1
  if (state.store) count += 1
  if (state.makeId) count += 1
  if (state.year !== undefined) count += 1
  if (state.minPrice !== undefined || state.maxPrice !== undefined) count += 1
  if (state.inStock) count += 1
  if (state.onSale) count += 1
  if (state.cityCode) count += 1
  if (state.near) count += 1
  if (state.minRating !== undefined) count += 1
  if (state.minProductRating !== undefined) count += 1
  if (state.universal) count += 1
  return count
}

export type InterpretedChip = 'make' | 'model' | 'year' | 'city' | 'reference'

/**
 * Quitar una ficha interpretada: se vuelve a buscar con `interpret=false` y los filtros explícitos
 * que se mantienen (los demás reconocidos). Sin la marca no hay modelo (jerarquía del servidor).
 */
export function dropInterpretedChip(
  state: SearchState,
  interpreted: InterpretedQuery,
  chip: InterpretedChip,
): Partial<SearchState> {
  const keepMake = chip !== 'make'
  const keepModel = keepMake && chip !== 'model'
  return {
    interpret: false,
    makeId: keepMake ? (interpreted.makeId ?? state.makeId) : undefined,
    modelId: keepModel ? (interpreted.modelId ?? state.modelId) : undefined,
    versionId: keepModel ? state.versionId : undefined,
    year: chip === 'year' ? undefined : (interpreted.year ?? state.year),
    cityCode: chip === 'city' ? undefined : (interpreted.cityCode ?? state.cityCode),
  }
}
