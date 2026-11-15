import type { ServiceCategory, StoreBranch } from '@/features/marketplace/api/types'

/**
 * Modelo de UI de los servicios (talleres, instalación, diagnóstico…). Lo mapea cada `MarketplaceService`
 * desde su origen; el frontend solo muestra lo que el servidor devuelve (precio, promoción, horario).
 */

export interface ServicePromotion {
  id: string
  /** Texto que decide el servidor: «-15%», «-$9.000». */
  label: string
  /** ISO 8601; la promoción vence en esa fecha. */
  endsAt?: string
}

/** Sucursal más cercana (o la primera) que presta el servicio. */
export interface ServiceBranchRef {
  id: string
  name: string
  city?: string
  address?: string
  point?: { lat: number; lng: number }
  /** `undefined` si la sede no publicó horario semanal. */
  openNow?: boolean
}

export interface ServiceStoreRef {
  id: string
  name: string
  logoUrl?: string
  /** Promedio de la tienda con un decimal; `undefined` sin reseñas. */
  rating?: number
  businessType?: 'PARTS' | 'SERVICES' | 'BOTH'
}

/** `ServiceCard` del servidor, y el servicio del perfil de la tienda llevado a la misma forma. */
export interface ServiceSummary {
  id: string
  name: string
  category: ServiceCategory
  imageUrl?: string
  /** «Desde», ya con la promoción vigente; sin él el precio es «a consultar». */
  priceFrom?: number
  /** Precio «desde» antes de la promoción. */
  basePriceFrom?: number
  promotion?: ServicePromotion
  durationMinutes?: number
  bookingEnabled: boolean
  store?: ServiceStoreRef
  branch?: ServiceBranchRef
  /** Solo si se envió la ubicación. */
  distanceKm?: number
}

export interface ServiceCategoryCount {
  category: ServiceCategory
  label: string
  /** Nombre de ícono Lucide en kebab-case (`wrench`, `zap`…). */
  icon: string
  count: number
}

export type ServiceSort = 'relevance' | 'nearest' | 'price_asc' | 'rating'

/** Consulta de `GET /marketplace/services/search`; `page` va desde 1. */
export interface ServiceSearchQuery {
  q: string
  category?: ServiceCategory
  cityCode?: string
  lat?: number
  lng?: number
  radiusKm?: number
  store?: string
  priceMax?: number
  onSale: boolean
  bookable: boolean
  openNow: boolean
  sort: ServiceSort
  page: number
  pageSize: number
}

export interface ServiceSearchResult {
  items: ServiceSummary[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  facets?: {
    categories: { category: ServiceCategory; count: number }[]
    cities: { code: string; name: string; count: number }[]
  }
}

/** `GET /marketplace/services/{id}`. */
export interface ServiceDetail extends ServiceSummary {
  description?: string
  /** Solo las sedes que prestan el servicio. */
  branches: StoreBranch[]
  whatsappEnabled: boolean
  /** Plantilla del almacén (`{servicio}`, `{sucursal}`, `{enlace}`). */
  whatsappMessage?: string
  related: ServiceSummary[]
}

/** Franja de servicios de la búsqueda unificada. */
export interface ServicesStrip {
  items: ServiceSummary[]
  total: number
}

/** Bloques de servicios del inicio. */
export interface HomeServices {
  categories: ServiceCategoryCount[]
  nearby: ServiceSummary[]
  promoted: ServiceSummary[]
}

export interface ServiceSuggestion {
  id: string
  name: string
  category: ServiceCategory
  storeName: string
}
