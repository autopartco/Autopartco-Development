import type { ProductQuery, SortOption } from '../api/types'

export const PAGE_SIZE = 12

/** Nombres de los parámetros en la URL (en español, visibles para la persona). */
export const QUERY_PARAMS = {
  q: 'q',
  category: 'categoria',
  brands: 'marca',
  store: 'tienda',
  makeId: 'vehiculo',
  modelId: 'modelo',
  versionId: 'version',
  minPrice: 'precio_min',
  maxPrice: 'precio_max',
  inStockOnly: 'disponible',
  recommendedOnly: 'recomendada',
  sort: 'orden',
  page: 'pagina',
} as const

const SORT_VALUES: readonly SortOption[] = ['relevance', 'price_asc', 'price_desc']

export const SORT_LABELS: Record<SortOption, string> = {
  relevance: 'Más relevantes',
  price_asc: 'Menor precio',
  price_desc: 'Mayor precio',
}

function positiveInt(value: string | null): number | undefined {
  if (!value) return undefined
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

function text(value: string | null): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

export function parseProductQuery(params: URLSearchParams, fixedCategory?: string): ProductQuery {
  const sort = params.get(QUERY_PARAMS.sort) as SortOption | null
  // Jerarquía válida: sin marca no hay modelo y sin modelo no hay versión (el servidor respondería 400).
  const makeId = text(params.get(QUERY_PARAMS.makeId))
  const modelId = makeId ? text(params.get(QUERY_PARAMS.modelId)) : undefined
  const versionId = modelId ? text(params.get(QUERY_PARAMS.versionId)) : undefined
  return {
    q: text(params.get(QUERY_PARAMS.q)) ?? '',
    category: fixedCategory ?? text(params.get(QUERY_PARAMS.category)),
    brands: (params.get(QUERY_PARAMS.brands) ?? '').split(',').filter(Boolean),
    store: text(params.get(QUERY_PARAMS.store)),
    makeId,
    modelId,
    versionId,
    minPrice: positiveInt(params.get(QUERY_PARAMS.minPrice)),
    maxPrice: positiveInt(params.get(QUERY_PARAMS.maxPrice)),
    inStockOnly: params.get(QUERY_PARAMS.inStockOnly) === '1',
    recommendedOnly: params.get(QUERY_PARAMS.recommendedOnly) === '1',
    sort: sort && SORT_VALUES.includes(sort) ? sort : 'relevance',
    page: positiveInt(params.get(QUERY_PARAMS.page)) ?? 1,
    pageSize: PAGE_SIZE,
  }
}

/** Serializa la consulta omitiendo valores por defecto para mantener URLs cortas. */
export function toSearchParams(query: ProductQuery, options: { omitCategory?: boolean } = {}): URLSearchParams {
  const params = new URLSearchParams()
  const set = (key: string, value: string | number | undefined) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  set(QUERY_PARAMS.q, query.q)
  if (!options.omitCategory) set(QUERY_PARAMS.category, query.category)
  if (query.brands.length > 0) set(QUERY_PARAMS.brands, query.brands.join(','))
  set(QUERY_PARAMS.store, query.store)
  set(QUERY_PARAMS.makeId, query.makeId)
  set(QUERY_PARAMS.modelId, query.modelId)
  set(QUERY_PARAMS.versionId, query.versionId)
  set(QUERY_PARAMS.minPrice, query.minPrice)
  set(QUERY_PARAMS.maxPrice, query.maxPrice)
  if (query.inStockOnly) set(QUERY_PARAMS.inStockOnly, 1)
  if (query.recommendedOnly) set(QUERY_PARAMS.recommendedOnly, 1)
  if (query.sort !== 'relevance') set(QUERY_PARAMS.sort, query.sort)
  if (query.page > 1) set(QUERY_PARAMS.page, query.page)
  return params
}

/** Clave estable para cachear una consulta. */
export function productQueryKey(query: ProductQuery): string {
  return `products?${toSearchParams(query).toString()}&size=${query.pageSize}`
}

/** Filtros activos (sin contar texto, orden ni página). La categoría fija de una ruta no cuenta. */
export function countActiveFilters(query: ProductQuery, options: { fixedCategory?: boolean } = {}): number {
  let count = query.brands.length
  if (query.category && !options.fixedCategory) count += 1
  if (query.store) count += 1
  if (query.makeId) count += 1
  if (query.minPrice !== undefined || query.maxPrice !== undefined) count += 1
  if (query.inStockOnly) count += 1
  if (query.recommendedOnly) count += 1
  return count
}

export function searchUrl(params: Partial<Record<keyof typeof QUERY_PARAMS, string | undefined>>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value) search.set(QUERY_PARAMS[key as keyof typeof QUERY_PARAMS], value)
  }
  const qs = search.toString()
  return qs ? `/buscar?${qs}` : '/buscar'
}

export function emptyQuery(overrides: Partial<ProductQuery> = {}): ProductQuery {
  return { ...parseProductQuery(new URLSearchParams()), ...overrides }
}
