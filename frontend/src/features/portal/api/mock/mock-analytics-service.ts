import { ApiError } from '@/lib/api'
import type { AnalyticsService } from '../analytics-service'
import type {
  AbcClass,
  AssociationReport,
  BranchBreakdown,
  CustomerSegmentCode,
  Forecast,
  ForecastWeek,
  Granularity,
  ProductAction,
  ProductInsight,
  ReportTotals,
  SegmentSummary,
  SeriesPoint,
  StoreCustomer,
  TrafficSource,
  VisibilityReport,
  XyzClass,
} from '../analytics-types'
import {
  addDays,
  datasetFor,
  hashOf,
  daysBetween,
  parseYmd,
  prng,
  segmentOf,
  SEGMENT_WINDOW_DAYS,
  startOfWeek,
  today,
  ymd,
  MOCK_DEFAULT_MIN_STOCK,
  type MockDataset,
  type MockOrder,
} from './mock-analytics-data'
import { getStore, requireCapability, requirePermission, simulateNetwork } from './mock-state'

export const SEGMENT_LABELS: Record<CustomerSegmentCode, string> = {
  NEW: 'Nuevos',
  FREQUENT: 'Frecuentes',
  AT_RISK: 'En riesgo',
  DORMANT: 'Dormidos',
  OCCASIONAL: 'Ocasionales',
}

export const SEGMENT_ORDER: CustomerSegmentCode[] = ['NEW', 'FREQUENT', 'AT_RISK', 'DORMANT', 'OCCASIONAL']

const round = (value: number, digits = 0) => {
  const factor = 10 ** digits
  return Math.round(value * factor) / factor
}

function totalsOf(orders: MockOrder[]): ReportTotals {
  const active = orders.filter((order) => !order.cancelled)
  const lines = active.flatMap((order) => order.lines)
  const revenue = lines.reduce((sum, line) => sum + line.revenue, 0)
  return {
    revenue,
    orders: active.length,
    units: lines.reduce((sum, line) => sum + line.units, 0),
    averageTicket: active.length ? Math.round(revenue / active.length) : 0,
    customers: new Set(active.map((order) => order.personId)).size,
    discounts: lines.reduce((sum, line) => sum + line.discount, 0),
    cancelledOrders: orders.length - active.length,
    cancellationRate: orders.length ? round((orders.length - active.length) / orders.length, 4) : 0,
  }
}

function inRange(orders: MockOrder[], from: Date, to: Date, branchId?: string): MockOrder[] {
  return orders.filter((order) => order.day >= from && order.day <= to && (!branchId || order.branchId === branchId))
}

function bucketStart(day: Date, granularity: Granularity): Date {
  if (granularity === 'DAY') return day
  if (granularity === 'WEEK') return startOfWeek(day)
  return new Date(day.getFullYear(), day.getMonth(), 1)
}

function nextBucket(start: Date, granularity: Granularity): Date {
  if (granularity === 'DAY') return addDays(start, 1)
  if (granularity === 'WEEK') return addDays(start, 7)
  return new Date(start.getFullYear(), start.getMonth() + 1, 1)
}

function invalidRange(): ApiError {
  return new ApiError('El rango de fechas no es válido. Elige un rango de máximo 366 días.', { status: 400, code: 'INVALID_RANGE' })
}

function guard(storeId: string) {
  const store = getStore(storeId)
  requirePermission(store, 'ANALYTICS_READ')
  requireCapability(store, 'ADVANCED_REPORTS')
  return store
}

/** Coeficiente de variación de las unidades semanales: baja = demanda estable. */
function xyzOf(weekly: number[]): XyzClass | null {
  const total = weekly.reduce((sum, value) => sum + value, 0)
  if (total === 0 || weekly.filter((value) => value > 0).length < 4) return null
  const mean = total / weekly.length
  const variance = weekly.reduce((sum, value) => sum + (value - mean) ** 2, 0) / weekly.length
  const cv = Math.sqrt(variance) / mean
  return cv < 0.5 ? 'X' : cv < 1 ? 'Y' : 'Z'
}

const PRODUCT_WINDOW_DAYS = 90

function productInsights(data: MockDataset, branchId?: string): ProductInsight[] {
  const end = today()
  const from = addDays(end, -(PRODUCT_WINDOW_DAYS - 1))
  const orders = inRange(data.orders, from, end, branchId).filter((order) => !order.cancelled)
  const weekIndex = (day: Date) => Math.min(12, Math.floor(daysBetween(from, day) / 7))
  const sales = new Map<string, { revenue: number; units: number; weekly: number[] }>()
  for (const order of orders) {
    for (const line of order.lines) {
      const entry = sales.get(line.productId) ?? { revenue: 0, units: 0, weekly: Array<number>(13).fill(0) }
      entry.revenue += line.revenue
      entry.units += line.units
      entry.weekly[weekIndex(order.day)] = (entry.weekly[weekIndex(order.day)] ?? 0) + line.units
      sales.set(line.productId, entry)
    }
  }

  const items = data.catalog
    .map((item) => ({ item, rows: item.rows.filter((row) => !branchId || row.branchId === branchId), sale: sales.get(item.productId) }))
    .filter((entry) => entry.rows.length > 0)
    .toSorted((a, b) => (b.sale?.revenue ?? 0) - (a.sale?.revenue ?? 0))
  const totalRevenue = items.reduce((sum, entry) => sum + (entry.sale?.revenue ?? 0), 0)

  let cumulative = 0
  return items.map(({ item, rows, sale }) => {
    const revenue = sale?.revenue ?? 0
    cumulative += revenue
    const share = totalRevenue ? cumulative / totalRevenue : 1
    const abcClass: AbcClass = revenue === 0 ? 'C' : share <= 0.8 ? 'A' : share <= 0.95 ? 'B' : 'C'
    const units = sale?.units ?? 0
    const stock = rows.reduce((sum, row) => sum + row.quantity, 0)
    const avgDailyUnits = round(units / PRODUCT_WINDOW_DAYS, 2)
    const coverageDays = avgDailyUnits > 0 ? Math.round(stock / avgDailyUnits) : null
    const currentMinStock = Math.max(...rows.map((row) => row.minStock ?? MOCK_DEFAULT_MIN_STOCK))
    // El mock no guarda la fecha de alta: la antigüedad sale del id del producto (60 a 360 días).
    const ageDays = 60 + (hashOf(item.productId) % 300)
    const weeklyUnits = sale?.weekly ?? []
    const meanWeekly = weeklyUnits.reduce((sum, value) => sum + value, 0) / 13
    const sdWeekly = Math.sqrt(weeklyUnits.reduce((sum, value) => sum + (value - meanWeekly) ** 2, 0) / 13)
    let action: ProductAction = 'MAINTAIN'
    if (units === 0 && stock > 0) action = ageDays > 180 ? 'LIQUIDATE' : 'NO_MOVEMENT'
    else if (abcClass === 'A' && units > 0 && (stock === 0 || (coverageDays !== null && coverageDays < 14))) action = 'PROTECT_STOCK'
    else if (abcClass === 'C' && coverageDays !== null && coverageDays > 120) action = 'REVIEW_PRICE'
    return {
      productId: item.productId,
      name: item.name,
      sku: item.sku,
      revenue,
      units,
      abcClass,
      xyzClass: sale ? xyzOf(sale.weekly) : null,
      stock,
      avgDailyUnits,
      coverageDays,
      currentMinStock,
      suggestedMinStock: avgDailyUnits > 0 ? Math.max(1, Math.ceil(avgDailyUnits * 7 + (abcClass === 'A' ? 1.65 : 1.28) * sdWeekly)) : null,
      action,
    }
  })
}

const REQUIRED_WEEKS = 8
const FORECAST_WEEKS = 5

function forecastOf(data: MockDataset, branchId?: string): Forecast {
  const orders = data.orders.filter((order) => !order.cancelled && (!branchId || order.branchId === branchId))
  const first = orders[0]?.day
  const thisWeek = startOfWeek(today())
  const weeksAvailable = first ? Math.max(0, Math.floor(daysBetween(first, thisWeek) / 7)) : 0
  const base = { weeksAvailable, weeksRequired: REQUIRED_WEEKS, method: 'Promedio móvil de 8 semanas con tendencia' }
  if (weeksAvailable < REQUIRED_WEEKS) return { ...base, status: 'INSUFFICIENT_HISTORY', nextMonth: null, weekly: [], topProducts: [] }

  const history = Math.min(26, weeksAvailable)
  const revenueByWeek = new Map<string, number>()
  const unitsByProduct = new Map<string, { units: number; revenue: number }>()
  const recentFrom = addDays(thisWeek, -7 * REQUIRED_WEEKS)
  for (const order of orders) {
    const key = ymd(startOfWeek(order.day))
    const revenue = order.lines.reduce((sum, line) => sum + line.revenue, 0)
    revenueByWeek.set(key, (revenueByWeek.get(key) ?? 0) + revenue)
    if (order.day >= recentFrom && order.day < thisWeek) {
      for (const line of order.lines) {
        const entry = unitsByProduct.get(line.productId) ?? { units: 0, revenue: 0 }
        entry.units += line.units
        entry.revenue += line.revenue
        unitsByProduct.set(line.productId, entry)
      }
    }
  }

  const weekly: ForecastWeek[] = []
  for (let back = history; back >= 1; back -= 1) {
    const start = addDays(thisWeek, -7 * back)
    weekly.push({ weekStart: ymd(start), actual: revenueByWeek.get(ymd(start)) ?? 0, forecast: null, low: null, high: null })
  }
  const recent = weekly.slice(-REQUIRED_WEEKS).map((week) => week.actual ?? 0)
  const mean = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / (values.length || 1)
  const level = mean(recent)
  const slope = (mean(recent.slice(-4)) - mean(recent.slice(0, 4))) / 4
  const deviation = Math.sqrt(mean(recent.map((value) => (value - level) ** 2)))
  const projected: ForecastWeek[] = []
  for (let step = 1; step <= FORECAST_WEEKS; step += 1) {
    const expected = Math.max(0, level + slope * (step + 1.5) * 0.6)
    const spread = 1.28 * deviation * Math.sqrt(1 + step * 0.15)
    projected.push({
      weekStart: ymd(addDays(thisWeek, 7 * (step - 1))),
      actual: null,
      forecast: Math.round(expected),
      low: Math.round(Math.max(0, expected - spread)),
      high: Math.round(expected + spread),
    })
  }

  const now = new Date()
  const target = new Date(now.getFullYear(), now.getMonth() + 1, 1)
  const daysInMonth = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  const perWeek = (key: 'forecast' | 'low' | 'high') => mean(projected.map((week) => week[key] ?? 0))
  const monthFactor = daysInMonth / 7
  const expected = Math.round(perWeek('forecast') * monthFactor)

  const baseline = (mean(recent) || 1) * monthFactor
  const scale = expected / baseline
  const topProducts = [...unitsByProduct.entries()]
    .map(([productId, entry]) => ({
      productId,
      name: data.catalog.find((item) => item.productId === productId)?.name ?? 'Producto',
      expectedUnits: Math.max(1, Math.round((entry.units / REQUIRED_WEEKS) * monthFactor * scale)),
      expectedRevenue: Math.round((entry.revenue / REQUIRED_WEEKS) * monthFactor * scale),
    }))
    .toSorted((a, b) => b.expectedRevenue - a.expectedRevenue)
    .slice(0, 5)

  return {
    ...base,
    status: 'OK',
    nextMonth: {
      month: `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}`,
      expected,
      low: Math.round(perWeek('low') * monthFactor),
      high: Math.round(perWeek('high') * monthFactor),
    },
    weekly: [...weekly, ...projected],
    topProducts,
  }
}

/** Reportes simulados con el mismo contrato que el servidor: permiso, capability, rango válido y estados vacíos. */
export function createMockAnalyticsService(): AnalyticsService {
  return {
    async report(storeId, query) {
      await simulateNetwork(550)
      const store = guard(storeId)
      const from = parseYmd(query.from)
      const to = parseYmd(query.to)
      if (!from || !to || to < from || daysBetween(from, to) + 1 > 366) throw invalidRange()
      const data = datasetFor(store)
      const length = daysBetween(from, to) + 1
      const current = inRange(data.orders, from, to, query.branchId)
      const previousTo = addDays(from, -1)
      const previous = inRange(data.orders, addDays(previousTo, -(length - 1)), previousTo, query.branchId)

      const series: SeriesPoint[] = []
      const index = new Map<string, SeriesPoint>()
      for (let start = bucketStart(from, query.granularity); start <= to; start = nextBucket(start, query.granularity)) {
        const point = { periodStart: ymd(start), revenue: 0, orders: 0, units: 0 }
        series.push(point)
        index.set(point.periodStart, point)
      }
      const active = current.filter((order) => !order.cancelled)
      for (const order of active) {
        const point = index.get(ymd(bucketStart(order.day, query.granularity)))
        if (!point) continue
        point.orders += 1
        for (const line of order.lines) {
          point.revenue += line.revenue
          point.units += line.units
        }
      }

      const byBranch: BranchBreakdown[] = store.branches
        .filter((branch) => !query.branchId || branch.id === query.branchId)
        .map((branch) => {
          const own = active.filter((order) => order.branchId === branch.id)
          const lines = own.flatMap((order) => order.lines)
          return {
            branchId: branch.id,
            branchName: branch.name,
            revenue: lines.reduce((sum, line) => sum + line.revenue, 0),
            orders: own.length,
            units: lines.reduce((sum, line) => sum + line.units, 0),
          }
        })

      const perProduct = new Map<string, { revenue: number; units: number }>()
      for (const line of active.flatMap((order) => order.lines)) {
        const entry = perProduct.get(line.productId) ?? { revenue: 0, units: 0 }
        entry.revenue += line.revenue
        entry.units += line.units
        perProduct.set(line.productId, entry)
      }
      const catalog = new Map(data.catalog.map((item) => [item.productId, item]))
      const perCategory = new Map<string, { categoryCode: string; revenue: number; units: number }>()
      for (const [productId, entry] of perProduct) {
        const item = catalog.get(productId)
        if (!item) continue
        const category = perCategory.get(item.categoryId) ?? { categoryCode: item.categoryCode, revenue: 0, units: 0 }
        category.revenue += entry.revenue
        category.units += entry.units
        perCategory.set(item.categoryId, category)
      }

      return {
        from: query.from,
        to: query.to,
        granularity: query.granularity,
        totals: totalsOf(current),
        previous: totalsOf(previous),
        series,
        byBranch,
        byCategory: [...perCategory.entries()]
          .map(([categoryId, entry]) => ({ categoryId, ...entry }))
          .toSorted((a, b) => b.revenue - a.revenue),
        topProducts: [...perProduct.entries()]
          .map(([productId, entry]) => ({
            productId,
            name: catalog.get(productId)?.name ?? 'Producto',
            sku: catalog.get(productId)?.sku ?? null,
            ...entry,
          }))
          .toSorted((a, b) => b.revenue - a.revenue)
          .slice(0, 10),
      }
    },

    async customers(storeId, segment) {
      await simulateNetwork(500)
      const store = guard(storeId)
      const data = datasetFor(store)
      const end = today()
      const since = addDays(end, -(SEGMENT_WINDOW_DAYS - 1))
      const people = new Map(data.people.map((person) => [person.personId, person]))
      const perPerson = new Map<string, MockOrder[]>()
      for (const order of data.orders) {
        if (order.cancelled || order.day < since) continue
        perPerson.set(order.personId, [...(perPerson.get(order.personId) ?? []), order])
      }
      const customers: StoreCustomer[] = [...perPerson.entries()].map(([personId, orders]) => {
        const revenue = orders.reduce((sum, order) => sum + order.lines.reduce((inner, line) => inner + line.revenue, 0), 0)
        const first = orders[0]!
        const last = orders[orders.length - 1]!
        const daysSinceLastOrder = daysBetween(last.day, end)
        return {
          personId,
          name: people.get(personId)?.name ?? 'Cliente',
          orders: orders.length,
          revenue,
          averageTicket: Math.round(revenue / orders.length),
          firstOrderAt: first.at,
          lastOrderAt: last.at,
          daysSinceLastOrder,
          segment: segmentOf(orders.length, daysBetween(first.day, end), daysSinceLastOrder),
        }
      })
      const segments: SegmentSummary[] = SEGMENT_ORDER.map((code) => {
        const own = customers.filter((customer) => customer.segment === code)
        return { code, label: SEGMENT_LABELS[code], customers: own.length, revenue: own.reduce((sum, customer) => sum + customer.revenue, 0) }
      })
      return {
        asOf: ymd(end),
        windowDays: SEGMENT_WINDOW_DAYS,
        segments,
        customers: customers.filter((customer) => !segment || customer.segment === segment).toSorted((a, b) => b.revenue - a.revenue),
      }
    },

    async products(storeId, branchId) {
      await simulateNetwork(500)
      const store = guard(storeId)
      return { windowDays: PRODUCT_WINDOW_DAYS, products: productInsights(datasetFor(store), branchId) }
    },

    async forecast(storeId, branchId) {
      await simulateNetwork(500)
      const store = guard(storeId)
      return forecastOf(datasetFor(store), branchId)
    },

    async associations(storeId) {
      await simulateNetwork(500)
      const store = guard(storeId)
      return associationsOf(datasetFor(store))
    },

    async visibility(storeId, range) {
      await simulateNetwork(450)
      // Como el servidor: solo `ANALYTICS_READ`, sin la capability del plan.
      const store = getStore(storeId)
      requirePermission(store, 'ANALYTICS_READ')
      const from = parseYmd(range.from)
      const to = parseYmd(range.to)
      if (!from || !to || to < from || daysBetween(from, to) + 1 > 366) throw invalidRange()
      return visibilityOf(datasetFor(store), store.summary.storeId, from, to)
    },
  }
}

const VISIBILITY_SOURCES: [TrafficSource, number][] = [
  ['WHATSAPP', 0.34],
  ['GOOGLE', 0.22],
  ['INTERNAL', 0.18],
  ['DIRECT', 0.1],
  ['FACEBOOK', 0.07],
  ['CHATGPT', 0.04],
  ['TIKTOK', 0.03],
  ['OTHER', 0.02],
]

/** Visitas simuladas y deterministas por día (datos de demostración del modo `mock`). */
function visibilityOf(dataset: MockDataset, storeId: string, from: Date, to: Date): VisibilityReport {
  const days = daysBetween(from, to) + 1
  const daily: VisibilityReport['daily'] = []
  for (let index = 0; index < days; index += 1) {
    const day = addDays(from, index)
    const rand = prng(hashOf(`${storeId}:${ymd(day)}`))
    daily.push({ day: ymd(day), profileViews: Math.round(rand() * 14), productViews: Math.round(10 + rand() * 45) })
  }
  const profileViews = daily.reduce((sum, row) => sum + row.profileViews, 0)
  const productViews = daily.reduce((sum, row) => sum + row.productViews, 0)
  const views = profileViews + productViews
  const shares = Math.round(views * 0.03)
  const bySource = VISIBILITY_SOURCES.map(([source, weight]) => ({
    source,
    views: Math.round(views * weight),
    visitors: Math.round(views * weight * 0.7),
    shares: source === 'INTERNAL' || source === 'DIRECT' || source === 'GOOGLE' || source === 'CHATGPT' || source === 'OTHER' ? 0 : Math.round(shares * weight * 2),
  }))
  const rand = prng(hashOf(`${storeId}:top`))
  const topProducts = dataset.catalog
    .slice(0, 8)
    .map((item) => {
      const itemViews = Math.round(productViews * (0.04 + rand() * 0.12))
      return { productId: item.productId, name: item.name, views: itemViews, visitors: Math.round(itemViews * 0.72), shares: Math.round(itemViews * 0.04) }
    })
    .toSorted((a, b) => b.views - a.views)
  return {
    from: ymd(from),
    to: ymd(to),
    profileViews,
    profileVisitors: Math.round(profileViews * 0.75),
    productViews,
    productVisitors: Math.round(productViews * 0.7),
    shares,
    bySource,
    topProducts,
    daily,
  }
}

/** Pares a partir de los pedidos simulados (soporte, confianza y lift reales sobre el historial sintético). */
function associationsOf(dataset: MockDataset): AssociationReport {
  const orders = dataset.orders.filter((order) => !order.cancelled && order.lines.length > 1)
  if (orders.length < 10) return { computedAt: null, source: null, pairs: [], suggestedCombo: null }
  const all = dataset.orders.filter((order) => !order.cancelled)
  const names = new Map(dataset.catalog.map((item) => [item.productId, item.name]))
  const single = new Map<string, number>()
  for (const order of all) for (const line of order.lines) single.set(line.productId, (single.get(line.productId) ?? 0) + 1)
  const together = new Map<string, number>()
  for (const order of orders) {
    const ids = order.lines.map((line) => line.productId).toSorted()
    for (let i = 0; i < ids.length; i += 1) {
      for (let j = i + 1; j < ids.length; j += 1) together.set(`${ids[i]}|${ids[j]}`, (together.get(`${ids[i]}|${ids[j]}`) ?? 0) + 1)
    }
  }
  const total = all.length
  const pairs = [...together.entries()]
    .filter(([, count]) => count >= 3)
    .map(([key, count]) => {
      const [a = '', b = ''] = key.split('|')
      const countA = single.get(a) ?? 1
      const countB = single.get(b) ?? 1
      return {
        antecedent: { productId: a, name: names.get(a) ?? 'Producto' },
        consequent: { productId: b, name: names.get(b) ?? 'Producto' },
        support: round(count / total, 3),
        confidence: round(count / countA, 2),
        lift: round(count / total / ((countA / total) * (countB / total)), 2),
        orders: count,
      }
    })
    .filter((pair) => pair.lift > 1)
    .toSorted((x, y) => y.lift - x.lift || y.orders - x.orders)
    .slice(0, 10)
  const top = pairs[0]
  const night = new Date()
  night.setHours(3, 0, 0, 0)
  return {
    computedAt: night.toISOString(),
    source: 'ORDERS',
    pairs,
    suggestedCombo: top
      ? {
          productIds: [top.antecedent.productId, top.consequent.productId],
          names: [top.antecedent.name, top.consequent.name],
          reason: `Se compraron juntos en ${top.orders} pedidos y la probabilidad de llevarlos juntos es ${top.lift.toString().replace('.', ',')} veces la normal.`,
        }
      : null,
  }
}
