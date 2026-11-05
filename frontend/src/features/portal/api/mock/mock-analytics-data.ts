import { DEFAULT_MIN_STOCK } from '../types'
import type { CustomerSegmentCode } from '../analytics-types'
import { MOCK_OPTIONS, type MockStore } from './mock-state'

/**
 * Historial de ventas sintético y determinista para los reportes de inteligencia comercial simulados.
 * Se arma con el catálogo, las sucursales y las existencias reales del almacén simulado, de modo que
 * los reportes sean coherentes con Inventario (mismos productos, SKU, sucursales y mínimos).
 */

export const DAY_MS = 86_400_000

/** PRNG determinista (mulberry32). */
export function prng(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export const hashOf = (text: string) => [...text].reduce((sum, char) => (sum * 31 + char.charCodeAt(0)) >>> 0, 7)

const pad = (value: number) => String(value).padStart(2, '0')

/** `YYYY-MM-DD` en hora local. */
export function ymd(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** Medianoche local del día `YYYY-MM-DD`, o `undefined` si no es una fecha válida. */
export function parseYmd(value: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return undefined
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return ymd(date) === value ? date : undefined
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

export function today(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

/** Lunes de la semana de `date`. */
export function startOfWeek(date: Date): Date {
  return addDays(date, -((date.getDay() + 6) % 7))
}

export function daysBetween(from: Date, to: Date): number {
  return Math.round((to.getTime() - from.getTime()) / DAY_MS)
}

export interface MockLine {
  productId: string
  units: number
  /** Importe neto de la línea (ya con descuento). */
  revenue: number
  discount: number
}

export interface MockOrder {
  day: Date
  at: string
  branchId: string
  personId: string
  cancelled: boolean
  lines: MockLine[]
}

export interface MockCatalogItem {
  productId: string
  name: string
  sku: string | null
  categoryId: string
  categoryCode: string
  price: number
  /** Existencias y mínimo por sucursal (referencia viva a las filas del almacén simulado). */
  rows: { branchId: string; quantity: number; minStock: number | null | undefined }[]
}

export interface MockPerson {
  personId: string
  name: string
  email: string
  marketingEmail: boolean
  marketingWhatsapp: boolean
}

export interface MockDataset {
  orders: MockOrder[]
  catalog: MockCatalogItem[]
  people: MockPerson[]
  firstOrderDay: Date | undefined
}

const FIRST = ['Camila', 'Andrés', 'Laura', 'Sebastián', 'Diana', 'Julián', 'Paola', 'Carlos', 'Valentina', 'Mateo', 'Daniela', 'Santiago', 'Natalia', 'Felipe', 'Juliana', 'Esteban', 'Luisa', 'Camilo', 'Mariana', 'Jorge']
const LAST = ['Rodríguez', 'Gómez', 'Pineda', 'Ramírez', 'Cárdenas', 'Mora', 'Vega', 'Ospina', 'Cifuentes', 'Zapata', 'Herrera', 'Londoño', 'Quintero', 'Bedoya', 'Salazar']

const noAccents = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

export function makePeople(count: number, seed: number): MockPerson[] {
  const rand = prng(seed)
  return Array.from({ length: count }, (_, index) => {
    const first = FIRST[index % FIRST.length] ?? 'Cliente'
    const last = LAST[Math.floor(index / FIRST.length + rand() * 3) % LAST.length] ?? ''
    return {
      personId: `00000000-0000-4000-8000-${String(seed % 1000).padStart(3, '0')}${String(index).padStart(9, '0')}`,
      name: `${first} ${last}`,
      email: `${noAccents(first)}.${noAccents(last)}${index}@correo.co`,
      marketingEmail: rand() > 0.35,
      marketingWhatsapp: rand() > 0.55,
    }
  })
}

const datasets = new Map<string, MockDataset>()

/** `?simulateHistory=short` deja solo 5 semanas de historial para ver `INSUFFICIENT_HISTORY`. */
export function shortHistoryRequested(): boolean {
  return new URLSearchParams(window.location.search).get('simulateHistory') === 'short'
}

export function datasetFor(store: MockStore): MockDataset {
  const short = shortHistoryRequested()
  const key = `${store.summary.storeId}:${short ? 'short' : 'full'}`
  const cached = datasets.get(key)
  // El catálogo se vuelve a leer en cada consulta (las filas son las vivas), las ventas no cambian.
  const catalog = buildCatalog(store)
  if (cached) return { ...cached, catalog }

  const history = short ? 35 : 430
  const rand = prng(hashOf(key))
  const now = new Date()
  const branches = store.branches.filter((branch) => branch.state === 'ACTIVE')
  const people = makePeople(48, hashOf(store.summary.storeId))
  // Cada cliente compra entre `startBack` y `stopBack` (días atrás): hay nuevos, constantes, en riesgo y dormidos.
  const lifecycle = people.map((_, index) => {
    const roll = rand()
    const stopBack = roll < 0.22 ? 181 + Math.floor(rand() * 200) : roll < 0.4 ? 61 + Math.floor(rand() * 115) : 0
    const startBack =
      index % 6 === 0 ? Math.floor(rand() * Math.min(history, 28)) : Math.min(history - 1, stopBack + 25 + Math.floor(rand() * history))
    return { startBack, stopBack: Math.min(stopBack, startBack), weight: 0.15 + rand() * rand() * 3 }
  })

  const orders: MockOrder[] = []
  if (catalog.length > 0 && branches.length > 0) {
    for (let back = history - 1; back >= 0; back -= 1) {
      const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() - back)
      const weekday = day.getDay()
      const growth = 1 + (history - back) / (history * 2.2)
      const expected = (weekday === 0 ? 1.1 : weekday === 6 ? 2 : 2.6) * (day.getMonth() === 11 ? 1.4 : 1) * growth
      const perDay = Math.floor(rand() * expected * 2 + rand())
      const eligible = people.map((person, index) => ({ person, ...lifecycle[index]! })).filter((item) => back <= item.startBack && back >= item.stopBack)
      if (eligible.length === 0) continue
      const total = eligible.reduce((sum, item) => sum + item.weight, 0)
      for (let index = 0; index < perDay; index += 1) {
        let pick = rand() * total
        const customer = eligible.find((item) => (pick -= item.weight) <= 0) ?? eligible[0]!
        const branch = branches[Math.floor(rand() * branches.length)]!
        const listed = catalog.filter((item) => item.rows.some((row) => row.branchId === branch.id))
        if (listed.length === 0) continue
        const lines: MockLine[] = []
        for (let line = 0, count = 1 + Math.floor(rand() * 3); line < count; line += 1) {
          // Zipf: los primeros productos del catálogo rotan mucho más que los últimos.
          const rank = Math.min(listed.length - 1, Math.floor(listed.length * rand() ** 2.2))
          const item = listed[rank]!
          if (lines.some((existing) => existing.productId === item.productId)) continue
          const units = 1 + Math.floor(rand() * rand() * 3)
          lines.push({ productId: item.productId, units, revenue: item.price * units, discount: 0 })
        }
        const hour = 8 + Math.floor(rand() * 11)
        orders.push({
          day,
          at: new Date(day.getFullYear(), day.getMonth(), day.getDate(), hour, Math.floor(rand() * 60)).toISOString(),
          branchId: branch.id,
          personId: customer.person.personId,
          cancelled: rand() < 0.06,
          lines,
        })
      }
    }
  }
  const dataset: MockDataset = { orders, catalog, people, firstOrderDay: orders[0]?.day }
  datasets.set(key, dataset)
  return dataset
}

function buildCatalog(store: MockStore): MockCatalogItem[] {
  const items = new Map<string, MockCatalogItem>()
  for (const row of store.products) {
    const existing = items.get(row.id)
    const entry = { branchId: row.branchId, get quantity() { return row.quantity }, get minStock() { return row.minStock } }
    if (existing) existing.rows.push(entry)
    else {
      items.set(row.id, {
        productId: row.id,
        name: row.name,
        sku: row.sku,
        categoryId: row.categoryId,
        categoryCode: MOCK_OPTIONS.categories.find((category) => category.id === row.categoryId)?.code ?? 'OTROS',
        price: row.effectivePrice,
        rows: [entry],
      })
    }
  }
  return [...items.values()]
}

export const MOCK_DEFAULT_MIN_STOCK = DEFAULT_MIN_STOCK

// ------------------------------------------------------------------ segmentos

export const SEGMENT_WINDOW_DAYS = 365

/** Reglas simuladas: ver `SEGMENT_RULES` en la pantalla de Clientes para el texto que ve la persona. */
export function segmentOf(orders: number, firstDaysAgo: number, lastDaysAgo: number): CustomerSegmentCode {
  if (lastDaysAgo > 180) return 'DORMANT'
  if (orders >= 2 && lastDaysAgo > 60) return 'AT_RISK'
  if (orders === 1 && firstDaysAgo <= 30) return 'NEW'
  if (orders >= 3 && lastDaysAgo <= 60) return 'FREQUENT'
  return 'OCCASIONAL'
}
