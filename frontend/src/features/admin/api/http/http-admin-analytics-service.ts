import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { AdminAnalyticsService, AdminCustomerReport, SearchReport } from '../analytics-types'

/** `/admin/analytics`: un 401 limpia la sesión y un 403 se muestra como «sin acceso» (ver `portalRequest`). */
export function createHttpAdminAnalyticsService(): AdminAnalyticsService {
  return {
    customers: ({ segment, page, size }) => {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (segment) params.set('segment', segment)
      return portalRequest<AdminCustomerReport>(`/admin/analytics/customers?${params}`)
    },
    searches: ({ from, to }) => portalRequest<SearchReport>(`/admin/analytics/searches?${new URLSearchParams({ from, to })}`),
  }
}
