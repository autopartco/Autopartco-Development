import type { AssociationReport, CustomerReport, Forecast, ProductReport, SalesReport } from '../analytics-types'
import type { AnalyticsService } from '../analytics-service'
import { portalRequest, storePath } from './portal-http'

function query(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) if (value) search.set(key, value)
  const text = search.toString()
  return text ? `?${text}` : ''
}

const base = (storeId: string, suffix: string) => storePath(storeId, `/analytics${suffix}`)

/** El cliente solo muestra lo que calcula el servidor: no recalcula totales ni segmentos. */
export function createHttpAnalyticsService(): AnalyticsService {
  return {
    report: (storeId, { from, to, granularity, branchId }) =>
      portalRequest<SalesReport>(base(storeId, `/report${query({ from, to, granularity, branchId })}`)),
    customers: (storeId, segment) => portalRequest<CustomerReport>(base(storeId, `/customers${query({ segment })}`)),
    products: (storeId, branchId) => portalRequest<ProductReport>(base(storeId, `/products${query({ branchId })}`)),
    forecast: (storeId, branchId) => portalRequest<Forecast>(base(storeId, `/forecast${query({ branchId })}`)),
    associations: (storeId) => portalRequest<AssociationReport>(base(storeId, '/associations')),
  }
}
