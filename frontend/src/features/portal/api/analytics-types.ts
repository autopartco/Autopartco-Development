/**
 * Inteligencia comercial (fase 1). Contratos de `/stores/{id}/analytics/*` (permiso `ANALYTICS_READ` y
 * capability `ADVANCED_REPORTS`) y de `/admin/analytics/*`. Las fechas `YYYY-MM-DD` son de Colombia.
 */

export type Granularity = 'DAY' | 'WEEK' | 'MONTH'

export interface ReportTotals {
  revenue: number
  orders: number
  units: number
  averageTicket: number
  customers: number
  discounts: number
  cancelledOrders: number
  /** Fracción 0..1 (0,06 = 6 %). */
  cancellationRate: number
}

export interface SeriesPoint {
  /** `YYYY-MM-DD`: primer día del día, semana o mes. */
  periodStart: string
  revenue: number
  orders: number
  units: number
}

export interface BranchBreakdown {
  branchId: string
  branchName: string
  revenue: number
  orders: number
  units: number
}

export interface CategoryBreakdown {
  categoryId: string
  categoryCode: string
  revenue: number
  units: number
}

export interface TopProduct {
  productId: string
  name: string
  sku: string | null
  revenue: number
  units: number
}

/** `GET /stores/{id}/analytics/report`. Error 400 `INVALID_RANGE` (rango invertido o de más de 366 días). */
export interface SalesReport {
  from: string
  to: string
  granularity: Granularity
  totals: ReportTotals
  previous: ReportTotals
  series: SeriesPoint[]
  byBranch: BranchBreakdown[]
  byCategory: CategoryBreakdown[]
  topProducts: TopProduct[]
}

export interface ReportQuery {
  from: string
  to: string
  granularity: Granularity
  branchId?: string
}

// ------------------------------------------------------------------ clientes

export type CustomerSegmentCode = 'NEW' | 'FREQUENT' | 'AT_RISK' | 'DORMANT' | 'OCCASIONAL'

export interface SegmentSummary {
  code: CustomerSegmentCode
  label: string
  customers: number
  revenue: number
}

export interface StoreCustomer {
  personId: string
  name: string
  orders: number
  revenue: number
  averageTicket: number
  firstOrderAt: string
  lastOrderAt: string
  daysSinceLastOrder: number
  segment: CustomerSegmentCode
}

/** `GET /stores/{id}/analytics/customers?segment=`. */
export interface CustomerReport {
  asOf: string
  windowDays: number
  segments: SegmentSummary[]
  customers: StoreCustomer[]
}

// ----------------------------------------------------------------- productos

export type AbcClass = 'A' | 'B' | 'C'
export type XyzClass = 'X' | 'Y' | 'Z'
export type ProductAction = 'PROTECT_STOCK' | 'NO_MOVEMENT' | 'LIQUIDATE' | 'REVIEW_PRICE' | 'MAINTAIN'

export interface ProductInsight {
  productId: string
  name: string
  sku: string | null
  revenue: number
  units: number
  abcClass: AbcClass
  xyzClass: XyzClass | null
  stock: number
  avgDailyUnits: number
  coverageDays: number | null
  currentMinStock: number
  suggestedMinStock: number | null
  action: ProductAction
}

/** `GET /stores/{id}/analytics/products?branchId=`. */
export interface ProductReport {
  windowDays: number
  products: ProductInsight[]
}

// ----------------------------------------------------------------- proyección

export interface ForecastWeek {
  weekStart: string
  actual: number | null
  forecast: number | null
  low: number | null
  high: number | null
}

export interface ForecastProduct {
  productId: string
  name: string
  expectedUnits: number
  expectedRevenue: number
}

export interface Forecast {
  status: 'OK' | 'INSUFFICIENT_HISTORY'
  weeksAvailable: number
  weeksRequired: number
  method: string
  nextMonth: { month: string; expected: number; low: number; high: number } | null
  weekly: ForecastWeek[]
  topProducts: ForecastProduct[]
}
