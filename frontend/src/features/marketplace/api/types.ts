/**
 * Modelo de UI del marketplace. Es independiente de los DTO del backend: cada
 * implementación de `MarketplaceService` mapea su origen a estos tipos.
 * Los campos opcionales se muestran solo cuando el servicio los devuelve.
 */

export interface Category {
  id: string
  slug: string
  name: string
  description?: string
  productCount?: number
}

export interface Brand {
  id: string
  name: string
}

export interface VehicleFit {
  id: string
  make: string
  model: string
  version?: string
  yearFrom?: number
  yearTo?: number
}

export interface StoreSummary {
  id: string
  name: string
  city?: string
  /**
   * Distintivo "Recomendado": tiendas con buen servicio y respuesta en Autopartco.
   * Todas las tiendas están verificadas, así que eso no se marca. Lo informa el servidor;
   * cómo se gana o se pierde no lo decide el frontend.
   */
  recommended?: boolean
  logoUrl?: string
  productCount?: number
}

export type AvailabilityStatus = 'in_stock' | 'low_stock' | 'out_of_stock'

export interface Availability {
  status: AvailabilityStatus
  quantity?: number
  branchName?: string
}

export interface Specification {
  label: string
  value: string
}

export interface Product {
  id: string
  /** Clave única del listado (producto + sucursal); úsala como `key` en listas. */
  listingKey: string
  name: string
  partNumber?: string
  description?: string
  /** Precio final que cobra la tienda (ya con promociones aplicadas por el servidor). */
  price: number
  /** Precio antes de la promoción, si el servidor informa un descuento. */
  listPrice?: number
  discountAmount?: number
  promotionName?: string
  images: string[]
  category?: Category
  brand?: Brand
  store?: StoreSummary
  availability?: Availability
  compatibilities: VehicleFit[]
  specifications: Specification[]
}

export type SortOption = 'relevance' | 'price_asc' | 'price_desc'

export interface ProductQuery {
  q: string
  category?: string
  brands: string[]
  store?: string
  make?: string
  model?: string
  year?: number
  minPrice?: number
  maxPrice?: number
  inStockOnly: boolean
  recommendedOnly: boolean
  sort: SortOption
  page: number
  pageSize: number
}

export interface ProductPage {
  items: Product[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface VehicleMakeOption {
  make: string
  models: string[]
}

export interface FilterOptions {
  categories: Category[]
  brands: Brand[]
  vehicles: VehicleMakeOption[]
  years?: number[]
  priceRange?: { min: number; max: number }
}

export type FilterKey =
  | 'category'
  | 'brand'
  | 'vehicle'
  | 'year'
  | 'price'
  | 'availability'
  | 'recommendedStore'
  | 'store'

/**
 * Qué puede resolver el origen de datos. La UI solo muestra los controles que
 * el servicio soporta, en lugar de fingir búsquedas o filtros.
 */
export interface MarketplaceCapabilities {
  textSearch: boolean
  sorting: boolean
  pagination: boolean
  filters: ReadonlySet<FilterKey>
}

export interface MarketplaceService {
  readonly capabilities: MarketplaceCapabilities
  searchProducts(query: ProductQuery): Promise<ProductPage>
  getProduct(id: string): Promise<Product>
  getRelatedProducts(product: Product, limit: number): Promise<Product[]>
  getFilterOptions(): Promise<FilterOptions>
  getCategories(): Promise<Category[]>
  listStores(): Promise<StoreSummary[]>
}

export class MarketplaceError extends Error {
  readonly status?: number
  readonly code?: string

  constructor(message: string, options: { status?: number; code?: string; cause?: unknown } = {}) {
    super(message, { cause: options.cause })
    this.name = 'MarketplaceError'
    this.status = options.status
    this.code = options.code
  }

  get isNotFound(): boolean {
    return this.status === 404
  }
}

export function isNotFoundError(error: unknown): boolean {
  return error instanceof MarketplaceError && error.isNotFound
}
