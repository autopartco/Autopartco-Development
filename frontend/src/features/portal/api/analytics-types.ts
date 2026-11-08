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

// ------------------------------------------------------------- se compran juntos

export type AssociationSource = 'ORDERS' | 'COMPATIBILITY' | 'CATEGORY'

export interface AssociationProduct {
  productId: string
  name: string
}

/** `support`, `confidence` son fracciones 0..1; `lift` es un múltiplo (2,4 = 2,4 veces más probable que al azar). */
export interface AssociationPair {
  antecedent: AssociationProduct
  consequent: AssociationProduct
  support: number
  confidence: number
  lift: number
  orders: number
}

export interface SuggestedCombo {
  productIds: string[]
  names: string[]
  reason: string
}

/**
 * `GET /stores/{id}/analytics/associations`. Se calcula cada noche: `computedAt` y `source` llegan `null` mientras
 * no haya un cálculo. `source` indica en qué se basan los pares (pedidos reales, compatibilidad o categoría).
 */
export interface AssociationReport {
  computedAt: string | null
  source: AssociationSource | null
  pairs: AssociationPair[]
  suggestedCombo: SuggestedCombo | null
}

/** Origen estimado de la visita (`core/analytics/TrafficSource`). */
export type TrafficSource =
  | 'WHATSAPP'
  | 'FACEBOOK'
  | 'INSTAGRAM'
  | 'TIKTOK'
  | 'X'
  | 'TELEGRAM'
  | 'CHATGPT'
  | 'OTHER_AI'
  | 'GOOGLE'
  | 'OTHER_SEARCH'
  | 'COPY_LINK'
  | 'INTERNAL'
  | 'DIRECT'
  | 'OTHER'

/**
 * `GET /stores/{id}/analytics/visibility?from&to` (todos los planes, `ANALYTICS_READ`). «Visitantes» son visitantes
 * diarios: la misma persona cuenta una vez por día.
 */
export interface VisibilityReport {
  from: string
  to: string
  profileViews: number
  profileVisitors: number
  productViews: number
  productVisitors: number
  shares: number
  /** Visitas (perfil + publicaciones) y compartidos por origen, de más a menos visitas. */
  bySource: { source: TrafficSource; views: number; visitors: number; shares: number }[]
  /** Hasta 20 publicaciones con más visitas. */
  topProducts: { productId: string; name: string; views: number; visitors: number; shares: number }[]
  /** Un día por fila, con ceros. */
  daily: { day: string; profileViews: number; productViews: number }[]
}
