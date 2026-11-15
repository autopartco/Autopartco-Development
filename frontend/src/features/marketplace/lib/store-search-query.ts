import type { StoreSearchQuery } from '../api/types'
import { SEARCH_PARAMS } from './search-query'

export const STORE_PAGE_SIZE = 12

/** Estado de la pestaña «Tiendas» de `/buscar`: texto, ciudad y página (la pestaña la pone la URL). */
export interface StoreState {
  q: string
  cityCode?: string
  page: number
}

export function parseStoreState(params: URLSearchParams): StoreState {
  const page = Number.parseInt(params.get(SEARCH_PARAMS.page) ?? '', 10)
  return {
    q: params.get(SEARCH_PARAMS.q)?.trim() ?? '',
    cityCode: params.get(SEARCH_PARAMS.cityCode)?.trim() || undefined,
    page: Number.isFinite(page) && page > 0 ? Math.min(page, 1001) : 1,
  }
}

/** Serializa conservando `tab=stores`; omite los valores por defecto. */
export function toStoreParams(state: StoreState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.q) params.set(SEARCH_PARAMS.q, state.q)
  params.set(SEARCH_PARAMS.tab, 'stores')
  if (state.cityCode) params.set(SEARCH_PARAMS.cityCode, state.cityCode)
  if (state.page > 1) params.set(SEARCH_PARAMS.page, String(state.page))
  return params
}

export function toStoreQuery(state: StoreState): StoreSearchQuery {
  return { q: state.q, cityCode: state.cityCode, page: state.page, pageSize: STORE_PAGE_SIZE }
}

export function storeQueryKey(query: StoreSearchQuery): string {
  return `stores:search?${JSON.stringify(query)}`
}
