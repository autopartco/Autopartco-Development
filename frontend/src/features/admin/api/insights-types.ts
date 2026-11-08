/**
 * Contratos de `AuditAdminController` (`/admin/audit`) y `PlatformAnalyticsController#platform`
 * (`/admin/analytics/platform`). Solo ROOT.
 */

export interface AuditEntry {
  id: string
  /** ISO con zona. */
  occurredAt: string
  action: string
  actor: { accountId: string | null; email: string | null }
  entityType: string
  entityId: string | null
  ipAddress: string | null
  metadata: Record<string, unknown>
}

export interface AuditReport {
  page: { items: AuditEntry[]; page: number; size: number; total: number }
}

export interface AuditQuery {
  from: string
  to: string
  action?: string
  entityType?: string
  actor?: string
  page: number
  size: number
}

export interface AuditActionOption {
  action: string
  entityType: string
}

export type StatsGranularity = 'DAY' | 'WEEK' | 'MONTH'

export interface StatCount {
  key: string
  count: number
}

export interface PlatformStatsReport {
  from: string
  to: string
  granularity: StatsGranularity
  traffic: {
    visits: number
    uniqueVisitors: number
    series: { date: string; visits: number; visitors: number }[]
    topPaths: StatCount[]
    referrers: StatCount[]
    devices: Record<string, number>
  }
  accounts: {
    total: number
    created: number
    byType: StatCount[]
    /** Fracción 0..1. */
    verifiedRate: number
    locked: number
    socialShare: StatCount[]
    series: { date: string; created: number }[]
  }
  access: {
    logins: number
    failedLogins: number
    dau: number
    wau: number
    mau: number
    series: { date: string; logins: number; failed: number }[]
  }
  commerce: {
    orders: number
    gmv: number
    averageTicket: number
    activeStores: number
    pendingApplications: number
    subscriptionsByPlan: StatCount[]
    mrr: number
  }
}

export interface AdminInsightsService {
  audit(query: AuditQuery): Promise<AuditReport>
  auditActions(): Promise<AuditActionOption[]>
  platformStats(range: { from: string; to: string; granularity: StatsGranularity }): Promise<PlatformStatsReport>
}
