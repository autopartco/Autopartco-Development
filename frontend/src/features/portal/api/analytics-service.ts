import { DATA_SOURCE } from '@/lib/api'
import type {
  AssociationReport,
  CustomerReport,
  CustomerSegmentCode,
  Forecast,
  ProductReport,
  ReportQuery,
  SalesReport,
  VisibilityReport,
} from './analytics-types'

/**
 * Inteligencia comercial del almacén (`/stores/{id}/analytics`). Exige `ANALYTICS_READ` y la capability
 * `ADVANCED_REPORTS` del plan Empresarial: sin ella el servidor responde 409 `SELLER_PLAN_FEATURE_UNAVAILABLE`.
 */
export interface AnalyticsService {
  /** `GET /report?from&to&granularity&branchId?`. 400 `INVALID_RANGE` (máx. 366 días). */
  report(storeId: string, query: ReportQuery): Promise<SalesReport>
  /** `GET /customers?segment?`. */
  customers(storeId: string, segment?: CustomerSegmentCode): Promise<CustomerReport>
  /** `GET /products?branchId?`. */
  products(storeId: string, branchId?: string): Promise<ProductReport>
  /** `GET /forecast?branchId?`. `INSUFFICIENT_HISTORY` llega con 200 y sin proyección. */
  forecast(storeId: string, branchId?: string): Promise<Forecast>
  /** `GET /associations`: productos que se compran juntos (se recalcula cada noche). */
  associations(storeId: string): Promise<AssociationReport>
  /**
   * `GET /visibility?from&to`: visitas al perfil y a las publicaciones, compartidos y origen. Solo exige
   * `ANALYTICS_READ` (está en todos los planes, sin `ADVANCED_REPORTS`).
   */
  visibility(storeId: string, range: { from: string; to: string }): Promise<VisibilityReport>
}

let servicePromise: Promise<AnalyticsService> | undefined

export function getAnalyticsService(): Promise<AnalyticsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-analytics-service').then((module) => module.createHttpAnalyticsService())
      : import('./mock/mock-analytics-service').then((module) => module.createMockAnalyticsService())
  return servicePromise
}
