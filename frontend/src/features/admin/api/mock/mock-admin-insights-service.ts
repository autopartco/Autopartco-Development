import { simulateLatency } from '@/lib/api'
import type { AdminInsightsService, AuditActionOption, AuditEntry, PlatformStatsReport, StatsGranularity } from '../insights-types'

/** Auditoría (300 eventos) y estadísticas simuladas, coherentes con el contrato del servidor. */

const ACTIONS: AuditActionOption[] = [
  { action: 'LOGIN_SUCCEEDED', entityType: 'ACCOUNT' },
  { action: 'LOGIN_FAILED', entityType: 'ACCOUNT' },
  { action: 'SOCIAL_LOGIN', entityType: 'ACCOUNT' },
  { action: 'USER_REGISTRATION_REQUESTED', entityType: 'ACCOUNT' },
  { action: 'ACCOUNT_LOCKED', entityType: 'ACCOUNT' },
  { action: 'PASSWORD_RESET', entityType: 'ACCOUNT' },
  { action: 'PLAN_UPDATED', entityType: 'PLAN' },
  { action: 'PLATFORM_PROMOTION_CREATED', entityType: 'PROMOTION' },
  { action: 'MANUFACTURER_APPROVED', entityType: 'MANUFACTURER' },
  { action: 'STORE_APPLICATION_REVIEWED', entityType: 'STORE_APPLICATION' },
  { action: 'STAFF_INVITED', entityType: 'STAFF_INVITATION' },
]
const EMAILS = ['root@autopartco.co', 'ana.gomez@correo.co', 'gerencia@frenosdelnorte.co', 'taller.medellin@correo.co', null]

let cached: AuditEntry[] | undefined

function events(): AuditEntry[] {
  if (cached) return cached
  const now = Date.now()
  cached = Array.from({ length: 300 }, (_, index) => {
    const option = ACTIONS[(index * 7) % ACTIONS.length]
    const email = option.entityType === 'ACCOUNT' ? EMAILS[1 + (index % 3)] : EMAILS[0]
    return {
      id: `audit-${index}`,
      occurredAt: new Date(now - index * 2.3 * 3_600_000).toISOString(),
      action: option.action,
      actor: { accountId: email ? `acc-${index % 4}` : null, email },
      entityType: option.entityType,
      entityId: `ent-${index % 17}`,
      ipAddress: option.entityType === 'ACCOUNT' ? `181.49.${index % 250}.${(index * 3) % 250}` : null,
      metadata: option.action === 'PLAN_UPDATED' ? { code: 'PRO', monthlyPriceCop: 149000, enabled: true } : { provider: 'google' },
    }
  })
  return cached
}

function days(from: string, to: string): string[] {
  const out: string[] = []
  const end = new Date(`${to}T00:00:00`)
  for (let day = new Date(`${from}T00:00:00`); day <= end; day.setDate(day.getDate() + 1)) {
    out.push(day.toISOString().slice(0, 10))
  }
  return out
}

function bucket(date: string, granularity: StatsGranularity): string {
  const day = new Date(`${date}T00:00:00Z`)
  if (granularity === 'MONTH') return `${date.slice(0, 7)}-01`
  if (granularity === 'WEEK') {
    const offset = (day.getUTCDay() + 6) % 7
    day.setUTCDate(day.getUTCDate() - offset)
    return day.toISOString().slice(0, 10)
  }
  return date
}

function group<T extends Record<string, number>>(rows: { date: string; values: T }[], granularity: StatsGranularity): ({ date: string } & T)[] {
  const map = new Map<string, Record<string, number>>()
  for (const row of rows) {
    const key = bucket(row.date, granularity)
    const current = map.get(key) ?? {}
    for (const [name, value] of Object.entries(row.values)) current[name] = (current[name] ?? 0) + value
    map.set(key, current)
  }
  return [...map.entries()].map(([date, values]) => ({ date, ...values }) as { date: string } & T)
}

export function createMockAdminInsightsService(): AdminInsightsService {
  return {
    async audit(query) {
      await simulateLatency(400)
      const from = Date.parse(`${query.from}T00:00:00`)
      const to = Date.parse(`${query.to}T00:00:00`) + 86_400_000
      const actor = query.actor?.toLowerCase()
      const filtered = events().filter((entry) => {
        const at = Date.parse(entry.occurredAt)
        return at >= from && at < to
          && (!query.action || entry.action === query.action)
          && (!query.entityType || entry.entityType === query.entityType)
          && (!actor || (entry.actor.email ?? '').toLowerCase().includes(actor))
      })
      const start = (query.page - 1) * query.size
      return { page: { items: filtered.slice(start, start + query.size), page: query.page, size: query.size, total: filtered.length } }
    },
    async auditActions() {
      await simulateLatency(200)
      return ACTIONS
    },
    async platformStats({ from, to, granularity }): Promise<PlatformStatsReport> {
      await simulateLatency(500)
      const list = days(from, to)
      const daily = list.map((date, index) => {
        const weekend = [0, 6].includes(new Date(`${date}T00:00:00`).getDay())
        const visits = Math.round((weekend ? 260 : 410) + 60 * Math.sin(index / 3))
        return { date, visits, visitors: Math.round(visits * 0.62), created: 3 + (index % 5), logins: 40 + (index % 9) * 3, failed: 2 + (index % 4) }
      })
      const sum = (key: keyof (typeof daily)[number]) => daily.reduce((total, row) => total + Number(row[key]), 0)
      const visits = sum('visits')
      const orders = Math.round(visits * 0.018)
      return {
        from,
        to,
        granularity,
        traffic: {
          visits,
          uniqueVisitors: Math.round(sum('visitors') * 0.8),
          series: group(daily.map((row) => ({ date: row.date, values: { visits: row.visits, visitors: row.visitors } })), granularity),
          topPaths: [
            { key: '/', count: Math.round(visits * 0.31) },
            { key: '/buscar', count: Math.round(visits * 0.24) },
            { key: '/producto/:id', count: Math.round(visits * 0.22) },
            { key: '/categorias', count: Math.round(visits * 0.09) },
            { key: '/tiendas/:id', count: Math.round(visits * 0.06) },
            { key: '/carrito', count: Math.round(visits * 0.04) },
          ],
          referrers: [
            { key: 'google.com', count: Math.round(visits * 0.28) },
            { key: 'facebook.com', count: Math.round(visits * 0.09) },
            { key: 'instagram.com', count: Math.round(visits * 0.05) },
            { key: 'l.wl.co', count: Math.round(visits * 0.03) },
          ],
          devices: { mobile: Math.round(visits * 0.71), desktop: Math.round(visits * 0.29) },
        },
        accounts: {
          total: 1284,
          created: sum('created'),
          byType: [
            { key: 'CUSTOMER', count: 1196 },
            { key: 'STORE_MANAGER', count: 81 },
            { key: 'ROOT', count: 7 },
          ],
          verifiedRate: 0.87,
          locked: 6,
          socialShare: [
            { key: 'google', count: 214 },
            { key: 'facebook', count: 63 },
          ],
          series: group(daily.map((row) => ({ date: row.date, values: { created: row.created } })), granularity),
        },
        access: {
          logins: sum('logins'),
          failedLogins: sum('failed'),
          dau: 132,
          wau: 486,
          mau: 941,
          series: group(daily.map((row) => ({ date: row.date, values: { logins: row.logins, failed: row.failed } })), granularity),
        },
        commerce: {
          orders,
          gmv: orders * 186_400,
          averageTicket: 186_400,
          activeStores: 42,
          pendingApplications: 9,
          subscriptionsByPlan: [
            { key: 'Profesional', count: 21 },
            { key: 'Básico', count: 14 },
            { key: 'Empresarial', count: 7 },
          ],
          mrr: 21 * 149_000 + 7 * 459_000,
        },
      }
    },
  }
}
