import type { PublicBranchDto } from '@/features/marketplace/api/dto'

/**
 * Copia de los DTO públicos de servicios. Origen: backend `marketplace/services/{ServiceCard,ServiceDetail,
 * ServiceBrowseController}`. UUID -> string, BigDecimal -> number, OffsetDateTime -> string ISO.
 */

export interface ServiceCardDto {
  id: string
  name: string
  category: string
  imageUrl: string | null
  priceFrom: number | null
  basePriceFrom: number | null
  promotion: { id: string; label: string; endsAt: string | null } | null
  durationMinutes: number | null
  bookingEnabled: boolean
  store: { id: string; name: string; logoUrl: string | null; rating: number | null; businessType: string | null }
  branch: {
    id: string
    name: string
    city: string | null
    address: string | null
    latitude: number | null
    longitude: number | null
    openNow: boolean | null
  }
  distanceKm: number | null
}

/** `ServiceDetail`: la tarjeta con sus campos al mismo nivel (`@JsonUnwrapped`) más lo propio del detalle. */
export interface ServiceDetailDto extends ServiceCardDto {
  description: string | null
  branches: PublicBranchDto[]
  whatsappEnabled: boolean
  whatsappServiceMessage: string | null
  related: ServiceCardDto[]
}

export interface ServiceCategoryItemDto {
  category: string
  label: string
  icon: string
  count: number
}

export interface ServiceSearchResponseDto {
  items: ServiceCardDto[]
  page: number
  size: number
  total: number
  facets: {
    categories: { category: string; count: number }[]
    cities: { code: string; name: string; count: number }[]
  } | null
}

/** `SearchResponse.services` (franja de la búsqueda unificada). */
export interface ServicesStripDto {
  items: ServiceCardDto[]
  total: number
}

/** `HomeResponse.services`. */
export interface HomeServicesDto {
  categories: ServiceCategoryItemDto[]
  nearby: ServiceCardDto[]
  promoted: ServiceCardDto[]
}

/** `core/search/Suggestions.ServiceSuggestion`. */
export interface ServiceSuggestionDto {
  id: string
  name: string
  category: string
  storeName: string
}
