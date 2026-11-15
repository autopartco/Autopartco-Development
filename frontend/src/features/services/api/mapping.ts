import { apiAssetUrl } from '@/lib/api'
import type { StoreBranch } from '@/features/marketplace/api/types'
import type { PublicBranchDto } from '@/features/marketplace/api/dto'
import { toServiceCategory } from '../lib/categories'
import type {
  HomeServicesDto,
  ServiceCardDto,
  ServiceCategoryItemDto,
  ServiceDetailDto,
  ServiceSearchResponseDto,
  ServicesStripDto,
} from './dto'
import type {
  HomeServices,
  ServiceCategoryCount,
  ServiceDetail,
  ServiceSearchResult,
  ServiceStoreRef,
  ServiceSummary,
  ServicesStrip,
} from './types'

const BUSINESS_TYPES = ['PARTS', 'SERVICES', 'BOTH'] as const

export function toServiceSummary(dto: ServiceCardDto): ServiceSummary {
  const { store, branch } = dto
  const hasPoint = typeof branch?.latitude === 'number' && typeof branch?.longitude === 'number'
  return {
    id: dto.id,
    name: dto.name,
    category: toServiceCategory(dto.category),
    imageUrl: apiAssetUrl(dto.imageUrl) ?? undefined,
    priceFrom: dto.priceFrom ?? undefined,
    basePriceFrom: dto.basePriceFrom ?? undefined,
    promotion: dto.promotion
      ? { id: dto.promotion.id, label: dto.promotion.label, endsAt: dto.promotion.endsAt ?? undefined }
      : undefined,
    durationMinutes: dto.durationMinutes ?? undefined,
    bookingEnabled: dto.bookingEnabled,
    store: store
      ? ({
          id: store.id,
          name: store.name,
          logoUrl: apiAssetUrl(store.logoUrl) ?? undefined,
          rating: store.rating ?? undefined,
          businessType: BUSINESS_TYPES.find((type) => type === store.businessType),
        } satisfies ServiceStoreRef)
      : undefined,
    branch: branch
      ? {
          id: branch.id,
          name: branch.name,
          city: branch.city ?? undefined,
          address: branch.address ?? undefined,
          point: hasPoint ? { lat: Number(branch.latitude), lng: Number(branch.longitude) } : undefined,
          openNow: branch.openNow ?? undefined,
        }
      : undefined,
    distanceKm: dto.distanceKm ?? undefined,
  }
}

export function toServiceCategoryCount(dto: ServiceCategoryItemDto): ServiceCategoryCount {
  const category = toServiceCategory(dto.category)
  return { category, label: dto.label, icon: dto.icon, count: dto.count }
}

export function toServiceSearchResult(dto: ServiceSearchResponseDto): ServiceSearchResult {
  return {
    items: dto.items.map(toServiceSummary),
    total: dto.total,
    page: dto.page + 1,
    pageSize: dto.size,
    totalPages: Math.max(1, Math.ceil(dto.total / dto.size)),
    facets: dto.facets
      ? {
          categories: dto.facets.categories.map((item) => ({ category: toServiceCategory(item.category), count: item.count })),
          cities: dto.facets.cities,
        }
      : undefined,
  }
}

/** Las sedes usan el mismo formato que `/stores/{id}/branches`; el mapeo vive en el servicio del marketplace. */
export function toServiceDetail(dto: ServiceDetailDto, toBranch: (branch: PublicBranchDto) => StoreBranch): ServiceDetail {
  return {
    ...toServiceSummary(dto),
    description: dto.description ?? undefined,
    branches: (dto.branches ?? []).map(toBranch),
    whatsappEnabled: dto.whatsappEnabled,
    whatsappMessage: dto.whatsappServiceMessage ?? undefined,
    related: (dto.related ?? []).map(toServiceSummary),
  }
}

export function toServicesStrip(dto: ServicesStripDto): ServicesStrip {
  return { items: dto.items.map(toServiceSummary), total: dto.total }
}

export function toHomeServices(dto: HomeServicesDto): HomeServices {
  return {
    categories: (dto.categories ?? []).map(toServiceCategoryCount),
    nearby: (dto.nearby ?? []).map(toServiceSummary),
    promoted: (dto.promoted ?? []).map(toServiceSummary),
  }
}
