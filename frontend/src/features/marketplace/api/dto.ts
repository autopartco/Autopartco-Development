/**
 * Copia en TypeScript de los DTO públicos del backend (solo campos reales).
 * Origen: backend/src/main/java/com/autopartco/backend/core/catalog/ y core/organization/.
 * UUID -> string, BigDecimal -> number, LocalDateTime -> string ISO.
 */

/** core/catalog/ProductImageResponse.java */
export interface ProductImageResponseDto {
  id: string
  /** Original (detalle). */
  url: string
  /** Variante pequeña (400 px). Puede faltar en servidores antiguos. */
  thumbUrl?: string | null
  /** Variante de tarjeta (800 px). */
  cardUrl?: string | null
  /** Componente `isMain` del record; algunas configuraciones de Jackson lo serializan como `main`. */
  isMain?: boolean
  main?: boolean
  displayOrder: number
  createdAt: string
}

/** core/catalog/ProductResponse.java. Representa un listado: producto en una sucursal. */
export interface ProductResponseDto {
  id: string
  storeId: string
  branchId: string | null
  branchName: string | null
  name: string
  partNumber: string | null
  description: string | null
  price: number
  effectivePrice: number | null
  discountAmount: number | null
  appliedPromotionName: string | null
  appliedPromotionId?: string | null
  /** `ALL`, `PLATFORM_FREQUENT` o `STORE_*`; distinto de `ALL` significa precio para un segmento de compradores. */
  appliedPromotionAudience?: string | null
  quantity: number | null
  manufacturerId: string | null
  /** Visible aunque el fabricante esté pendiente de aprobación. */
  manufacturerName: string | null
  manufacturerApproved: boolean | null
  categoryId: string | null
  state: string | null
  attributes: Record<string, unknown> | null
  /** Producto que no depende del vehículo (sin reglas). */
  universal: boolean | null
  vehicleRules: VehicleRuleResponseDto[] | null
  images: ProductImageResponseDto[] | null
  /** Promedio de reseñas (un decimal); `null` sin reseñas. */
  rating?: number | null
  reviewCount?: number | null
}

/** core/catalog/ProductOptionsResponse.java */
export interface ManufacturerOptionDto {
  id: string
  name: string
}

export interface CategoryOptionDto {
  id: string
  code: string
}

export interface ProductOptionsResponseDto {
  manufacturers: ManufacturerOptionDto[]
  categories: CategoryOptionDto[]
}

/** Regla de compatibilidad con nombres; los niveles «todos» vienen en `null`. */
export interface VehicleRuleResponseDto {
  makeId: string
  makeName: string
  modelId: string | null
  modelName: string | null
  versionId: string | null
  versionLabel: string | null
}

/** GET /marketplace/vehicles/makes, /makes/{id}/models */
export interface VehicleNameOptionDto {
  id: string
  name: string
}

/** GET /marketplace/vehicles/models/{id}/versions */
export interface VehicleVersionOptionDto {
  id: string
  label: string
}

/** core/organization/StoreResponse.java */
export interface StoreResponseDto {
  id: string
  businessName: string
  nit: string
  businessEmail: string
  state: string | null
}

/** `marketplace/stores/StorePublicProfile`. `logoUrl`/`bannerUrl` son rutas de la API con `?v=` o `null`. */
export interface StorePublicProfileDto {
  id: string
  name: string
  description: string | null
  logoUrl: string | null
  bannerUrl: string | null
  cities: string[]
  branchCount: number
  memberSince: string | null
  rating: { average: number | null; count: number }
  publishedProducts: number
  contact: {
    whatsapp: string | null
    phone: string | null
    website: string | null
    instagram: string | null
    facebook: string | null
    tiktok: string | null
    youtube?: string | null
    x?: string | null
    email?: string | null
  }
  tagline?: string | null
  verified?: boolean
  whatsappEnabled?: boolean
  whatsappMessage?: string | null
  services?: {
    id: string
    category: string
    name: string
    description: string | null
    priceFrom: number | null
    durationMinutes: number | null
    branchIds: string[]
  }[]
  featuredProductIds?: string[]
}

/** `core/organization/PublicBranches.PublicBranch` (`GET /marketplace/stores/{id}/branches`). */
export interface PublicBranchDto {
  id: string
  name: string
  address: string | null
  city: string | null
  department: string | null
  latitude: number | null
  longitude: number | null
  /** El punto es el centro de la ciudad o del barrio, no la puerta del almacén. */
  approximate?: boolean
  phone: string | null
  whatsapp: string | null
  openingHours: Record<string, unknown> | null
  openNow: boolean | null
  serviceIds: string[] | null
}

/** `marketplace/reviews/ReviewSummary`. `distribution` trae siempre las claves "1"…"5". */
export interface ReviewSummaryDto {
  average: number | null
  count: number
  distribution: Record<string, number>
  aspects: { aspect: string; average: number; count: number }[]
  highlights: string[]
}

/** `marketplace/reviews/ProductReviewResponse` y `StoreReviewResponse` (esta añade `authorName`, igual a `author`). */
export interface ReviewResponseDto {
  id: string
  rating: number
  comment: string | null
  aspects: Record<string, number> | null
  author: string
  verified: boolean
  createdAt: string
  updatedAt: string
  reply: { text: string; repliedAt: string } | null
}

/** `marketplace/reviews/ProductReviewEligibility`. */
export interface ProductReviewEligibilityDto {
  eligibleOrderIds: string[]
  existingReviewId: string | null
  editableUntil: string | null
}

/** `marketplace/catalog/SearchResponse.Item`: el producto al mismo nivel, más distancia y datos del anuncio. */
export type SearchItemDto = ProductResponseDto & {
  distanceKm: number | null
  sponsored: boolean
  adImpressionToken: string | null
}

/** `marketplace/catalog/SearchResponse` (página desde 0). */
export interface SearchResponseDto {
  items: SearchItemDto[]
  page: number
  size: number
  total: number
  fuzzy: boolean
  interpreted: {
    text: string
    makeId: string | null
    makeName: string | null
    modelId: string | null
    modelName: string | null
    year: number | null
    cityCode: string | null
    cityName: string | null
    reference: string | null
  } | null
  facets: {
    categories: { id: string; name: string; count: number }[]
    manufacturers: { id: string; name: string; count: number }[]
    cities: { code: string; name: string; count: number }[]
    price: { min: number | null; max: number | null } | null
    inStock: number
    onSale: number
  } | null
  sponsored: SearchItemDto[]
}

/** `marketplace/home/HomeResponse` (`GET /marketplace/home`). */
export interface HomeResponseDto {
  stats: { stores: number; cities: number; products: number }
  popularSearches: string[]
  categories: { id: string; code: string; productCount: number }[]
  nearby: SearchItemDto[]
  featured: SearchItemDto[]
}

/** `GET /marketplace/ads` (CATEGORY_TOP y PRODUCT_RELATED). */
export interface AdsResponseDto {
  items: SearchItemDto[]
}

/** `core/search/Suggestions.Result` */
export interface SuggestResponseDto {
  suggestions: { term: string; kind: string }[]
  products: ProductResponseDto[]
}

/** `shared/web/PageResponse` (páginas desde 1). */
export interface PageResponseDto<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}
