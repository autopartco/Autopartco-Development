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
  /** `false` = fabricante pendiente de aprobación: su nombre se muestra igual, con un aviso. */
  approved?: boolean
}

/** Regla de compatibilidad: un nivel en `null` significa «todos» (todos los modelos / todas las versiones). */
export interface VehicleRule {
  makeId: string
  makeName: string
  modelId: string | null
  modelName: string | null
  versionId: string | null
  versionLabel: string | null
}

/** Opción del catálogo de vehículos en cascada (marca, modelo o versión). */
export interface VehicleOption {
  id: string
  name: string
}

/** Variantes de una imagen: miniatura (400), tarjeta (800) y original para el detalle. */
export interface ProductImage {
  url: string
  thumbUrl: string
  cardUrl: string
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
  /** Sucursal del listado; el carrito y el checkout la envían al servidor. En datos mock es el id de la tienda. */
  branchId?: string
  name: string
  partNumber?: string
  description?: string
  /** Precio final que cobra la tienda (ya con promociones aplicadas por el servidor). */
  price: number
  /** Precio antes de la promoción, si el servidor informa un descuento. */
  listPrice?: number
  discountAmount?: number
  promotionName?: string
  /**
   * Audiencia de la promoción aplicada (`ALL`, `PLATFORM_FREQUENT`, `STORE_*`). Si no es `ALL`, el precio es un precio
   * para clientes frecuentes. El servidor decide a quién aplica; aquí solo se etiqueta.
   */
  promotionAudience?: string
  images: ProductImage[]
  category?: Category
  brand?: Brand
  store?: StoreSummary
  availability?: Availability
  /** No depende del vehículo: aparece en cualquier búsqueda por vehículo. */
  universal: boolean
  vehicleRules: VehicleRule[]
  specifications: Specification[]
  /** Solo en la búsqueda: distancia en km a la sede, cuando se envió la ubicación. */
  distanceKm?: number
  /** Solo en la búsqueda: es un anuncio pagado; debe llevar la etiqueta «Patrocinado». */
  sponsored?: boolean
  /** Token firmado del anuncio para registrar impresión y clic. */
  adImpressionToken?: string
  /** Promedio de reseñas del producto (un decimal); `undefined` sin reseñas. Nunca se muestra «0,0». */
  rating?: number
  /** Reseñas visibles del producto. */
  reviewCount?: number
}

/** Por qué se sugiere: pedidos reales, mismo vehículo o misma categoría. */
export type FrequentlyBoughtReason = 'ORDERS' | 'COMPATIBILITY' | 'CATEGORY'

export interface FrequentlyBoughtItem {
  productId: string
  name: string
  price: number
  image?: string
  reason: FrequentlyBoughtReason
}

export type SortOption = 'relevance' | 'price_asc' | 'price_desc'

export interface ProductQuery {
  q: string
  category?: string
  brands: string[]
  store?: string
  /** Sucursal de `store`; el servidor deja solo el inventario publicado de esa sede. */
  branch?: string
  /** Filtro por vehículo: el modelo exige la marca y la versión exige el modelo. */
  makeId?: string
  modelId?: string
  versionId?: string
  minPrice?: number
  maxPrice?: number
  inStockOnly: boolean
  recommendedOnly: boolean
  sort: SortOption
  page: number
  pageSize: number
}

export type SearchSort = 'relevance' | 'price_asc' | 'price_desc' | 'nearest' | 'newest' | 'rating' | 'product_rating' | 'name'

/** Consulta del motor de búsqueda (`GET /marketplace/search`). `page` va desde 1. */
export interface SearchQuery {
  q: string
  /** Slug o id de categoría; el servicio lo resuelve a `categoryId`. */
  category?: string
  manufacturers: string[]
  makeId?: string
  modelId?: string
  versionId?: string
  year?: number
  minPrice?: number
  maxPrice?: number
  inStock: boolean
  onSale: boolean
  store?: string
  cityCode?: string
  lat?: number
  lng?: number
  radiusKm?: number
  /** Calificación mínima de la tienda (1–5). */
  minRating?: number
  /** Calificación mínima del producto (1–5). */
  minProductRating?: number
  universal: boolean
  sort: SearchSort
  page: number
  pageSize: number
  /** `false` desactiva la interpretación del texto (al quitar un chip). */
  interpret: boolean
}

export interface InterpretedQuery {
  text: string
  makeId?: string
  makeName?: string
  modelId?: string
  modelName?: string
  year?: number
  cityCode?: string
  cityName?: string
  reference?: string
}

export interface IdFacet {
  id: string
  name: string
  count: number
}

export interface SearchFacets {
  categories: IdFacet[]
  manufacturers: IdFacet[]
  cities: { code: string; name: string; count: number }[]
  price?: { min: number; max: number }
  inStock: number
  onSale: number
}

export interface SearchResult {
  items: Product[]
  /** Anuncios que van arriba de los resultados. */
  sponsored: Product[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  /** Sin coincidencias exactas: son resultados parecidos. */
  fuzzy: boolean
  interpreted?: InterpretedQuery
  facets?: SearchFacets
}

export type SuggestionKind = 'CATEGORY' | 'MANUFACTURER' | 'VEHICLE' | 'QUERY'

export interface SearchSuggestions {
  suggestions: { term: string; kind: SuggestionKind }[]
  products: Product[]
}

export interface ProductPage {
  items: Product[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface FilterOptions {
  categories: Category[]
  brands: Brand[]
  priceRange?: { min: number; max: number }
}

export type FilterKey =
  | 'category'
  | 'brand'
  | 'vehicle'
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

/** Contacto público que el almacén decide mostrar; redes como usuario, sin URL. */
export interface StoreContact {
  whatsapp?: string
  phone?: string
  website?: string
  instagram?: string
  facebook?: string
  tiktok?: string
  youtube?: string
  x?: string
  email?: string
}

/** Categorías de servicio del almacén (`ServiceCategory` del servidor). */
export type ServiceCategory =
  | 'MECHANICS'
  | 'ELECTRICAL'
  | 'ALIGNMENT_BALANCING'
  | 'TIRES'
  | 'LUBRICATION'
  | 'BODY_PAINT'
  | 'INSTALLATION'
  | 'DIAGNOSTICS'
  | 'AC'
  | 'OTHER'

/** Servicio que ofrece el almacén (instalación, alineación…). `priceFrom` en COP; sin él, «a cotizar». */
export interface StoreService {
  id: string
  category: ServiceCategory
  name: string
  description?: string
  priceFrom?: number
  durationMinutes?: number
  /** Sedes que lo prestan. */
  branchIds: string[]
}

/** Horario semanal: días `mon..sun` con franjas `[«08:00», «18:00»]` y notas libres. */
export interface OpeningHours {
  mon?: [string, string][]
  tue?: [string, string][]
  wed?: [string, string][]
  thu?: [string, string][]
  fri?: [string, string][]
  sat?: [string, string][]
  sun?: [string, string][]
  notes?: string
}

/** Sucursal pública (`GET /marketplace/stores/{id}/branches`). */
export interface StoreBranch {
  id: string
  name: string
  address: string
  city?: string
  department?: string
  /** Sin ambas coordenadas la sede solo aparece en la lista, no en el mapa. */
  point?: { lat: number; lng: number }
  phone?: string
  /** Ya resuelto por el servidor (cae al del almacén; ausente si el almacén desactivó WhatsApp). */
  whatsapp?: string
  openingHours?: OpeningHours
  /** `undefined` cuando la sede no publicó horario semanal. */
  openNow?: boolean
  serviceIds: string[]
}

/** Perfil público del almacén (`GET /marketplace/stores/{id}`). Nunca incluye NIT ni correo. */
export interface StoreProfile {
  id: string
  name: string
  description?: string
  logoUrl?: string
  bannerUrl?: string
  /** Ciudades de las sedes activas, sin repetir. */
  cities: string[]
  branchCount: number
  /** `YYYY-MM-DD`. */
  memberSince?: string
  /** Promedio con un decimal; `undefined` sin reseñas. */
  ratingAverage?: number
  ratingCount: number
  publishedProducts: number
  contact: StoreContact
  tagline?: string
  verified: boolean
  whatsappEnabled: boolean
  /** Plantilla con `{producto}` y `{enlace}`. */
  whatsappMessage?: string
  services: StoreService[]
  featuredProductIds: string[]
}

/** Reseñas de un producto o de un almacén (mismas formas; solo cambian los códigos de categoría). */
export interface ReviewTarget {
  type: 'product' | 'store'
  id: string
}

/** Código de categoría del servidor (`ProductAspect` / `StoreAspect`); el texto vive en `lib/review-labels`. */
export type ReviewAspectCode = string

export interface ReviewAspectSummary {
  aspect: ReviewAspectCode
  /** Promedio con un decimal. */
  average: number
  count: number
}

/** Resumen público de reseñas visibles. `average` es `undefined` sin reseñas. */
export interface ReviewSummary {
  average?: number
  count: number
  /** Reseñas por estrella: índice 0 = 1 estrella … índice 4 = 5 estrellas. */
  distribution: [number, number, number, number, number]
  aspects: ReviewAspectSummary[]
  /** Categorías destacadas (insignias); el servidor ya aplicó el mínimo y el umbral. */
  highlights: ReviewAspectCode[]
}

export interface ReviewReply {
  text: string
  repliedAt: string
}

export interface Review {
  id: string
  rating: number
  comment?: string
  aspects: Record<ReviewAspectCode, number>
  /** «Ana P.»: nombre + inicial del apellido. */
  author: string
  verified: boolean
  createdAt: string
  updatedAt: string
  reply?: ReviewReply
}

export interface ReviewPage {
  items: Review[]
  page: number
  totalPages: number
  total: number
}

export type ReviewSort = 'recent' | 'highest' | 'lowest'

export interface ReviewListQuery {
  page: number
  sort: ReviewSort
  /** Solo reseñas de esta calificación (1–5). */
  stars?: number
  withComment: boolean
}

/** Qué puede hacer la persona con sesión con un producto (`GET /products/{id}/reviews/eligibility`). */
export interface ProductReviewEligibility {
  eligibleOrderIds: string[]
  existingReviewId?: string
  editableUntil?: string
}

/** Igual que el del producto (`GET /stores/{id}/reviews/eligibility`): la reseña vigente es la del pedido entregado más reciente. */
export type StoreReviewEligibility = ProductReviewEligibility

/** Punto del área elegida para el inicio; sin él el servidor devuelve datos de todo el país. */
export interface HomeQuery {
  lat?: number
  lng?: number
  cityCode?: string
}

/** Cifras reales de la plataforma (`GET /marketplace/home`). */
export interface PlatformStats {
  stores: number
  cities: number
  products: number
}

export interface HomeCategory {
  id: string
  code: string
  productCount: number
}

export interface HomeData {
  stats: PlatformStats
  /** Puede venir vacío. */
  popularSearches: string[]
  categories: HomeCategory[]
  nearby: Product[]
  /** Mezcla de anuncios (`sponsored`) y relleno orgánico. */
  featured: Product[]
}

/** Ubicaciones de anuncio que se piden aparte de la búsqueda. */
export type AdPlacementQuery =
  | { placement: 'CATEGORY_TOP'; categoryId: string }
  | { placement: 'PRODUCT_RELATED'; productId: string }

export interface MarketplaceService {
  readonly capabilities: MarketplaceCapabilities
  searchProducts(query: ProductQuery): Promise<ProductPage>
  /** Motor de búsqueda v2: texto libre, filtros, facetas, cercanía y anuncios. */
  search(query: SearchQuery, signal?: AbortSignal): Promise<SearchResult>
  suggest(text: string, near?: { lat: number; lng: number }, signal?: AbortSignal): Promise<SearchSuggestions>
  /** `GET /marketplace/home`: una sola llamada con cifras, búsquedas populares, categorías, cercanos y destacados. */
  getHome(query: HomeQuery, signal?: AbortSignal): Promise<HomeData>
  /** `GET /marketplace/ads`: anuncios de una ubicación; vacío si no hay campañas activas. */
  listAds(query: AdPlacementQuery, signal?: AbortSignal): Promise<Product[]>
  getProduct(id: string): Promise<Product>
  getRelatedProducts(product: Product, limit: number): Promise<Product[]>
  /** `GET /marketplace/products/{id}/frequently-bought` (público): hasta 6 productos que se compran junto con este. */
  getFrequentlyBought(productId: string): Promise<FrequentlyBoughtItem[]>
  getFilterOptions(): Promise<FilterOptions>
  /** Catálogo de vehículos en cascada (público). */
  listVehicleMakes(): Promise<VehicleOption[]>
  listVehicleModels(makeId: string): Promise<VehicleOption[]>
  listVehicleVersions(modelId: string): Promise<VehicleOption[]>
  getCategories(): Promise<Category[]>
  listStores(): Promise<StoreSummary[]>
  /** 404 (`isNotFoundError`) si el almacén no está publicado. */
  getStoreProfile(storeId: string): Promise<StoreProfile>
  /** Sucursales activas del almacén publicado; 404 (`isNotFoundError`) si no está publicado. */
  listStoreBranches(storeId: string): Promise<StoreBranch[]>
  /** `GET /marketplace/{products|stores}/{id}/reviews/summary` (público). */
  getReviewSummary(target: ReviewTarget): Promise<ReviewSummary>
  /** `GET /marketplace/{products|stores}/{id}/reviews` (público). Páginas desde 1. */
  listReviews(target: ReviewTarget, query: ReviewListQuery): Promise<ReviewPage>
  /** Con sesión: pedidos con los que puede calificar el producto, o su reseña existente. Sin sesión, nada elegible. */
  getProductReviewEligibility(productId: string): Promise<ProductReviewEligibility>
  /** Pedidos entregados del comprador con sesión que aún no tienen reseña; vacío sin sesión. */
  getReviewEligibility(storeId: string): Promise<StoreReviewEligibility>
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
