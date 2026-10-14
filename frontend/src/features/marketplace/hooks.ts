import { useCallback } from 'react'
import { useSearchParams } from 'react-router'
import { useAsync } from '@/lib/use-async'
import { getMarketplaceService } from './api/marketplace-service'
import type { MarketplaceCapabilities, MarketplaceService, Product, ProductQuery } from './api/types'
import { parseProductQuery, productQueryKey, toSearchParams } from './lib/product-query'

function useMarketplace<T>(key: string, run: (service: MarketplaceService) => Promise<T>) {
  return useAsync(key, () => getMarketplaceService().then(run))
}

export function useCapabilities(): MarketplaceCapabilities | undefined {
  return useMarketplace('capabilities', async (service) => service.capabilities).data
}

export function useProductSearch(query: ProductQuery) {
  return useMarketplace(productQueryKey(query), (service) => service.searchProducts(query))
}

export function useProduct(id: string) {
  return useMarketplace(`product:${id}`, (service) => service.getProduct(id))
}

export function useRelatedProducts(product: Product | undefined, limit = 4) {
  return useMarketplace(`related:${product?.id ?? 'none'}:${limit}`, (service) =>
    product ? service.getRelatedProducts(product, limit) : Promise.resolve([]),
  )
}

export function useFilterOptions() {
  return useMarketplace('filter-options', (service) => service.getFilterOptions())
}

export function useCategories() {
  return useMarketplace('categories', (service) => service.getCategories())
}

export function useStores() {
  return useMarketplace('stores', (service) => service.listStores())
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
          return toSearchParams(next, { omitCategory: Boolean(fixedCategory) })
        },
        // Los filtros reemplazan la entrada del historial; cambiar de página crea una nueva.
        { replace: !options.keepPage, preventScrollReset: !options.keepPage },
      )
    },
    [fixedCategory, setSearchParams],
  )

  return [query, update] as const
}
