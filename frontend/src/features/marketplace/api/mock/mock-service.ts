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
import { MOCK_BRANDS, MOCK_CATEGORIES, MOCK_PRODUCTS, MOCK_STORES, MOCK_VEHICLE_CATALOG, ruleMatches } from './mock-data'

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
          ...product.vehicleRules.map((rule) => `${rule.makeName} ${rule.modelName ?? ''}`),
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
  // Como el servidor: los universales aparecen siempre; el resto, si alguna regla cubre el vehículo.
  if (query.makeId && !product.universal && !product.vehicleRules.some((rule) => ruleMatches(rule, query))) return false
  return true
}

/** 400 `VEHICLE_FILTER_INVALID`: el modelo exige la marca y la versión exige el modelo. */
function assertVehicleFilter(query: ProductQuery): void {
  if ((query.modelId && !query.makeId) || (query.versionId && !query.modelId)) {
    throw new MarketplaceError('El filtro de vehículo no es válido: elige primero la marca y luego el modelo.', {
      status: 400,
      code: 'VEHICLE_FILTER_INVALID',
    })
  }
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
  let min = Number.POSITIVE_INFINITY
  let max = 0
  for (const product of MOCK_PRODUCTS) {
    min = Math.min(min, product.price)
    max = Math.max(max, product.price)
  }
  return {
    categories: MOCK_CATEGORIES,
    brands: MOCK_BRANDS,
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
      filters: new Set(['category', 'brand', 'vehicle', 'price', 'availability', 'recommendedStore', 'store']),
    },

    async searchProducts(query) {
      await simulateNetwork()
      assertVehicleFilter(query)
      return search(query)
    },

    async listVehicleMakes() {
      await simulateNetwork()
      return MOCK_VEHICLE_CATALOG.map(({ id, name }) => ({ id, name })).toSorted((a, b) => a.name.localeCompare(b.name, 'es'))
    },

    async listVehicleModels(makeId) {
      await simulateNetwork()
      const make = MOCK_VEHICLE_CATALOG.find((item) => item.id === makeId)
      if (!make) throw new MarketplaceError('No encontramos esa marca.', { status: 404, code: 'VEHICLE_MAKE_NOT_FOUND' })
      return make.models.map(({ id, name }) => ({ id, name })).toSorted((a, b) => a.name.localeCompare(b.name, 'es'))
    },

    async listVehicleVersions(modelId) {
      await simulateNetwork()
      const model = MOCK_VEHICLE_CATALOG.flatMap((make) => make.models).find((item) => item.id === modelId)
      if (!model) throw new MarketplaceError('No encontramos ese modelo.', { status: 404, code: 'VEHICLE_MODEL_NOT_FOUND' })
      return model.versions.map(({ id, label }) => ({ id, name: label }))
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
