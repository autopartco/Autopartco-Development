import {
  MarketplaceError,
  type Category,
  type FilterOptions,
  type MarketplaceService,
  type Product,
  type ProductPage,
  type ProductQuery,
  type StoreSummary,
} from '../types'
import { MOCK_BRANDS, MOCK_CATEGORIES, MOCK_PRODUCTS, MOCK_STORES } from './mock-data'

/**
 * Implementación simulada: búsqueda, filtros, orden y paginación en el cliente,
 * con latencia artificial. Añade `?simulateError=1` a la URL para forzar errores.
 */

const MIN_LATENCY_MS = 250
const MAX_LATENCY_MS = 650

const ACCENTS = /[̀-ͯ]/g
const WORD_SPLIT = /\s+/

function normalize(value: string): string {
  return value.normalize('NFD').replace(ACCENTS, '').toLowerCase()
}

async function simulateNetwork(): Promise<void> {
  const latency = MIN_LATENCY_MS + Math.random() * (MAX_LATENCY_MS - MIN_LATENCY_MS)
  await new Promise((resolve) => setTimeout(resolve, latency))
  if (new URLSearchParams(window.location.search).has('simulateError')) {
    throw new MarketplaceError('Error simulado: el servicio de catálogo no respondió.', { status: 503 })
  }
}

/** Texto indexado por producto, precalculado una sola vez. */
const searchIndex = new Map(
  MOCK_PRODUCTS.map((product) => [
    product.listingKey,
    {
      name: normalize(product.name),
      all: normalize(
        [
          product.name,
          product.partNumber,
          product.brand?.name,
          product.category?.name,
          ...product.compatibilities.map((fit) => `${fit.make} ${fit.model}`),
        ]
          .filter(Boolean)
          .join(' '),
      ),
    },
  ]),
)

function relevance(product: Product, terms: string[]): number {
  const indexed = searchIndex.get(product.listingKey)
  if (!indexed) return 0
  let score = 0
  for (const term of terms) {
    if (!indexed.all.includes(term)) return -1
    score += indexed.name.includes(term) ? 2 : 1
  }
  return score
}

function matchesFilters(product: Product, query: ProductQuery): boolean {
  if (query.category && product.category?.slug !== query.category) return false
  if (query.brands.length > 0 && !(product.brand && query.brands.includes(product.brand.id))) return false
  if (query.store && product.store?.id !== query.store) return false
  if (query.minPrice !== undefined && product.price < query.minPrice) return false
  if (query.maxPrice !== undefined && product.price > query.maxPrice) return false
  if (query.inStockOnly && product.availability?.status === 'out_of_stock') return false
  if (query.recommendedOnly && !product.store?.recommended) return false
  if (query.make || query.model || query.year) {
    const fits = product.compatibilities.some(
      (fit) =>
        (!query.make || fit.make === query.make) &&
        (!query.model || fit.model === query.model) &&
        (!query.year || ((fit.yearFrom ?? 0) <= query.year && query.year <= (fit.yearTo ?? 9999))),
    )
    if (!fits) return false
  }
  return true
}

function search(query: ProductQuery): ProductPage {
  const terms = normalize(query.q).split(WORD_SPLIT).filter(Boolean)
  const scored: { product: Product; score: number; order: number }[] = []
  MOCK_PRODUCTS.forEach((product, order) => {
    if (!matchesFilters(product, query)) return
    const score = terms.length > 0 ? relevance(product, terms) : 0
    if (score >= 0) scored.push({ product, score, order })
  })

  const sorted = scored.toSorted((a, b) => {
    if (query.sort === 'price_asc') return a.product.price - b.product.price
    if (query.sort === 'price_desc') return b.product.price - a.product.price
    return b.score - a.score || a.order - b.order
  })

  const total = sorted.length
  const totalPages = Math.max(1, Math.ceil(total / query.pageSize))
  const page = Math.min(query.page, totalPages)
  const start = (page - 1) * query.pageSize
  return {
    items: sorted.slice(start, start + query.pageSize).map((entry) => entry.product),
    total,
    page,
    pageSize: query.pageSize,
    totalPages,
  }
}

function filterOptions(): FilterOptions {
  const makes = new Map<string, Set<string>>()
  const years = new Set<number>()
  let min = Number.POSITIVE_INFINITY
  let max = 0
  for (const product of MOCK_PRODUCTS) {
    min = Math.min(min, product.price)
    max = Math.max(max, product.price)
    for (const fit of product.compatibilities) {
      const models = makes.get(fit.make) ?? new Set<string>()
      models.add(fit.model)
      makes.set(fit.make, models)
      if (fit.yearFrom && fit.yearTo) {
        for (let year = fit.yearFrom; year <= fit.yearTo; year += 1) years.add(year)
      }
    }
  }
  return {
    categories: MOCK_CATEGORIES,
    brands: MOCK_BRANDS,
    vehicles: [...makes.entries()]
      .map(([make, models]) => ({ make, models: [...models].toSorted() }))
      .toSorted((a, b) => a.make.localeCompare(b.make, 'es')),
    years: [...years].toSorted((a, b) => b - a),
    priceRange: { min, max },
  }
}

function countBy<K>(keyOf: (product: Product) => K | undefined): Map<K, number> {
  const counts = new Map<K, number>()
  for (const product of MOCK_PRODUCTS) {
    const key = keyOf(product)
    if (key !== undefined) counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return counts
}

export function createMockMarketplaceService(): MarketplaceService {
  return {
    capabilities: {
      textSearch: true,
      sorting: true,
      pagination: true,
      filters: new Set(['category', 'brand', 'vehicle', 'year', 'price', 'availability', 'recommendedStore', 'store']),
    },

    async searchProducts(query) {
      await simulateNetwork()
      return search(query)
    },

    async getProduct(id) {
      await simulateNetwork()
      const product = MOCK_PRODUCTS.find((item) => item.id === id)
      if (!product) throw new MarketplaceError('No se encontró el repuesto.', { status: 404, code: 'PRODUCT_NOT_FOUND' })
      return product
    },

    async getRelatedProducts(product, limit) {
      await simulateNetwork()
      return MOCK_PRODUCTS.filter(
        (item) => item.id !== product.id && item.category?.id === product.category?.id,
      ).slice(0, limit)
    },

    async getFilterOptions() {
      await simulateNetwork()
      return filterOptions()
    },

    async getCategories(): Promise<Category[]> {
      await simulateNetwork()
      const counts = countBy((product) => product.category?.id)
      return MOCK_CATEGORIES.map((category) => ({ ...category, productCount: counts.get(category.id) ?? 0 }))
    },

    async listStores(): Promise<StoreSummary[]> {
      await simulateNetwork()
      const counts = countBy((product) => product.store?.id)
      return MOCK_STORES.map((store) => ({ ...store, productCount: counts.get(store.id) ?? 0 }))
    },
  }
}
