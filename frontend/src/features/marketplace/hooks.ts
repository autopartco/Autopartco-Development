import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useAsync } from '@/lib/use-async'
import { getMarketplaceService } from './api/marketplace-service'
import type {
  MarketplaceCapabilities,
  MarketplaceService,
  Product,
  ProductQuery,
  SearchQuery,
  SearchSuggestions,
} from './api/types'
import { parseProductQuery, productQueryKey, toSearchParams as toProductSearchParams } from './lib/product-query'
import { parseSearchState, searchQueryKey, toSearchParams, type SearchState } from './lib/search-query'

function useMarketplace<T>(key: string, run: (service: MarketplaceService) => Promise<T>) {
  return useAsync(key, () => getMarketplaceService().then(run))
}

export function useCapabilities(): MarketplaceCapabilities | undefined {
  return useMarketplace('capabilities', async (service) => service.capabilities).data
}

export function useProductSearch(query: ProductQuery) {
  return useMarketplace(productQueryKey(query), (service) => service.searchProducts(query))
}

export function useListingSearch(query: SearchQuery) {
  return useMarketplace(searchQueryKey(query), (service) => service.search(query))
}

/**
 * Sugerencias del buscador: espera 250 ms tras la última tecla, cancela la petición anterior y
 * no consulta con menos de 2 letras. Un fallo simplemente no muestra sugerencias.
 */
export function useSearchSuggestions(text: string, near: { lat: number; lng: number } | undefined, enabled: boolean) {
  const trimmed = text.trim()
  const lat = near?.lat
  const lng = near?.lng
  const [state, setState] = useState<{ key: string; data: SearchSuggestions } | undefined>()
  const key = `${trimmed}|${lat ?? ''}|${lng ?? ''}`
  const active = enabled && trimmed.length >= 2

  useEffect(() => {
    if (!active) return
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      getMarketplaceService()
        .then((service) => service.suggest(trimmed, lat !== undefined && lng !== undefined ? { lat, lng } : undefined, controller.signal))
        .then(
          (data) => {
            if (!controller.signal.aborted) setState({ key, data })
          },
          () => {
            if (!controller.signal.aborted) setState({ key, data: { suggestions: [], products: [] } })
          },
        )
    }, 250)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [active, key, trimmed, lat, lng])

  return {
    data: active && state?.key === key ? state.data : undefined,
    loading: active && state?.key !== key,
  }
}

export function useProduct(id: string) {
  return useMarketplace(`product:${id}`, (service) => service.getProduct(id))
}

export function useRelatedProducts(product: Product | undefined, limit = 4) {
  return useMarketplace(`related:${product?.id ?? 'none'}:${limit}`, (service) =>
    product ? service.getRelatedProducts(product, limit) : Promise.resolve([]),
  )
}

/** Productos que se compran junto con este; un fallo no estorba la ficha (la sección simplemente no se muestra). */
export function useFrequentlyBought(productId: string) {
  return useMarketplace(`frequently-bought:${productId}`, (service) => service.getFrequentlyBought(productId).catch(() => []))
}

export function useFilterOptions() {
  return useMarketplace('filter-options', (service) => service.getFilterOptions())
}

const NO_VEHICLE_KEY = 'vehicles:none'

/** Catálogo de vehículos en cascada: cada nivel se pide al elegir el anterior y queda en caché. */
export function useVehicleMakes() {
  return useMarketplace('vehicles:makes', (service) => service.listVehicleMakes())
}

export function useVehicleModels(makeId: string | undefined) {
  return useMarketplace(makeId ? `vehicles:models:${makeId}` : NO_VEHICLE_KEY, (service) =>
    makeId ? service.listVehicleModels(makeId) : Promise.resolve([]),
  )
}

export function useVehicleVersions(modelId: string | undefined) {
  return useMarketplace(modelId ? `vehicles:versions:${modelId}` : NO_VEHICLE_KEY, (service) =>
    modelId ? service.listVehicleVersions(modelId) : Promise.resolve([]),
  )
}

/** «Renault Duster 2.0 4x4» a partir de los ids del filtro; `undefined` mientras se resuelven los nombres. */
export function useVehicleFilterLabel(selection: { makeId?: string; modelId?: string; versionId?: string }): string | undefined {
  const makes = useVehicleMakes()
  const models = useVehicleModels(selection.makeId)
  const versions = useVehicleVersions(selection.modelId)
  if (!selection.makeId) return undefined
  const make = makes.data?.find((item) => item.id === selection.makeId)
  if (!make) return undefined
  const model = selection.modelId && !models.isStale ? models.data?.find((item) => item.id === selection.modelId) : undefined
  const version = selection.versionId && !versions.isStale ? versions.data?.find((item) => item.id === selection.versionId) : undefined
  return [make.name, model?.name, version?.name].filter(Boolean).join(' ')
}

export function useCategories() {
  return useMarketplace('categories', (service) => service.getCategories())
}

export function useStores() {
  return useMarketplace('stores', (service) => service.listStores())
}

export function useStoreProfile(storeId: string) {
  return useMarketplace(`store-profile:${storeId}`, (service) => service.getStoreProfile(storeId))
}

export function useStoreReviews(storeId: string, page: number) {
  return useMarketplace(`store-reviews:${storeId}:${page}`, (service) => service.listStoreReviews(storeId, page))
}

/** Pedidos con los que el comprador puede calificar; la clave incluye la sesión para no reutilizar la de otro usuario. */
export function useReviewEligibility(storeId: string, sessionKey: string | undefined) {
  return useMarketplace(`store-review-eligibility:${storeId}:${sessionKey ?? 'anon'}`, (service) =>
    sessionKey ? service.getReviewEligibility(storeId).catch(() => []) : Promise.resolve([]),
  )
}

/**
 * Estado de búsqueda guardado en la URL. `fixedCategory` viene de rutas como
 * `/categoria/:slug` y no se escribe en los query params.
 */
export function useProductQueryState(fixedCategory?: string) {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = parseProductQuery(searchParams, fixedCategory)

  const update = useCallback(
    (patch: Partial<ProductQuery>, options: { keepPage?: boolean } = {}) => {
      setSearchParams(
        (current) => {
          const next = { ...parseProductQuery(current, fixedCategory), ...patch }
          if (!options.keepPage) next.page = 1
          return toProductSearchParams(next, { omitCategory: Boolean(fixedCategory) })
        },
        // Los filtros reemplazan la entrada del historial; cambiar de página crea una nueva.
        { replace: !options.keepPage, preventScrollReset: !options.keepPage },
      )
    },
    [fixedCategory, setSearchParams],
  )

  return [query, update] as const
}

/**
 * Estado del motor de búsqueda v2 guardado en la URL (`/buscar?...`). `fixedCategory` viene de
 * `/categoria/:slug` y no se escribe en los parámetros.
 */
export function useSearchState(fixedCategory?: string) {
  const [searchParams, setSearchParams] = useSearchParams()
  const state = parseSearchState(searchParams, fixedCategory)

  const update = useCallback(
    (patch: Partial<SearchState>, options: { keepPage?: boolean } = {}) => {
      setSearchParams(
        (current) => {
          const next = { ...parseSearchState(current, fixedCategory), ...patch }
          if (!options.keepPage) next.page = 1
          return toSearchParams(next, { omitCategory: Boolean(fixedCategory) })
        },
        { replace: !options.keepPage, preventScrollReset: !options.keepPage },
      )
    },
    [fixedCategory, setSearchParams],
  )

  return [state, update] as const
}
