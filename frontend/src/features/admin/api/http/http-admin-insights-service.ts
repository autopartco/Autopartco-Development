import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { AdminInsightsService, AuditActionOption, AuditReport, PlatformStatsReport } from '../insights-types'

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  return search.toString()
}

export function createHttpAdminInsightsService(): AdminInsightsService {
  return {
    audit: (filter) => portalRequest<AuditReport>(`/admin/audit?${query({ ...filter })}`),
    auditActions: () => portalRequest<AuditActionOption[]>('/admin/audit/actions'),
    platformStats: (range) => portalRequest<PlatformStatsReport>(`/admin/analytics/platform?${query({ ...range })}`),
  }
}
