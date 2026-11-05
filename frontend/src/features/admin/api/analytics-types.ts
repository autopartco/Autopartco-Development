import type { CustomerSegmentCode, SegmentSummary } from '@/features/portal/api/analytics-types'

/** Inteligencia comercial de la plataforma (`/admin/analytics`, solo administración). */

export interface AdminCustomer {
  personId: string
  name: string
  email: string
  orders: number
  /** Almacenes distintos donde compró. */
  stores: number
  revenue: number
  averageTicket: number
  lastOrderAt: string
  daysSinceLastOrder: number
  segment: CustomerSegmentCode
  /** Autorización de la persona para recibir correos comerciales (Ley 1581). */
  marketingEmail: boolean
  marketingWhatsapp: boolean
}

/** `GET /admin/analytics/customers?segment?&page=1&size=50`. Páginas desde 1. */
export interface AdminCustomerReport {
  asOf: string
  windowDays: number
  segments: SegmentSummary[]
  page: { items: AdminCustomer[]; page: number; size: number; total: number }
}

export interface AdminCustomerQuery {
  segment?: CustomerSegmentCode
  page: number
  size: number
}

export interface SearchQueryStat {
  query: string
  searches: number
  unmet: number
}

export interface UnmetQueryStat {
  query: string
  searches: number
}

export interface VehicleSearchStat {
  makeId: string
  makeName: string
  modelId: string | null
  modelName: string | null
  year: number | null
  searches: number
  unmet: number
}

export interface MunicipalitySearchStat {
  municipalityCode: string
  municipalityName: string
  searches: number
  unmet: number
}

/** `GET /admin/analytics/searches?from&to`. 400 `INVALID_RANGE`. `unmetRate` es una fracción 0..1. */
export interface SearchReport {
  from: string
  to: string
  totalSearches: number
  unmetSearches: number
  unmetRate: number
  topQueries: SearchQueryStat[]
  topUnmetQueries: UnmetQueryStat[]
  topVehicles: VehicleSearchStat[]
  byMunicipality: MunicipalitySearchStat[]
}

export interface AdminAnalyticsService {
  customers(query: AdminCustomerQuery): Promise<AdminCustomerReport>
  searches(range: { from: string; to: string }): Promise<SearchReport>
}
