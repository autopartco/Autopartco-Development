import { ApiError, simulateLatency } from '@/lib/api'
import type { CustomerSegmentCode, SegmentSummary } from '@/features/portal/api/analytics-types'
import { SEGMENT_LABELS, SEGMENT_ORDER } from '@/features/portal/api/mock/mock-analytics-service'
import { addDays, daysBetween, makePeople, parseYmd, prng, segmentOf, SEGMENT_WINDOW_DAYS, today, ymd } from '@/features/portal/api/mock/mock-analytics-data'
import { MOCK_VEHICLES } from '@/features/portal/api/mock/mock-state'
import type { AdminAnalyticsService, AdminCustomer, MunicipalitySearchStat, SearchQueryStat, VehicleSearchStat } from '../analytics-types'

/**
 * Datos simulados de administración: 140 clientes de la plataforma (con autorizaciones de marketing variadas) y
 * estadísticas de búsqueda que escalan con el rango pedido. Mismo contrato que el servidor, incluido `INVALID_RANGE`.
 */

let cachedCustomers: AdminCustomer[] | undefined

function allCustomers(): AdminCustomer[] {
  if (cachedCustomers) return cachedCustomers
  const rand = prng(20261105)
  const end = today()
  cachedCustomers = makePeople(140, 4242).map((person) => {
    const orders = 1 + Math.floor(rand() * rand() * 14)
    const lastDays = rand() < 0.25 ? 181 + Math.floor(rand() * 180) : rand() < 0.3 ? 61 + Math.floor(rand() * 115) : Math.floor(rand() * 60)
    const firstDays = Math.min(SEGMENT_WINDOW_DAYS - 1, lastDays + (orders === 1 ? 0 : 10 + Math.floor(rand() * 200)))
    const revenue = Math.round((orders * (60_000 + rand() * 340_000)) / 1000) * 1000
    return {
      personId: person.personId,
      name: person.name,
      email: person.email,
      orders,
      stores: Math.min(orders, 1 + Math.floor(rand() * 3)),
      revenue,
      averageTicket: Math.round(revenue / orders),
      lastOrderAt: addDays(end, -lastDays).toISOString(),
      daysSinceLastOrder: lastDays,
      segment: segmentOf(orders, firstDays, lastDays),
      marketingEmail: person.marketingEmail,
      marketingWhatsapp: person.marketingWhatsapp,
    }
  })
  return cachedCustomers
}

const QUERIES: [string, number, number][] = [
  ['pastillas de freno', 100, 4],
  ['filtro de aceite', 90, 3],
  ['kit de embrague', 55, 12],
  ['amortiguador delantero', 50, 8],
  ['bujías', 46, 5],
  ['correa de distribución', 42, 11],
  ['rótula', 31, 7],
  ['bomba de agua', 28, 9],
  ['sensor de oxígeno', 22, 15],
  ['farola led', 14, 13],
]

const UNMET: [string, number][] = [
  ['sensor de oxígeno', 15],
  ['farola led', 13],
  ['kit de embrague', 12],
  ['correa de distribución', 11],
  ['bomba de agua', 9],
  ['amortiguador delantero', 8],
  ['rótula', 7],
  ['bobina de encendido', 6],
]

const MUNICIPALITIES: [string, string, number, number][] = [
  ['11001', 'Bogotá, D.C.', 100, 9],
  ['05001', 'Medellín', 72, 8],
  ['76001', 'Cali', 54, 12],
  ['08001', 'Barranquilla', 33, 13],
  ['68001', 'Bucaramanga', 21, 10],
  ['66001', 'Pereira', 15, 14],
  ['13001', 'Cartagena de Indias', 12, 15],
]

function invalidRange(): ApiError {
  return new ApiError('El rango de fechas no es válido. Elige un rango de máximo 366 días.', { status: 400, code: 'INVALID_RANGE' })
}

export function createMockAdminAnalyticsService(): AdminAnalyticsService {
  return {
    async customers({ segment, page, size }) {
      await simulateLatency(450)
      const all = allCustomers()
      const segments: SegmentSummary[] = SEGMENT_ORDER.map((code: CustomerSegmentCode) => {
        const own = all.filter((customer) => customer.segment === code)
        return { code, label: SEGMENT_LABELS[code], customers: own.length, revenue: own.reduce((sum, customer) => sum + customer.revenue, 0) }
      })
      const filtered = all.filter((customer) => !segment || customer.segment === segment).toSorted((a, b) => b.revenue - a.revenue)
      const start = (Math.max(1, page) - 1) * size
      return {
        asOf: ymd(today()),
        windowDays: SEGMENT_WINDOW_DAYS,
        segments,
        page: { items: filtered.slice(start, start + size), page: Math.max(1, page), size, total: filtered.length },
      }
    },

    async searches({ from, to }) {
      await simulateLatency(450)
      const start = parseYmd(from)
      const end = parseYmd(to)
      if (!start || !end || end < start || daysBetween(start, end) + 1 > 366) throw invalidRange()
      const days = daysBetween(start, end) + 1
      const scale = (days * 2.4) / 10
      const scaled = (value: number) => Math.max(1, Math.round(value * scale))
      const topQueries: SearchQueryStat[] = QUERIES.map(([query, searches, unmetPct]) => ({
        query,
        searches: scaled(searches),
        unmet: Math.round((scaled(searches) * unmetPct) / 100),
      }))
      const makes = MOCK_VEHICLES.flatMap((make) => make.models.map((model) => ({ make, model })))
      const topVehicles: VehicleSearchStat[] = makes.slice(0, 8).map(({ make, model }, index) => {
        const searches = scaled(60 - index * 6)
        return { makeId: make.id, makeName: make.name, modelId: model.id, modelName: model.name, year: 2022 - (index % 8) * 2, searches, unmet: Math.round(searches * (0.06 + (index % 4) * 0.03)) }
      })
      const byMunicipality: MunicipalitySearchStat[] = MUNICIPALITIES.map(([municipalityCode, municipalityName, searches, unmetPct]) => ({
        municipalityCode,
        municipalityName,
        searches: scaled(searches),
        unmet: Math.round((scaled(searches) * unmetPct) / 100),
      }))
      const totalSearches = byMunicipality.reduce((sum, row) => sum + row.searches, 0)
      const unmetSearches = byMunicipality.reduce((sum, row) => sum + row.unmet, 0)
      return {
        from,
        to,
        totalSearches,
        unmetSearches,
        unmetRate: totalSearches ? Math.round((unmetSearches / totalSearches) * 10_000) / 10_000 : 0,
        topQueries,
        topUnmetQueries: UNMET.map(([query, searches]) => ({ query, searches: scaled(searches) })),
        topVehicles,
        byMunicipality,
      }
    },
  }
}
